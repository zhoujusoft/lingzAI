package lingzhou.agent.backend.business.channel.adapter.wechat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lingzhou.agent.backend.business.channel.adapter.AbstractChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;
import lingzhou.agent.backend.business.channel.service.ChannelSessionBindingService;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.spring.ai.wechat.ilink.ILinkClient;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.SessionExpiredException;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginStatus;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.FileItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.MessageItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VideoItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VoiceItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.WeixinMessage;
import org.springframework.util.StringUtils;

public class WechatIlinkChannelAdapter extends AbstractChannelAdapter {

    private static final String WEIXIN_SCAN_URL_TEMPLATE =
            "https://liteapp.weixin.qq.com/q/7GiQu1?qrcode=%s&bot_type=3";
    private static final long DEFAULT_INBOUND_MESSAGE_DEDUP_TTL_MS = 5 * 60 * 1000L;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final ChannelSessionBindingService channelSessionBindingService;
    private final ChannelUserBindingService channelUserBindingService;
    private final ChatFileService chatFileService;
    private final Map<Long, UserRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<String, Long> inboundMessageDedupCache = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> runtimeOperationLocks = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> loginGenerations = new ConcurrentHashMap<>();
    private final Map<Long, ExecutorService> pollExecutors = new ConcurrentHashMap<>();

    public WechatIlinkChannelAdapter(
            ChannelConfig channelConfig,
            ChannelMessageRouter messageRouter,
            ChannelSessionBindingService channelSessionBindingService,
            ChannelUserBindingService channelUserBindingService) {
        this(channelConfig, messageRouter, channelSessionBindingService, channelUserBindingService, null);
    }

    public WechatIlinkChannelAdapter(
            ChannelConfig channelConfig,
            ChannelMessageRouter messageRouter,
            ChannelSessionBindingService channelSessionBindingService,
            ChannelUserBindingService channelUserBindingService,
            ChatFileService chatFileService) {
        super(channelConfig, messageRouter);
        this.channelSessionBindingService = channelSessionBindingService;
        this.channelUserBindingService = channelUserBindingService;
        this.chatFileService = chatFileService;
    }

    @Override
    public String getChannelType() {
        return "weixin";
    }

    @Override
    public void sendMessage(String targetId, String content) {
        UserRuntime runtime = selectDefaultRuntime();
        sendMessage(runtime == null ? null : runtime.ownerUserId(), targetId, content);
    }

