package lingzhou.agent.backend.business.channel.adapter.dingtalk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lingzhou.agent.backend.business.channel.adapter.AbstractChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.util.StringUtils;

public class DingTalkChannelAdapter extends AbstractChannelAdapter {

    private static final String CHANNEL_TYPE = "dingtalk";
    private static final String DEFAULT_REPLY_TITLE = "灵洲智能体";
    private static final String DINGTALK_API_BASE = "https://oapi.dingtalk.com";
    private static final String DINGTALK_OPEN_API_BASE = "https://api.dingtalk.com";
    private static final long DEFAULT_DUPLICATE_TTL_MS = 10 * 60 * 1000L;
    private static final int DEFAULT_CONSUME_THREADS = 2;
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 30_000L;
    private static final long ACCESS_TOKEN_REFRESH_SKEW_MS = 5 * 60 * 1000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, Long> recentMessageIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReplyContext> replyContexts = new ConcurrentHashMap<>();
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private final HttpClient apiHttpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private volatile OpenDingTalkClient streamClient;
    private volatile String accessToken;
    private volatile long accessTokenExpireAt;
    private volatile String status = "NOT_STARTED";
    private volatile String lastError;
    private volatile long lastMessageAt;
    private final ChatFileService chatFileService;
    private final ChannelUserBindingService channelUserBindingService;

    public DingTalkChannelAdapter(ChannelConfig channelConfig, ChannelMessageRouter messageRouter) {
        this(channelConfig, messageRouter, null);
    }

    public DingTalkChannelAdapter(
            ChannelConfig channelConfig, ChannelMessageRouter messageRouter, ChatFileService chatFileService) {
        this(channelConfig, messageRouter, chatFileService, null);
    }

