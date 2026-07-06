package lingzhou.agent.backend.capability.agentruntime.capabilities;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.service.ConversationEventService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import lingzhou.agent.backend.business.chat.service.StructuredArtifactExtractor;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillReadFactContract;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContract;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContractSupport;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentExecutionSnapshotService;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class EventPersistenceCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private static final Logger logger = LoggerFactory.getLogger(EventPersistenceCapabilityAdapter.class);

    private final ConversationEventService conversationEventService;
    private final ConversationHistoryService conversationHistoryService;
    private final ContextEngineeringService contextEngineeringService;
    private final SkillCatalogService skillCatalogService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final RuntimeSkillStateContractSupport runtimeSkillStateContractSupport;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;
    private final PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService;

    public EventPersistenceCapabilityAdapter(
            ConversationEventService conversationEventService,
            ConversationHistoryService conversationHistoryService,
            ContextEngineeringService contextEngineeringService,
            SkillCatalogService skillCatalogService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            RuntimeSkillStateContractSupport runtimeSkillStateContractSupport,
            TokenUsageCapabilityAdapter tokenUsageCapability,
            PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService) {
        super(RuntimeCapabilitySlot.EVENT_PERSISTENCE, "event-persistence", RuntimeCapabilityStatus.ACTIVE);
        this.conversationEventService = conversationEventService;
        this.conversationHistoryService = conversationHistoryService;
        this.contextEngineeringService = contextEngineeringService;
        this.skillCatalogService = skillCatalogService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.runtimeSkillStateContractSupport = runtimeSkillStateContractSupport;
        this.tokenUsageCapability = tokenUsageCapability;
        this.personalAgentExecutionSnapshotService = personalAgentExecutionSnapshotService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistFailedGeneralResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String friendlyMessage,
            String answer,
            List<Map<String, Object>> segments,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt,
            List<Map<String, Object>> toolEvents) {
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionFailed(prepared, friendlyMessage, Map.of());
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.failMessage(
                context,
                friendlyMessage,
                answer,
                segmentsJson,
                paramsJson,
                System.currentTimeMillis() - startedAt,
                usagePayload);
        conversationEventService.upsertAssistantMessage(
                context, answer, segmentsJson, finalizedPrepared.messageType(), paramsJson, null);
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistCompletedGeneralResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String answer,
            List<Map<String, Object>> segments,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt,
            List<Map<String, Object>> toolEvents) {
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionCompleted(prepared, Map.of());
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.completeMessage(
                context,
                answer,
                segmentsJson,
                null,
                finalizedPrepared.fileListJson(),
                paramsJson,
                System.currentTimeMillis() - startedAt,
                usagePayload);
        conversationEventService.upsertAssistantMessage(
                context, answer, segmentsJson, finalizedPrepared.messageType(), paramsJson, null);
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistInterruptedGeneralResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String answer,
            List<Map<String, Object>> segments,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt,
            List<Map<String, Object>> toolEvents) {
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionCancelled(prepared, Map.of());
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.interruptMessage(
                context, answer, segmentsJson, paramsJson, System.currentTimeMillis() - startedAt, usagePayload);
        conversationEventService.upsertAssistantMessage(
                context, answer, segmentsJson, finalizedPrepared.messageType(), paramsJson, null);
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistFailedSkillResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String friendlyMessage,
            String answer,
            List<Map<String, Object>> segments,
            List<Map<String, Object>> toolEvents,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt) {
        Map<String, Object> finalArtifact = extractFinalArtifact(toolEvents, answer);
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionFailed(prepared, friendlyMessage, finalArtifact);
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents, finalArtifact);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.failMessage(
                context,
                friendlyMessage,
                answer,
                segmentsJson,
                paramsJson,
                System.currentTimeMillis() - startedAt,
                usagePayload);
        conversationEventService.upsertAssistantMessage(
                context,
                answer,
                segmentsJson,
                finalizedPrepared.messageType(),
                paramsJson,
                toArtifactSummaryJson(finalArtifact));
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistCompletedSkillResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String answer,
            List<Map<String, Object>> segments,
            List<Map<String, Object>> toolEvents,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt) {
        Map<String, Object> finalArtifact = extractFinalArtifact(toolEvents, answer);
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionCompleted(prepared, finalArtifact);
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents, finalArtifact);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.completeMessage(
                context,
                answer,
                segmentsJson,
                toArtifactSummaryJson(finalArtifact),
                finalizedPrepared.fileListJson(),
                paramsJson,
                System.currentTimeMillis() - startedAt,
                usagePayload);
        conversationEventService.upsertAssistantMessage(
                context,
                answer,
                segmentsJson,
                finalizedPrepared.messageType(),
                paramsJson,
                toArtifactSummaryJson(finalArtifact));
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistInterruptedSkillResponse(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            String answer,
            List<Map<String, Object>> segments,
            List<Map<String, Object>> toolEvents,
            ConversationMessageUsagePayload usagePayload,
            RuntimeRunUsageSnapshot usageSnapshot,
            long startedAt) {
        Map<String, Object> finalArtifact = extractFinalArtifact(toolEvents, answer);
        ChatRuntimePreparedRequest finalizedPrepared =
                personalAgentExecutionSnapshotService.markExecutionCancelled(prepared, finalArtifact);
        String paramsJson = mergePreparedParamsJson(finalizedPrepared, requestSkillKit, toolEvents, finalArtifact);
        String segmentsJson = toSegmentsJson(segments);
        conversationHistoryService.interruptMessage(
                context, answer, segmentsJson, paramsJson, System.currentTimeMillis() - startedAt, usagePayload);
        conversationEventService.upsertAssistantMessage(
                context,
                answer,
                segmentsJson,
                finalizedPrepared.messageType(),
                paramsJson,
                toArtifactSummaryJson(finalArtifact));
        tokenUsageCapability.finalizeRun(context, finalizedPrepared, usageSnapshot);
        contextEngineeringService.compactIfNeeded(context);
    }

    public void recordToolEvent(String eventType, String payload, List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || (!"tool".equals(eventType) && !"result".equals(eventType))) {
            return;
        }
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Object content = wrapper == null ? null : wrapper.get("content");
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", eventType);
            if (content instanceof Map<?, ?> contentMap) {
                event.put("content", new LinkedHashMap<>(contentMap));
            } else if (content != null) {
                event.put("content", content);
            }
            toolEvents.add(event);
        } catch (Exception ex) {
            logger.warn("记录工具事件失败：eventType={}, error={}", eventType, ex.getMessage());
        }
    }

    public void appendTextSegment(List<Map<String, Object>> segments, String text) {
        if (segments == null || !StringUtils.hasText(text)) {
            return;
        }
        Map<String, Object> lastSegment = lastSegment(segments);
        if (isTextSegment(lastSegment)) {
            String existing = lastSegment.get("text") == null ? "" : String.valueOf(lastSegment.get("text"));
            String normalized = normalizeAppendedText(existing, text);
            if (!StringUtils.hasText(normalized)) {
                return;
            }
            lastSegment.put("text", existing + normalized);
            return;
        }
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("type", "text");
        segment.put("text", text);
        segments.add(segment);
    }

    private String normalizeAppendedText(String existing, String incoming) {
        String currentText = existing == null ? "" : existing;
        String nextText = incoming == null ? "" : incoming;
        if (!StringUtils.hasText(currentText)) {
            return nextText;
        }
        if (!StringUtils.hasText(nextText)) {
            return "";
        }
        if (nextText.equals(currentText) || currentText.endsWith(nextText)) {
            return "";
        }
        if (nextText.startsWith(currentText)) {
            return nextText.substring(currentText.length());
        }
        return nextText;
    }

    public void recordTimelineToolEvent(List<Map<String, Object>> segments, String eventType, String payload) {
        if (segments == null || (!"tool".equals(eventType) && !"result".equals(eventType))) {
            return;
        }
        Map<String, Object> content = parseToolEventContent(payload);
        if (content.isEmpty()) {
            return;
        }
        String traceKey = resolveToolTraceKey(content);
        if (!StringUtils.hasText(traceKey)) {
            return;
        }
        if ("tool".equals(eventType)) {
            upsertToolCallSegment(segments, traceKey, content);
            return;
        }
        applyToolResultSegment(segments, traceKey, content);
    }

    public String toSegmentsJson(List<Map<String, Object>> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(List.copyOf(segments));
    }

    private void upsertToolCallSegment(
            List<Map<String, Object>> segments, String traceKey, Map<String, Object> content) {
        Map<String, Object> existing = findToolSegment(segments, traceKey);
        if (existing != null) {
            existing.putAll(buildToolSegment(traceKey, content, existing));
            existing.put("status", "running");
            return;
        }
        segments.add(buildToolSegment(traceKey, content, null));
    }

    private void applyToolResultSegment(
            List<Map<String, Object>> segments, String traceKey, Map<String, Object> content) {
        Map<String, Object> toolSegment = findToolSegment(segments, traceKey);
        if (toolSegment == null) {
            toolSegment = buildToolSegment(traceKey, content, null);
            segments.add(toolSegment);
        }
        toolSegment.put("response", extractToolResponseText(content));
        toolSegment.put("status", "done");
        copyToolTimingFields(toolSegment, content, toolSegment);
        appendArtifactSegment(segments, toolSegment, content);
    }

    private void appendArtifactSegment(
            List<Map<String, Object>> segments, Map<String, Object> toolSegment, Map<String, Object> resultContent) {
        Map<String, Object> artifact = extractArtifactFromToolResult(toolSegment, resultContent);
        if (artifact.isEmpty()) {
            return;
        }
        String artifactKey = firstNonBlank(
                normalizeText(artifact.get("id")),
                normalizeText(artifact.get("objectName")),
                normalizeText(artifact.get("downloadUrl")),
                normalizeText(artifact.get("path")),
                normalizeText(artifact.get("fileName")));
        if (!StringUtils.hasText(artifactKey) || hasArtifactSegment(segments, artifactKey)) {
            return;
        }
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("type", "artifact");
        segment.put("key", "artifact:" + artifactKey);
        segment.put(
                "title",
                firstNonBlank(
                        normalizeText(artifact.get("fileName")),
                        normalizeText(artifact.get("objectName")),
                        "generated-artifact"));
        segment.put("fileName", normalizeText(artifact.get("fileName")));
        segment.put("size", normalizeText(artifact.get("contentType")));
        segment.put("contentType", normalizeText(artifact.get("contentType")));
        segment.put("previewUrl", normalizeText(artifact.get("previewUrl")));
        segment.put("downloadUrl", normalizeText(artifact.get("downloadUrl")));
        segment.put("path", normalizeText(artifact.get("path")));
        segment.put("objectName", normalizeText(artifact.get("objectName")));
        segment.put("previewable", isPreviewableArtifact(artifact));
        segments.add(segment);
    }

    private boolean hasArtifactSegment(List<Map<String, Object>> segments, String artifactKey) {
        for (Map<String, Object> segment : segments) {
            if (segment == null || !"artifact".equals(segment.get("type"))) {
                continue;
            }
            if (("artifact:" + artifactKey).equals(normalizeText(segment.get("key")))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreviewableArtifact(Map<String, Object> artifact) {
        String contentType = normalizeText(artifact.get("contentType")).toLowerCase();
        if (contentType.startsWith("text/html")) {
            return true;
        }
        String fileName = normalizeText(artifact.get("fileName")).toLowerCase();
        return fileName.endsWith(".html") || fileName.endsWith(".htm");
    }

    private Map<String, Object> buildToolSegment(
            String traceKey, Map<String, Object> content, Map<String, Object> previousSegment) {
        Map<String, Object> segment = new LinkedHashMap<>();
        String toolName = firstNonBlank(
                normalizeText(content.get("name")),
                previousSegment == null ? "" : normalizeText(previousSegment.get("name")),
                "unknown");
        String displayName =
                firstNonBlank(normalizeText(content.get("displayName")), resolveToolDisplayName(toolName), toolName);
        String action = firstNonBlank(
                extractRuntimeToolAction(content.get("arguments")),
                extractRuntimeToolAction(content.get("input")),
                previousSegment == null ? "" : normalizeText(previousSegment.get("action")));
        segment.put("type", "tool");
        segment.put("id", normalizeText(content.get("id")));
        segment.put("key", traceKey);
        segment.put("toolCallId", traceKey);
        segment.put("name", toolName);
        segment.put("displayName", displayName);
        segment.put("action", action);
        segment.put("actionLabel", resolveToolActionLabel(action));
        segment.put("inputText", extractToolInputText(content));
        segment.put("response", previousSegment == null ? "" : normalizeText(previousSegment.get("response")));
        segment.put(
                "status",
                firstNonBlank(previousSegment == null ? "" : normalizeText(previousSegment.get("status")), "running"));
        copyToolTimingFields(segment, content, previousSegment);
        return segment;
    }

    private void copyToolTimingFields(
            Map<String, Object> target, Map<String, Object> content, Map<String, Object> previousSegment) {
        if (target == null) {
            return;
        }
        copyLongField(
                target,
                "startedAt",
                firstNonNull(
                        content == null ? null : content.get("startedAt"),
                        previousSegment == null ? null : previousSegment.get("startedAt")));
        copyLongField(
                target,
                "completedAt",
                firstNonNull(
                        content == null ? null : content.get("completedAt"),
                        previousSegment == null ? null : previousSegment.get("completedAt")));
        copyLongField(
                target,
                "durationMs",
                firstNonNull(
                        content == null ? null : content.get("durationMs"),
                        previousSegment == null ? null : previousSegment.get("durationMs")));
    }

    private void copyLongField(Map<String, Object> target, String fieldName, Object value) {
        Long normalized = toLongValue(value);
        if (normalized == null) {
            target.remove(fieldName);
            return;
        }
        target.put(fieldName, normalized);
    }

    private String extractToolInputText(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String toolName = normalizeToolName(normalizeText(content.get("name")));
        if ("runtime_tool".equals(toolName)) {
            Map<String, Object> arguments = toObjectMap(content.get("arguments"));
            String action = firstNonBlank(normalizeText(arguments.get("action")), normalizeText(arguments.get("arg0")));
            Object params = firstNonBlankObject(arguments.get("params"), arguments.get("arg1"));
            if (params != null) {
                return stringifySegmentValue(toObjectMap(params).isEmpty() ? params : toObjectMap(params));
            }
            if (StringUtils.hasText(action)) {
                return action;
            }
        }
        return stringifySegmentValue(firstNonBlankObject(content.get("arguments"), content.get("input")));
    }

    private String extractToolResponseText(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        Object response = payload.get("response");
        if (response == null) {
            response = payload.get("output");
        }
        if (response == null) {
            response = payload.get("result");
        }
        if (response == null) {
            response = payload.get("textOutput");
        }
        if (response == null) {
            response = payload.get("data");
        }
        String text = stringifySegmentValue(response);
        if (StringUtils.hasText(text)) {
            return text;
        }
        String errorMessage = normalizeText(payload.get("errorMessage"));
        if (StringUtils.hasText(errorMessage)) {
            String errorCode = normalizeText(payload.get("errorCode"));
            return StringUtils.hasText(errorCode) ? errorCode + ": " + errorMessage : errorMessage;
        }
        return "";
    }

    private String stringifySegmentValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return JSON.toJSONString(value, true);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String resolveToolActionLabel(String action) {
        return switch (normalizeToolName(action)) {
            case "file_read" -> "读取文件";
            case "file_write" -> "写入文件";
            case "list_dir" -> "查看目录";
            case "stat" -> "查看属性";
            case "run_python" -> "执行 Python";
            case "write_artifact" -> "写出产物";
            default -> normalizeText(action);
        };
    }

    private Map<String, Object> findToolSegment(List<Map<String, Object>> segments, String traceKey) {
        for (Map<String, Object> segment : segments) {
            if (segment == null || !"tool".equals(segment.get("type"))) {
                continue;
            }
            if (traceKey.equals(normalizeText(segment.get("key")))
                    || traceKey.equals(normalizeText(segment.get("toolCallId")))) {
                return segment;
            }
        }
        return null;
    }

    private boolean isTextSegment(Map<String, Object> segment) {
        return segment != null && "text".equals(normalizeText(segment.get("type")));
    }

    private Map<String, Object> lastSegment(List<Map<String, Object>> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        return segments.get(segments.size() - 1);
    }

    public void persistCompletedToolTrace(
            ConversationHistoryService.ConversationContext context,
            String eventType,
            String payload,
            Map<String, Map<String, Object>> pendingToolCalls) {
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(payload) || context == null) {
            return;
        }
        Map<String, Object> content = parseToolEventContent(payload);
        if (content.isEmpty()) {
            return;
        }
        String traceKey = resolveToolTraceKey(content);
        if (!StringUtils.hasText(traceKey)) {
            return;
        }
        if ("tool".equals(eventType)) {
            pendingToolCalls.put(traceKey, new LinkedHashMap<>(content));
            return;
        }
        if (!"result".equals(eventType)) {
            return;
        }
        Map<String, Object> callContent = pendingToolCalls.remove(traceKey);
        Map<String, Object> tracePayload = new LinkedHashMap<>();
        tracePayload.put("id", traceKey);
        tracePayload.put("toolName", firstNonBlank(resolveToolLabel(callContent), resolveToolLabel(content)));
        if (callContent != null && !callContent.isEmpty()) {
            tracePayload.put("call", callContent);
            String callSummary = summarizeToolCall(callContent);
            if (StringUtils.hasText(callSummary)) {
                tracePayload.put("callSummary", callSummary);
            }
        }
        tracePayload.put("result", content);
        String resultSummary = summarizeToolResult(content);
        if (StringUtils.hasText(resultSummary)) {
            tracePayload.put("resultSummary", resultSummary);
        }
        String contentText = buildToolTraceLine(tracePayload);
        Map<String, Object> artifact = extractArtifactFromToolResult(callContent, content);
        if (artifact != null && !artifact.isEmpty()) {
            conversationEventService.appendEvent(
                    context,
                    context.assistantMessageId(),
                    "ARTIFACT_GENERATED",
                    traceKey,
                    shrink(toArtifactSummaryText(artifact), 240),
                    JSON.toJSONString(artifact));
        }
        conversationEventService.upsertToolTraceMessage(
                context,
                traceKey,
                contentText,
                JSON.toJSONString(tracePayload),
                JSON.toJSONString(Map.of("toolCallId", traceKey)));
    }

    public String enrichToolEventPayload(String payload) {
        return enrichToolEventPayload(null, payload, null);
    }

    public String enrichToolEventPayload(String eventType, String payload, Map<String, Long> startedAtByTrace) {
        if (!StringUtils.hasText(payload)) {
            return payload;
        }
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> normalizedWrapper = new LinkedHashMap<>();
            if (wrapper != null) {
                normalizedWrapper.putAll(wrapper);
            }
            Object content = normalizedWrapper.get("content");
            if (!(content instanceof Map<?, ?> contentMap)) {
                return payload;
            }
            Map<String, Object> normalizedContent = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                normalizedContent.put(String.valueOf(key), entry.getValue());
            }
            String toolName = normalizeText(normalizedContent.get("name"));
            if (StringUtils.hasText(toolName)) {
                normalizedContent.put("displayName", resolveToolDisplayName(toolName));
            }
            String traceKey = resolveToolTraceKey(normalizedContent);
            enrichToolEventTiming(eventType, normalizedContent, traceKey, startedAtByTrace);
            normalizedWrapper.put("content", normalizedContent);
            return JSON.toJSONString(normalizedWrapper);
        } catch (Exception ex) {
            logger.warn("补充工具事件展示信息失败：eventType={}, error={}", eventType, ex.getMessage());
            return payload;
        }
    }

    private void enrichToolEventTiming(
            String eventType, Map<String, Object> content, String traceKey, Map<String, Long> startedAtByTrace) {
        if (content == null || content.isEmpty() || !StringUtils.hasText(eventType)) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        if ("tool".equals(eventType)) {
            Long startedAt = firstNonNullLong(toLongValue(content.get("startedAt")), nowMillis);
            content.put("startedAt", startedAt);
            if (startedAtByTrace != null && StringUtils.hasText(traceKey)) {
                startedAtByTrace.put(traceKey, startedAt);
            }
            return;
        }
        if (!"result".equals(eventType)) {
            return;
        }
        Long startedAt = toLongValue(content.get("startedAt"));
        if (startedAt == null && startedAtByTrace != null && StringUtils.hasText(traceKey)) {
            startedAt = startedAtByTrace.remove(traceKey);
        } else if (startedAtByTrace != null && StringUtils.hasText(traceKey)) {
            startedAtByTrace.remove(traceKey);
        }
        if (startedAt != null) {
            content.put("startedAt", startedAt);
        }
        Long completedAt = firstNonNullLong(toLongValue(content.get("completedAt")), nowMillis);
        content.put("completedAt", completedAt);
        if (startedAt != null) {
            content.put("durationMs", Math.max(0L, completedAt - startedAt));
        }
    }

    public Map<String, Object> extractFinalArtifact(List<Map<String, Object>> toolEvents, String answer) {
        Map<String, Object> artifactFromTools = extractArtifactFromToolEvents(toolEvents);
        if (artifactFromTools != null && !artifactFromTools.isEmpty()) {
            return artifactFromTools;
        }
        return StructuredArtifactExtractor.extract(answer);
    }

    public String toArtifactSummaryJson(Map<String, Object> artifact) {
        if (artifact == null || artifact.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(artifact);
    }

    public String mergeParamsJson(String paramsJson, List<Map<String, Object>> toolEvents) {
        return mergeParamsJson(paramsJson, toolEvents, null);
    }

    public String mergeParamsJson(
            String paramsJson, List<Map<String, Object>> toolEvents, Map<String, Object> finalArtifact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(paramsJson)) {
            try {
                Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
                if (parsed != null) {
                    payload.putAll(parsed);
                }
            } catch (Exception ex) {
                logger.warn("解析参数 JSON 失败，将重建参数：error={}", ex.getMessage());
            }
        }
        payload.put("toolEvents", toolEvents == null ? List.of() : List.copyOf(toolEvents));
        List<RequestScopedSkillRuntimeService.SkillReadFact> skillReadFacts =
                requestScopedSkillRuntimeService.extractSkillReadFacts(toolEvents, null);
        if (runtimeSkillStateContractSupport != null) {
            RuntimeSkillStateContract contract = new RuntimeSkillStateContract(
                    List.of(), "", null, "", null, toSkillReadFactContracts(skillReadFacts));
            String mergedWithContract = runtimeSkillStateContractSupport.mergeContractIntoParams(
                    JSON.toJSONString(payload), contract);
            try {
                Map<String, Object> mergedPayload =
                        JSON.parseObject(mergedWithContract, new TypeReference<Map<String, Object>>() {});
                if (mergedPayload != null) {
                    payload.clear();
                    payload.putAll(mergedPayload);
                }
            } catch (Exception ex) {
                logger.warn("合并技能读取 contract 失败，将保留基础参数：error={}", ex.getMessage());
            }
        }
        if (finalArtifact != null && !finalArtifact.isEmpty()) {
            payload.put("final_artifact", finalArtifact);
        }
        if (resolveCodeExecutionActive(payload, toolEvents)) {
            payload.put("codeExecutionActive", true);
        } else {
            payload.remove("codeExecutionActive");
        }
        return JSON.toJSONString(payload);
    }

    private String mergePreparedParamsJson(
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            List<Map<String, Object>> toolEvents) {
        return mergePreparedParamsJson(prepared, requestSkillKit, toolEvents, null);
    }

    private String mergePreparedParamsJson(
            ChatRuntimePreparedRequest prepared,
            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit,
            List<Map<String, Object>> toolEvents,
            Map<String, Object> finalArtifact) {
        String merged = mergeParamsJson(prepared == null ? null : prepared.paramsJson(), toolEvents, finalArtifact);
        if (prepared == null) {
            return merged;
        }
        List<RequestScopedSkillRuntimeService.SkillReadFact> skillReadFacts =
                requestScopedSkillRuntimeService.extractSkillReadFacts(toolEvents, prepared.availableSkills());
        List<RuntimeLoadedSkill> loadedSkills =
                requestScopedSkillRuntimeService.extractLoadedSkills(requestSkillKit, prepared.availableSkills());
        String currentRuntimeSkillName =
                requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(requestSkillKit, prepared);
        String mergedWithSkillState = requestScopedSkillRuntimeService.mergeSkillStateParams(
                merged, prepared.availableSkills(), loadedSkills, currentRuntimeSkillName);
        if (runtimeSkillStateContractSupport == null || skillReadFacts.isEmpty()) {
            return mergedWithSkillState;
        }
        return runtimeSkillStateContractSupport.mergeContractIntoParams(
                mergedWithSkillState,
                new RuntimeSkillStateContract(
                        List.of(), "", null, "", null, toSkillReadFactContracts(skillReadFacts)));
    }

    private List<RuntimeSkillReadFactContract> toSkillReadFactContracts(
            List<RequestScopedSkillRuntimeService.SkillReadFact> skillReadFacts) {
        if (skillReadFacts == null || skillReadFacts.isEmpty()) {
            return List.of();
        }
        List<RuntimeSkillReadFactContract> payload = new ArrayList<>();
        for (RequestScopedSkillRuntimeService.SkillReadFact fact : skillReadFacts) {
            if (fact == null) {
                continue;
            }
            RuntimeSkillReadFactContract contract = requestScopedSkillRuntimeService.toContract(fact);
            if (contract != null) {
                payload.add(contract);
            }
        }
        return List.copyOf(payload);
    }

    private Map<String, Object> parseToolEventContent(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Object content = wrapper == null ? null : wrapper.get("content");
            if (!(content instanceof Map<?, ?> contentMap)) {
                return Map.of();
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        } catch (Exception ex) {
            logger.warn("解析工具事件内容失败：error={}", ex.getMessage());
            return Map.of();
        }
    }

    private String resolveToolTraceKey(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return firstNonBlank(
                normalizeText(content.get("id")),
                normalizeText(content.get("name")),
                normalizeText(content.get("displayName")));
    }

    private String buildToolTraceLine(Map<String, Object> tracePayload) {
        if (tracePayload == null || tracePayload.isEmpty()) {
            return "";
        }
        String toolName = normalizeText(tracePayload.get("toolName"));
        String callSummary = normalizeText(tracePayload.get("callSummary"));
        String resultSummary = normalizeText(tracePayload.get("resultSummary"));
        StringBuilder line = new StringBuilder();
        line.append(StringUtils.hasText(toolName) ? toolName : "未命名工具");
        if (StringUtils.hasText(callSummary)) {
            line.append(" | 参数: ").append(callSummary);
        }
        if (StringUtils.hasText(resultSummary)) {
            line.append(" | 结果: ").append(resultSummary);
        }
        return line.toString();
    }

    private String summarizeToolCall(Map<String, Object> content) {
        String toolName = resolveToolLabel(content);
        String argumentsSummary = summarizeKeyValueCandidates(
                content.get("arguments"), content.get("input"), content.get("request"), content.get("params"));
        if (!StringUtils.hasText(toolName) && !StringUtils.hasText(argumentsSummary)) {
            return "";
        }
        if (!StringUtils.hasText(argumentsSummary)) {
            return toolName;
        }
        return StringUtils.hasText(toolName) ? toolName + "（参数：" + argumentsSummary + "）" : "参数：" + argumentsSummary;
    }

    private String summarizeToolResult(Map<String, Object> content) {
        String toolName = resolveToolLabel(content);
        String status = firstNonBlank(
                normalizeText(content.get("status")),
                normalizeText(content.get("state")),
                normalizeText(content.get("code")));
        String resultSummary = summarizeKeyValueCandidates(
                content.get("output"), content.get("result"), content.get("response"), content.get("data"));
        List<String> pieces = new ArrayList<>();
        if (StringUtils.hasText(toolName)) {
            pieces.add(toolName);
        }
        if (StringUtils.hasText(status)) {
            pieces.add("状态=" + shrink(status, 60));
        }
        if (StringUtils.hasText(resultSummary)) {
            pieces.add(resultSummary);
        }
        return pieces.isEmpty() ? "" : String.join("，", pieces);
    }

    private String resolveToolLabel(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String displayName = normalizeText(content.get("displayName"));
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        String name = normalizeText(content.get("name"));
        return StringUtils.hasText(name) ? name : "";
    }

    private String summarizeKeyValueCandidates(Object... candidates) {
        if (candidates == null || candidates.length == 0) {
            return "";
        }
        for (Object candidate : candidates) {
            String summary = summarizeObject(candidate);
            if (StringUtils.hasText(summary)) {
                return summary;
            }
        }
        return "";
    }

    private String summarizeObject(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> mapValue) {
            List<String> pairs = new ArrayList<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey()).trim();
                String summarizedValue = summarizeScalar(entry.getValue());
                if (!StringUtils.hasText(key) || !StringUtils.hasText(summarizedValue)) {
                    continue;
                }
                pairs.add(key + "=" + summarizedValue);
                count++;
                if (count >= 3) {
                    break;
                }
            }
            return String.join(", ", pairs);
        }
        if (value instanceof List<?> listValue) {
            if (listValue.isEmpty()) {
                return "";
            }
            List<String> items = new ArrayList<>();
            for (Object item : listValue) {
                String summarized = summarizeScalar(item);
                if (!StringUtils.hasText(summarized)) {
                    continue;
                }
                items.add(summarized);
                if (items.size() >= 3) {
                    break;
                }
            }
            return items.isEmpty()
                    ? "共 " + listValue.size() + " 项"
                    : "共 " + listValue.size() + " 项：" + String.join(", ", items);
        }
        return summarizeScalar(value);
    }

    private boolean resolveCodeExecutionActive(Map<String, Object> payload, List<Map<String, Object>> toolEvents) {
        if (readBoolean(payload, "codeExecutionActive")) {
            return true;
        }
        if (!readNestedBoolean(payload, "toolToCodeDecision", "allowCodeExecution")) {
            return false;
        }
        return hasWorkspaceCodeToolActivity(toolEvents);
    }

    private boolean hasWorkspaceCodeToolActivity(List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return false;
        }
        for (Map<String, Object> event : toolEvents) {
            if (isWorkspaceCodeToolEvent(event)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWorkspaceCodeToolEvent(Map<String, Object> event) {
        Map<String, Object> content = toObjectMap(event == null ? null : event.get("content"));
        if (content.isEmpty()) {
            return false;
        }
        String toolName = normalizeToolName(
                firstNonBlank(normalizeText(content.get("name")), normalizeText(content.get("displayName"))));
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        if ("runtime_tool".equals(toolName)) {
            Map<String, Object> arguments = toObjectMap(content.get("arguments"));
            String action = normalizeToolName(
                    firstNonBlank(normalizeText(arguments.get("arg0")), normalizeText(arguments.get("action"))));
            if ("run_python".equals(action)) {
                Map<String, Object> actionParams =
                        toObjectMap(firstNonBlankObject(arguments.get("arg1"), arguments.get("params")));
                return isWorkspacePythonScript(firstNonBlank(
                        normalizeText(actionParams.get("scriptPath")), normalizeText(arguments.get("scriptPath"))));
            }
            if ("file_write".equals(action)) {
                Map<String, Object> actionParams =
                        toObjectMap(firstNonBlankObject(arguments.get("arg1"), arguments.get("params")));
                return isWorkspacePythonScript(
                        firstNonBlank(normalizeText(actionParams.get("path")), normalizeText(arguments.get("path"))));
            }
            return false;
        }
        if ("run_python".equals(toolName) || "runpython".equals(toolName)) {
            Map<String, Object> arguments = toObjectMap(content.get("arguments"));
            return isWorkspacePythonScript(firstNonBlank(
                    normalizeText(arguments.get("scriptPath")), normalizeText(content.get("scriptPath"))));
        }
        if ("file_write".equals(toolName) || "writefile".equals(toolName)) {
            Map<String, Object> arguments = toObjectMap(content.get("arguments"));
            return isWorkspacePythonScript(
                    firstNonBlank(normalizeText(arguments.get("path")), normalizeText(content.get("path"))));
        }
        return false;
    }

    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                Map<String, Object> parsed = JSON.parseObject(text, new TypeReference<Map<String, Object>>() {});
                return parsed == null ? Map.of() : parsed;
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Object firstNonBlankObject(Object first, Object second) {
        if (first instanceof String firstText) {
            if (StringUtils.hasText(firstText)) {
                return first;
            }
        } else if (first != null) {
            return first;
        }
        return second;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private Long firstNonNullLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private boolean isWorkspacePythonScript(String path) {
        return StringUtils.hasText(path)
                && path.trim().startsWith("/workspace/")
                && path.trim().toLowerCase().endsWith(".py");
    }

    private boolean readBoolean(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty() || !StringUtils.hasText(key)) {
            return false;
        }
        Object value = payload.get(key);
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private boolean readNestedBoolean(Map<String, Object> payload, String objectKey, String valueKey) {
        if (payload == null || payload.isEmpty() || !StringUtils.hasText(objectKey) || !StringUtils.hasText(valueKey)) {
            return false;
        }
        Object nested = payload.get(objectKey);
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return false;
        }
        Object value = nestedMap.get(valueKey);
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String normalizeToolName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('-', '_').toLowerCase();
    }

    private Long toLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String summarizeScalar(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (looksLikeJson(text)) {
            try {
                Object parsed = JSON.parse(text);
                if (parsed != null && parsed != text) {
                    return summarizeObject(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        return shrink(text, 120);
    }

    private boolean looksLikeJson(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        return (normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"));
    }

    private Map<String, Object> extractArtifactFromToolEvents(List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return Map.of();
        }
        for (int index = toolEvents.size() - 1; index >= 0; index--) {
            Map<String, Object> event = toolEvents.get(index);
            if (event == null || event.isEmpty()) {
                continue;
            }
            Object rawContent = event.get("content");
            if (!(rawContent instanceof Map<?, ?> contentMap)) {
                continue;
            }
            Map<String, Object> normalizedContent = normalizeMap(contentMap);
            Map<String, Object> artifact = extractArtifactFromToolResult(null, normalizedContent);
            if (!artifact.isEmpty()) {
                return artifact;
            }
        }
        return Map.of();
    }

    private Map<String, Object> extractArtifactFromToolResult(
            Map<String, Object> callContent, Map<String, Object> resultContent) {
        String toolName = firstNonBlank(
                normalizeText(callContent == null ? null : callContent.get("name")),
                normalizeText(resultContent == null ? null : resultContent.get("name")),
                resolveToolLabel(callContent),
                resolveToolLabel(resultContent));
        if (!isArtifactToolResult(toolName, callContent, resultContent)) {
            return Map.of();
        }
        Object response = resultContent == null ? null : resultContent.get("response");
        Map<String, Object> parsed =
                StructuredArtifactExtractor.extract(response == null ? "" : String.valueOf(response));
        if (parsed.isEmpty() && response instanceof Map<?, ?> responseMap) {
            parsed = normalizeMap(responseMap);
        }
        if (parsed.isEmpty()) {
            return Map.of();
        }
        if (parsed.containsKey("artifact")) {
            Object artifact = parsed.get("artifact");
            if (artifact instanceof Map<?, ?> artifactMap) {
                return normalizeMap(artifactMap);
            }
        }
        Object data = parsed.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> normalizedData = normalizeMap(dataMap);
            Object artifact = normalizedData.get("artifact");
            if (artifact instanceof Map<?, ?> artifactMap) {
                return normalizeMap(artifactMap);
            }
            Object file = normalizedData.get("file");
            if (file instanceof Map<?, ?> fileMap) {
                return normalizeMap(fileMap);
            }
        }
        if (parsed.containsKey("file")) {
            Object file = parsed.get("file");
            if (file instanceof Map<?, ?> fileMap) {
                return normalizeMap(fileMap);
            }
        }
        return parsed;
    }

    private boolean isArtifactToolResult(
            String toolName, Map<String, Object> callContent, Map<String, Object> resultContent) {
        String normalizedToolName = normalizeToolName(toolName);
        if ("write_artifact".equals(normalizedToolName)
                || "writeartifact".equals(normalizedToolName)
                || "写出产物".equals(normalizeText(toolName))) {
            return true;
        }
        if (!"runtime_tool".equals(normalizedToolName) && !"运行时工具".equals(normalizeText(toolName))) {
            return false;
        }
        String action = firstNonBlank(
                extractRuntimeToolAction(callContent == null ? null : callContent.get("arguments")),
                extractRuntimeToolAction(callContent == null ? null : callContent.get("input")),
                extractRuntimeToolAction(resultContent == null ? null : resultContent.get("arguments")),
                extractRuntimeToolAction(resultContent == null ? null : resultContent.get("input")),
                normalizeText(resultContent == null ? null : resultContent.get("action")));
        return "write_artifact".equals(normalizeToolName(action));
    }

    private String extractRuntimeToolAction(Object rawPayload) {
        if (rawPayload == null) {
            return "";
        }
        if (rawPayload instanceof Map<?, ?> mapPayload) {
            Object action = mapPayload.get("action");
            return normalizeText(action);
        }
        String payload = normalizeText(rawPayload);
        if (!StringUtils.hasText(payload) || !looksLikeJson(payload)) {
            return "";
        }
        try {
            Object parsed = JSON.parse(payload);
            if (parsed instanceof Map<?, ?> mapPayload) {
                Object action = mapPayload.get("action");
                return normalizeText(action);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private String toArtifactSummaryText(Map<String, Object> artifact) {
        if (artifact == null || artifact.isEmpty()) {
            return "";
        }
        String fileName = normalizeText(artifact.get("fileName"));
        String downloadUrl = normalizeText(artifact.get("downloadUrl"));
        String path = normalizeText(artifact.get("path"));
        if (StringUtils.hasText(fileName) && StringUtils.hasText(downloadUrl)) {
            return "产物已生成: " + fileName + " " + downloadUrl;
        }
        if (StringUtils.hasText(fileName) && StringUtils.hasText(path)) {
            return "产物已生成: " + fileName + " " + path;
        }
        if (StringUtils.hasText(fileName)) {
            return "产物已生成: " + fileName;
        }
        return "产物已生成";
    }

    private String resolveToolDisplayName(String toolName) {
        String normalizedToolName = StringUtils.hasText(toolName) ? toolName.trim() : "";
        if (!StringUtils.hasText(normalizedToolName)) {
            return "";
        }
        return switch (normalizedToolName) {
            case "search_dataset_summary" -> "查看数据集摘要";
            case "get_dataset_schema" -> "查看数据集结构";
            case "execute_dataset_sql" -> "执行数据集 SQL";
            default -> skillCatalogService.resolveToolDisplayName(normalizedToolName);
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace("\r", "").replace("\n", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }
}
