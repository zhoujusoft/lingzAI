package lingzhou.agent.backend.business.channel.adapter.wecom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lingzhou.agent.backend.business.channel.adapter.AbstractChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public class WeComChannelAdapter extends AbstractChannelAdapter {

    private static final String CHANNEL_TYPE = "wecom";
    private static final String DEFAULT_WS_URL = "wss://openws.work.weixin.qq.com";
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final int MAX_MISSED_HEARTBEATS = 2;
    private static final long REPLY_FRAME_TTL_MS = 10 * 60 * 1000L;
    private static final long REPLY_CONTEXT_RETENTION_MS = 24 * 60 * 60 * 1000L;
    private static final long TYPING_CONTEXT_RETENTION_MS = 5 * 60 * 1000L;
    private static final String TYPING_PLACEHOLDER_TEXT = "思考中...";

    private static final String CMD_SUBSCRIBE = "aibot_subscribe";
    private static final String CMD_HEARTBEAT = "ping";
    private static final String CMD_RESPONSE = "aibot_respond_msg";
    private static final String CMD_RESPONSE_WELCOME = "aibot_respond_welcome_msg";
    private static final String CMD_SEND_MSG = "aibot_send_msg";
    private static final String CMD_UPLOAD_INIT = "aibot_upload_media_init";
    private static final String CMD_UPLOAD_CHUNK = "aibot_upload_media_chunk";
    private static final String CMD_UPLOAD_FINISH = "aibot_upload_media_finish";
    private static final String CMD_MSG_CALLBACK = "aibot_msg_callback";
    private static final String CMD_EVENT_CALLBACK = "aibot_event_callback";
    private static final int UPLOAD_CHUNK_SIZE = 512 * 1024;
    private static final long UPLOAD_ACK_TIMEOUT_MS = 30_000L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<Long, RuntimeState> runtimes = new ConcurrentHashMap<>();
    private final ChannelUserBindingService channelUserBindingService;
    private final ChatFileService chatFileService;
    private final HttpClient mediaHttpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final Semaphore uploadLock = new Semaphore(1);

    public WeComChannelAdapter(
            ChannelConfig channelConfig,
            ChannelMessageRouter messageRouter,
            ChannelUserBindingService channelUserBindingService) {
        this(channelConfig, messageRouter, channelUserBindingService, null);
    }

    public WeComChannelAdapter(
            ChannelConfig channelConfig,
            ChannelMessageRouter messageRouter,
            ChannelUserBindingService channelUserBindingService,
            ChatFileService chatFileService) {
        super(channelConfig, messageRouter);
        this.channelUserBindingService = channelUserBindingService;
        this.chatFileService = chatFileService;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void sendMessage(String targetId, String content) {
        sendMessage(null, targetId, content);
    }

    @Override
    public void sendMessage(Long ownerUserId, String targetId, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        RuntimeState runtime = resolveRuntimeForSend(ownerUserId);
        TypingContext typingContext = resolveAndConsumeTypingContext(runtime, targetId);
        if (isTypingContextUsable(typingContext)
                && sendReplyStream(runtime, typingContext.frameReqId(), typingContext.streamId(), content, true)) {
            return;
        }
        ReplyContext replyContext = resolveReplyContext(runtime, targetId);
        if (isReplyFrameUsable(replyContext) && sendReplyMessage(runtime, replyContext.frameReqId(), content)) {
            return;
        }
        sendMessageToChat(runtime, resolveFallbackTarget(targetId, replyContext), content);
    }

    @Override
    public boolean supportsFileMessage() {
        return true;
    }

    @Override
    public void sendFileMessage(
            Long ownerUserId, String targetId, byte[] fileBytes, String fileName, String caption) {
        if (!StringUtils.hasText(targetId) || fileBytes == null || fileBytes.length == 0) {
            return;
        }
        RuntimeState runtime = resolveRuntimeForSend(ownerUserId);
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        String mediaId = uploadMedia(runtime, fileBytes, resolvedFileName, "file");
        if (!StringUtils.hasText(mediaId)) {
            throw new IllegalStateException("企业微信文件上传失败");
        }
        TypingContext typingContext = resolveAndConsumeTypingContext(runtime, targetId);
        ReplyContext replyContext = resolveReplyContext(runtime, targetId);
        String frameReqId = isTypingContextUsable(typingContext)
                ? typingContext.frameReqId()
                : (isReplyFrameUsable(replyContext) ? replyContext.frameReqId() : null);
        sendMediaMessage(runtime, resolveFallbackTarget(targetId, replyContext), mediaId, "file", frameReqId);
        if (StringUtils.hasText(caption)) {
            sendMessage(ownerUserId, targetId, caption);
        }
    }

    @Override
    public void startTyping(Long ownerUserId, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return;
        }
        RuntimeState runtime = resolveRuntimeForSend(ownerUserId);
        ReplyContext replyContext = resolveReplyContext(runtime, targetId);
        if (!isReplyFrameUsable(replyContext)) {
            return;
        }
        String streamId = generateReqId(runtime, "typing_stream");
        if (sendReplyStream(runtime, replyContext.frameReqId(), streamId, TYPING_PLACEHOLDER_TEXT, false)) {
            runtime.typingContexts.put(
                    targetId,
                    new TypingContext(
                            replyContext.frameReqId(), streamId, TYPING_PLACEHOLDER_TEXT, System.currentTimeMillis()));
            return;
        }
        logger.debug(
                "企业微信发送 typing 占位消息失败，channelId={}, ownerUserId={}, targetId={}",
                channelConfig.getId(),
                runtime.ownerUserId,
                targetId);
    }

    @Override
    public void stopTyping(Long ownerUserId, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return;
        }
        RuntimeState runtime = resolveRuntimeForSend(ownerUserId);
        TypingContext typingContext = resolveAndConsumeTypingContext(runtime, targetId);
        if (!isTypingContextUsable(typingContext)) {
            return;
        }
        if (!sendReplyStream(
                runtime, typingContext.frameReqId(), typingContext.streamId(), typingContext.placeholderText(), true)) {
            logger.debug(
                    "企业微信结束 typing 状态失败：channelId={}, ownerUserId={}, targetId={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    targetId);
        }
    }

    @Override
    protected void doStart() {
        List<RuntimeCredential> credentials = loadStartupCredentials();
        if (credentials.isEmpty()) {
            throw new IllegalArgumentException("当前渠道暂无可用企业微信登录态，请先在任一账号下扫码绑定");
        }
        for (RuntimeCredential credential : credentials) {
            ensureRuntime(credential.ownerUserId(), credential.botId(), credential.secret());
        }
    }

    @Override
    protected void doStop() {
        for (RuntimeState runtime : List.copyOf(runtimes.values())) {
            stopRuntime(runtime, "channel stopping");
        }
        runtimes.clear();
    }

    public synchronized void switchActiveOwner(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少可用的 ownerUserId");
        }
        ChannelUserBindingService.WecomCredential credential =
                channelUserBindingService.getWecomCredential(channelConfig.getId(), ownerUserId);
        if (credential == null
                || !StringUtils.hasText(credential.botId())
                || !StringUtils.hasText(credential.secret())) {
            throw new IllegalArgumentException("当前账号未绑定企业微信登录态，请在该账号下重新扫码绑定");
        }
        ensureRuntime(ownerUserId, credential.botId(), credential.secret());
    }

    @Override
    public synchronized void disconnectUser(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return;
        }
        RuntimeState runtime = runtimes.remove(ownerUserId);
        if (runtime != null) {
            stopRuntime(runtime, "user disconnected");
        }
    }

    public Map<String, Object> getRuntimeStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtimeCount", runtimes.size());
        List<Map<String, Object>> items = new ArrayList<>();
        for (RuntimeState runtime : List.copyOf(runtimes.values())) {
            items.add(toRuntimeStatus(runtime));
        }
        result.put("runtimes", items);
        return result;
    }

    public Map<String, Object> getRuntimeStatus(Long ownerUserId) {
        RuntimeState runtime = ownerUserId == null ? null : runtimes.get(ownerUserId);
        if (runtime == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "NOT_STARTED");
            result.put("connected", Boolean.FALSE);
            result.put("authenticated", Boolean.FALSE);
            result.put("running", isRunning());
            result.put("ownerUserId", ownerUserId);
            result.put("lastError", null);
            result.put("lastHeartbeatAckAt", null);
            result.put("lastMessageAt", null);
            result.put("reconnectAttempts", 0);
            return result;
        }
        return toRuntimeStatus(runtime);
    }

    private List<RuntimeCredential> loadStartupCredentials() {
        Map<Long, RuntimeCredential> dedup = new LinkedHashMap<>();
        for (ChannelUserBinding binding : channelUserBindingService.listWithRuntime(channelConfig.getId())) {
            if (binding.getOwnerUserId() == null || binding.getOwnerUserId() <= 0) {
                continue;
            }
            ChannelUserBindingService.WecomCredential credential =
                    channelUserBindingService.getWecomCredential(channelConfig.getId(), binding.getOwnerUserId());
            if (credential == null
                    || !StringUtils.hasText(credential.botId())
                    || !StringUtils.hasText(credential.secret())) {
                continue;
            }
            dedup.put(
                    binding.getOwnerUserId(),
                    new RuntimeCredential(
                            binding.getOwnerUserId(),
                            credential.botId().trim(),
                            credential.secret().trim()));
        }
        if (dedup.isEmpty()) {
            Long ownerUserId = channelConfig.getOwnerUserId();
            if (ownerUserId != null && ownerUserId > 0) {
                ChannelUserBindingService.WecomCredential credential =
                        channelUserBindingService.getWecomCredential(channelConfig.getId(), ownerUserId);
                if (credential != null
                        && StringUtils.hasText(credential.botId())
                        && StringUtils.hasText(credential.secret())) {
                    dedup.put(
                            ownerUserId,
                            new RuntimeCredential(
                                    ownerUserId,
                                    credential.botId().trim(),
                                    credential.secret().trim()));
                }
            }
        }
        return new ArrayList<>(dedup.values());
    }

    private synchronized RuntimeState ensureRuntime(Long ownerUserId, String botId, String secret) {
        RuntimeState existing = runtimes.get(ownerUserId);
        String normalizedBotId = botId == null ? null : botId.trim();
        String normalizedSecret = secret == null ? null : secret.trim();
        if (existing != null
                && existing.botId.equals(normalizedBotId)
                && existing.secret.equals(normalizedSecret)
                && existing.scheduler != null) {
            return existing;
        }
        RuntimeState runtime = new RuntimeState(ownerUserId, normalizedBotId, normalizedSecret);
        RuntimeState previous = runtimes.put(ownerUserId, runtime);
        if (previous != null) {
            stopRuntime(previous, "replace runtime");
        }
        startRuntime(runtime);
        return runtime;
    }

    private void startRuntime(RuntimeState runtime) {
        if (!running.get()) {
            return;
        }
        runtime.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        runtime.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "channel-wecom-" + channelConfig.getId() + "-" + runtime.ownerUserId);
            thread.setDaemon(true);
            return thread;
        });
        runtime.lastError = null;
        runtime.authenticated = false;
        runtime.lastHeartbeatAckAt = 0L;
        runtime.lastMessageAt = 0L;
        runtime.reconnectAttempts.set(0);
        runtime.missedHeartbeats.set(0);
        runtime.reconnectScheduled.set(false);
        runtime.status = "CONNECTING";
        logger.info("企业微信渠道初始化账号运行态：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
        connectWebSocket(runtime);
    }

    private void stopRuntime(RuntimeState runtime, String reason) {
        runtime.status = "STOPPED";
        runtime.authenticated = false;
        runtime.reconnectScheduled.set(false);
        runtime.missedHeartbeats.set(0);
        runtime.reconnectAttempts.set(0);
        runtime.replyContexts.clear();
        runtime.typingContexts.clear();
        cancelHeartbeatTask(runtime);
        closeWebSocketQuietly(runtime, reason);
        ScheduledExecutorService scheduler = runtime.scheduler;
        runtime.scheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        runtime.httpClient = null;
    }

    private RuntimeState resolveRuntimeForSend(Long ownerUserId) {
        if (ownerUserId != null && ownerUserId > 0) {
            RuntimeState runtime = runtimes.get(ownerUserId);
            if (runtime != null) {
                return runtime;
            }
            switchActiveOwner(ownerUserId);
            runtime = runtimes.get(ownerUserId);
            if (runtime != null) {
                return runtime;
            }
            throw new IllegalStateException("当前账号企业微信运行态不可用");
        }
        RuntimeState authenticatedRuntime = runtimes.values().stream()
                .filter(runtime -> runtime.authenticated && runtime.webSocket != null)
                .findFirst()
                .orElse(null);
        if (authenticatedRuntime != null) {
            return authenticatedRuntime;
        }
        RuntimeState anyRuntime = runtimes.values().stream().findFirst().orElse(null);
        if (anyRuntime != null) {
            return anyRuntime;
        }
        throw new IllegalStateException("当前渠道暂无可用企业微信运行态");
    }

    private Map<String, Object> toRuntimeStatus(RuntimeState runtime) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerUserId", runtime.ownerUserId);
        result.put("status", runtime.status);
        result.put("connected", runtime.webSocket != null);
        result.put("authenticated", runtime.authenticated);
        result.put("running", isRunning());
        result.put("lastError", runtime.lastError);
        result.put("lastHeartbeatAckAt", runtime.lastHeartbeatAckAt <= 0 ? null : runtime.lastHeartbeatAckAt);
        result.put("lastMessageAt", runtime.lastMessageAt <= 0 ? null : runtime.lastMessageAt);
        result.put("reconnectAttempts", runtime.reconnectAttempts.get());
        return result;
    }

    private void connectWebSocket(RuntimeState runtime) {
        if (!running.get() || !isCurrentRuntime(runtime)) {
            return;
        }
        String wsUrl = getString("ws_url", DEFAULT_WS_URL);
        runtime.status = "CONNECTING";
        HttpClient client = runtime.httpClient;
        if (client == null) {
            runtime.lastError = "企业微信 HTTP 客户端未初始化";
            runtime.status = "ERROR";
            return;
        }
        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .buildAsync(URI.create(wsUrl), new WeComWebSocketListener(runtime))
                .whenComplete((socket, throwable) -> {
                    if (!running.get() || !isCurrentRuntime(runtime)) {
                        if (socket != null) {
                            try {
                                socket.sendClose(WebSocket.NORMAL_CLOSURE, "adapter stopped");
                            } catch (Exception ignored) {
                                // no-op
                            }
                        }
                        return;
                    }
                    if (throwable != null) {
                        handleDisconnected(runtime, "WebSocket 连接失败: " + throwable.getMessage(), true);
                        return;
                    }
                    runtime.webSocket = socket;
                    runtime.status = "AUTHENTICATING";
                    runtime.lastError = null;
                    runtime.missedHeartbeats.set(0);
                    runtime.reconnectScheduled.set(false);
                    sendAuthFrame(runtime);
                });
    }

    private void sendAuthFrame(RuntimeState runtime) {
        if (!StringUtils.hasText(runtime.botId) || !StringUtils.hasText(runtime.secret)) {
            throw new IllegalStateException("企业微信登录态缺失，请重新扫码绑定");
        }
        String reqId = generateReqId(runtime, CMD_SUBSCRIBE);
        Map<String, Object> frame = Map.of(
                "cmd",
                CMD_SUBSCRIBE,
                "headers",
                Map.of("req_id", reqId),
                "body",
                Map.of("bot_id", runtime.botId, "secret", runtime.secret));
        sendFrame(runtime, frame);
        logger.info(
                "企业微信渠道发送鉴权请求：channelId={}, ownerUserId={}, status={}",
                channelConfig.getId(),
                runtime.ownerUserId,
                runtime.status);
    }

    private void startHeartbeatTask(RuntimeState runtime) {
        ScheduledExecutorService scheduler = runtime.scheduler;
        if (scheduler == null || !isCurrentRuntime(runtime)) {
            return;
        }
        cancelHeartbeatTask(runtime);
        runtime.heartbeatFuture = scheduler.scheduleAtFixedRate(
                () -> sendHeartbeatSafely(runtime),
                HEARTBEAT_INTERVAL_MS,
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeatSafely(RuntimeState runtime) {
        if (!running.get() || !isCurrentRuntime(runtime) || runtime.webSocket == null) {
            return;
        }
        if (runtime.missedHeartbeats.get() >= MAX_MISSED_HEARTBEATS) {
            handleDisconnected(runtime, "企业微信心跳超时", true);
            return;
        }
        runtime.missedHeartbeats.incrementAndGet();
        sendFrame(
                runtime,
                Map.of("cmd", CMD_HEARTBEAT, "headers", Map.of("req_id", generateReqId(runtime, CMD_HEARTBEAT))));
    }

    private boolean sendReplyMessage(RuntimeState runtime, String frameReqId, String content) {
        if (!StringUtils.hasText(frameReqId) || runtime.webSocket == null) {
            return false;
        }
        Map<String, Object> frame = Map.of(
                "cmd",
                CMD_RESPONSE,
                "headers",
                Map.of("req_id", frameReqId),
                "body",
                Map.of("msgtype", "markdown", "markdown", Map.of("content", content)));
        return sendFrame(runtime, frame);
    }

    private boolean sendReplyStream(
            RuntimeState runtime, String frameReqId, String streamId, String content, boolean finish) {
        if (!StringUtils.hasText(frameReqId)
                || !StringUtils.hasText(streamId)
                || !StringUtils.hasText(content)
                || runtime.webSocket == null) {
            return false;
        }
        Map<String, Object> streamBody = new LinkedHashMap<>();
        streamBody.put("id", streamId);
        streamBody.put("content", content);
        streamBody.put("finish", finish);
        Map<String, Object> frame = Map.of(
                "cmd",
                CMD_RESPONSE,
                "headers",
                Map.of("req_id", frameReqId),
                "body",
                Map.of("msgtype", "stream", "stream", streamBody));
        return sendFrame(runtime, frame);
    }

    private void sendMessageToChat(RuntimeState runtime, String targetId, String content) {
        if (!StringUtils.hasText(targetId)) {
            logger.warn("企业微信发送失败，缺少目标标识：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
            return;
        }
        Map<String, Object> frame = Map.of(
                "cmd",
                CMD_SEND_MSG,
                "headers",
                Map.of("req_id", generateReqId(runtime, CMD_SEND_MSG)),
                "body",
                Map.of("chatid", targetId, "msgtype", "markdown", "markdown", Map.of("content", content)));
        sendFrame(runtime, frame);
    }

    private void sendMediaMessage(
            RuntimeState runtime, String targetId, String mediaId, String mediaType, String frameReqId) {
        if (!StringUtils.hasText(mediaId) || !StringUtils.hasText(mediaType)) {
            return;
        }
        Map<String, Object> mediaBody = Map.of("msgtype", mediaType, mediaType, Map.of("media_id", mediaId));
        if (StringUtils.hasText(frameReqId)) {
            sendFrame(runtime, Map.of("cmd", CMD_RESPONSE, "headers", Map.of("req_id", frameReqId), "body", mediaBody));
            return;
        }
        if (!StringUtils.hasText(targetId)) {
            logger.warn("企业微信发送媒体失败，缺少目标标识：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>(mediaBody);
        body.put("chatid", targetId);
        sendFrame(
                runtime,
                Map.of(
                        "cmd",
                        CMD_SEND_MSG,
                        "headers",
                        Map.of("req_id", generateReqId(runtime, CMD_SEND_MSG)),
                        "body",
                        body));
    }

    private String uploadMedia(RuntimeState runtime, byte[] fileBytes, String fileName, String mediaType) {
        if (runtime == null || runtime.webSocket == null || fileBytes == null || fileBytes.length == 0) {
            return null;
        }
        int totalChunks = (int) Math.ceil((double) fileBytes.length / UPLOAD_CHUNK_SIZE);
        if (totalChunks <= 0 || totalChunks > 100) {
            logger.warn(
                    "企业微信文件上传被拒绝：channelId={}, ownerUserId={}, fileName={}, size={}, chunks={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    fileName,
                    fileBytes.length,
                    totalChunks);
            return null;
        }
        boolean acquired = false;
        try {
            acquired = uploadLock.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("企业微信文件上传等待超时：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
                return null;
            }
            String uploadId = initMediaUpload(runtime, fileBytes, fileName, mediaType, totalChunks);
            if (!StringUtils.hasText(uploadId)) {
                return null;
            }
            for (int index = 0; index < totalChunks; index++) {
                int offset = index * UPLOAD_CHUNK_SIZE;
                int length = Math.min(UPLOAD_CHUNK_SIZE, fileBytes.length - offset);
                byte[] chunk = Arrays.copyOfRange(fileBytes, offset, offset + length);
                sendMediaUploadChunk(runtime, uploadId, index, chunk);
            }
            String mediaId = finishMediaUpload(runtime, uploadId);
            if (StringUtils.hasText(mediaId)) {
                logger.info(
                        "企业微信文件上传完成：channelId={}, ownerUserId={}, fileName={}, mediaType={}, size={}, mediaId={}",
                        channelConfig.getId(),
                        runtime.ownerUserId,
                        fileName,
                        mediaType,
                        fileBytes.length,
                        abbreviate(mediaId));
            }
            return mediaId;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("企业微信文件上传被中断：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
            return null;
        } catch (Exception ex) {
            logger.warn(
                    "企业微信文件上传失败：channelId={}, ownerUserId={}, fileName={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    fileName,
                    ex.getMessage(),
                    ex);
            return null;
        } finally {
            if (acquired) {
                uploadLock.release();
            }
        }
    }

    private String initMediaUpload(
            RuntimeState runtime, byte[] fileBytes, String fileName, String mediaType, int totalChunks) {
        String reqId = generateReqId(runtime, CMD_UPLOAD_INIT);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", mediaType);
        body.put("filename", StringUtils.hasText(fileName) ? fileName : "artifact");
        body.put("total_size", fileBytes.length);
        body.put("total_chunks", totalChunks);
        body.put("md5", md5Hex(fileBytes));
        Map<String, Object> ack = sendFrameWithAckBlocking(
                runtime, reqId, Map.of("cmd", CMD_UPLOAD_INIT, "headers", Map.of("req_id", reqId), "body", body));
        return asString(asMap(ack.get("body")).get("upload_id"));
    }

    private void sendMediaUploadChunk(RuntimeState runtime, String uploadId, int chunkIndex, byte[] chunk) {
        String reqId = generateReqId(runtime, CMD_UPLOAD_CHUNK);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("upload_id", uploadId);
        body.put("chunk_index", chunkIndex);
        body.put("base64_data", Base64.getEncoder().encodeToString(chunk));
        sendFrameWithAckBlocking(
                runtime, reqId, Map.of("cmd", CMD_UPLOAD_CHUNK, "headers", Map.of("req_id", reqId), "body", body));
    }

    private String finishMediaUpload(RuntimeState runtime, String uploadId) {
        String reqId = generateReqId(runtime, CMD_UPLOAD_FINISH);
        Map<String, Object> ack = sendFrameWithAckBlocking(
                runtime,
                reqId,
                Map.of(
                        "cmd",
                        CMD_UPLOAD_FINISH,
                        "headers",
                        Map.of("req_id", reqId),
                        "body",
                        Map.of("upload_id", uploadId)));
        return asString(asMap(ack.get("body")).get("media_id"));
    }

    private Map<String, Object> sendFrameWithAckBlocking(RuntimeState runtime, String reqId, Map<String, Object> frame) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        runtime.pendingAcks.put(reqId, future);
        try {
            if (!sendFrame(runtime, frame)) {
                throw new IllegalStateException("send frame failed");
            }
            return future.get(UPLOAD_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("wait upload ack timeout: " + reqId, ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("wait upload ack failed: " + reqId, ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("wait upload ack interrupted: " + reqId, ex);
        } finally {
            runtime.pendingAcks.remove(reqId, future);
        }
    }

    private boolean sendFrame(RuntimeState runtime, Map<String, Object> frame) {
        WebSocket socket = runtime.webSocket;
        if (socket == null) {
            return false;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(frame);
            socket.sendText(json, true);
            return true;
        } catch (Exception ex) {
            logger.error(
                    "企业微信发送帧失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    ex.getMessage(),
                    ex);
            return false;
        }
    }

    private void handleWebSocketFrame(RuntimeState runtime, String payload) {
        if (!StringUtils.hasText(payload) || !isCurrentRuntime(runtime)) {
            return;
        }
        try {
            Map<String, Object> frame = OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            String cmd = asString(frame.get("cmd"));
            if (CMD_MSG_CALLBACK.equals(cmd)) {
                handleMessageCallback(runtime, frame);
                return;
            }
            if (CMD_EVENT_CALLBACK.equals(cmd)) {
                handleEventCallback(runtime, frame);
                return;
            }
            Map<String, Object> headers = asMap(frame.get("headers"));
            String reqId = asString(headers.get("req_id"));
            if (StringUtils.hasText(reqId)) {
                CompletableFuture<Map<String, Object>> ackFuture = runtime.pendingAcks.remove(reqId);
                if (ackFuture != null) {
                    Integer errCode = asInteger(frame.get("errcode"));
                    if (errCode != null && errCode != 0) {
                        ackFuture.completeExceptionally(new IllegalStateException(asString(frame.get("errmsg"))));
                    } else {
                        ackFuture.complete(frame);
                    }
                    return;
                }
            }
            if (reqId.startsWith(CMD_SUBSCRIBE)) {
                handleAuthResponse(runtime, frame);
                return;
            }
            if (reqId.startsWith(CMD_HEARTBEAT)) {
                handleHeartbeatResponse(runtime, frame);
                return;
            }
            Integer errCode = asInteger(frame.get("errcode"));
            if (errCode != null && errCode != 0) {
                logger.warn(
                        "企业微信帧返回错误：channelId={}, ownerUserId={}, reqId={}, errcode={}, errmsg={}",
                        channelConfig.getId(),
                        runtime.ownerUserId,
                        reqId,
                        errCode,
                        asString(frame.get("errmsg")));
            }
        } catch (Exception ex) {
            logger.warn(
                    "企业微信解析帧失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    ex.getMessage());
        }
    }

    private void handleAuthResponse(RuntimeState runtime, Map<String, Object> frame) {
        Integer errCode = asInteger(frame.get("errcode"));
        if (errCode != null && errCode != 0) {
            String errmsg = asString(frame.get("errmsg"));
            runtime.status = "AUTH_FAILED";
            runtime.lastError = "企业微信鉴权失败: " + errmsg;
            runtime.authenticated = false;
            logger.error(
                    "企业微信鉴权失败：channelId={}, ownerUserId={}, errcode={}, errmsg={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    errCode,
                    errmsg);
            scheduleReconnect(runtime);
            return;
        }
        runtime.authenticated = true;
        runtime.status = "CONNECTED";
        runtime.lastError = null;
        runtime.reconnectAttempts.set(0);
        runtime.lastHeartbeatAckAt = System.currentTimeMillis();
        startHeartbeatTask(runtime);
        logger.info("企业微信鉴权成功：channelId={}, ownerUserId={}", channelConfig.getId(), runtime.ownerUserId);
    }

    private void handleHeartbeatResponse(RuntimeState runtime, Map<String, Object> frame) {
        Integer errCode = asInteger(frame.get("errcode"));
        if (errCode != null && errCode != 0) {
            logger.warn(
                    "企业微信心跳响应异常：channelId={}, ownerUserId={}, errcode={}, errmsg={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    errCode,
                    asString(frame.get("errmsg")));
            return;
        }
        runtime.missedHeartbeats.set(0);
        runtime.lastHeartbeatAckAt = System.currentTimeMillis();
    }

    private void handleMessageCallback(RuntimeState runtime, Map<String, Object> frame) {
        Map<String, Object> body = asMap(frame.get("body"));
        Map<String, Object> headers = asMap(frame.get("headers"));
        String frameReqId = asString(headers.get("req_id"));
        String msgType = asString(body.get("msgtype"));
        Map<String, Object> from = asMap(body.get("from"));
        String senderId = asString(from.get("userid"));
        if (!StringUtils.hasText(senderId)) {
            return;
        }
        String chatType = asString(body.get("chattype"));
        if (!StringUtils.hasText(chatType)) {
            chatType = "single";
        }
        String chatId = asString(body.get("chatid"));
        String replyTarget = "group".equalsIgnoreCase(chatType) && StringUtils.hasText(chatId) ? chatId : senderId;
        String externalSessionKey = "group".equalsIgnoreCase(chatType) && StringUtils.hasText(chatId)
                ? "wecom:group:" + chatId
                : "wecom:user:" + senderId;
        String messageId = asString(body.get("msgid"));
        if (!StringUtils.hasText(messageId)) {
            messageId = senderId + "_" + System.currentTimeMillis();
        }
        Long ownerUserId = runtime.ownerUserId;
        InboundMessagePayload inboundPayload = buildInboundPayload(msgType, body, ownerUserId);
        if (inboundPayload == null || !StringUtils.hasText(inboundPayload.content())) {
            return;
        }
        runtime.replyContexts.put(
                replyTarget, new ReplyContext(frameReqId, chatId, chatType, senderId, System.currentTimeMillis()));
        ChannelUserBinding userBinding = resolveUserBinding(ownerUserId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("msgType", msgType);
        metadata.put("chatType", chatType);
        metadata.put("chatId", chatId);
        metadata.put("senderId", senderId);
        metadata.put("frameReqId", frameReqId);
        metadata.put("parts", inboundPayload.parts());
        metadata.put("mediaSummaries", inboundPayload.mediaSummaries());
        if (!inboundPayload.fileIds().isEmpty()) {
            metadata.put("fileIds", inboundPayload.fileIds());
        }
        ChannelMessage message = ChannelMessage.builder()
                .messageId(messageId)
                .channelType(CHANNEL_TYPE)
                .senderId(senderId)
                .senderName(senderId)
                .externalSessionKey(externalSessionKey)
                .replyTarget(replyTarget)
                .ownerUserId(ownerUserId)
                .routeType(userBinding == null ? channelConfig.getRouteType() : userBinding.getRouteType())
                .routeTargetId(userBinding == null ? channelConfig.getRouteTargetId() : userBinding.getRouteTargetId())
                .content(inboundPayload.content())
                .contentType(inboundPayload.contentType())
                .inputMode(inboundPayload.inputMode())
                .metadata(metadata)
                .fileIds(inboundPayload.fileIds())
                .timestamp(LocalDateTime.now())
                .rawPayload(frame)
                .build();
        runtime.lastMessageAt = System.currentTimeMillis();
        onMessage(message);
    }

    private ChannelUserBinding resolveUserBinding(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return null;
        }
        if (channelUserBindingService == null) {
            return null;
        }
        return channelUserBindingService.findByChannelAndUser(channelConfig.getId(), ownerUserId);
    }

    private void handleEventCallback(RuntimeState runtime, Map<String, Object> frame) {
        String welcomeText = getString("welcome_text", "");
        if (!StringUtils.hasText(welcomeText)) {
            return;
        }
        Map<String, Object> body = asMap(frame.get("body"));
        Map<String, Object> event = asMap(body.get("event"));
        String eventType = asString(event.get("eventtype"));
        if (!"enter_chat".equalsIgnoreCase(eventType)) {
            return;
        }
        String reqId = asString(asMap(frame.get("headers")).get("req_id"));
        if (!StringUtils.hasText(reqId)) {
            return;
        }
        sendFrame(
                runtime,
                Map.of(
                        "cmd",
                        CMD_RESPONSE_WELCOME,
                        "headers",
                        Map.of("req_id", reqId),
                        "body",
                        Map.of("msgtype", "markdown", "markdown", Map.of("content", welcomeText))));
    }

    private InboundMessagePayload buildInboundPayload(String msgType, Map<String, Object> body, Long ownerUserId) {
        if (!StringUtils.hasText(msgType)) {
            return null;
        }
        InboundPayloadBuilder builder = new InboundPayloadBuilder();
        appendInboundPart(builder, msgType, body, ownerUserId);
        return builder.build();
    }

    private void appendInboundPart(
            InboundPayloadBuilder builder, String msgType, Map<String, Object> body, Long ownerUserId) {
        if (builder == null || !StringUtils.hasText(msgType)) {
            return;
        }
        if ("text".equalsIgnoreCase(msgType)) {
            String text = safeTrim(asString(asMap(body.get("text")).get("content")));
            if (StringUtils.hasText(text)) {
                builder.addText(text);
            }
            return;
        }
        if ("voice".equalsIgnoreCase(msgType)) {
            String text = safeTrim(asString(asMap(body.get("voice")).get("content")));
            builder.addMedia("voice", StringUtils.hasText(text) ? text : "[语音消息]", Map.of());
            return;
        }
        if ("image".equalsIgnoreCase(msgType)) {
            builder.addMedia("image", "[图片消息]", Map.of());
            return;
        }
        if ("file".equalsIgnoreCase(msgType)) {
            appendFilePart(builder, asMap(body.get("file")), ownerUserId);
            return;
        }
        if ("mixed".equalsIgnoreCase(msgType)) {
            Map<String, Object> mixedBody = asMap(body.get("mixed"));
            Object itemsRaw = firstPresent(mixedBody, "msg_item", "msgItem", "items");
            if (!(itemsRaw instanceof List<?> items)) {
                return;
            }
            for (Object itemRaw : items) {
                Map<String, Object> item = asMap(itemRaw);
                String itemType = asString(firstPresent(item, "msgtype", "msgType"));
                if (!StringUtils.hasText(itemType)) {
                    continue;
                }
                appendInboundPart(builder, itemType, item, ownerUserId);
            }
        }
    }

    private void appendFilePart(InboundPayloadBuilder builder, Map<String, Object> fileBody, Long ownerUserId) {
        String fileName = firstText(
                asString(firstPresent(fileBody, "filename", "file_name", "fileName", "name")),
                "wecom-file");
        String summary = "[文件] " + fileName;
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("fileName", fileName);
        extra.put("size", firstText(asString(firstPresent(fileBody, "filesize", "file_size", "size")), ""));
        extra.put("mediaId", firstText(asString(firstPresent(fileBody, "media_id", "mediaId")), ""));
        extra.put("aesKeyPresent", StringUtils.hasText(resolveFileAesKey(fileBody)));
        extra.put("downloadUrl", resolveFileDownloadUrl(fileBody));
        registerInboundFile(ownerUserId, fileBody, fileName, extra, builder.fileIds());
        builder.addMedia("file", summary, extra);
    }

    private void registerInboundFile(
            Long ownerUserId,
            Map<String, Object> fileBody,
            String fileName,
            Map<String, Object> extra,
            List<String> fileIds) {
        if (extra == null || fileIds == null) {
            return;
        }
        if (chatFileService == null) {
            extra.put("uploadStatus", "UNAVAILABLE");
            logger.warn(
                    "企业微信入站文件无法注册为聊天附件：channelId={}, ownerUserId={}, fileName={}, reason=chatFileService_missing",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName);
            return;
        }
        String downloadUrl = resolveFileDownloadUrl(fileBody);
        if (!StringUtils.hasText(downloadUrl)) {
            extra.put("uploadStatus", "MEDIA_MISSING");
            logger.warn(
                    "企业微信入站文件缺少可下载媒体：channelId={}, ownerUserId={}, fileName={}",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName);
            return;
        }
        try {
            byte[] content = downloadInboundFile(downloadUrl, resolveFileAesKey(fileBody));
            SniffedFile sniffedFile = sniffInboundFile(content);
            String uploadFileName = resolveInboundUploadFileName(fileName, sniffedFile);
            String contentType = StringUtils.hasText(sniffedFile.contentType())
                    ? sniffedFile.contentType()
                    : detectContentType(uploadFileName);
            ChatFileService.UploadResponse upload =
                    chatFileService.uploadBytes(uploadFileName, content, contentType, ownerUserId, null);
            if (upload == null || !StringUtils.hasText(upload.id())) {
                throw new IllegalStateException("missing uploaded file id");
            }
            if (!fileIds.contains(upload.id())) {
                fileIds.add(upload.id());
            }
            extra.put("fileName", uploadFileName);
            extra.put("fileId", upload.id());
            extra.put("fileIds", List.of(upload.id()));
            extra.put("objectName", upload.file() == null ? null : upload.file().objectName());
            extra.put("uploadStatus", "UPLOADED");
            logger.info(
                    "企业微信入站文件已注册为聊天附件：channelId={}, ownerUserId={}, fileName={}, fileId={}, size={}",
                    channelConfig.getId(),
                    ownerUserId,
                    uploadFileName,
                    upload.id(),
                    upload.size());
        } catch (Exception ex) {
            extra.put("uploadStatus", "FAILED");
            extra.put("uploadError", ex.getMessage());
            logger.warn(
                    "企业微信入站文件注册为聊天附件失败：channelId={}, ownerUserId={}, fileName={}, error={}",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName,
                    ex.getMessage(),
                    ex);
        }
    }

    private String resolveInboundUploadFileName(String fileName, SniffedFile sniffedFile) {
        String normalized = StringUtils.hasText(fileName) ? fileName.trim() : "wecom-file";
        String extension = sniffedFile == null ? "" : sniffedFile.extension();
        if (!StringUtils.hasText(extension)) {
            return normalized;
        }
        String lower = normalized.toLowerCase();
        boolean genericName = "wecom-file".equals(lower) || "file".equals(lower) || "file.bin".equals(lower);
        if (genericName || !lower.contains(".")) {
            return stripExtension(normalized) + extension;
        }
        return normalized;
    }

    private SniffedFile sniffInboundFile(byte[] content) {
        if (content == null || content.length < 4) {
            return SniffedFile.UNKNOWN;
        }
        if (startsWith(content, (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46)) {
            return new SniffedFile(".pdf", "application/pdf");
        }
        if (startsWith(content, (byte) 0x50, (byte) 0x4b, (byte) 0x03, (byte) 0x04)) {
            return refineZipInboundFile(content);
        }
        if (startsWith(content, (byte) 0xff, (byte) 0xd8, (byte) 0xff)) {
            return new SniffedFile(".jpg", "image/jpeg");
        }
        if (startsWith(content, (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47)) {
            return new SniffedFile(".png", "image/png");
        }
        if (startsWith(content, (byte) 0x47, (byte) 0x49, (byte) 0x46)) {
            return new SniffedFile(".gif", "image/gif");
        }
        if (content.length >= 8
                && startsWith(
                        content,
                        (byte) 0xd0,
                        (byte) 0xcf,
                        (byte) 0x11,
                        (byte) 0xe0,
                        (byte) 0xa1,
                        (byte) 0xb1,
                        (byte) 0x1a,
                        (byte) 0xe1)) {
            return new SniffedFile(".xls", "application/vnd.ms-excel");
        }
        return SniffedFile.UNKNOWN;
    }

    private SniffedFile refineZipInboundFile(byte[] content) {
        SniffedFile zip = new SniffedFile(".zip", "application/zip");
        if (content == null || content.length < 30) {
            return zip;
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zipInputStream.getNextEntry()) != null && seen < 32) {
                String name = entry.getName();
                if (name == null) {
                    seen++;
                    continue;
                }
                if (name.startsWith("xl/")) {
                    return new SniffedFile(
                            ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                }
                if (name.startsWith("word/")) {
                    return new SniffedFile(
                            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                }
                if (name.startsWith("ppt/")) {
                    return new SniffedFile(
                            ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                }
                seen++;
            }
        } catch (Exception ex) {
            logger.debug("企业微信入站 ZIP 文件类型识别失败：channelId={}, error={}", channelConfig.getId(), ex.getMessage());
            return zip;
        }
        return zip;
    }

    private String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "wecom-file";
        }
        String normalized = fileName.trim();
        int dotIndex = normalized.lastIndexOf('.');
        return dotIndex <= 0 ? normalized : normalized.substring(0, dotIndex);
    }

    private byte[] downloadInboundFile(String downloadUrl, String aesKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl.trim()))
                .timeout(Duration.ofSeconds(getLong("media_download_timeout_seconds", 60L)))
                .GET()
                .build();
        HttpResponse<byte[]> response = mediaHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("download failed: HTTP " + response.statusCode());
        }
        byte[] content = response.body();
        if (content == null || content.length == 0) {
            throw new IllegalStateException("downloaded file is empty");
        }
        if (StringUtils.hasText(aesKey)) {
            try {
                return decryptInboundFile(content, aesKey.trim());
            } catch (Exception ex) {
                if (looksLikePlainFile(content)) {
                    logger.warn(
                            "企业微信入站文件解密失败，按明文内容注册：channelId={}, error={}",
                            channelConfig.getId(),
                            ex.getMessage());
                    return content;
                }
                throw ex;
            }
        }
        return content;
    }

    private byte[] decryptInboundFile(byte[] encryptedContent, String aesKey) {
        if (encryptedContent == null || encryptedContent.length == 0) {
            throw new IllegalArgumentException("encrypted file is empty");
        }
        if (!StringUtils.hasText(aesKey)) {
            return encryptedContent;
        }
        try {
            String normalizedKey = aesKey.trim();
            normalizedKey = normalizedKey + "=".repeat(Math.floorMod(-normalizedKey.length(), 4));
            byte[] key = Base64.getDecoder().decode(normalizedKey);
            if (key.length != 32) {
                throw new IllegalArgumentException("invalid aes key length: " + key.length);
            }
            byte[] iv = new byte[16];
            System.arraycopy(key, 0, iv, 0, iv.length);
            int remainder = encryptedContent.length % 16;
            if (remainder != 0) {
                encryptedContent = Arrays.copyOf(encryptedContent, encryptedContent.length + (16 - remainder));
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encryptedContent);
            int padLength = decrypted[decrypted.length - 1] & 0xff;
            if (padLength < 1 || padLength > 32 || padLength > decrypted.length) {
                throw new IllegalArgumentException("invalid pkcs7 padding value: " + padLength);
            }
            for (int index = decrypted.length - padLength; index < decrypted.length; index++) {
                if ((decrypted[index] & 0xff) != padLength) {
                    throw new IllegalArgumentException("invalid pkcs7 padding bytes");
                }
            }
            return Arrays.copyOf(decrypted, decrypted.length - padLength);
        } catch (Exception ex) {
            throw new IllegalStateException("decrypt failed: " + ex.getMessage(), ex);
        }
    }

    private boolean looksLikePlainFile(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        if (startsWith(content, (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46)
                || startsWith(content, (byte) 0x50, (byte) 0x4b, (byte) 0x03, (byte) 0x04)
                || startsWith(content, (byte) 0xff, (byte) 0xd8, (byte) 0xff)
                || startsWith(content, (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47)
                || startsWith(content, (byte) 0x47, (byte) 0x49, (byte) 0x46)) {
            return true;
        }
        int sampleSize = Math.min(content.length, 256);
        int printableCount = 0;
        for (int index = 0; index < sampleSize; index++) {
            int value = content[index] & 0xff;
            if (value == 9 || value == 10 || value == 13 || (value >= 32 && value <= 126) || value >= 0x80) {
                printableCount++;
            }
        }
        return printableCount >= sampleSize * 0.85;
    }

    private boolean startsWith(byte[] content, byte... prefix) {
        if (content == null || prefix == null || content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String resolveFileDownloadUrl(Map<String, Object> fileBody) {
        return firstText(
                asString(firstPresent(
                        fileBody,
                        "download_url",
                        "downloadUrl",
                        "file_url",
                        "fileUrl",
                        "url",
                        "media_url",
                        "mediaUrl")),
                "");
    }

    private String resolveFileAesKey(Map<String, Object> fileBody) {
        return firstText(asString(firstPresent(fileBody, "aeskey", "aes_key", "aesKey")), "");
    }

    private String detectContentType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "application/octet-stream";
        }
        String lower = fileName.trim().toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "text/plain";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/octet-stream";
    }

    private ReplyContext resolveReplyContext(RuntimeState runtime, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return null;
        }
        ReplyContext context = runtime.replyContexts.get(targetId);
        if (context == null) {
            return null;
        }
        if (System.currentTimeMillis() - context.createdAt() > REPLY_CONTEXT_RETENTION_MS) {
            runtime.replyContexts.remove(targetId);
            return null;
        }
        return context;
    }

    private boolean isReplyFrameUsable(ReplyContext context) {
        return context != null
                && StringUtils.hasText(context.frameReqId())
                && System.currentTimeMillis() - context.createdAt() <= REPLY_FRAME_TTL_MS;
    }

    private TypingContext resolveAndConsumeTypingContext(RuntimeState runtime, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return null;
        }
        TypingContext typingContext = runtime.typingContexts.remove(targetId);
        if (!isTypingContextUsable(typingContext)) {
            return null;
        }
        return typingContext;
    }

    private boolean isTypingContextUsable(TypingContext context) {
        return context != null
                && StringUtils.hasText(context.frameReqId())
                && StringUtils.hasText(context.streamId())
                && StringUtils.hasText(context.placeholderText())
                && System.currentTimeMillis() - context.createdAt() <= TYPING_CONTEXT_RETENTION_MS;
    }

    private String resolveFallbackTarget(String targetId, ReplyContext context) {
        if (context != null && StringUtils.hasText(context.chatId())) {
            return context.chatId();
        }
        return targetId;
    }

    private void handleDisconnected(RuntimeState runtime, String reason, boolean tryReconnect) {
        if (!running.get() || !isCurrentRuntime(runtime)) {
            return;
        }
        runtime.authenticated = false;
        runtime.status = tryReconnect ? "DISCONNECTED" : "ERROR";
        runtime.lastError = reason;
        cancelHeartbeatTask(runtime);
        closeWebSocketQuietly(runtime, reason);
        runtime.typingContexts.clear();
        if (tryReconnect) {
            scheduleReconnect(runtime);
        }
    }

    private void cancelHeartbeatTask(RuntimeState runtime) {
        ScheduledFuture<?> future = runtime.heartbeatFuture;
        runtime.heartbeatFuture = null;
        if (future != null) {
            future.cancel(true);
        }
    }

    private void scheduleReconnect(RuntimeState runtime) {
        if (!running.get() || !isCurrentRuntime(runtime)) {
            return;
        }
        if (!runtime.reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt = runtime.reconnectAttempts.incrementAndGet();
        int maxAttempts = getInt("max_reconnect_attempts", -1);
        if (maxAttempts >= 0 && attempt > maxAttempts) {
            runtime.status = "RECONNECT_EXHAUSTED";
            runtime.reconnectScheduled.set(false);
            logger.error(
                    "企业微信重连次数耗尽：channelId={}, ownerUserId={}, attempts={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    attempt);
            return;
        }
        long delaySeconds = Math.min(30L, 1L << Math.min(attempt, 5));
        runtime.status = "RECONNECTING";
        ScheduledExecutorService scheduler = runtime.scheduler;
        if (scheduler == null) {
            runtime.reconnectScheduled.set(false);
            return;
        }
        scheduler.schedule(
                () -> {
                    runtime.reconnectScheduled.set(false);
                    if (!running.get() || !isCurrentRuntime(runtime)) {
                        return;
                    }
                    connectWebSocket(runtime);
                },
                delaySeconds,
                TimeUnit.SECONDS);
        logger.warn(
                "企业微信计划重连：channelId={}, ownerUserId={}, attempt={}, delay={}s",
                channelConfig.getId(),
                runtime.ownerUserId,
                attempt,
                delaySeconds);
    }

    private void closeWebSocketQuietly(RuntimeState runtime, String reason) {
        WebSocket socket = runtime.webSocket;
        runtime.webSocket = null;
        if (socket == null) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, reason == null ? "closing" : reason);
        } catch (Exception ex) {
            logger.debug(
                    "企业微信关闭 WebSocket 失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId,
                    ex.getMessage());
        }
    }

    private boolean isCurrentRuntime(RuntimeState runtime) {
        RuntimeState current = runtimes.get(runtime.ownerUserId);
        return current == runtime;
    }

    private String generateReqId(RuntimeState runtime, String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + runtime.reqCounter.incrementAndGet();
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key) && source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Map.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String md5Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("md5 failed: " + ex.getMessage(), ex);
        }
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
    }

    private record InboundMessagePayload(
            String content,
            String contentType,
            String inputMode,
            List<Map<String, Object>> parts,
            List<String> mediaSummaries,
            List<String> fileIds) {}

    private record SniffedFile(String extension, String contentType) {
        private static final SniffedFile UNKNOWN = new SniffedFile("", "");
    }

    private static class InboundPayloadBuilder {
        private final List<String> segments = new ArrayList<>();
        private final List<Map<String, Object>> parts = new ArrayList<>();
        private final List<String> mediaSummaries = new ArrayList<>();
        private final List<String> fileIds = new ArrayList<>();

        private void addText(String text) {
            if (!StringUtils.hasText(text)) {
                return;
            }
            String normalized = text.trim();
            segments.add(normalized);
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", "text");
            part.put("summary", normalized);
            parts.add(part);
        }

        private void addMedia(String type, String summary, Map<String, Object> extra) {
            if (!StringUtils.hasText(type) || !StringUtils.hasText(summary)) {
                return;
            }
            String normalizedSummary = summary.trim();
            segments.add(normalizedSummary);
            mediaSummaries.add(normalizedSummary);
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", type.trim());
            part.put("summary", normalizedSummary);
            if (extra != null && !extra.isEmpty()) {
                part.putAll(extra);
            }
            parts.add(part);
        }

        private List<String> fileIds() {
            return fileIds;
        }

        private InboundMessagePayload build() {
            if (segments.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> immutableParts = List.copyOf(parts);
            return new InboundMessagePayload(
                    String.join("\n", segments),
                    resolveContentType(immutableParts),
                    resolveInputMode(immutableParts),
                    immutableParts,
                    List.copyOf(mediaSummaries),
                    List.copyOf(fileIds));
        }

        private String resolveContentType(List<Map<String, Object>> parts) {
            if (parts == null || parts.isEmpty()) {
                return "text";
            }
            if (parts.size() == 1) {
                Object type = parts.get(0).get("type");
                return type == null ? "text" : String.valueOf(type);
            }
            return "mixed";
        }

        private String resolveInputMode(List<Map<String, Object>> parts) {
            if (parts == null || parts.isEmpty()) {
                return "text";
            }
            boolean hasText = false;
            boolean hasImage = false;
            boolean hasVoice = false;
            boolean hasFile = false;
            for (Map<String, Object> part : parts) {
                String type = part == null ? null : String.valueOf(part.get("type"));
                if ("text".equalsIgnoreCase(type)) {
                    hasText = true;
                } else if ("image".equalsIgnoreCase(type)) {
                    hasImage = true;
                } else if ("voice".equalsIgnoreCase(type)) {
                    hasVoice = true;
                } else if ("file".equalsIgnoreCase(type)) {
                    hasFile = true;
                }
            }
            int categories = (hasText ? 1 : 0) + (hasImage ? 1 : 0) + (hasVoice ? 1 : 0) + (hasFile ? 1 : 0);
            if (categories > 1) {
                return "mixed";
            }
            if (hasImage) {
                return "image";
            }
            if (hasVoice) {
                return "voice";
            }
            if (hasFile) {
                return "file";
            }
            return "text";
        }
    }

    private class WeComWebSocketListener implements WebSocket.Listener {

        private final RuntimeState runtime;

        private WeComWebSocketListener(RuntimeState runtime) {
            this.runtime = runtime;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (runtime.frameLock) {
                runtime.frameBuffer.append(data);
                if (last) {
                    String frame = runtime.frameBuffer.toString();
                    runtime.frameBuffer.setLength(0);
                    handleWebSocketFrame(runtime, frame);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            synchronized (runtime.frameLock) {
                runtime.frameBuffer.append(new String(bytes));
                if (last) {
                    String frame = runtime.frameBuffer.toString();
                    runtime.frameBuffer.setLength(0);
                    handleWebSocketFrame(runtime, frame);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            handleDisconnected(runtime, "WebSocket 关闭: code=" + statusCode + ", reason=" + reason, true);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            handleDisconnected(runtime, "WebSocket 异常: " + error.getMessage(), true);
        }
    }

    private static class RuntimeState {
        private final Long ownerUserId;
        private final String botId;
        private final String secret;
        private final AtomicLong reqCounter = new AtomicLong(0);
        private final AtomicInteger missedHeartbeats = new AtomicInteger(0);
        private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
        private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
        private final ConcurrentHashMap<String, ReplyContext> replyContexts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, TypingContext> typingContexts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pendingAcks =
                new ConcurrentHashMap<>();
        private final Object frameLock = new Object();
        private final StringBuilder frameBuffer = new StringBuilder();

        private volatile HttpClient httpClient;
        private volatile WebSocket webSocket;
        private volatile ScheduledExecutorService scheduler;
        private volatile ScheduledFuture<?> heartbeatFuture;
        private volatile String status = "STOPPED";
        private volatile String lastError;
        private volatile boolean authenticated;
        private volatile long lastHeartbeatAckAt;
        private volatile long lastMessageAt;

        private RuntimeState(Long ownerUserId, String botId, String secret) {
            this.ownerUserId = ownerUserId;
            this.botId = botId;
            this.secret = secret;
        }
    }

    private record ReplyContext(String frameReqId, String chatId, String chatType, String senderId, long createdAt) {}

    private record TypingContext(String frameReqId, String streamId, String placeholderText, long createdAt) {}

    private record RuntimeCredential(Long ownerUserId, String botId, String secret) {}
}
