package lingzhou.agent.backend.business.channel.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import lingzhou.agent.backend.business.channel.domain.enums.ChannelRouteType;
import lingzhou.agent.backend.business.channel.model.ChannelDispatchResult;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class ChannelConversationBridgeService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private final ChatConversationService chatConversationService;

    public ChannelConversationBridgeService(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    public ChannelDispatchResult dispatch(
            ChannelConfig config, ChannelSessionBinding binding, ChannelMessage message, String sessionCode) {
        if (ChannelArtifactSupport.requiresReadableFileButMissing(message)) {
            log.warn(
                    "渠道文件处理请求缺少可读取文件：channelId={}, channelType={}, sessionId={}, messageId={}",
                    config == null ? null : config.getId(),
                    message == null ? null : message.getChannelType(),
                    sessionCode,
                    message == null ? null : message.getMessageId());
            return new ChannelDispatchResult(
                    sessionCode,
                    "这条消息没有关联到可读取的文件，无法解析或生成文件。请重新发送文件后，再发送处理要求。");
        }
        Flux<ServerSentEvent<String>> stream = createStream(config, binding, message, sessionCode);
        List<ServerSentEvent<String>> events = stream.collectList().block(DEFAULT_TIMEOUT);
        String resolvedSessionId = sessionCode;
        StringBuilder reply = new StringBuilder();
        boolean artifactRequired = ChannelArtifactSupport.requiresArtifactOutput(message);
        Map<String, ChannelDispatchResult.GeneratedFile> generatedFiles = new LinkedHashMap<>();
        String error = null;
        int messageEventCount = 0;
        int resultEventCount = 0;
        if (events != null) {
            for (ServerSentEvent<String> event : events) {
                Map<String, Object> payload = parsePayload(event.data());
                String type = payload.get("type") == null ? null : String.valueOf(payload.get("type"));
                if (!StringUtils.hasText(type) && StringUtils.hasText(event.event())) {
                    type = resolveTypeFromEventName(event.event());
                }
                Object content = payload.containsKey("content") ? payload.get("content") : payload;
                if ("meta".equals(type) && content instanceof Map<?, ?> meta) {
                    Object sessionId = meta.get("sessionId");
                    if (sessionId != null && StringUtils.hasText(String.valueOf(sessionId))) {
                        resolvedSessionId = String.valueOf(sessionId);
                    }
                    continue;
                }
                if ("message".equals(type) && content != null) {
                    messageEventCount++;
                    reply.append(String.valueOf(content));
                    continue;
                }
                if ("result".equals(type) && content != null) {
                    resultEventCount++;
                    mergeGeneratedFiles(generatedFiles, ChannelArtifactSupport.extractGeneratedFiles(content));
                    continue;
                }
                if ("error".equals(type) && content != null) {
                    error = String.valueOf(content);
                }
            }
        }
        if (StringUtils.hasText(error) && reply.length() == 0) {
            reply.append(error);
        }
        List<ChannelDispatchResult.GeneratedFile> files = List.copyOf(generatedFiles.values());
        String replyContent = reply.toString();
        if (!files.isEmpty()) {
            replyContent = ChannelArtifactSupport.stripDownloadUrls(replyContent);
        } else if (artifactRequired && !StringUtils.hasText(error)) {
            replyContent = "文件没有生成成功：本轮没有检测到真实产物工具结果，请重新发起生成文件请求。";
        }
        log.info(
                "渠道会话响应已聚合：channelId={}, channelType={}, sessionId={}, artifactRequired={}, messageEvents={}, resultEvents={}, generatedFileCount={}",
                config == null ? null : config.getId(),
                message == null ? null : message.getChannelType(),
                resolvedSessionId,
                artifactRequired,
                messageEventCount,
                resultEventCount,
                files.size());
        return new ChannelDispatchResult(resolvedSessionId, replyContent, files);
    }

    private Flux<ServerSentEvent<String>> createStream(
            ChannelConfig config, ChannelSessionBinding binding, ChannelMessage message, String sessionCode) {
        String routeTypeValue =
                StringUtils.hasText(message.getRouteType()) ? message.getRouteType() : config.getRouteType();
        Long routeTargetId =
                message.getRouteTargetId() != null ? message.getRouteTargetId() : config.getRouteTargetId();
        Long ownerUserId = binding != null && binding.getOwnerUserId() != null
                ? binding.getOwnerUserId()
                : message.getOwnerUserId();
        if (ownerUserId == null) {
            ownerUserId = config.getOwnerUserId();
        }
        ChannelRouteType routeType = ChannelRouteType.fromValue(routeTypeValue);
        String inboundText = buildInboundText(message);
        List<String> fileIds = ChannelArtifactSupport.resolveFileIds(message);
        boolean artifactRequired = ChannelArtifactSupport.requiresArtifactOutput(message);
        Map<String, Object> options = ChannelArtifactSupport.buildRuntimeOptions(fileIds, artifactRequired);
        log.info(
                "渠道运行时请求已准备：channelId={}, channelType={}, routeType={}, sessionId={}, fileCount={}, artifactRequired={}, parseAttachments={}",
                config.getId(),
                message == null ? null : message.getChannelType(),
                routeType,
                sessionCode,
                fileIds.size(),
                artifactRequired,
                options == null ? null : options.get("parseAttachments"));
        return switch (routeType) {
            case GENERAL_CHAT -> chatConversationService.streamChannelGeneral(
                    new ChatConversationService.GeneralChatRequest(
                            inboundText, fileIds, sessionCode, "normal", null, null, options, null, null),
                    ownerUserId);
            case SKILL_CHAT -> chatConversationService.streamChannelSkill(
                    new ChatConversationService.SkillChatRequest(
                            routeTargetId, inboundText, fileIds, sessionCode, "normal", null, null, options),
                    ownerUserId);
            case DATASET_CHAT -> chatConversationService.streamChannelDataset(
                    routeTargetId,
                    new ChatConversationService.DatasetChatRequest(
                            inboundText, sessionCode, "normal", null, null, null),
                    ownerUserId);
        };
    }

    private Map<String, Object> parsePayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return JSON.parseObject(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "message");
            fallback.put("content", raw);
            return fallback;
        }
    }

    private String resolveTypeFromEventName(String eventName) {
        if (!StringUtils.hasText(eventName)) {
            return null;
        }
        return switch (eventName.trim()) {
            case "message", "content_delta" -> "message";
            case "tool", "tool_call_started" -> "tool";
            case "result", "tool_call_completed" -> "result";
            case "meta" -> "meta";
            case "error" -> "error";
            default -> null;
        };
    }

    private void mergeGeneratedFiles(
            Map<String, ChannelDispatchResult.GeneratedFile> target,
            List<ChannelDispatchResult.GeneratedFile> files) {
        if (target == null || files == null || files.isEmpty()) {
            return;
        }
        for (ChannelDispatchResult.GeneratedFile file : files) {
            if (file == null) {
                continue;
            }
            String key = firstText(file.downloadUrl(), file.objectName(), file.assetCode(), file.id(), file.fileName());
            if (StringUtils.hasText(key)) {
                target.putIfAbsent(key, file);
            }
        }
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

    private String buildInboundText(ChannelMessage message) {
        if (message == null) {
            return "";
        }
        String baseContent =
                message.getContent() == null ? "" : message.getContent().trim();
        Map<String, Object> metadata = message.getMetadata();
        boolean hasMetadata = metadata != null && !metadata.isEmpty();
        if (!hasMetadata
                && (!StringUtils.hasText(message.getInputMode()) || "text".equalsIgnoreCase(message.getInputMode()))) {
            return baseContent;
        }

        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(baseContent)) {
            builder.append(baseContent);
        } else {
            builder.append("用户发送了一条")
                    .append(resolveInputModeLabel(message.getInputMode()))
                    .append("消息。");
        }
        builder.append("\n\n[渠道上下文]");
        builder.append("\n渠道: ").append(defaultText(message.getChannelType(), "unknown"));
        builder.append("\n发送者: ").append(defaultText(message.getSenderName(), message.getSenderId()));
        builder.append("\n输入类型: ").append(resolveInputModeLabel(message.getInputMode()));

        Object mediaSummaries = metadata == null ? null : metadata.get("mediaSummaries");
        if (mediaSummaries instanceof List<?> summaries && !summaries.isEmpty()) {
            builder.append("\n媒体摘要:");
            for (Object summary : summaries) {
                if (summary != null && StringUtils.hasText(String.valueOf(summary))) {
                    builder.append("\n- ").append(summary);
                }
            }
        }
        return builder.toString();
    }

    private String resolveInputModeLabel(String inputMode) {
        if (!StringUtils.hasText(inputMode)) {
            return "文本";
        }
        return switch (inputMode.trim().toLowerCase()) {
            case "image" -> "图片";
            case "voice" -> "语音";
            case "video" -> "视频";
            case "file" -> "文件";
            case "mixed" -> "混合";
            default -> "文本";
        };
    }

    private String defaultText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback;
    }
}