    @Override
    public void sendMessage(Long ownerUserId, String targetId, String content) {
        if (!StringUtils.hasText(targetId) || !StringUtils.hasText(content)) {
            return;
        }
        UserRuntime runtime = resolveReplyRuntime(ownerUserId);
        ReentrantLock lock = runtimeOperationLock(runtime.ownerUserId());
        lock.lock();
        try {
            runtime.client().sendText(targetId, content);
        } catch (IOException ex) {
            throw new IllegalStateException("微信发送消息失败: " + ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }
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
        UserRuntime runtime = resolveReplyRuntime(ownerUserId);
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        ReentrantLock lock = runtimeOperationLock(runtime.ownerUserId());
        lock.lock();
        try {
            runtime.client().sendFile(targetId, fileBytes, resolvedFileName, caption);
        } catch (IOException ex) {
            throw new IllegalStateException("微信发送文件失败: " + ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void startTyping(Long ownerUserId, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return;
        }
        UserRuntime runtime = resolveReplyRuntime(ownerUserId);
        ReentrantLock lock = runtimeOperationLock(runtime.ownerUserId());
        lock.lock();
        try {
            runtime.client().startTyping(targetId);
        } catch (IOException ex) {
            logger.warn(
                    "微信发送输入中状态失败：channelId={}, ownerUserId={}, targetId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    targetId,
                    ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warn(
                    "微信输入中状态运行时异常：channelId={}, ownerUserId={}, targetId={}, error={}",
                    channelConfig.getId(),
                    ownerUserId,
                    targetId,
                    ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stopTyping(Long ownerUserId, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return;
        }
        UserRuntime runtime = resolveReplyRuntime(ownerUserId);
        ReentrantLock lock = runtimeOperationLock(runtime.ownerUserId());
        lock.lock();
        try {
            runtime.client().stopTyping(targetId);
        } catch (IOException ex) {
            logger.warn(
                    "微信停止输入中状态失败：channelId={}, ownerUserId={}, targetId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    targetId,
                    ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warn(
                    "微信停止输入中状态运行时异常：channelId={}, ownerUserId={}, targetId={}, error={}",
                    channelConfig.getId(),
                    ownerUserId,
                    targetId,
                    ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnectUser(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return;
        }
        UserRuntime runtime = runtimes.remove(ownerUserId);
        stopRuntimePollLoop(ownerUserId);
        runtimeOperationLocks.remove(ownerUserId);
        loginGenerations.remove(ownerUserId);
        closeClientQuietly(runtime);
    }

    public Map<String, Object> beginLoginPayload(Long ownerUserId) {
        UserRuntime runtime = requireRuntime(ownerUserId);
        ILinkClient activeClient = runtime.client();
        long loginGeneration = nextLoginGeneration(ownerUserId);
        String qrcodeImageContent;
        String qrcode;
        ReentrantLock lock = runtimeOperationLock(ownerUserId);
        lock.lock();
        try {
            qrcodeImageContent = normalizeQrCodeContent(activeClient.executeLogin());
            attachLoginPersistence(ownerUserId, activeClient, loginGeneration);
            qrcode = activeClient.getQrcode();
        } finally {
            lock.unlock();
        }
        String qrcodeScanUrl = resolveQrCodeScanUrl(qrcodeImageContent, qrcode);
        String qrcodeImageBase64 = generateQrCodeBase64(qrcodeScanUrl);
        logger.info(
                "微信登录二维码已生成：channelId={}, ownerUserId={}, sourceType={}, hasQrcode={}",
                channelConfig.getId(),
                ownerUserId,
                detectQrCodeSourceType(qrcodeImageContent),
                StringUtils.hasText(qrcode));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qrcode", qrcode);
        payload.put("qrcode_url", qrcodeScanUrl);
        payload.put("qrcode_img", qrcodeImageBase64);
        payload.put("qrcode_img_content", qrcodeImageBase64);
        payload.put("qrcodeImageContent", qrcodeImageBase64);
        payload.put("ownerUserId", ownerUserId);
        return payload;
    }

    public LoginStatus getLoginStatus(Long ownerUserId) {
        UserRuntime runtime = ownerUserId == null ? null : runtimes.get(ownerUserId);
        return runtime == null ? new LoginStatus() : runtime.client().getLoginStatus();
    }

    public boolean isLoggedIn(Long ownerUserId) {
        UserRuntime runtime = ownerUserId == null ? null : runtimes.get(ownerUserId);
        return runtime != null && runtime.client().isLoggedIn();
    }

    public void pollOnce(Long ownerUserId) {
        pollRuntime(requireRuntime(ownerUserId));
    }

    @Override
    public void refreshConfig(ChannelConfig channelConfig) {
        boolean previousAutoPoll = getBoolean("autoPoll", true);
        super.refreshConfig(channelConfig);
        if (isRunning() && previousAutoPoll != getBoolean("autoPoll", true)) {
            restartPollLoopIfNeeded();
        }
    }

    @Override
    protected void doStart() {
        restorePersistedLogins();
        startPollLoopIfNeeded();
        logger.info("微信 iLink 渠道已启动：channelId={}", channelConfig.getId());
    }

    @Override
    protected void doStop() {
        polling.set(false);
        stopAllPollLoops();
        closeAllClientsQuietly();
        runtimeOperationLocks.clear();
        loginGenerations.clear();
        logger.info("微信 iLink 渠道已停止：channelId={}", channelConfig.getId());
    }

    private void restartPollLoopIfNeeded() {
        polling.set(false);
        stopAllPollLoops();
        startPollLoopIfNeeded();
    }

    private void startPollLoopIfNeeded() {
        if (!getBoolean("autoPoll", true)) {
            polling.set(false);
            return;
        }
        polling.set(true);
        for (UserRuntime runtime : List.copyOf(runtimes.values())) {
            startRuntimePollLoopIfNeeded(runtime);
        }
    }

    private void startRuntimePollLoopIfNeeded(UserRuntime runtime) {
        if (runtime == null || !polling.get() || runtimes.get(runtime.ownerUserId()) != runtime) {
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread =
                    new Thread(r, "channel-weixin-poll-" + channelConfig.getId() + "-" + runtime.ownerUserId());
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService existing = pollExecutors.putIfAbsent(runtime.ownerUserId(), executor);
        if (existing != null) {
            executor.shutdownNow();
            return;
        }
        executor.execute(() -> {
            try {
                while (polling.get()
                        && runtimes.get(runtime.ownerUserId()) == runtime
                        && !Thread.currentThread().isInterrupted()) {
                    try {
                        pollRuntime(runtime);
                        Thread.sleep(Math.max(1000L, getLong("pollIntervalMs", 3000L)));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        logger.warn(
                                "微信轮询线程异常：channelId={}, ownerUserId={}, error={}",
                                channelConfig.getId(),
                                runtime.ownerUserId(),
                                ex.getMessage());
                    }
                }
            } finally {
                pollExecutors.remove(runtime.ownerUserId(), executor);
                executor.shutdown();
            }
        });
    }

    private void stopRuntimePollLoop(Long ownerUserId) {
        ExecutorService executor = pollExecutors.remove(ownerUserId);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void stopAllPollLoops() {
        List<ExecutorService> currentExecutors = List.copyOf(pollExecutors.values());
        pollExecutors.clear();
        for (ExecutorService executor : currentExecutors) {
            executor.shutdownNow();
        }
    }

    private void pollRuntime(UserRuntime runtime) {
        ReentrantLock lock = runtimeOperationLock(runtime.ownerUserId());
        lock.lock();
        ILinkClient activeClient = runtime.client();
        try {
            if (!activeClient.isLoggedIn()) {
                return;
            }
            List<WeixinMessage> updates = activeClient.getUpdates();
            if (updates == null || updates.isEmpty()) {
                return;
            }
            ChannelUserBinding userBinding =
                    channelUserBindingService.findByChannelAndUser(channelConfig.getId(), runtime.ownerUserId());
            for (WeixinMessage update : updates) {
                ChannelMessage message = toChannelMessage(update, runtime, userBinding);
                if (message != null) {
                    if (isDuplicateInboundMessage(message, runtime.ownerUserId())) {
                        continue;
                    }
                    onMessage(message);
                }
            }
        } catch (IOException ex) {
            if (isExpectedPollingInterruption(runtime)) {
                return;
            }
            logger.warn(
                    "微信拉取消息失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    ex.getMessage());
        } catch (RuntimeException ex) {
            if (isSessionExpired(ex)) {
                handleSessionExpired(runtime, ex);
                return;
            }
            logger.warn(
                    "微信运行时状态异常：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private boolean isExpectedPollingInterruption(UserRuntime runtime) {
        return Thread.currentThread().isInterrupted()
                || !isRunning()
                || runtimes.get(runtime.ownerUserId()) != runtime;
    }

    private boolean isSessionExpired(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SessionExpiredException
                    || current.getClass().getSimpleName().contains("SessionExpired")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void handleSessionExpired(UserRuntime runtime, RuntimeException ex) {
        logger.warn(
                "微信登录态已过期，停止该用户轮询：channelId={}, ownerUserId={}, error={}",
                channelConfig.getId(),
                runtime.ownerUserId(),
                ex.getMessage());
        try {
            channelUserBindingService.clearLoginContext(channelConfig.getId(), runtime.ownerUserId());
        } catch (Exception clearEx) {
            logger.warn(
                    "清理微信登录上下文失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    clearEx.getMessage());
        }
        if (runtimes.remove(runtime.ownerUserId(), runtime)) {
            stopRuntimePollLoop(runtime.ownerUserId());
            closeClientQuietly(runtime);
            runtimeOperationLocks.remove(runtime.ownerUserId());
            loginGenerations.remove(runtime.ownerUserId());
        }
    }

    private void restorePersistedLogins() {
        for (ChannelUserBinding binding : channelUserBindingService.listWithRuntime(channelConfig.getId())) {
            LoginContext loginContext =
                    channelUserBindingService.getLoginContext(channelConfig.getId(), binding.getOwnerUserId());
            if (loginContext == null || binding.getOwnerUserId() == null || binding.getOwnerUserId() <= 0) {
                continue;
            }
            try {
                UserRuntime runtime = requireRuntime(binding.getOwnerUserId());
                runtime.client().restoreLogin(loginContext);
                logger.info(
                        "微信登录上下文已恢复：channelId={}, ownerUserId={}, botId={}",
                        channelConfig.getId(),
                        binding.getOwnerUserId(),
                        loginContext.getBotId());
            } catch (Exception ex) {
                logger.warn(
                        "微信登录上下文恢复失败：channelId={}, ownerUserId={}, error={}",
                        channelConfig.getId(),
                        binding.getOwnerUserId(),
                        ex.getMessage());
            }
        }
    }

    private ILinkConfig buildIlinkConfig() {
        return ILinkConfig.builder()
                .connectTimeoutMs(getLong("connectTimeoutMs", 35000L))
                .readTimeoutMs(getLong("readTimeoutMs", 35000L))
                .writeTimeoutMs(getLong("writeTimeoutMs", 35000L))
                .httpMaxRetries(getInt("httpMaxRetries", 3))
                .retryBaseDelayMs(getLong("retryBaseDelayMs", 1000L))
                .retryMaxDelayMs(getLong("retryMaxDelayMs", 10000L))
                .retryJitterEnabled(getBoolean("retryJitterEnabled", true))
                .loginTimeoutMs(getLong("loginTimeoutMs", 180000L))
                // The adapter owns getupdates polling; the SDK heartbeat also polls and can consume messages.
                .heartbeatEnabled(false)
                .heartbeatIntervalMs(getLong("heartbeatIntervalMs", 30000L))
                .ioCoreThreads(getInt("ioCoreThreads", 4))
                .ioMaxThreads(getInt("ioMaxThreads", 8))
                .schedulerThreads(getInt("schedulerThreads", 2))
                .queueCapacity(getInt("queueCapacity", 1000))
                .channelVersion(getString("channelVersion", "1.0.0"))
                .routeTag(getString("routeTag", null))
                .autoReconnectEnabled(getBoolean("autoReconnectEnabled", true))
                .build();
    }

    private ChannelMessage toChannelMessage(WeixinMessage update, UserRuntime runtime, ChannelUserBinding userBinding) {
        if (update == null || !StringUtils.hasText(update.getFrom_user_id())) {
            return null;
        }
        Long ownerUserId = runtime == null ? null : runtime.ownerUserId();
        ILinkClient activeClient = runtime == null ? null : runtime.client();
        StringBuilder content = new StringBuilder();
        List<String> mediaSummaries = new ArrayList<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        List<String> fileIds = new ArrayList<>();
        if (update.getItem_list() != null) {
            for (MessageItem item : update.getItem_list()) {
                appendItemContent(content, mediaSummaries, parts, item, activeClient, ownerUserId, fileIds);
            }
        }
        if (!StringUtils.hasText(content.toString())) {
            return null;
        }
        String inputMode = resolveInputMode(parts);
        String contentType = resolveContentType(parts);
        LocalDateTime timestamp = update.getCreate_time_ms() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(update.getCreate_time_ms()), ZoneId.systemDefault());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contextToken", update.getContext_token());
        metadata.put(
                "itemCount",
                update.getItem_list() == null ? 0 : update.getItem_list().size());
        metadata.put("parts", parts);
        metadata.put("mediaSummaries", mediaSummaries);
        if (!fileIds.isEmpty()) {
            metadata.put("fileIds", List.copyOf(fileIds));
        }
        return ChannelMessage.builder()
                .messageId(update.getMessage_id() == null ? null : String.valueOf(update.getMessage_id()))
                .channelType(getChannelType())
                .senderId(update.getFrom_user_id())
                .senderName(update.getFrom_user_id())
                .externalSessionKey(buildExternalSessionKey(update.getFrom_user_id()))
                .replyTarget(update.getFrom_user_id())
                .ownerUserId(ownerUserId)
                .routeType(userBinding == null ? channelConfig.getRouteType() : userBinding.getRouteType())
                .routeTargetId(userBinding == null ? channelConfig.getRouteTargetId() : userBinding.getRouteTargetId())
                .content(content.toString().trim())
                .contentType(contentType)
                .inputMode(inputMode)
                .metadata(metadata)
                .fileIds(List.copyOf(fileIds))
                .timestamp(timestamp)
                .rawPayload(update)
                .build();
    }

    private String buildExternalSessionKey(String fromUserId) {
        return "weixin:" + fromUserId;
    }

    private boolean isDuplicateInboundMessage(ChannelMessage message, Long ownerUserId) {
        if (message == null || !StringUtils.hasText(message.getMessageId())) {
            return false;
        }
        long now = System.currentTimeMillis();
        long ttlMs = Math.max(1000L, getLong("inboundMessageDedupTtlMs", DEFAULT_INBOUND_MESSAGE_DEDUP_TTL_MS));
        cleanupExpiredInboundDedupEntries(now, ttlMs);
        String dedupKey = buildInboundDedupKey(message, ownerUserId);
        Long existingTimestamp = inboundMessageDedupCache.putIfAbsent(dedupKey, now);
        if (existingTimestamp == null) {
            return false;
        }
        if (now - existingTimestamp < ttlMs) {
            logger.info(
                    "微信重复入站消息已忽略：channelId={}, ownerUserId={}, messageId={}, externalSessionKey={}",
                    channelConfig.getId(),
                    ownerUserId,
                    message.getMessageId(),
                    message.getExternalSessionKey());
            return true;
        }
        inboundMessageDedupCache.put(dedupKey, now);
        return false;
    }

    private void cleanupExpiredInboundDedupEntries(long now, long ttlMs) {
        inboundMessageDedupCache.entrySet().removeIf(entry -> now - entry.getValue() >= ttlMs);
    }

    private String buildInboundDedupKey(ChannelMessage message, Long ownerUserId) {
        return channelConfig.getId()
                + ":"
                + (ownerUserId == null ? "0" : ownerUserId)
                + ":"
                + message.getMessageId().trim();
    }

    private void appendItemContent(
            StringBuilder builder,
            List<String> mediaSummaries,
            List<Map<String, Object>> parts,
            MessageItem item,
            ILinkClient activeClient,
            Long ownerUserId,
            List<String> fileIds) {
        if (item == null) {
            return;
        }
        if (item.getText_item() != null
                && StringUtils.hasText(item.getText_item().getText())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            String text = item.getText_item().getText();
            builder.append(text);
            parts.add(part("text", text));
            return;
        }
        if (item.getImage_item() != null) {
            String summary = "[图片] 用户发送了一张图片";
            appendPlaceholder(builder, summary);
            mediaSummaries.add(summary);
            parts.add(part("image", summary));
            return;
        }
        if (item.getFile_item() != null) {
            FileItem fileItem = item.getFile_item();
            StringBuilder summary = new StringBuilder("[文件] 用户发送了文件");
            if (StringUtils.hasText(fileItem.getFile_name())) {
                summary.append("，文件名: ").append(fileItem.getFile_name());
            }
            if (StringUtils.hasText(fileItem.getLen())) {
                summary.append("，大小: ").append(fileItem.getLen()).append(" bytes");
            }
            String text = summary.toString();
            appendPlaceholder(builder, text);
            mediaSummaries.add(text);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("fileName", safeText(fileItem.getFile_name()));
            extra.put("size", safeText(fileItem.getLen()));
            extra.put("hasMedia", fileItem.getMedia() != null);
            registerInboundFile(activeClient, ownerUserId, item, fileItem, extra, fileIds);
            parts.add(part("file", text, extra));
            return;
        }
        if (item.getVoice_item() != null) {
            VoiceItem voiceItem = item.getVoice_item();
            StringBuilder summary = new StringBuilder("[语音] 用户发送了一段语音");
            if (voiceItem.getPlaytime() != null) {
                summary.append("，时长: ").append(voiceItem.getPlaytime()).append(" ms");
            }
            if (voiceItem.getSample_rate() != null) {
                summary.append("，采样率: ").append(voiceItem.getSample_rate());
            }
            String text = summary.toString();
            appendPlaceholder(builder, text);
            mediaSummaries.add(text);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("playtime", voiceItem.getPlaytime());
            extra.put("sampleRate", voiceItem.getSample_rate());
            extra.put("hasMedia", voiceItem.getMedia() != null);
            parts.add(part("voice", text, extra));
            return;
        }
        if (item.getVideo_item() != null) {
            VideoItem videoItem = item.getVideo_item();
            StringBuilder summary = new StringBuilder("[视频] 用户发送了一段视频");
            if (videoItem.getPlay_length() != null) {
                summary.append("，时长: ").append(videoItem.getPlay_length()).append(" ms");
            }
            if (videoItem.getVideo_size() != null) {
                summary.append("，大小: ").append(videoItem.getVideo_size()).append(" bytes");
            }
            String text = summary.toString();
            appendPlaceholder(builder, text);
            mediaSummaries.add(text);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("playLength", videoItem.getPlay_length());
            extra.put("videoSize", videoItem.getVideo_size());
            extra.put("hasMedia", videoItem.getMedia() != null);
            parts.add(part("video", text, extra));
        }
    }

    private void registerInboundFile(
            ILinkClient activeClient,
            Long ownerUserId,
            MessageItem item,
            FileItem fileItem,
            Map<String, Object> extra,
            List<String> fileIds) {
        if (fileItem == null || extra == null || fileIds == null) {
            return;
        }
        String fileName =
                StringUtils.hasText(fileItem.getFile_name()) ? fileItem.getFile_name().trim() : "wechat-file";
        if (chatFileService == null) {
            extra.put("uploadStatus", "UNAVAILABLE");
            logger.warn(
                    "微信入站文件无法注册为聊天附件：channelId={}, ownerUserId={}, fileName={}, reason=chatFileService_missing",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName);
            return;
        }
        if (activeClient == null || fileItem.getMedia() == null) {
            extra.put("uploadStatus", "MEDIA_MISSING");
            logger.warn(
                    "微信入站文件缺少可下载媒体：channelId={}, ownerUserId={}, fileName={}, hasClient={}, hasMedia={}",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName,
                    activeClient != null,
                    fileItem.getMedia() != null);
            return;
        }
        try {
            byte[] content = activeClient.downloadFileFromMessageItem(item);
            ChatFileService.UploadResponse upload =
                    chatFileService.uploadBytes(fileName, content, detectContentType(fileName), ownerUserId, null);
            if (upload == null || !StringUtils.hasText(upload.id())) {
                throw new IllegalStateException("missing uploaded file id");
            }
            if (!fileIds.contains(upload.id())) {
                fileIds.add(upload.id());
            }
            extra.put("fileId", upload.id());
            extra.put("fileIds", List.of(upload.id()));
            extra.put("objectName", upload.file() == null ? null : upload.file().objectName());
            extra.put("uploadStatus", "UPLOADED");
            logger.info(
                    "微信入站文件已注册为聊天附件：channelId={}, ownerUserId={}, fileName={}, fileId={}, size={}",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName,
                    upload.id(),
                    upload.size());
        } catch (Exception ex) {
            extra.put("uploadStatus", "FAILED");
            extra.put("uploadError", ex.getMessage());
            logger.warn(
                    "微信入站文件注册为聊天附件失败：channelId={}, ownerUserId={}, fileName={}, error={}",
                    channelConfig.getId(),
                    ownerUserId,
                    fileName,
                    ex.getMessage(),
                    ex);
        }
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

    private void appendPlaceholder(StringBuilder builder, String placeholder) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(placeholder);
    }

    private String resolveInputMode(List<Map<String, Object>> parts) {
        boolean hasText = false;
        boolean hasImage = false;
        boolean hasVoice = false;
        boolean hasVideo = false;
        boolean hasFile = false;
        for (Map<String, Object> part : parts) {
            String type = part == null ? null : String.valueOf(part.get("type"));
            if ("text".equals(type)) {
                hasText = true;
            } else if ("image".equals(type)) {
                hasImage = true;
            } else if ("voice".equals(type)) {
                hasVoice = true;
            } else if ("video".equals(type)) {
                hasVideo = true;
            } else if ("file".equals(type)) {
                hasFile = true;
            }
        }
        int categories =
                (hasText ? 1 : 0) + (hasImage ? 1 : 0) + (hasVoice ? 1 : 0) + (hasVideo ? 1 : 0) + (hasFile ? 1 : 0);
        if (categories > 1) {
            return "mixed";
        }
        if (hasImage) {
            return "image";
        }
        if (hasVoice) {
            return "voice";
        }
        if (hasVideo) {
            return "video";
        }
        if (hasFile) {
            return "file";
        }
        return "text";
    }

    private String resolveContentType(List<Map<String, Object>> parts) {
        if (parts == null || parts.isEmpty()) {
            return "text";
        }
        if (parts.size() == 1) {
            return String.valueOf(parts.get(0).get("type"));
        }
        return "mixed";
    }

    private Map<String, Object> part(String type, String summary) {
        return part(type, summary, Map.of());
    }

    private Map<String, Object> part(String type, String summary, Map<String, Object> extra) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("summary", summary);
        if (extra != null && !extra.isEmpty()) {
            item.putAll(extra);
        }
        return item;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String normalizeQrCodeContent(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return rawContent;
        }
        return rawContent.trim();
    }

    private String resolveQrCodeScanUrl(String rawContent, String qrcode) {
        if (StringUtils.hasText(qrcode)) {
            return WEIXIN_SCAN_URL_TEMPLATE.formatted(qrcode);
        }
        if (StringUtils.hasText(rawContent)
                && (rawContent.startsWith("http://") || rawContent.startsWith("https://"))) {
            return rawContent;
        }
        throw new IllegalStateException("微信二维码生成失败: 缺少可用的 qrcode");
    }

    private String detectQrCodeSourceType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty";
        }
        if (rawContent.startsWith("data:")) {
            return "data-url";
        }
        if (rawContent.startsWith("http://") || rawContent.startsWith("https://")) {
            return "url";
        }
        return "base64";
    }

    private String generateQrCodeBase64(String content) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints =
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2);
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("微信二维码生成失败: " + ex.getMessage(), ex);
        }
    }

    private void attachLoginPersistence(Long ownerUserId, ILinkClient client) {
        attachLoginPersistence(ownerUserId, client, currentLoginGeneration(ownerUserId));
    }

    private void attachLoginPersistence(Long ownerUserId, ILinkClient client, long loginGeneration) {
        CompletableFuture<LoginContext> loginFuture = client.getLoginFuture();
        if (loginFuture == null) {
            return;
        }
        loginFuture.whenComplete((ctx, ex) -> {
            if (ex != null || ctx == null) {
                return;
            }
            if (!isCurrentLoginGeneration(ownerUserId, loginGeneration)) {
                logger.info(
                        "忽略过期微信登录上下文：channelId={}, ownerUserId={}, generation={}",
                        channelConfig.getId(),
                        ownerUserId,
                        loginGeneration);
                return;
            }
            channelUserBindingService.saveLoginContext(channelConfig.getId(), ownerUserId, ctx);
            logger.info(
                    "微信登录上下文已持久化：channelId={}, ownerUserId={}, botId={}",
                    channelConfig.getId(),
                    ownerUserId,
                    ctx.getBotId());
        });
    }

    private UserRuntime requireRuntime(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少当前用户信息");
        }
        UserRuntime runtime = runtimes.computeIfAbsent(ownerUserId, this::createRuntime);
        startRuntimePollLoopIfNeeded(runtime);
        return runtime;
    }

    private ReentrantLock runtimeOperationLock(Long ownerUserId) {
        return runtimeOperationLocks.computeIfAbsent(ownerUserId, ignored -> new ReentrantLock());
    }

    private long nextLoginGeneration(Long ownerUserId) {
        return loginGenerations
                .computeIfAbsent(ownerUserId, ignored -> new AtomicLong())
                .incrementAndGet();
    }

    private long currentLoginGeneration(Long ownerUserId) {
        AtomicLong generation = loginGenerations.get(ownerUserId);
        return generation == null ? 0L : generation.get();
    }

    private boolean isCurrentLoginGeneration(Long ownerUserId, long loginGeneration) {
        return currentLoginGeneration(ownerUserId) == loginGeneration;
    }

    private UserRuntime resolveReplyRuntime(Long ownerUserId) {
        if (ownerUserId != null && ownerUserId > 0) {
            UserRuntime existing = runtimes.get(ownerUserId);
            if (existing != null && existing.client().isLoggedIn()) {
                return existing;
            }
            throw new IllegalStateException("当前用户的微信登录态不存在或已失效");
        }
        throw new IllegalStateException("未指定可用的微信登录账号");
    }

    private UserRuntime createRuntime(Long ownerUserId) {
        return new UserRuntime(
                ownerUserId, ILinkClient.builder().config(buildIlinkConfig()).build());
    }

    private UserRuntime selectDefaultRuntime() {
        return runtimes.values().stream()
                .filter(runtime -> runtime.client().isLoggedIn())
                .findFirst()
                .orElse(runtimes.values().stream().findFirst().orElse(null));
    }

    private void closeAllClientsQuietly() {
        List<UserRuntime> currentRuntimes = List.copyOf(runtimes.values());
        runtimes.clear();
        for (UserRuntime runtime : currentRuntimes) {
            closeClientQuietly(runtime);
        }
    }

    private void closeClientQuietly(UserRuntime runtime) {
        if (runtime == null || runtime.client() == null) {
            return;
        }
        try {
            runtime.client().close();
        } catch (Exception ex) {
            logger.debug(
                    "关闭微信客户端失败：channelId={}, ownerUserId={}, error={}",
                    channelConfig.getId(),
                    runtime.ownerUserId(),
                    ex.getMessage());
        }
    }

    private record UserRuntime(Long ownerUserId, ILinkClient client) {}
}
