package lingzhou.agent.backend.business.channel.service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import lingzhou.agent.backend.business.channel.adapter.ChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import lingzhou.agent.backend.business.channel.model.ChannelDispatchResult;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChannelMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(ChannelMessageRouter.class);
    private static final Duration RECENT_FILE_CONTEXT_TTL = Duration.ofMinutes(30);

    private final ChannelConversationBridgeService channelConversationBridgeService;
    private final ChannelSessionBindingService channelSessionBindingService;
    private final MinioService minioService;
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "channel-router");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingChannelFileContext> pendingFileContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingChannelFileContext> recentFileContexts = new ConcurrentHashMap<>();

    public ChannelMessageRouter(
            ChannelConversationBridgeService channelConversationBridgeService,
            ChannelSessionBindingService channelSessionBindingService,
            MinioService minioService) {
        this.channelConversationBridgeService = channelConversationBridgeService;
        this.channelSessionBindingService = channelSessionBindingService;
        this.minioService = minioService;
    }

    public void enqueue(ChannelMessage message, ChannelAdapter adapter, ChannelConfig channelConfig) {
        executorService.execute(() -> process(message, adapter, channelConfig));
    }

    private void process(ChannelMessage message, ChannelAdapter adapter, ChannelConfig channelConfig) {
        String lockKey = channelConfig.getId() + ":" + message.getExternalSessionKey();
        ReentrantLock lock = sessionLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        Long replyOwnerUserId = message.getOwnerUserId();
        boolean typingStarted = false;
        try {
            ChannelSessionBinding binding = channelSessionBindingService.touch(
                    channelConfig.getId(),
                    channelConfig.getChannelType(),
                    message.getExternalSessionKey(),
                    message.getSenderId(),
                    message.getSenderName(),
                    message.getReplyTarget(),
                    message.getOwnerUserId());
            replyOwnerUserId = message.getOwnerUserId() != null
                    ? message.getOwnerUserId()
                    : (binding == null ? null : binding.getOwnerUserId());
            if (ChannelArtifactSupport.isStandaloneFileMessage(message)) {
                PendingChannelFileContext fileContext = PendingChannelFileContext.from(message);
                pendingFileContexts.put(lockKey, fileContext);
                recentFileContexts.put(lockKey, fileContext);
                logger.info(
                        "渠道文件消息已暂存，等待用户问题：channelId={}, senderId={}, fileCount={}",
                        channelConfig.getId(),
                        message.getSenderId(),
                        ChannelArtifactSupport.resolveFileIds(message).size());
                if (StringUtils.hasText(message.getReplyTarget())) {
                    adapter.sendMessage(replyOwnerUserId, message.getReplyTarget(), "已收到文件，请继续发送你的问题或处理要求。");
                }
                return;
            }
            mergePendingFileContext(lockKey, message);
            if (StringUtils.hasText(message.getReplyTarget())) {
                adapter.startTyping(replyOwnerUserId, message.getReplyTarget());
                typingStarted = true;
            }
            ChannelDispatchResult result = channelConversationBridgeService.dispatch(
                    channelConfig, binding, message, binding == null ? null : binding.getChatSessionCode());
            if (binding != null && StringUtils.hasText(result.sessionId())) {
                channelSessionBindingService.bindSessionCode(binding.getId(), result.sessionId());
                binding.setChatSessionCode(result.sessionId());
            }
            if (StringUtils.hasText(message.getReplyTarget()) && StringUtils.hasText(result.replyContent())) {
                adapter.sendMessage(replyOwnerUserId, message.getReplyTarget(), result.replyContent());
            }
            if (StringUtils.hasText(message.getReplyTarget())
                    && result.generatedFiles() != null
                    && !result.generatedFiles().isEmpty()) {
                sendGeneratedFiles(
                        replyOwnerUserId, message.getReplyTarget(), result.generatedFiles(), adapter, channelConfig);
            }
        } catch (Exception ex) {
            logger.error(
                    "渠道消息处理失败：channelId={}, senderId={}, error={}",
                    channelConfig.getId(),
                    message.getSenderId(),
                    ex.getMessage(),
                    ex);
            if (StringUtils.hasText(message.getReplyTarget())) {
                try {
                    adapter.sendMessage(replyOwnerUserId, message.getReplyTarget(), "抱歉，消息处理失败：" + ex.getMessage());
                } catch (Exception sendEx) {
                    logger.error(
                            "渠道错误消息发送失败：channelId={}, error={}", channelConfig.getId(), sendEx.getMessage(), sendEx);
                }
            }
        } finally {
            if (typingStarted && StringUtils.hasText(message.getReplyTarget())) {
                try {
                    adapter.stopTyping(replyOwnerUserId, message.getReplyTarget());
                } catch (Exception ex) {
                    logger.warn(
                            "渠道停止输入中状态失败：channelId={}, senderId={}, error={}",
                            channelConfig.getId(),
                            message.getSenderId(),
                            ex.getMessage());
                }
            }
            lock.unlock();
        }
    }

    private void sendGeneratedFiles(
            Long ownerUserId,
            String replyTarget,
            List<ChannelDispatchResult.GeneratedFile> files,
            ChannelAdapter adapter,
            ChannelConfig channelConfig) {
        if (files == null || files.isEmpty() || !StringUtils.hasText(replyTarget)) {
            return;
        }
        if (!adapter.supportsFileMessage()) {
            String fileMessage = buildGeneratedFilesMessage(files);
            if (StringUtils.hasText(fileMessage)) {
                adapter.sendMessage(ownerUserId, replyTarget, fileMessage);
            }
            logger.info(
                    "渠道产物文件降级为链接文本发送：channelId={}, channelType={}, adapterType={}, adapterChannelType={}, fileCount={}",
                    channelConfig.getId(),
                    channelConfig.getChannelType(),
                    adapter.getClass().getName(),
                    adapter.getChannelType(),
                    files.size());
            return;
        }

        int sentCount = 0;
        List<String> failedFiles = new ArrayList<>();
        for (ChannelDispatchResult.GeneratedFile file : files) {
            if (file == null) {
                continue;
            }
            try {
                GeneratedFilePayload payload = loadGeneratedFile(file);
                adapter.sendFileMessage(ownerUserId, replyTarget, payload.content(), payload.fileName(), null);
                sentCount++;
                logger.info(
                        "渠道产物文件已通过文件消息发送：channelId={}, channelType={}, fileName={}, objectName={}, size={}",
                        channelConfig.getId(),
                        channelConfig.getChannelType(),
                        payload.fileName(),
                        payload.objectName(),
                        payload.content().length);
            } catch (Exception ex) {
                String fileName = resolveGeneratedFileName(file, file == null ? null : file.objectName());
                failedFiles.add(fileName);
                logger.warn(
                        "渠道产物文件发送失败：channelId={}, channelType={}, fileName={}, objectName={}, downloadUrl={}, error={}",
                        channelConfig.getId(),
                        channelConfig.getChannelType(),
                        fileName,
                        file == null ? null : file.objectName(),
                        file == null ? null : file.downloadUrl(),
                        ex.getMessage(),
                        ex);
            }
        }
        if (sentCount == 0 && !failedFiles.isEmpty()) {
            adapter.sendMessage(ownerUserId, replyTarget, "文件已生成，但渠道文件消息发送失败，请稍后重试。");
        }
    }

    private GeneratedFilePayload loadGeneratedFile(ChannelDispatchResult.GeneratedFile file) throws Exception {
        String objectName = resolveArtifactObjectName(file);
        if (!StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("缺少可读取的产物 objectName");
        }
        String fileName = resolveGeneratedFileName(file, objectName);
        try (InputStream inputStream = minioService.getFile(objectName)) {
            byte[] content = inputStream.readAllBytes();
            if (content.length == 0) {
                throw new IllegalStateException("产物文件内容为空");
            }
            return new GeneratedFilePayload(fileName, objectName, content);
        }
    }

    private String resolveArtifactObjectName(ChannelDispatchResult.GeneratedFile file) {
        if (file == null) {
            return "";
        }
        if (StringUtils.hasText(file.objectName())) {
            return file.objectName().trim();
        }
        return resolveObjectNameFromDownloadUrl(file.downloadUrl());
    }

    private String resolveObjectNameFromDownloadUrl(String downloadUrl) {
        if (!StringUtils.hasText(downloadUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(downloadUrl.trim());
            String queryObjectName = queryParam(uri.getRawQuery(), "objectName");
            if (StringUtils.hasText(queryObjectName)) {
                return queryObjectName;
            }
            String path = uri.getPath();
            String marker = "/api/files/artifacts/";
            int markerIndex = path == null ? -1 : path.indexOf(marker);
            if (markerIndex < 0) {
                return "";
            }
            String tail = path.substring(markerIndex + marker.length());
            int slashIndex = tail.indexOf('/');
            if (slashIndex <= 0) {
                return "";
            }
            String artifactId = tail.substring(0, slashIndex);
            return minioService.fromArtifactId(artifactId);
        } catch (Exception ex) {
            logger.warn("解析产物下载地址失败：downloadUrl={}, error={}", downloadUrl, ex.getMessage());
            return "";
        }
    }

    private String queryParam(String rawQuery, String name) {
        if (!StringUtils.hasText(rawQuery) || !StringUtils.hasText(name)) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            int equalsIndex = pair.indexOf('=');
            String key = equalsIndex < 0 ? pair : pair.substring(0, equalsIndex);
            if (!name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                continue;
            }
            String value = equalsIndex < 0 ? "" : pair.substring(equalsIndex + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String resolveGeneratedFileName(ChannelDispatchResult.GeneratedFile file, String objectName) {
        if (file != null && StringUtils.hasText(file.fileName())) {
            return file.fileName().trim();
        }
        if (StringUtils.hasText(objectName)) {
            String normalized = objectName.trim().replace('\\', '/');
            int slashIndex = normalized.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
                return normalized.substring(slashIndex + 1);
            }
            return normalized;
        }
        return "artifact";
    }

    private String buildGeneratedFilesMessage(List<ChannelDispatchResult.GeneratedFile> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("下载文件：");
        boolean first = true;
        for (ChannelDispatchResult.GeneratedFile file : files) {
            if (file == null || !StringUtils.hasText(file.downloadUrl())) {
                continue;
            }
            if (!first) {
                builder.append("\n");
            }
            String fileName = StringUtils.hasText(file.fileName()) ? file.fileName().trim() : "文件";
            builder.append("[").append(fileName).append("](").append(file.downloadUrl().trim()).append(")");
            first = false;
        }
        return first ? "" : builder.toString();
    }

    private record GeneratedFilePayload(String fileName, String objectName, byte[] content) {}

    private void mergePendingFileContext(String lockKey, ChannelMessage message) {
        if (message == null) {
            return;
        }
        PendingChannelFileContext pending = pendingFileContexts.remove(lockKey);
        if (pending == null && ChannelArtifactSupport.resolveFileIds(message).isEmpty()) {
            PendingChannelFileContext recent = recentFileContexts.get(lockKey);
            if (isExpired(recent)) {
                recentFileContexts.remove(lockKey);
                recent = null;
            }
            pending = recent;
        }
        if (pending == null) {
            if (!ChannelArtifactSupport.resolveFileIds(message).isEmpty()) {
                recentFileContexts.put(lockKey, PendingChannelFileContext.from(message));
            }
            return;
        }
        LinkedHashSet<String> mergedFileIds = new LinkedHashSet<>(pending.fileIds());
        mergedFileIds.addAll(ChannelArtifactSupport.resolveFileIds(message));
        message.setFileIds(List.copyOf(mergedFileIds));

        Map<String, Object> metadata = new LinkedHashMap<>(
                message.getMetadata() == null ? Map.of() : message.getMetadata());
        metadata.put(
                "channelFileContext",
                Map.of(
                        "messageId", pending.messageId(),
                        "fileIds", pending.fileIds(),
                        "mediaSummaries", pending.mediaSummaries(),
                        "receivedAt", pending.receivedAt().toString()));
        message.setMetadata(metadata);
        recentFileContexts.put(lockKey, pending);
        logger.info(
                "渠道待处理文件上下文已合并：lockKey={}, fileCount={}, summaryCount={}",
                lockKey,
                pending.fileIds().size(),
                pending.mediaSummaries().size());
    }

    private record PendingChannelFileContext(
            String messageId, List<String> fileIds, List<String> mediaSummaries, LocalDateTime receivedAt) {

        private static PendingChannelFileContext from(ChannelMessage message) {
            return new PendingChannelFileContext(
                    message == null ? null : message.getMessageId(),
                    List.copyOf(ChannelArtifactSupport.resolveFileIds(message)),
                    List.copyOf(ChannelArtifactSupport.resolveMediaSummaries(message)),
                    LocalDateTime.now());
        }
    }

    private boolean isExpired(PendingChannelFileContext fileContext) {
        if (fileContext == null || fileContext.receivedAt() == null) {
            return true;
        }
        return fileContext.receivedAt().plus(RECENT_FILE_CONTEXT_TTL).isBefore(LocalDateTime.now());
    }
}
