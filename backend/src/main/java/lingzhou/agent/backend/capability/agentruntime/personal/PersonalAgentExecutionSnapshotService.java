package lingzhou.agent.backend.capability.agentruntime.personal;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.business.chat.runtime.ToolToCodeEscalationDecision;
import lingzhou.agent.backend.business.chat.runtime.ToolToCodeEscalationPolicy;
import lingzhou.agent.backend.business.chat.service.ConversationEventService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class PersonalAgentExecutionSnapshotService {

    private static final String PRECHECK_BLOCKED = "BLOCKED";
    private static final String PRECHECK_CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";
    private static final String PRECHECK_READY = "READY";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATE_BLOCKED = "BLOCKED";
    private static final String STATE_CANCELLED = "CANCELLED";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_DIRECT_ANSWER = "DIRECT_ANSWER";
    private static final String STATE_FAILED = "FAILED";
    private static final String STATE_PLANNED = "PLANNED";
    private static final String STATE_RUNNING = "RUNNING";
    private static final String STATE_WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String STEP_ROUTE = "step-route";
    private static final String EXECUTOR_MODEL = "MODEL";
    private static final String EXECUTOR_MODEL_OR_PARSE_FILE = "MODEL_OR_PARSE_FILE";
    private static final String EXECUTOR_SKILL = "SKILL";
    private static final String EXECUTOR_TOOL = "TOOL";
    private static final String EXECUTOR_CODE = "CODE";

    private final PersonalAgentModeResolver personalAgentModeResolver;
    private final ConversationEventService conversationEventService;
    private final ToolToCodeEscalationPolicy toolToCodeEscalationPolicy;

    public PersonalAgentExecutionSnapshotService(
            PersonalAgentModeResolver personalAgentModeResolver,
            ConversationEventService conversationEventService,
            ToolToCodeEscalationPolicy toolToCodeEscalationPolicy) {
        this.personalAgentModeResolver = personalAgentModeResolver;
        this.conversationEventService = conversationEventService;
        this.toolToCodeEscalationPolicy = toolToCodeEscalationPolicy;
    }

    public ChatRuntimePreparedRequest enrichPreparedRequest(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        PersonalAgentMode mode = personalAgentModeResolver.resolve(prepared);
        PersonalAgentExecutionSnapshot snapshot = buildSnapshot(prepared, mode);
        log.debug(
                "[个人Agent] 已构建执行快照：会话ID={}, 模式={}, 计划ID={}, 步骤执行器={}, 相关技能={}, 允许技能内部能力={}",
                prepared.sessionId(),
                mode,
                snapshot.executionPlan() == null
                        ? null
                        : snapshot.executionPlan().planId(),
                snapshot.executionPrecheck() == null
                        ? null
                        : snapshot.executionPrecheck().stepExecutor(),
                snapshot.executionPrecheck() == null
                        ? null
                        : snapshot.executionPrecheck().relevantSkill(),
                snapshot.executionPrecheck() != null
                        && snapshot.executionPrecheck().allowSkillInternals());
        String mergedParamsJson = mergeExecutionSnapshot(prepared.paramsJson(), snapshot);
        return prepared.withParamsJson(mergedParamsJson).withPersonalAgentMode(mode.name());
    }

    public void recordPlanningEvents(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (context == null || prepared == null || !prepared.personalAgent()) {
            return;
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        Map<String, Object> personalAgent = getObjectMap(payload.get("personalAgent"));
        String mode = firstNonBlank(
                normalizeText(personalAgent.get("mode")),
                personalAgentModeResolver.resolve(prepared).name());
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "PERSONAL_AGENT_MODE_RESOLVED",
                mode,
                mode,
                JSON.toJSONString(Map.of("mode", mode, "enabled", Boolean.TRUE)));
        Object executionPlan = payload.get("executionPlan");
        if (executionPlan == null) {
            return;
        }
        Map<String, Object> planMap = getObjectMap(executionPlan);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "EXECUTION_PLAN_CREATED",
                normalizeText(planMap.get("planId")),
                firstNonBlank(normalizeText(planMap.get("goal")), mode),
                JSON.toJSONString(planMap.isEmpty() ? executionPlan : planMap));
        Object executionPrecheck = payload.get("executionPrecheck");
        if (executionPrecheck == null) {
            return;
        }
        Map<String, Object> precheckMap = getObjectMap(executionPrecheck);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "EXECUTION_PRECHECK_COMPLETED",
                normalizeText(precheckMap.get("status")),
                buildPrecheckSummary(precheckMap),
                JSON.toJSONString(precheckMap.isEmpty() ? executionPrecheck : precheckMap));
    }

    public ChatRuntimePreparedRequest markExecutionRunning(ChatRuntimePreparedRequest prepared) {
        return updateExecutionState(prepared, resolveRunningState(prepared));
    }

    public ChatRuntimePreparedRequest markExecutionCompleted(
            ChatRuntimePreparedRequest prepared, Map<String, Object> finalArtifact) {
        return updateExecutionState(
                prepared, resolveTerminalState(prepared, STATE_COMPLETED, STATUS_COMPLETED, null, finalArtifact));
    }

    public ChatRuntimePreparedRequest markExecutionFailed(
            ChatRuntimePreparedRequest prepared, String errorMessage, Map<String, Object> finalArtifact) {
        return updateExecutionState(
                prepared, resolveTerminalState(prepared, STATE_FAILED, STATUS_FAILED, errorMessage, finalArtifact));
    }

    public ChatRuntimePreparedRequest markExecutionCancelled(
            ChatRuntimePreparedRequest prepared, Map<String, Object> finalArtifact) {
        return updateExecutionState(
                prepared, resolveTerminalState(prepared, STATE_CANCELLED, STATUS_CANCELLED, null, finalArtifact));
    }

    public ChatRuntimePreparedRequest startCurrentStep(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        return mutateExecution(prepared, payload -> {
            Map<String, Object> state = getMutableMap(payload, "executionState");
            List<Map<String, Object>> steps = getMutablePlanSteps(payload);
            String currentStepId = resolveCurrentStepId(state, steps);
            if (!StringUtils.hasText(currentStepId)) {
                return;
            }
            Map<String, Object> currentStep = findStep(steps, currentStepId);
            if (STATUS_RUNNING.equalsIgnoreCase(normalizeText(currentStep.get("status")))) {
                return;
            }
            currentStep.put("status", STATUS_RUNNING);
            state.put("currentStepId", currentStepId);
            state.put(
                    "status",
                    PersonalAgentMode.fromPreparedMode(prepared.personalAgentMode()) == PersonalAgentMode.CHAT_ONLY
                            ? STATE_DIRECT_ANSWER
                            : STATE_RUNNING);
            state.put("completedStepIds", mergeCompletedSteps(List.of(STEP_ROUTE), previousCompletedStepIds(payload)));
            state.put("updatedAt", Instant.now().toString());
            log.debug(
                    "[个人Agent] 步骤开始：会话ID={}, 步骤ID={}, 步骤名={}",
                    prepared.sessionId(),
                    currentStepId,
                    firstNonBlank(normalizeText(currentStep.get("name")), currentStepId));
            appendStepEvent(context, "EXECUTION_STEP_STARTED", currentStepId, stepSummary(currentStep, "开始执行"));
        });
    }

    public ChatRuntimePreparedRequest moveToStep(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String nextStepId,
            String reason) {
        if (prepared == null || !prepared.personalAgent() || !StringUtils.hasText(nextStepId)) {
            return prepared;
        }
        return mutateExecution(prepared, payload -> {
            Map<String, Object> state = getMutableMap(payload, "executionState");
            List<Map<String, Object>> steps = getMutablePlanSteps(payload);
            String currentStepId = resolveCurrentStepId(state, steps);
            if (!StringUtils.hasText(currentStepId) || currentStepId.equals(nextStepId.trim())) {
                return;
            }
            Map<String, Object> currentStep = findStep(steps, currentStepId);
            if (!currentStep.isEmpty()) {
                currentStep.put("status", STATUS_COMPLETED);
                List<String> completedStepIds =
                        mergeCompletedSteps(List.of(currentStepId), previousCompletedStepIds(payload));
                state.put("completedStepIds", completedStepIds);
                log.debug("[个人Agent] 切换前完成当前步骤：会话ID={}, 步骤ID={}, 原因={}", prepared.sessionId(), currentStepId, reason);
                appendStepEvent(context, "EXECUTION_STEP_COMPLETED", currentStepId, stepSummary(currentStep, reason));
            }
            Map<String, Object> nextStep = findStep(steps, nextStepId);
            if (nextStep.isEmpty()) {
                return;
            }
            nextStep.put("status", STATUS_RUNNING);
            state.put("currentStepId", nextStepId.trim());
            state.put("status", STATE_RUNNING);
            state.put("updatedAt", Instant.now().toString());
            log.debug("[个人Agent] 步骤切换：会话ID={}, 下一步骤ID={}, 原因={}", prepared.sessionId(), nextStepId, reason);
            appendStepEvent(context, "EXECUTION_STEP_STARTED", nextStepId, stepSummary(nextStep, reason));
        });
    }

    public ChatRuntimePreparedRequest prepareForSuccessfulCompletion(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            Map<String, Object> finalArtifact) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        log.debug("[个人Agent] 准备成功收尾：会话ID={}, 产物={}", prepared.sessionId(), finalArtifact);
        PersonalAgentMode mode = PersonalAgentMode.fromPreparedMode(prepared.personalAgentMode());
        ChatRuntimePreparedRequest current = prepared;
        String finalStepId = resolveTerminalStepId(mode);
        if (StringUtils.hasText(finalStepId)) {
            current = moveToStep(context, current, finalStepId, "进入收尾阶段");
        }
        current = completeCurrentStep(context, current, "执行完成");
        return markExecutionCompleted(current, finalArtifact);
    }

    public ChatRuntimePreparedRequest prepareForFailureCompletion(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String errorMessage,
            Map<String, Object> finalArtifact) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        log.debug("[个人Agent] 准备失败收尾：会话ID={}, 错误={}, 产物={}", prepared.sessionId(), errorMessage, finalArtifact);
        ChatRuntimePreparedRequest current = failCurrentStep(context, prepared, errorMessage);
        return markExecutionFailed(current, errorMessage, finalArtifact);
    }

    public ChatRuntimePreparedRequest prepareForCancelledCompletion(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            Map<String, Object> finalArtifact) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        log.debug("[个人Agent] 准备取消收尾：会话ID={}, 产物={}", prepared.sessionId(), finalArtifact);
        ChatRuntimePreparedRequest current = failCurrentStep(context, prepared, "执行已取消");
        return markExecutionCancelled(current, finalArtifact);
    }

    public PrecheckDecision resolvePrecheckDecision(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return PrecheckDecision.ready();
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        Map<String, Object> precheck = getObjectMap(payload.get("executionPrecheck"));
        String status = normalizeText(precheck.get("status"));
        List<String> blockers = getStringList(precheck.get("blockers"));
        List<String> warnings = getStringList(precheck.get("warnings"));
        log.debug(
                "[个人Agent] 解析执行预检：会话ID={}, 状态={}, 步骤执行器={}, 相关技能={}, 允许技能内部能力={}, 阻塞项={}, 警告项={}",
                prepared.sessionId(),
                status,
                normalizeText(precheck.get("stepExecutor")),
                normalizeText(precheck.get("relevantSkill")),
                normalizeText(precheck.get("allowSkillInternals")),
                blockers,
                warnings);
        if (PRECHECK_BLOCKED.equals(status)) {
            return new PrecheckDecision(PRECHECK_BLOCKED, true, buildBlockedResponse(blockers, warnings));
        }
        if (PRECHECK_CONFIRMATION_REQUIRED.equals(status)) {
            return new PrecheckDecision(
                    PRECHECK_CONFIRMATION_REQUIRED, true, buildConfirmationResponse(precheck, warnings));
        }
        return PrecheckDecision.ready();
    }

    public ChatRuntimePreparedRequest prepareForBlockedResponse(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        Map<String, Object> precheck = getObjectMap(payload.get("executionPrecheck"));
        String reason = firstNonBlank(String.join("；", getStringList(precheck.get("blockers"))), "预检未通过");
        ChatRuntimePreparedRequest current = startCurrentStep(context, markExecutionRunning(prepared));
        current = failCurrentStep(context, current, reason);
        log.debug("[个人Agent] 预检阻塞：会话ID={}, 原因={}", prepared.sessionId(), reason);
        current = mutateExecution(current, mutablePayload -> {
            Map<String, Object> state = getMutableMap(mutablePayload, "executionState");
            state.put("status", STATE_BLOCKED);
            state.put("waitingReason", reason);
            state.put("updatedAt", Instant.now().toString());
            conversationEventService.appendEvent(
                    context,
                    context.assistantMessageId(),
                    "EXECUTION_PRECHECK_BLOCKED",
                    PRECHECK_BLOCKED,
                    reason,
                    JSON.toJSONString(Map.of("reason", reason, "stepId", state.get("currentStepId"))));
        });
        return current;
    }

    public ChatRuntimePreparedRequest prepareForConfirmationResponse(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return prepared;
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        Map<String, Object> precheck = getObjectMap(payload.get("executionPrecheck"));
        String reason = firstNonBlank(String.join("；", getStringList(precheck.get("warnings"))), "该操作需要用户确认后再继续");
        ChatRuntimePreparedRequest current = startCurrentStep(context, markExecutionRunning(prepared));
        current = completeCurrentStep(context, current, "预检已完成，等待确认");
        log.debug("[个人Agent] 预检需要确认：会话ID={}, 原因={}", prepared.sessionId(), reason);
        current = mutateExecution(current, mutablePayload -> {
            Map<String, Object> state = getMutableMap(mutablePayload, "executionState");
            state.put("status", STATE_WAITING_CONFIRMATION);
            state.put("waitingReason", reason);
            state.put("updatedAt", Instant.now().toString());
            conversationEventService.appendEvent(
                    context,
                    context.assistantMessageId(),
                    "EXECUTION_CONFIRMATION_REQUIRED",
                    PRECHECK_CONFIRMATION_REQUIRED,
                    reason,
                    JSON.toJSONString(Map.of("reason", reason, "stepId", state.get("currentStepId"))));
        });
        return current;
    }

    private PersonalAgentExecutionSnapshot buildSnapshot(ChatRuntimePreparedRequest prepared, PersonalAgentMode mode) {
        PersonalAgentExecutionPlan plan = buildPlan(prepared, mode);
        PersonalAgentExecutionState state = buildState(mode, plan.steps());
        PersonalAgentExecutionPrecheck precheck = buildPrecheck(prepared, mode);
        return new PersonalAgentExecutionSnapshot(
                new PersonalAgentExecutionMeta(true, mode.name()), plan, state, precheck);
    }

    private PersonalAgentExecutionPlan buildPlan(ChatRuntimePreparedRequest prepared, PersonalAgentMode mode) {
        List<PersonalAgentExecutionStep> steps = buildSteps(prepared, mode);
        return new PersonalAgentExecutionPlan(
                UlidGenerator.next(), 1, resolveGoal(prepared, mode), resolveStrategy(mode), steps);
    }

    private List<PersonalAgentExecutionStep> buildSteps(ChatRuntimePreparedRequest prepared, PersonalAgentMode mode) {
        if (mode == PersonalAgentMode.CHAT_ONLY) {
            return List.of(
                    new PersonalAgentExecutionStep(
                            STEP_ROUTE, "模式路由", "ROUTE", "MODEL", "personal-agent", STATUS_COMPLETED),
                    new PersonalAgentExecutionStep(
                            "step-answer", "直接回答", "ANSWER", "MODEL", "user_request", STATUS_PENDING));
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return List.of(
                    new PersonalAgentExecutionStep(
                            STEP_ROUTE, "模式路由", "ROUTE", "MODEL", "personal-agent", STATUS_COMPLETED),
                    new PersonalAgentExecutionStep(
                            "step-understand",
                            "内容理解",
                            "UNDERSTAND",
                            "MODEL_OR_PARSE_FILE",
                            resolvePrimaryTarget(prepared),
                            STATUS_PENDING),
                    new PersonalAgentExecutionStep(
                            "step-respond", "整理回答", "ANSWER", "MODEL", "user_request", STATUS_PENDING));
        }
        return List.of(
                new PersonalAgentExecutionStep(
                        STEP_ROUTE, "模式路由", "ROUTE", "MODEL", "personal-agent", STATUS_COMPLETED),
                new PersonalAgentExecutionStep(
                        "step-precheck", "执行预检", "PRECHECK", "MODEL", resolvePrimaryTarget(prepared), STATUS_PENDING),
                new PersonalAgentExecutionStep(
                        "step-execute",
                        "主执行",
                        "EXECUTE",
                        "SKILL_OR_RUNTIME",
                        "best_available_capability",
                        STATUS_PENDING),
                new PersonalAgentExecutionStep(
                        "step-verify", "结果校验", "VERIFY", "MODEL_OR_RUNTIME", "artifact_or_result", STATUS_PENDING));
    }

    private PersonalAgentExecutionState buildState(PersonalAgentMode mode, List<PersonalAgentExecutionStep> steps) {
        String currentStepId =
                steps.size() > 1 ? steps.get(1).stepId() : steps.get(0).stepId();
        return new PersonalAgentExecutionState(
                mode == PersonalAgentMode.CHAT_ONLY ? STATE_DIRECT_ANSWER : STATE_PLANNED,
                currentStepId,
                List.of(STEP_ROUTE),
                null,
                null,
                List.of(),
                Instant.now().toString());
    }

    private String resolveGoal(ChatRuntimePreparedRequest prepared, PersonalAgentMode mode) {
        if (mode == PersonalAgentMode.CHAT_ONLY) {
            return "直接回答用户当前问题";
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return hasAttachments(prepared) ? "理解附件内容并给出整理结果" : "理解用户输入内容并给出整理结果";
        }
        String message = prepared == null ? "" : normalizeText(prepared.message());
        if (StringUtils.hasText(message)) {
            return shrink(message, 80);
        }
        return "完成用户请求的执行任务";
    }

    private String resolveStrategy(PersonalAgentMode mode) {
        if (mode == PersonalAgentMode.CHAT_ONLY) {
            return "保持普通对话；仅在用户明确要求执行任务、操作文件或产生产物时再切换执行模式";
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return "优先理解用户输入与附件内容；parse_file 仅作为文件理解辅助，不默认进入脚本执行";
        }
        return "优先判断是否已有 skill 或现成 runtime 能力；parse_file 负责读取上传附件与 runtime 产物；若 parse_file 仍无法拿到有效内容，再进入 Python fallback";
    }

    private String resolvePrimaryTarget(ChatRuntimePreparedRequest prepared) {
        if (hasAttachments(prepared)) {
            return "attachments";
        }
        return "user_request";
    }

    private boolean hasAttachments(ChatRuntimePreparedRequest prepared) {
        return prepared != null
                && StringUtils.hasText(prepared.fileListJson())
                && prepared.fileListJson().contains("\"id\"");
    }

    private PersonalAgentExecutionPrecheck buildPrecheck(ChatRuntimePreparedRequest prepared, PersonalAgentMode mode) {
        boolean hasAttachments = hasAttachments(prepared);
        String selectedSkill = resolveSelectedSkill(prepared);
        ToolToCodeEscalationDecision toolToCodeDecision = toolToCodeEscalationPolicy.evaluate(
                prepared == null ? null : prepared.message(),
                resolveFileIds(prepared),
                resolveParsedAttachments(prepared),
                resolveSelectedSkillDescriptor(prepared, selectedSkill));
        String primaryExecutor = resolvePrimaryExecutor(mode, selectedSkill);
        String stepExecutor = resolveStepExecutor(mode, selectedSkill, toolToCodeDecision);
        String relevantSkill = EXECUTOR_SKILL.equalsIgnoreCase(stepExecutor) ? selectedSkill : "";
        boolean allowSkillInternals =
                EXECUTOR_SKILL.equalsIgnoreCase(stepExecutor) && StringUtils.hasText(relevantSkill);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean needsUserConfirmation = false;
        if (mode == PersonalAgentMode.CONTENT_ASSIST && !hasAttachments) {
            warnings.add("当前未发现附件，内容理解将仅基于用户输入");
        }
        if (mode == PersonalAgentMode.EXECUTION_TASK) {
            if (looksLikeDestructiveTask(prepared == null ? null : prepared.message())) {
                needsUserConfirmation = true;
                warnings.add("检测到可能影响现有数据或文件的操作，执行前需要用户确认");
            }
            if (StringUtils.hasText(selectedSkill)) {
                notes.add("已识别可优先尝试的技能：" + selectedSkill);
            } else if (hasLoadedSkills(prepared)) {
                notes.add("当前已有已加载技能，可优先沿用现有技能上下文");
            } else {
                warnings.add("当前未命中明确技能，将先走通用执行编排");
            }
            if (toolToCodeDecision.allowCodeExecution()) {
                notes.add("当前请求允许在 TOOL 不足时使用 CODE 兜底：" + toolToCodeDecision.reason());
            } else {
                notes.add("当前请求默认留在 TOOL world：" + toolToCodeDecision.reason());
            }
            if (!hasAttachments && !StringUtils.hasText(prepared == null ? null : prepared.message())) {
                blockers.add("缺少可执行输入，无法进入主执行");
            }
        }
        String status = blockers.isEmpty()
                ? (needsUserConfirmation ? PRECHECK_CONFIRMATION_REQUIRED : PRECHECK_READY)
                : PRECHECK_BLOCKED;
        return new PersonalAgentExecutionPrecheck(
                status,
                primaryExecutor,
                selectedSkill,
                stepExecutor,
                relevantSkill,
                allowSkillInternals,
                toolToCodeDecision.allowCodeExecution(),
                toolToCodeDecision.allowCodeExecution(),
                hasAttachments,
                needsUserConfirmation,
                blockers,
                warnings,
                notes);
    }

    private String mergeExecutionSnapshot(String paramsJson, PersonalAgentExecutionSnapshot snapshot) {
        Map<String, Object> payload = parsePayload(paramsJson);
        payload.put("personalAgent", snapshot.personalAgent());
        payload.put("executionPlan", snapshot.executionPlan());
        payload.put("executionState", snapshot.executionState());
        payload.put("executionPrecheck", snapshot.executionPrecheck());
        return JSON.toJSONString(payload);
    }

    private ChatRuntimePreparedRequest updateExecutionState(
            ChatRuntimePreparedRequest prepared, PersonalAgentExecutionState nextState) {
        if (prepared == null || !prepared.personalAgent() || nextState == null) {
            return prepared;
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        payload.put("executionState", nextState);
        return prepared.withParamsJson(JSON.toJSONString(payload));
    }

    private PersonalAgentExecutionState resolveRunningState(ChatRuntimePreparedRequest prepared) {
        Map<String, Object> payload = parsePayload(prepared == null ? null : prepared.paramsJson());
        Map<String, Object> plan = getObjectMap(payload.get("executionPlan"));
        List<Map<String, Object>> steps = getObjectList(plan.get("steps"));
        String currentStepId = firstPendingStepId(steps);
        if (!StringUtils.hasText(currentStepId)) {
            currentStepId = "step-answer";
        }
        String stateStatus =
                PersonalAgentMode.fromPreparedMode(prepared.personalAgentMode()) == PersonalAgentMode.CHAT_ONLY
                        ? STATE_DIRECT_ANSWER
                        : STATE_RUNNING;
        return new PersonalAgentExecutionState(
                stateStatus,
                currentStepId,
                mergeCompletedSteps(List.of(STEP_ROUTE), previousCompletedStepIds(payload)),
                null,
                null,
                previousArtifacts(payload),
                Instant.now().toString());
    }

    private PersonalAgentExecutionState resolveTerminalState(
            ChatRuntimePreparedRequest prepared,
            String stateStatus,
            String terminalStepStatus,
            String errorMessage,
            Map<String, Object> finalArtifact) {
        Map<String, Object> payload = parsePayload(prepared == null ? null : prepared.paramsJson());
        Map<String, Object> state = getObjectMap(payload.get("executionState"));
        String currentStepId = normalizeText(state.get("currentStepId"));
        return new PersonalAgentExecutionState(
                stateStatus,
                currentStepId,
                previousCompletedStepIds(payload),
                null,
                normalizeNullableText(errorMessage),
                mergeArtifacts(previousArtifacts(payload), finalArtifact),
                Instant.now().toString());
    }

    private ChatRuntimePreparedRequest completeCurrentStep(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String summary) {
        return mutateExecution(prepared, payload -> {
            Map<String, Object> state = getMutableMap(payload, "executionState");
            List<Map<String, Object>> steps = getMutablePlanSteps(payload);
            String currentStepId = resolveCurrentStepId(state, steps);
            if (!StringUtils.hasText(currentStepId)) {
                return;
            }
            Map<String, Object> currentStep = findStep(steps, currentStepId);
            if (currentStep.isEmpty()) {
                return;
            }
            currentStep.put("status", STATUS_COMPLETED);
            state.put(
                    "completedStepIds", mergeCompletedSteps(List.of(currentStepId), previousCompletedStepIds(payload)));
            state.put("updatedAt", Instant.now().toString());
            log.debug(
                    "[个人Agent] 步骤完成：会话ID={}, 步骤ID={}, 摘要={}",
                    prepared == null ? null : prepared.sessionId(),
                    currentStepId,
                    summary);
            appendStepEvent(context, "EXECUTION_STEP_COMPLETED", currentStepId, stepSummary(currentStep, summary));
        });
    }

    private ChatRuntimePreparedRequest failCurrentStep(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String summary) {
        return mutateExecution(prepared, payload -> {
            Map<String, Object> state = getMutableMap(payload, "executionState");
            List<Map<String, Object>> steps = getMutablePlanSteps(payload);
            String currentStepId = resolveCurrentStepId(state, steps);
            if (!StringUtils.hasText(currentStepId)) {
                return;
            }
            Map<String, Object> currentStep = findStep(steps, currentStepId);
            if (currentStep.isEmpty()) {
                return;
            }
            currentStep.put("status", STATUS_FAILED);
            state.put("lastError", normalizeNullableText(summary));
            state.put("updatedAt", Instant.now().toString());
            log.debug(
                    "[个人Agent] 步骤失败：会话ID={}, 步骤ID={}, 错误={}",
                    prepared == null ? null : prepared.sessionId(),
                    currentStepId,
                    summary);
            appendStepEvent(context, "EXECUTION_STEP_FAILED", currentStepId, stepSummary(currentStep, summary));
        });
    }

    private ChatRuntimePreparedRequest mutateExecution(
            ChatRuntimePreparedRequest prepared, Consumer<Map<String, Object>> payloadUpdater) {
        if (prepared == null || !prepared.personalAgent() || payloadUpdater == null) {
            return prepared;
        }
        Map<String, Object> payload = parsePayload(prepared.paramsJson());
        payloadUpdater.accept(payload);
        return prepared.withParamsJson(JSON.toJSONString(payload));
    }

    private Map<String, Object> parsePayload(String paramsJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!StringUtils.hasText(paramsJson)) {
            return payload;
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (parsed != null) {
                payload.putAll(parsed);
            }
        } catch (Exception ex) {
            log.warn("解析 personal-agent 参数快照失败，将重建参数：error={}", ex.getMessage());
        }
        return payload;
    }

    private Map<String, Object> getObjectMap(Object value) {
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
        return Map.of();
    }

    private Map<String, Object> getMutableMap(Map<String, Object> payload, String key) {
        Map<String, Object> current = getObjectMap(payload.get(key));
        Map<String, Object> mutable = new LinkedHashMap<>(current);
        payload.put(key, mutable);
        return mutable;
    }

    private List<Map<String, Object>> getMutablePlanSteps(Map<String, Object> payload) {
        Map<String, Object> plan = getMutableMap(payload, "executionPlan");
        List<Map<String, Object>> currentSteps = getObjectList(plan.get("steps"));
        List<Map<String, Object>> mutableSteps = new ArrayList<>();
        for (Map<String, Object> step : currentSteps) {
            mutableSteps.add(new LinkedHashMap<>(step));
        }
        plan.put("steps", mutableSteps);
        return mutableSteps;
    }

    private List<Map<String, Object>> getObjectList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : listValue) {
            Map<String, Object> normalized = getObjectMap(item);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> previousCompletedStepIds(Map<String, Object> payload) {
        Map<String, Object> state = getObjectMap(payload.get("executionState"));
        Object rawCompletedStepIds = state.get("completedStepIds");
        if (!(rawCompletedStepIds instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : listValue) {
            String value = normalizeText(item);
            if (StringUtils.hasText(value) && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<PersonalAgentExecutionArtifact> previousArtifacts(Map<String, Object> payload) {
        Map<String, Object> state = getObjectMap(payload.get("executionState"));
        Object rawArtifacts = state.get("artifacts");
        if (!(rawArtifacts instanceof List<?> listValue)) {
            return List.of();
        }
        List<PersonalAgentExecutionArtifact> result = new ArrayList<>();
        for (Object item : listValue) {
            Map<String, Object> artifact = getObjectMap(item);
            if (artifact.isEmpty()) {
                continue;
            }
            result.add(new PersonalAgentExecutionArtifact(
                    firstNonBlank(
                            normalizeText(artifact.get("type")),
                            normalizeText(artifact.get("artifactType")),
                            "artifact"),
                    firstNonBlank(
                            normalizeText(artifact.get("name")),
                            normalizeText(artifact.get("file")),
                            normalizeText(artifact.get("objectName")),
                            normalizeText(artifact.get("path")),
                            "artifact"),
                    firstNonBlank(
                            normalizeText(artifact.get("uri")),
                            normalizeText(artifact.get("downloadUrl")),
                            normalizeText(artifact.get("path")),
                            normalizeText(artifact.get("objectName")))));
        }
        return result;
    }

    private List<PersonalAgentExecutionArtifact> mergeArtifacts(
            List<PersonalAgentExecutionArtifact> existingArtifacts, Map<String, Object> finalArtifact) {
        List<PersonalAgentExecutionArtifact> merged =
                new ArrayList<>(existingArtifacts == null ? List.of() : existingArtifacts);
        PersonalAgentExecutionArtifact artifact = toExecutionArtifact(finalArtifact);
        if (artifact == null) {
            return merged;
        }
        boolean duplicate = merged.stream().anyMatch(item -> sameArtifact(item, artifact));
        if (!duplicate) {
            merged.add(artifact);
        }
        return merged;
    }

    private PersonalAgentExecutionArtifact toExecutionArtifact(Map<String, Object> finalArtifact) {
        if (finalArtifact == null || finalArtifact.isEmpty()) {
            return null;
        }
        String uri = firstNonBlank(
                normalizeText(finalArtifact.get("downloadUrl")),
                normalizeText(finalArtifact.get("path")),
                normalizeText(finalArtifact.get("objectName")));
        String name = firstNonBlank(
                normalizeText(finalArtifact.get("file")),
                normalizeText(finalArtifact.get("name")),
                normalizeText(finalArtifact.get("objectName")),
                normalizeText(finalArtifact.get("path")));
        if (!StringUtils.hasText(uri) && !StringUtils.hasText(name)) {
            return null;
        }
        return new PersonalAgentExecutionArtifact(
                firstNonBlank(
                        normalizeText(finalArtifact.get("type")),
                        normalizeText(finalArtifact.get("artifactType")),
                        "artifact"),
                StringUtils.hasText(name) ? name : "artifact",
                uri);
    }

    private boolean sameArtifact(PersonalAgentExecutionArtifact left, PersonalAgentExecutionArtifact right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeText(left.uri()).equals(normalizeText(right.uri()))
                && normalizeText(left.name()).equals(normalizeText(right.name()));
    }

    private List<String> mergeCompletedSteps(List<String> preferred, List<String> existing) {
        List<String> merged = new ArrayList<>();
        for (String stepId : existing == null ? List.<String>of() : existing) {
            if (StringUtils.hasText(stepId) && !merged.contains(stepId.trim())) {
                merged.add(stepId.trim());
            }
        }
        for (String stepId : preferred == null ? List.<String>of() : preferred) {
            if (StringUtils.hasText(stepId) && !merged.contains(stepId.trim())) {
                merged.add(stepId.trim());
            }
        }
        return merged;
    }

    private String firstPendingStepId(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps == null ? List.<Map<String, Object>>of() : steps) {
            String status = normalizeText(step.get("status"));
            if (!STATUS_COMPLETED.equalsIgnoreCase(status)) {
                return normalizeText(step.get("stepId"));
            }
        }
        return "";
    }

    private String lastStepId(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        return normalizeText(steps.get(steps.size() - 1).get("stepId"));
    }

    private String resolveCurrentStepId(Map<String, Object> state, List<Map<String, Object>> steps) {
        String currentStepId = state == null ? "" : normalizeText(state.get("currentStepId"));
        if (StringUtils.hasText(currentStepId)) {
            return currentStepId;
        }
        return firstPendingStepId(steps);
    }

    private Map<String, Object> findStep(List<Map<String, Object>> steps, String stepId) {
        if (!StringUtils.hasText(stepId)) {
            return Map.of();
        }
        for (Map<String, Object> step : steps == null ? List.<Map<String, Object>>of() : steps) {
            if (stepId.trim().equals(normalizeText(step.get("stepId")))) {
                return step;
            }
        }
        return Map.of();
    }

    private String resolveTerminalStepId(PersonalAgentMode mode) {
        if (mode == PersonalAgentMode.EXECUTION_TASK) {
            return "step-verify";
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return "step-respond";
        }
        return "step-answer";
    }

    private void appendStepEvent(
            ConversationHistoryService.ConversationContext context, String eventType, String stepId, String summary) {
        if (context == null || !StringUtils.hasText(eventType) || !StringUtils.hasText(stepId)) {
            return;
        }
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                eventType,
                stepId.trim(),
                summary,
                JSON.toJSONString(Map.of("stepId", stepId.trim(), "summaryText", summary)));
    }

    private String stepSummary(Map<String, Object> step, String suffix) {
        String stepName = firstNonBlank(normalizeText(step.get("name")), normalizeText(step.get("stepId")));
        if (!StringUtils.hasText(suffix)) {
            return stepName;
        }
        return stepName + "：" + suffix.trim();
    }

    private String resolveSelectedSkill(ChatRuntimePreparedRequest prepared) {
        if (prepared == null) {
            return "";
        }
        if (StringUtils.hasText(prepared.runtimeSkillName())) {
            return prepared.runtimeSkillName().trim();
        }
        for (RuntimeLoadedSkill loadedSkill :
                prepared.loadedSkills() == null ? List.<RuntimeLoadedSkill>of() : prepared.loadedSkills()) {
            if (loadedSkill != null && StringUtils.hasText(loadedSkill.runtimeSkillName())) {
                return loadedSkill.runtimeSkillName().trim();
            }
        }
        for (RuntimeSkillDescriptor descriptor :
                prepared.availableSkills() == null ? List.<RuntimeSkillDescriptor>of() : prepared.availableSkills()) {
            if (descriptor != null
                    && StringUtils.hasText(descriptor.runtimeSkillName())
                    && matchesSkillHint(prepared.message(), descriptor)) {
                return descriptor.runtimeSkillName().trim();
            }
        }
        return "";
    }

    private boolean matchesSkillHint(String message, RuntimeSkillDescriptor descriptor) {
        if (!StringUtils.hasText(message) || descriptor == null) {
            return false;
        }
        String normalizedMessage = message.trim();
        return containsIgnoreCase(normalizedMessage, descriptor.runtimeSkillName())
                || containsIgnoreCase(normalizedMessage, descriptor.displayName())
                || containsIgnoreCase(normalizedMessage, descriptor.description());
    }

    private boolean containsIgnoreCase(String source, String fragment) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(fragment)) {
            return false;
        }
        return source.toLowerCase().contains(fragment.trim().toLowerCase());
    }

    private String resolvePrimaryExecutor(PersonalAgentMode mode, String selectedSkill) {
        if (mode == PersonalAgentMode.CHAT_ONLY) {
            return EXECUTOR_MODEL;
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return EXECUTOR_MODEL_OR_PARSE_FILE;
        }
        if (StringUtils.hasText(selectedSkill)) {
            return EXECUTOR_SKILL;
        }
        return EXECUTOR_TOOL;
    }

    private String resolveStepExecutor(
            PersonalAgentMode mode, String selectedSkill, ToolToCodeEscalationDecision toolToCodeDecision) {
        if (mode == PersonalAgentMode.CHAT_ONLY) {
            return EXECUTOR_MODEL;
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return EXECUTOR_MODEL;
        }
        if (StringUtils.hasText(selectedSkill)) {
            return EXECUTOR_SKILL;
        }
        return EXECUTOR_TOOL;
    }

    private List<String> resolveFileIds(ChatRuntimePreparedRequest prepared) {
        Map<String, Object> payload = parsePayload(prepared == null ? null : prepared.paramsJson());
        Object value = payload.get("fileIds");
        return getStringList(value);
    }

    private List<Map<String, Object>> resolveParsedAttachments(ChatRuntimePreparedRequest prepared) {
        Map<String, Object> payload = parsePayload(prepared == null ? null : prepared.paramsJson());
        return getObjectList(payload.get("parsedAttachments"));
    }

    private RuntimeSkillDescriptor resolveSelectedSkillDescriptor(
            ChatRuntimePreparedRequest prepared, String selectedSkill) {
        if (!StringUtils.hasText(selectedSkill) || prepared == null || prepared.availableSkills() == null) {
            return null;
        }
        for (RuntimeSkillDescriptor descriptor : prepared.availableSkills()) {
            if (descriptor != null && selectedSkill.equalsIgnoreCase(descriptor.runtimeSkillName())) {
                return descriptor;
            }
        }
        return null;
    }

    private boolean hasLoadedSkills(ChatRuntimePreparedRequest prepared) {
        return prepared != null
                && prepared.loadedSkills() != null
                && !prepared.loadedSkills().isEmpty();
    }

    private boolean looksLikeDestructiveTask(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.trim();
        return text.contains("删除")
                || text.contains("清空")
                || text.contains("覆盖")
                || text.contains("批量修改")
                || text.contains("批量更新")
                || text.contains("替换原文件");
    }

    private String buildPrecheckSummary(Map<String, Object> precheckMap) {
        String status = normalizeText(precheckMap.get("status"));
        String primaryExecutor = normalizeText(precheckMap.get("primaryExecutor"));
        String stepExecutor = normalizeText(precheckMap.get("stepExecutor"));
        String relevantSkill = normalizeText(precheckMap.get("relevantSkill"));
        List<String> blockers = getStringList(precheckMap.get("blockers"));
        if (!blockers.isEmpty()) {
            return "预检结果：" + status + "，阻塞原因：" + String.join("；", blockers);
        }
        if (StringUtils.hasText(relevantSkill)) {
            return "预检结果：" + status + "，执行器：" + firstNonBlank(stepExecutor, primaryExecutor) + "，技能：" + relevantSkill;
        }
        return "预检结果：" + firstNonBlank(status, "UNKNOWN") + "，执行器："
                + firstNonBlank(stepExecutor, primaryExecutor, "UNKNOWN");
    }

    private String buildBlockedResponse(List<String> blockers, List<String> warnings) {
        String blockerText = blockers == null || blockers.isEmpty() ? "当前请求暂时无法进入执行" : String.join("；", blockers);
        if (warnings == null || warnings.isEmpty()) {
            return "当前无法继续执行。原因：" + blockerText;
        }
        return "当前无法继续执行。原因：" + blockerText + "。补充说明：" + String.join("；", warnings);
    }

    private String buildConfirmationResponse(Map<String, Object> precheck, List<String> warnings) {
        String warningText = warnings == null || warnings.isEmpty() ? "该操作需要你确认后我再继续执行。" : String.join("；", warnings);
        String relevantSkill = firstNonBlank(
                normalizeText(precheck.get("relevantSkill")), normalizeText(precheck.get("selectedSkill")));
        if (StringUtils.hasText(relevantSkill)) {
            return warningText + " 当前建议主执行能力为：" + relevantSkill + "。如果确认，请直接回复“继续执行”。";
        }
        return warningText + " 如果确认，请直接回复“继续执行”。";
    }

    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : listValue) {
            String text = normalizeText(item);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    public record PrecheckDecision(String status, boolean terminal, String responseMessage) {

        public static PrecheckDecision ready() {
            return new PrecheckDecision(PRECHECK_READY, false, "");
        }
    }

    private String firstNonBlank(String... values) {
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

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value) || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