    public DingTalkChannelAdapter(
            ChannelConfig channelConfig,
            ChannelMessageRouter messageRouter,
            ChatFileService chatFileService,
            ChannelUserBindingService channelUserBindingService) {
        super(channelConfig, messageRouter);
        this.chatFileService = chatFileService;
        this.channelUserBindingService = channelUserBindingService;
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
        if (!StringUtils.hasText(targetId) || !StringUtils.hasText(content)) {
            return;
        }
        try {
            replyMarkdown(targetId, getString("replyTitle", DEFAULT_REPLY_TITLE), content);
        } catch (Exception markdownEx) {
            try {
                replyText(targetId, content);
            } catch (Exception textEx) {
                logger.error(
                        "钉钉消息回复失败：channelId={}, error={}",
                        channelConfig.getId(),
                        textEx.getMessage(),
                        textEx);
            }
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
        ReplyContext replyContext = replyContexts.get(targetId);
        if (replyContext == null || !StringUtils.hasText(replyContext.conversationId())) {
            throw new IllegalStateException("缺少钉钉会话上下文，无法发送文件消息");
        }
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        String mediaId = uploadMedia(fileBytes, resolvedFileName);
        sendConversationFileMessage(replyContext, mediaId, resolvedFileName);
        if (StringUtils.hasText(caption)) {
            sendMessage(ownerUserId, targetId, caption);
        }
        logger.info(
                "钉钉文件消息已发送：channelId={}, conversationId={}, fileName={}, size={}",
                channelConfig.getId(),
                replyContext.conversationId(),
                resolvedFileName,
                fileBytes.length);
    }

    @Override
    public boolean supportsProactiveSend() {
        return false;
    }

    @Override
    public void startTyping(Long ownerUserId, String targetId) {
        // 钉钉普通机器人没有可更新的 typing 状态，避免发送“正在回复...”占位普通消息。
    }

    @Override
    protected void doStart() {
        String clientId = dingtalkConfigString("clientId", "client_id");
        String clientSecret = dingtalkConfigString("clientSecret", "client_secret");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            status = "PENDING_CREDENTIAL";
            lastError = null;
            running.set(false);
            logger.info("钉钉 Stream 渠道等待扫码授权或填写凭证：channelId={}", channelConfig.getId());
            return;
        }
        status = "CONNECTING";
        try {
            streamClient = createStreamClient(clientId.trim(), clientSecret.trim());
            streamClient.start();
            status = "RUNNING";
            lastError = null;
            logger.info("钉钉 Stream 渠道已启动：channelId={}", channelConfig.getId());
        } catch (Exception ex) {
            status = "ERROR";
            lastError = ex.getMessage();
            streamClient = null;
            running.set(false);
            throw new IllegalStateException("钉钉 Stream 渠道启动失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    protected void doStop() {
        OpenDingTalkClient client = streamClient;
        streamClient = null;
        accessToken = null;
        accessTokenExpireAt = 0L;
        if (client != null) {
            try {
                client.stop();
            } catch (Exception ex) {
                logger.warn("钉钉 Stream 渠道停止失败：channelId={}, error={}", channelConfig.getId(), ex.getMessage());
            }
        }
        status = "STOPPED";
        recentMessageIds.clear();
        replyContexts.clear();
        logger.info("钉钉 Stream 渠道已停止：channelId={}", channelConfig.getId());
    }

    public Map<String, Object> getRuntimeStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("running", isRunning());
        result.put("lastError", lastError);
        result.put("lastMessageAt", lastMessageAt <= 0 ? null : lastMessageAt);
        result.put("receivedCount", receivedCount.get());
        result.put("recentMessageCount", recentMessageIds.size());
        return result;
    }

    protected OpenDingTalkClient createStreamClient(String clientId, String clientSecret) {
        return OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                .consumeThreads(Math.max(1, getInt("consumeThreads", DEFAULT_CONSUME_THREADS)))
                .connectTimeout(Math.max(1L, getLong("connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS)))
                .registerCallbackListener(
                        DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                        new OpenDingTalkCallbackListener<ChatbotMessage, Object>() {
                            @Override
                            public Object execute(ChatbotMessage payload) {
                                return handleChatbotMessage(payload);
                            }
                        })
                .build();
    }

    protected void replyMarkdown(String webhook, String title, String content) throws Exception {
        BotReplier.fromWebhook(webhook).replyMarkdown(title, content);
    }

    protected void replyText(String webhook, String content) throws Exception {
        BotReplier.fromWebhook(webhook).replyText(content);
    }

    protected Object handleChatbotMessage(ChatbotMessage payload) {
        try {
            processChatbotMessage(payload);
        } catch (Exception ex) {
            lastError = ex.getMessage();
            logger.error(
                    "钉钉消息处理失败：channelId={}, msgId={}, error={}",
                    channelConfig.getId(),
                    payload == null ? null : payload.getMsgId(),
                    ex.getMessage(),
                    ex);
        }
        return Map.of();
    }

    void processChatbotMessage(ChatbotMessage payload) {
        if (payload == null) {
            return;
        }
        String msgId = safeTrim(payload.getMsgId());
        if (StringUtils.hasText(msgId) && isDuplicate(msgId)) {
            logger.debug("钉钉重复消息已忽略：channelId={}, msgId={}", channelConfig.getId(), msgId);
            return;
        }
        String msgType = safeTrim(payload.getMsgtype());
        if (isDownloadableMediaMessage(payload, msgType)) {
            processFileMessage(payload, msgType);
            return;
        }
        if (!"text".equalsIgnoreCase(msgType)) {
            handleUnsupportedMessage(payload, msgType);
            return;
        }
        String content = resolveTextContent(payload);
        if (!StringUtils.hasText(content)) {
            return;
        }
        String senderId = firstText(payload.getSenderStaffId(), payload.getSenderId(), "unknown");
        String conversationId = safeTrim(payload.getConversationId());
        if (!StringUtils.hasText(conversationId)) {
            conversationId = senderId;
        }
        String externalSessionKey = "dingtalk:" + conversationId;
        String replyTarget = safeTrim(payload.getSessionWebhook());
        if (StringUtils.hasText(replyTarget)) {
            replyContexts.put(
                    replyTarget,
                    new ReplyContext(
                            conversationId,
                            firstText(payload.getSenderStaffId(), payload.getSenderId()),
                            safeTrim(payload.getConversationType()),
                            System.currentTimeMillis()));
        }
        Map<String, Object> metadata = buildMetadata(payload, msgType);
        Long ownerUserId = channelConfig.getOwnerUserId();
        ChannelUserBinding userBinding = resolveUserBinding(ownerUserId);
        ChannelMessage message = ChannelMessage.builder()
                .messageId(StringUtils.hasText(msgId) ? msgId : senderId + "_" + System.currentTimeMillis())
                .channelType(CHANNEL_TYPE)
                .senderId(senderId)
                .senderName(firstText(payload.getSenderNick(), senderId))
                .externalSessionKey(externalSessionKey)
                .replyTarget(replyTarget)
                .ownerUserId(ownerUserId)
                .routeType(userBinding == null ? channelConfig.getRouteType() : userBinding.getRouteType())
                .routeTargetId(userBinding == null ? channelConfig.getRouteTargetId() : userBinding.getRouteTargetId())
                .content(content)
                .contentType("text")
                .inputMode("text")
                .metadata(metadata)
                .timestamp(resolveTimestamp(payload.getCreateAt()))
                .rawPayload(payload)
                .build();
        lastMessageAt = System.currentTimeMillis();
        receivedCount.incrementAndGet();
        onMessage(message);
    }

    private void processFileMessage(ChatbotMessage payload, String msgType) {
        String msgId = safeTrim(payload.getMsgId());
        String senderId = firstText(payload.getSenderStaffId(), payload.getSenderId(), "unknown");
        String conversationId = safeTrim(payload.getConversationId());
        if (!StringUtils.hasText(conversationId)) {
            conversationId = senderId;
        }
        String replyTarget = safeTrim(payload.getSessionWebhook());
        if (StringUtils.hasText(replyTarget)) {
            replyContexts.put(
                    replyTarget,
                    new ReplyContext(
                            conversationId,
                            firstText(payload.getSenderStaffId(), payload.getSenderId()),
                            safeTrim(payload.getConversationType()),
                            System.currentTimeMillis()));
        }
        MessageContent fileContent = payload.getContent();
        if (fileContent == null) {
            fileContent = payload.getText();
        }
        String fileName = firstText(
                fileContent == null ? null : fileContent.getFileName(),
                fileContent == null ? null : fileContent.getText(),
                defaultInboundFileName(msgType, fileContent));
        String downloadCode = firstText(
                fileContent == null ? null : fileContent.getDownloadCode(),
                fileContent == null ? null : fileContent.getPictureDownloadCode());
        String mediaLabel = mediaLabel(msgType);
        String mediaSummary = "[" + mediaLabel + "] " + fileName;
        Map<String, Object> metadata = buildMetadata(payload, msgType);
        metadata.put("mediaType", mediaKind(msgType));
        metadata.put("downloadCodePresent", StringUtils.hasText(downloadCode));
        metadata.put("fileName", fileName);
        List<String> fileIds = registerInboundFile(downloadCode, fileName, payload.getSenderStaffId(), metadata);
        metadata.put("fileIds", fileIds);
        metadata.put(
                "parts",
                List.of(Map.of(
                        "type",
                        "file",
                        "summary",
                        mediaSummary,
                        "fileName",
                        fileName,
                        "fileIds",
                        fileIds)));
        metadata.put("mediaSummaries", List.of(mediaSummary));
        Long ownerUserId = channelConfig.getOwnerUserId();
        ChannelUserBinding userBinding = resolveUserBinding(ownerUserId);
        ChannelMessage message = ChannelMessage.builder()
                .messageId(StringUtils.hasText(msgId) ? msgId : senderId + "_" + System.currentTimeMillis())
                .channelType(CHANNEL_TYPE)
                .senderId(senderId)
                .senderName(firstText(payload.getSenderNick(), senderId))
                .externalSessionKey("dingtalk:" + conversationId)
                .replyTarget(replyTarget)
                .ownerUserId(ownerUserId)
                .routeType(userBinding == null ? channelConfig.getRouteType() : userBinding.getRouteType())
                .routeTargetId(userBinding == null ? channelConfig.getRouteTargetId() : userBinding.getRouteTargetId())
                .content(mediaSummary)
                .contentType("file")
                .inputMode(mediaKind(msgType))
                .metadata(metadata)
                .fileIds(fileIds)
                .timestamp(resolveTimestamp(payload.getCreateAt()))
                .rawPayload(payload)
                .build();
        lastMessageAt = System.currentTimeMillis();
        receivedCount.incrementAndGet();
        onMessage(message);
    }

    private ChannelUserBinding resolveUserBinding(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || channelUserBindingService == null) {
            return null;
        }
        return channelUserBindingService.findByChannelAndUser(channelConfig.getId(), ownerUserId);
    }

    protected String uploadMedia(byte[] fileBytes, String fileName) {
        try {
            String boundary = "----lingzhou-dingtalk-" + System.nanoTime();
            byte[] body = multipartFileBody(boundary, "media", fileName, fileBytes);
            HttpRequest request = HttpRequest.newBuilder(URI.create(DINGTALK_API_BASE
                            + "/media/upload?access_token="
                            + urlEncode(accessToken())
                            + "&type=file"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            Map<String, Object> response = executeJson(request);
            if (!isOk(response) || !StringUtils.hasText(asString(response.get("media_id")))) {
                throw new IllegalStateException(errorMessage(response));
            }
            String mediaId = asString(response.get("media_id"));
            logger.info(
                    "钉钉文件媒体上传完成：channelId={}, fileName={}, size={}, mediaId={}",
                    channelConfig.getId(),
                    fileName,
                    fileBytes.length,
                    abbreviate(mediaId));
            return mediaId;
        } catch (Exception ex) {
            throw new IllegalStateException("钉钉文件媒体上传失败: " + ex.getMessage(), ex);
        }
    }

    protected void sendConversationFileMessage(ReplyContext replyContext, String mediaId, String fileName) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(outboundFileMessageEndpoint(replyContext)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("x-acs-dingtalk-access-token", accessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            OBJECT_MAPPER.writeValueAsString(
                                    outboundFileMessageRequestBody(replyContext, mediaId, fileName)),
                            StandardCharsets.UTF_8))
                    .build();
            Map<String, Object> response = executeJson(request);
            if (!isOk(response)) {
                throw new IllegalStateException(errorMessage(response));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("钉钉文件消息发送失败: " + ex.getMessage(), ex);
        }
    }

    protected Map<String, Object> outboundFileMessageRequestBody(
            ReplyContext replyContext, String mediaId, String fileName) throws Exception {
        if (replyContext == null) {
            throw new IllegalStateException("缺少钉钉会话上下文，无法发送文件消息");
        }
        String robotCode = resolveRobotCode();
        if (!StringUtils.hasText(robotCode)) {
            throw new IllegalStateException("钉钉渠道缺少 clientId/robotCode，无法发送文件消息");
        }
        if (!StringUtils.hasText(mediaId)) {
            throw new IllegalStateException("缺少钉钉 mediaId，无法发送文件消息");
        }

        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        Map<String, Object> fileParam = new LinkedHashMap<>();
        fileParam.put("mediaId", mediaId);
        fileParam.put("fileName", resolvedFileName);
        fileParam.put("fileType", inferFileType(resolvedFileName));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("robotCode", robotCode.trim());
        body.put("msgKey", "sampleFile");
        body.put("msgParam", OBJECT_MAPPER.writeValueAsString(fileParam));
        if (isGroupReplyContext(replyContext)) {
            if (!StringUtils.hasText(replyContext.conversationId())) {
                throw new IllegalStateException("缺少钉钉群会话 ID，无法发送文件消息");
            }
            body.put("openConversationId", replyContext.conversationId().trim());
        } else {
            if (!StringUtils.hasText(replyContext.senderId())) {
                throw new IllegalStateException("缺少钉钉用户 ID，无法发送文件消息");
            }
            body.put("userIds", List.of(replyContext.senderId().trim()));
        }
        return body;
    }

    protected String outboundFileMessageEndpoint(ReplyContext replyContext) {
        if (isGroupReplyContext(replyContext)) {
            return DINGTALK_OPEN_API_BASE + "/v1.0/robot/groupMessages/send";
        }
        return DINGTALK_OPEN_API_BASE + "/v1.0/robot/oToMessages/batchSend";
    }

    private List<String> registerInboundFile(
            String downloadCode, String fileName, String senderStaffId, Map<String, Object> metadata) {
        if (metadata == null) {
            return List.of();
        }
        if (chatFileService == null) {
            metadata.put("uploadStatus", "UNAVAILABLE");
            logger.warn(
                    "钉钉入站文件无法注册为聊天附件：channelId={}, fileName={}, reason=chatFileService_missing",
                    channelConfig.getId(),
                    fileName);
            return List.of();
        }
        if (!StringUtils.hasText(downloadCode)) {
            metadata.put("uploadStatus", "MEDIA_MISSING");
            logger.warn("钉钉入站文件缺少 downloadCode：channelId={}, fileName={}", channelConfig.getId(), fileName);
            return List.of();
        }
        try {
            String downloadUrl = resolveInboundFileDownloadUrl(downloadCode);
            metadata.put("downloadUrlPresent", StringUtils.hasText(downloadUrl));
            if (!StringUtils.hasText(downloadUrl)) {
                throw new IllegalStateException("missing download url");
            }
            byte[] content = downloadInboundFile(downloadUrl);
            String uploadFileName = StringUtils.hasText(fileName) ? fileName.trim() : "dingtalk-file";
            ChatFileService.UploadResponse upload = chatFileService.uploadBytes(
                    uploadFileName,
                    content,
                    detectContentType(uploadFileName),
                    channelConfig.getOwnerUserId(),
                    null);
            if (upload == null || !StringUtils.hasText(upload.id())) {
                throw new IllegalStateException("missing uploaded file id");
            }
            metadata.put("fileId", upload.id());
            metadata.put("objectName", upload.file() == null ? null : upload.file().objectName());
            metadata.put("uploadStatus", "UPLOADED");
            logger.info(
                    "钉钉入站文件已注册为聊天附件：channelId={}, senderStaffId={}, fileName={}, fileId={}, size={}",
                    channelConfig.getId(),
                    senderStaffId,
                    uploadFileName,
                    upload.id(),
                    upload.size());
            return List.of(upload.id());
        } catch (Exception ex) {
            metadata.put("uploadStatus", "FAILED");
            metadata.put("uploadError", ex.getMessage());
            logger.warn(
                    "钉钉入站文件注册为聊天附件失败：channelId={}, fileName={}, error={}",
                    channelConfig.getId(),
                    fileName,
                    ex.getMessage(),
                    ex);
            return List.of();
        }
    }

    protected String resolveInboundFileDownloadUrl(String downloadCode) throws Exception {
        Map<String, Object> body = inboundFileDownloadRequestBody(downloadCode);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(DINGTALK_OPEN_API_BASE + "/v1.0/robot/messageFiles/download"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("x-acs-dingtalk-access-token", accessToken())
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        Map<String, Object> response = executeJson(request);
        if (!isOk(response)) {
            throw new IllegalStateException(errorMessage(response));
        }
        String downloadUrl = resolveDownloadUrl(response.get("result"));
        return StringUtils.hasText(downloadUrl) ? downloadUrl : resolveDownloadUrl(response);
    }

    protected Map<String, Object> inboundFileDownloadRequestBody(String downloadCode) {
        String robotCode = resolveRobotCode();
        if (!StringUtils.hasText(robotCode)) {
            throw new IllegalStateException("钉钉渠道缺少 clientId/robotCode，无法下载机器人接收的文件");
        }
        return Map.of("downloadCode", downloadCode, "robotCode", robotCode.trim());
    }

    private String resolveDownloadUrl(Object result) {
        if (result instanceof Map<?, ?> resultMap) {
            String downloadUrl = firstText(
                    asString(resultMap.get("download_url")),
                    asString(resultMap.get("downloadUrl")),
                    asString(resultMap.get("url")));
            if (StringUtils.hasText(downloadUrl)) {
                return downloadUrl;
            }
        }
        return asString(result);
    }

    protected byte[] downloadInboundFile(String downloadUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl.trim()))
                .timeout(Duration.ofSeconds(getLong("mediaDownloadTimeoutSeconds", 60L)))
                .GET()
                .build();
        HttpResponse<byte[]> response = apiHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("download failed: HTTP " + response.statusCode());
        }
        byte[] content = response.body();
        if (content == null || content.length == 0) {
            throw new IllegalStateException("downloaded file is empty");
        }
        return content;
    }

    private String accessToken() throws Exception {
        long now = System.currentTimeMillis();
        String token = accessToken;
        if (StringUtils.hasText(token) && accessTokenExpireAt - ACCESS_TOKEN_REFRESH_SKEW_MS > now) {
            return token;
        }
        synchronized (this) {
            token = accessToken;
            if (StringUtils.hasText(token) && accessTokenExpireAt - ACCESS_TOKEN_REFRESH_SKEW_MS > now) {
                return token;
            }
            String clientId = dingtalkConfigString("clientId", "client_id");
            String clientSecret = dingtalkConfigString("clientSecret", "client_secret");
            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
                throw new IllegalStateException("钉钉渠道缺少 clientId/clientSecret");
            }
            Map<String, Object> body = Map.of("appKey", clientId.trim(), "appSecret", clientSecret.trim());
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(DINGTALK_OPEN_API_BASE + "/v1.0/oauth2/accessToken"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            Map<String, Object> response = executeJson(request);
            String resolvedAccessToken =
                    firstText(asString(response.get("accessToken")), asString(response.get("access_token")));
            if (!isOk(response) || !StringUtils.hasText(resolvedAccessToken)) {
                throw new IllegalStateException(errorMessage(response));
            }
            accessToken = resolvedAccessToken;
            long expiresInSeconds = asLong(firstPresent(response, "expireIn", "expiresIn", "expires_in"), 7200L);
            accessTokenExpireAt = now + Math.max(60L, expiresInSeconds) * 1000L;
            return accessToken;
        }
    }

    private String resolveRobotCode() {
        String configured = dingtalkConfigString("robotCode", "robot_code");
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return dingtalkConfigString("clientId", "client_id");
    }

    private String dingtalkConfigString(String camelKey, String snakeKey) {
        String camelValue = getString(camelKey, "");
        if (StringUtils.hasText(camelValue)) {
            return camelValue.trim();
        }
        String snakeValue = getString(snakeKey, "");
        return StringUtils.hasText(snakeValue) ? snakeValue.trim() : "";
    }

    private Map<String, Object> executeJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = apiHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        if (!StringUtils.hasText(response.body())) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private byte[] multipartFileBody(String boundary, String fieldName, String fileName, byte[] content) {
        String header = "--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\""
                + fieldName
                + "\"; filename=\""
                + fileName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + content.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(content, 0, body, headerBytes.length, content.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + content.length, footerBytes.length);
        return body;
    }

    private boolean isOk(Map<String, Object> response) {
        if (response == null) {
            return true;
        }
        Object errcode = firstPresent(response, "errcode", "code");
        return errcode == null || "0".equals(String.valueOf(errcode));
    }

    private String errorMessage(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "empty response";
        }
        String code = asString(firstPresent(response, "errcode", "code"));
        String message = firstText(asString(response.get("errmsg")), asString(response.get("message")));
        String requestId = firstText(asString(response.get("requestId")), asString(response.get("request_id")));
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(message)) {
            builder.append(message);
        } else {
            builder.append(response);
        }
        if (StringUtils.hasText(code)) {
            builder.append(" (code=").append(code).append(")");
        }
        if (StringUtils.hasText(requestId)) {
            builder.append(" (requestId=").append(requestId).append(")");
        }
        return builder.toString();
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void handleUnsupportedMessage(ChatbotMessage payload, String msgType) {
        logger.info(
                "钉钉非文本消息已忽略：channelId={}, msgId={}, msgType={}",
                channelConfig.getId(),
                payload.getMsgId(),
                msgType);
        if (!getBoolean("replyUnsupported", false)) {
            return;
        }
        String webhook = safeTrim(payload.getSessionWebhook());
        if (!StringUtils.hasText(webhook)) {
            return;
        }
        try {
            replyText(webhook, "暂不支持处理该类型消息，请发送文本内容。");
        } catch (Exception ex) {
            logger.warn("钉钉非文本提示发送失败：channelId={}, error={}", channelConfig.getId(), ex.getMessage());
        }
    }

    private Map<String, Object> buildMetadata(ChatbotMessage payload, String msgType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("msgType", msgType);
        metadata.put("conversationId", payload.getConversationId());
        metadata.put("conversationType", payload.getConversationType());
        metadata.put("conversationTitle", payload.getConversationTitle());
        metadata.put("senderId", payload.getSenderId());
        metadata.put("senderStaffId", payload.getSenderStaffId());
        metadata.put("senderCorpId", payload.getSenderCorpId());
        metadata.put("chatbotCorpId", payload.getChatbotCorpId());
        metadata.put("chatbotUserId", payload.getChatbotUserId());
        metadata.put("sessionWebhookExpiredTime", payload.getSessionWebhookExpiredTime());
        metadata.put("isInAtList", payload.getInAtList());
        metadata.put("isAdmin", payload.getAdmin());
        return metadata;
    }

    private boolean isDuplicate(String msgId) {
        long now = System.currentTimeMillis();
        pruneRecentMessageIds(now);
        Long previous = recentMessageIds.putIfAbsent(msgId, now);
        return previous != null && now - previous <= getLong("duplicateTtlMs", DEFAULT_DUPLICATE_TTL_MS);
    }

    private void pruneRecentMessageIds(long now) {
        long ttl = getLong("duplicateTtlMs", DEFAULT_DUPLICATE_TTL_MS);
        Iterator<Map.Entry<String, Long>> iterator = recentMessageIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > ttl) {
                iterator.remove();
            }
        }
    }

    private String resolveTextContent(ChatbotMessage payload) {
        String text = contentText(payload.getText());
        if (StringUtils.hasText(text)) {
            return text;
        }
        return contentText(payload.getContent());
    }

    private String contentText(MessageContent content) {
        if (content == null) {
            return "";
        }
        return firstText(content.getContent(), content.getText());
    }

    private boolean isDownloadableMediaMessage(ChatbotMessage payload, String msgType) {
        MessageContent content = payload == null ? null : payload.getContent();
        if (content == null && payload != null) {
            content = payload.getText();
        }
        if (content != null
                && StringUtils.hasText(firstText(content.getDownloadCode(), content.getPictureDownloadCode()))) {
            return true;
        }
        if ("file".equalsIgnoreCase(msgType)
                || "picture".equalsIgnoreCase(msgType)
                || "audio".equalsIgnoreCase(msgType)
                || "video".equalsIgnoreCase(msgType)) {
            return true;
        }
        return content != null
                && ("file".equalsIgnoreCase(content.getType())
                        || "picture".equalsIgnoreCase(content.getType())
                        || "audio".equalsIgnoreCase(content.getType())
                        || "video".equalsIgnoreCase(content.getType()));
    }

    private String mediaKind(String msgType) {
        if ("picture".equalsIgnoreCase(msgType)) {
            return "image";
        }
        if ("audio".equalsIgnoreCase(msgType)) {
            return "voice";
        }
        if ("video".equalsIgnoreCase(msgType)) {
            return "video";
        }
        return "file";
    }

    private String mediaLabel(String msgType) {
        return switch (mediaKind(msgType)) {
            case "image" -> "图片";
            case "voice" -> "语音";
            case "video" -> "视频";
            default -> "文件";
        };
    }

    private boolean isGroupReplyContext(ReplyContext replyContext) {
        return replyContext != null && "2".equals(safeTrim(replyContext.conversationType()));
    }

    private String inferFileType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String normalized = fileName.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot >= normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot + 1).toLowerCase();
    }

    private String defaultInboundFileName(String msgType, MessageContent content) {
        String fileId = content == null ? "" : safeTrim(content.getFileId());
        String suffix = StringUtils.hasText(fileId) ? "-" + fileId : "";
        return switch (mediaKind(msgType)) {
            case "image" -> "dingtalk-image" + suffix + ".jpg";
            case "voice" -> "dingtalk-audio" + suffix + ".amr";
            case "video" -> "dingtalk-video" + suffix + ".mp4";
            default -> "dingtalk-file" + suffix;
        };
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
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".amr")) {
            return "audio/amr";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    private LocalDateTime resolveTimestamp(Long createAt) {
        if (createAt == null || createAt <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(createAt), ZoneId.systemDefault());
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = safeTrim(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
    }

    protected record ReplyContext(String conversationId, String senderId, String conversationType, long createdAt) {}
}
