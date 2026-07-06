package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.ConversationRunUsage;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.mapper.ConversationRunUsageMapper;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeModelCallUsage;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationRunUsageService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationRunUsageService.class);

    private final ConversationRunUsageMapper conversationRunUsageMapper;
    private final ConversationEventService conversationEventService;
    private final LicenseService licenseService;
    private final UserTokenQuotaService userTokenQuotaService;

    public ConversationRunUsageService(
            ConversationRunUsageMapper conversationRunUsageMapper,
            ConversationEventService conversationEventService,
            LicenseService licenseService,
            UserTokenQuotaService userTokenQuotaService) {
        this.conversationRunUsageMapper = conversationRunUsageMapper;
        this.conversationEventService = conversationEventService;
        this.licenseService = licenseService;
        this.userTokenQuotaService = userTokenQuotaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureRunningRecord(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            Long modelId,
            String modelProvider,
            String modelName,
            String adapterType,
            long startedAtMillis) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        ConversationRunUsage entity =
                conversationRunUsageMapper.selectByAssistantMessageId(context.assistantMessageId());
        if (entity == null) {
            entity = new ConversationRunUsage();
            entity.setAssistantMessageId(context.assistantMessageId());
            entity.setUserMessageId(context.userMessageId());
            entity.setSessionId(context.sessionId());
            entity.setSessionCode(context.sessionCode());
            entity.setSessionType(context.sessionType());
            entity.setScopeType(resolveScopeType(prepared));
            entity.setScopeId(context.scopeId());
            entity.setUserId(context.createUserId());
            AgentIdentity agentIdentity = resolveAgentIdentity(context, prepared);
            entity.setAgentType(agentIdentity.agentType());
            entity.setAgentId(agentIdentity.agentId());
            entity.setAgentName(agentIdentity.agentName());
            entity.setRuntimeSkillName(normalizeNullableText(prepared == null ? null : prepared.runtimeSkillName()));
            entity.setCreatedAt(new Date());
            entity.setStartedAt(new Date(startedAtMillis));
        }
        entity.setModelId(modelId);
        entity.setModelProvider(normalizeNullableText(modelProvider));
        entity.setModelName(normalizeNullableText(modelName));
        entity.setAdapterType(normalizeNullableText(adapterType));
        entity.setRunStatus("RUNNING");
        entity.setUsageAvailable(Boolean.FALSE);
        entity.setUpdatedAt(new Date());
        if (entity.getId() == null) {
            conversationRunUsageMapper.insert(entity);
        } else {
            conversationRunUsageMapper.updateById(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finalizeRun(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeRunUsageSnapshot snapshot) {
        persistRun(context, prepared, snapshot, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistRun(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeRunUsageSnapshot snapshot) {
        persistRun(context, prepared, snapshot, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistRun(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeRunUsageSnapshot snapshot,
            boolean settleQuota) {
        if (context == null || context.assistantMessageId() == null || snapshot == null) {
            return;
        }
        ConversationRunUsage entity =
                conversationRunUsageMapper.selectByAssistantMessageId(context.assistantMessageId());
        if (entity == null) {
            entity = new ConversationRunUsage();
            entity.setAssistantMessageId(context.assistantMessageId());
            entity.setUserMessageId(context.userMessageId());
            entity.setSessionId(context.sessionId());
            entity.setSessionCode(context.sessionCode());
            entity.setSessionType(context.sessionType());
            entity.setScopeType(resolveScopeType(prepared));
            entity.setScopeId(context.scopeId());
            entity.setUserId(context.createUserId());
            entity.setCreatedAt(new Date());
            AgentIdentity agentIdentity = resolveAgentIdentity(context, prepared);
            entity.setAgentType(agentIdentity.agentType());
            entity.setAgentId(agentIdentity.agentId());
            entity.setAgentName(agentIdentity.agentName());
            entity.setRuntimeSkillName(normalizeNullableText(prepared == null ? null : prepared.runtimeSkillName()));
        }
        entity.setModelId(snapshot.modelId());
        entity.setModelProvider(normalizeNullableText(snapshot.modelProvider()));
        entity.setModelName(normalizeNullableText(snapshot.modelName()));
        entity.setAdapterType(normalizeNullableText(snapshot.adapterType()));
        entity.setRunStatus(normalizeNullableText(snapshot.runStatus()));
        entity.setUsageAvailable(snapshot.usageAvailable());
        entity.setPromptTokens(snapshot.promptTokens());
        entity.setCompletionTokens(snapshot.completionTokens());
        entity.setTotalTokens(snapshot.totalTokens());
        entity.setLlmCallCount(snapshot.llmCallCount());
        entity.setToolCallCount(snapshot.toolCallCount());
        entity.setDurationMs(snapshot.durationMs());
        entity.setStartedAt(new Date(snapshot.startedAtMillis()));
        entity.setCompletedAt(new Date(snapshot.completedAtMillis()));
        entity.setUpdatedAt(new Date());
        if (entity.getId() == null) {
            conversationRunUsageMapper.insert(entity);
        } else {
            conversationRunUsageMapper.updateById(entity);
        }
        appendUsageEvents(context, snapshot);
        if (settleQuota) {
            try {
                licenseService.settleRunUsage(
                        context.createUserId(), String.valueOf(context.assistantMessageId()), snapshot);
                userTokenQuotaService.settleUsage(
                        context.createUserId(), ConversationSessionType.fromValue(context.sessionType()), snapshot);
            } catch (Exception ex) {
                logger.error(
                        "用户 token 额度结算失败：userId={}, sessionType={}, assistantMessageId={}, error={}",
                        context.createUserId(),
                        context.sessionType(),
                        context.assistantMessageId(),
                        ex.getMessage(),
                        ex);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBySessionId(Long sessionId) {
        conversationRunUsageMapper.deleteBySessionId(sessionId);
    }

    private void appendUsageEvents(
            ConversationHistoryService.ConversationContext context, RuntimeRunUsageSnapshot snapshot) {
        List<RuntimeModelCallUsage> modelCalls = snapshot.modelCalls();
        for (RuntimeModelCallUsage call : modelCalls) {
            conversationEventService.appendEvent(
                    context,
                    context.assistantMessageId(),
                    resolveCallEventType(call.status()),
                    "CALL_" + call.callNo(),
                    buildCallSummary(call),
                    JSON.toJSONString(buildCallPayload(call, snapshot)));
        }
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "RUN_USAGE_FINALIZED",
                normalizeNullableText(snapshot.runStatus()),
                buildRunSummary(snapshot),
                JSON.toJSONString(buildRunPayload(snapshot)));
    }

    private Map<String, Object> buildCallPayload(RuntimeModelCallUsage call, RuntimeRunUsageSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callNo", call.callNo());
        payload.put("status", call.status());
        payload.put("usageAvailable", call.usageAvailable());
        payload.put("promptTokens", call.promptTokens());
        payload.put("completionTokens", call.completionTokens());
        payload.put("totalTokens", call.totalTokens());
        payload.put("outputChars", call.outputChars());
        payload.put("startedAtMillis", call.startedAtMillis());
        payload.put("completedAtMillis", call.completedAtMillis());
        payload.put("durationMs", call.durationMs());
        payload.put("modelId", snapshot.modelId());
        payload.put("modelProvider", snapshot.modelProvider());
        payload.put("modelName", snapshot.modelName());
        payload.put("adapterType", snapshot.adapterType());
        return payload;
    }

    private Map<String, Object> buildRunPayload(RuntimeRunUsageSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runStatus", snapshot.runStatus());
        payload.put("usageAvailable", snapshot.usageAvailable());
        payload.put("promptTokens", snapshot.promptTokens());
        payload.put("completionTokens", snapshot.completionTokens());
        payload.put("totalTokens", snapshot.totalTokens());
        payload.put("llmCallCount", snapshot.llmCallCount());
        payload.put("toolCallCount", snapshot.toolCallCount());
        payload.put("durationMs", snapshot.durationMs());
        payload.put("startedAtMillis", snapshot.startedAtMillis());
        payload.put("completedAtMillis", snapshot.completedAtMillis());
        payload.put("modelId", snapshot.modelId());
        payload.put("modelProvider", snapshot.modelProvider());
        payload.put("modelName", snapshot.modelName());
        payload.put("adapterType", snapshot.adapterType());
        payload.put("calls", snapshot.modelCalls());
        return payload;
    }

    private String buildCallSummary(RuntimeModelCallUsage call) {
        String tokenText = call.totalTokens() == null ? "无usage" : call.totalTokens() + " tokens";
        return "第" + call.callNo() + "轮模型调用 " + tokenText;
    }

    private String buildRunSummary(RuntimeRunUsageSnapshot snapshot) {
        String tokenText = snapshot.totalTokens() == null ? "无usage" : snapshot.totalTokens() + " tokens";
        return "本次运行统计已结算，" + tokenText;
    }

    private String resolveCallEventType(String callStatus) {
        String normalizedStatus = normalizeText(callStatus).toUpperCase();
        return switch (normalizedStatus) {
            case "FAILED" -> "MODEL_CALL_FAILED";
            case "CANCELLED" -> "MODEL_CALL_CANCELLED";
            default -> "MODEL_CALL_COMPLETED";
        };
    }

    private String resolveScopeType(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.scopeType() == null) {
            return null;
        }
        return prepared.scopeType().name();
    }

    private AgentIdentity resolveAgentIdentity(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        ConversationSessionType sessionType = resolveSessionType(context, prepared);
        Long scopeId = context == null ? null : context.scopeId();
        String scopeDisplayName = context == null ? "" : normalizeText(context.scopeDisplayName());
        String runtimeSkillName = prepared == null ? "" : normalizeText(prepared.runtimeSkillName());
        return switch (sessionType) {
            case SKILL_CHAT, PUBLISHED_SKILL_CHAT, CHANNEL_CHAT -> new AgentIdentity(
                    "SKILL", scopeId, firstNonBlank(scopeDisplayName, runtimeSkillName));
            case DATASET_CHAT -> new AgentIdentity(
                    "DATASET", scopeId, firstNonBlank(scopeDisplayName, runtimeSkillName));
            case KNOWLEDGE_QA -> new AgentIdentity(
                    "KNOWLEDGE_BASE", scopeId, firstNonBlank(scopeDisplayName, runtimeSkillName));
            case SKILL_STUDIO_PROJECT_CHAT, SKILL_STUDIO_PROJECT_PREVIEW_CHAT -> new AgentIdentity(
                    "SKILL_STUDIO_PROJECT", scopeId, firstNonBlank(scopeDisplayName, runtimeSkillName));
            default -> new AgentIdentity("GENERAL", 0L, firstNonBlank(runtimeSkillName, "通用对话"));
        };
    }

    private ConversationSessionType resolveSessionType(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (prepared != null && prepared.sessionType() != null) {
            return prepared.sessionType();
        }
        if (context != null && StringUtils.hasText(context.sessionType())) {
            try {
                return ConversationSessionType.fromValue(context.sessionType());
            } catch (IllegalArgumentException ex) {
                logger.warn("无法识别会话类型，回退为 GENERAL_CHAT：sessionType={}", context.sessionType());
            }
        }
        return ConversationSessionType.GENERAL_CHAT;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return normalizeNullableText(second);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private record AgentIdentity(String agentType, Long agentId, String agentName) {}
}
