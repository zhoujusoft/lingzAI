package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2ActiveToolRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RequestHints;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport.UsageGuardResult;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeState;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionAssessment;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionGate;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphEvent;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphModelSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphStateProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol.ReactDecision;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionRegressionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

public class RuntimeV2ReasoningNode implements NodeAction {

    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeV2GraphModelSupport graphModelSupport;
    private final RuntimeV2CompletionGate completionGate;
    private final RuntimeV2LedgerEngine ledgerEngine;
    private final RuntimeV2ActiveToolRegistry activeToolRegistry;
    private final RuntimeV2RecoveryPolicy recoveryPolicy;
    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final RuntimeExecutionProperties runtimeExecutionProperties;

    public RuntimeV2ReasoningNode(
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeV2GraphModelSupport graphModelSupport,
            RuntimeV2CompletionGate completionGate,
            RuntimeV2LedgerEngine ledgerEngine,
            RuntimeV2ActiveToolRegistry activeToolRegistry,
            RuntimeV2RecoveryPolicy recoveryPolicy,
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            RuntimeExecutionProperties runtimeExecutionProperties) {
        this.codeExecutionSupport = codeExecutionSupport;
        this.runtimeRegistry = runtimeRegistry;
        this.graphModelSupport = graphModelSupport;
        this.completionGate = completionGate;
        this.ledgerEngine = ledgerEngine;
        this.activeToolRegistry = activeToolRegistry;
        this.recoveryPolicy = recoveryPolicy;
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        String mode = state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.DIRECT.name());
        int llmCallCount = state.value(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0);
        List<String> availableToolNames = state.<List<String>>value(RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES)
                .orElse(List.of());
        Map<String, Object> codeState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                .orElse(Map.of());
        Map<String, Object> toolState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.TOOL_STATE)
                .orElse(Map.of());
        String runtimeContextKey = state.value(RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "");
        RuntimeV2GraphExecutionContext executionContext = runtimeRegistry.resolveExecutionContext(runtimeContextKey);
        if (executionContext != null && executionContext.runtimeState() != null) {
            Map<String, org.springframework.ai.tool.ToolCallback> refreshedToolIndex =
                    activeToolRegistry.refresh(executionContext.runtimeState());
            runtimeRegistry.replaceToolCallbackIndex(runtimeContextKey, refreshedToolIndex);
            availableToolNames = refreshedToolIndex.keySet().stream().distinct().collect(Collectors.toList());
            output.put(RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES, List.copyOf(availableToolNames));
        }

        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.REASONING.name());
        output.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);

        if (executionContext == null) {
            return failClosedUnavailable(
                    output,
                    llmCallCount,
                    "graph-runtime-context-missing",
                    "当前 graph 主链缺少真实运行时上下文，无法继续推理，也不会代替模型做业务决策。");
        }
        if (executionContext.runtimeState() == null || executionContext.decisionChatClient() == null) {
            return failClosedUnavailable(
                    output, llmCallCount, "graph-model-context-missing", "当前 graph 主链未接入真实模型推理上下文，已停止本次运行。");
        }

        if (RuntimeV2Mode.DIRECT.name().equalsIgnoreCase(mode)) {
            String directAnswer = graphModelSupport.streamDirectAnswer(
                    executionContext.runtimeState(),
                    executionContext.streamChatClient() != null
                            ? executionContext.streamChatClient()
                            : executionContext.decisionChatClient(),
                    delta -> emitTerminalContentDelta(executionContext, executionContext.runtimeState(), delta));
            if (!StringUtils.hasText(directAnswer)) {
                directAnswer = "未生成有效回答。";
            }
            UsageGuardResult usageGuardResult = RuntimeV2UsageGuardSupport.resolveTokenBudgetExceeded(
                    executionContext.runtimeState(), runtimeExecutionProperties);
            if (usageGuardResult != null) {
                return failClosedLimitExceeded(
                        output, executionContext.runtimeState().llmCallCount(), usageGuardResult);
            }
            output.put(
                    RuntimeV2GraphStateKeys.LLM_CALL_COUNT,
                    executionContext.runtimeState().llmCallCount());
            output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, directAnswer);
            output.put(
                    RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED,
                    executionContext.runtimeState().terminalAnswerStreamed());
            output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of(new AssistantMessage(directAnswer)));
            output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.DIRECT_ANSWER.name());
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
            output.put(RuntimeV2GraphStateKeys.LAST_DECISION, Map.of("type", "final", "source", "graph-direct-model"));
            return output;
        }

        String codeStatus = normalizeText(codeState.get("status"));
        Object lastToolResult =
                state.value(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT).orElse(null);
        if (RuntimeV2CodeState.CODE_SCRIPT_READY.equalsIgnoreCase(codeStatus)
                && availableToolNames.contains("run_python")) {
            var plan = codeExecutionSupport.readPlan(codeState, state.value(RuntimeV2GraphStateKeys.USER_REQUEST, ""));
            prepareToolCallOutput(
                    output, "run_python", codeExecutionSupport.buildRunPythonArguments(plan), "graph-code-stage", "");
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
            return output;
        }

        if (RuntimeV2CodeState.CODE_OUTPUT_READY.equalsIgnoreCase(codeStatus)
                && availableToolNames.contains("write_artifact")) {
            var plan = codeExecutionSupport.readPlan(codeState, state.value(RuntimeV2GraphStateKeys.USER_REQUEST, ""));
            prepareToolCallOutput(
                    output,
                    "write_artifact",
                    codeExecutionSupport.buildWriteArtifactArguments(plan),
                    "graph-code-stage",
                    "");
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
            return output;
        }

        if (RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED.equalsIgnoreCase(codeStatus)
                && recoveryPolicy.shouldRetryCodeScriptWrite(
                        lastToolResult, readRetryCount(codeState, "writeRepairCount"))
                && availableToolNames.contains("file_write")) {
            output.put(RuntimeV2GraphStateKeys.CODE_STATE, markCodeRetry(codeState, "writeRepairCount"));
            output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.TRUE);
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE);
            output.put(
                    RuntimeV2GraphStateKeys.LAST_DECISION,
                    Map.of(
                            "type",
                            "code-retry",
                            "source",
                            "graph-code-retry",
                            "status",
                            codeStatus,
                            "retryCount",
                            readRetryCount(codeState, "writeRepairCount")));
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
            return output;
        }

        if (RuntimeV2CodeState.CODE_RUN_FAILED.equalsIgnoreCase(codeStatus)
                && recoveryPolicy.shouldRetryCodeRun(lastToolResult, readRetryCount(codeState, "runRepairCount"))
                && availableToolNames.contains("file_write")) {
            output.put(RuntimeV2GraphStateKeys.CODE_STATE, markCodeRetry(codeState, "runRepairCount"));
            output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.TRUE);
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE);
            output.put(
                    RuntimeV2GraphStateKeys.LAST_DECISION,
                    Map.of(
                            "type",
                            "code-retry",
                            "source",
                            "graph-code-retry",
                            "status",
                            codeStatus,
                            "retryCount",
                            readRetryCount(codeState, "runRepairCount")));
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
            return output;
        }

        if (RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED.equalsIgnoreCase(codeStatus)
                && shouldRetryArtifactWrite(codeState)
                && availableToolNames.contains("write_artifact")) {
            var plan = codeExecutionSupport.readPlan(codeState, state.value(RuntimeV2GraphStateKeys.USER_REQUEST, ""));
            output.put(RuntimeV2GraphStateKeys.CODE_STATE, markCodeRetry(codeState, "artifactWriteRetryCount"));
            prepareToolCallOutput(
                    output,
                    "write_artifact",
                    codeExecutionSupport.buildWriteArtifactArguments(plan),
                    "graph-code-retry",
                    "");
            output.put(
                    RuntimeV2GraphStateKeys.LAST_DECISION,
                    Map.of(
                            "type", "tool-retry",
                            "source", "graph-code-retry",
                            "status", codeStatus,
                            "retryCount", readRetryCount(codeState, "artifactWriteRetryCount"),
                            "toolName", "write_artifact"));
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
            return output;
        }

        if (isTerminalCodeFailure(codeStatus, lastToolResult, codeState, availableToolNames)) {
            return failClosedCodeFailure(output, llmCallCount, codeStatus, lastToolResult);
        }

        RuntimeV2State runtimeState = executionContext.runtimeState();
        syncRuntimeState(runtimeState, state, codeState);
        String existingDraftAnswer = readExistingDraftAnswer(state, runtimeState);
        RuntimeV2CompletionAssessment preDecisionAssessment = completionGate.assess(runtimeState, existingDraftAnswer);
        runtimeState.setCompletionAssessment(preDecisionAssessment);
        output.put(RuntimeV2GraphStateKeys.COMPLETION_STATE, preDecisionAssessment.toPayload());
        if (preDecisionAssessment.completionConfirmed()) {
            output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, runtimeState.llmCallCount());
            output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, existingDraftAnswer);
            output.put(RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED, runtimeState.terminalAnswerStreamed());
            output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of(new AssistantMessage(existingDraftAnswer)));
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
            output.put(
                    RuntimeV2GraphStateKeys.LAST_DECISION,
                    Map.of("type", "final", "source", "graph-completion-precheck"));
            return output;
        }
        if (shouldGenerateFinalAnswer(preDecisionAssessment, runtimeState)) {
            String generatedFinalAnswer = generateFinalAnswer(executionContext, runtimeState, existingDraftAnswer);
            RuntimeV2CompletionAssessment finalAssessment = completionGate.assess(runtimeState, generatedFinalAnswer);
            runtimeState.setCompletionAssessment(finalAssessment);
            output.put(RuntimeV2GraphStateKeys.COMPLETION_STATE, finalAssessment.toPayload());
            if (!finalAssessment.blocked()) {
                output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, runtimeState.llmCallCount());
                output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, generatedFinalAnswer);
                output.put(RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED, runtimeState.terminalAnswerStreamed());
                output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of(new AssistantMessage(generatedFinalAnswer)));
                output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
                output.put(
                        RuntimeV2GraphStateKeys.LAST_DECISION,
                        Map.of("type", "final", "source", "graph-final-answer-stage"));
                return output;
            }
            if (shouldForceCodeArtifactDelivery(state, finalAssessment, availableToolNames, codeStatus)) {
                return forceCodeArtifactDelivery(output, runtimeState, finalAssessment);
            }
            appendBlockedContinuationBridge(runtimeState, output, finalAssessment);
        }
        ReactDecision decision = graphModelSupport.resolveReactDecision(
                runtimeState,
                executionContext.decisionChatClient(),
                availableToolNames,
                null);
        UsageGuardResult usageGuardResult =
                RuntimeV2UsageGuardSupport.resolveTokenBudgetExceeded(runtimeState, runtimeExecutionProperties);
        if (usageGuardResult != null) {
            return failClosedLimitExceeded(output, runtimeState.llmCallCount(), usageGuardResult);
        }
        output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, runtimeState.llmCallCount());
        if (!"tool".equalsIgnoreCase(decision.type())) {
            ledgerEngine.refresh(runtimeState);
            RuntimeV2CompletionAssessment assessment = completionGate.assess(runtimeState, decision.answer());
            runtimeState.setCompletionAssessment(assessment);
            output.put(RuntimeV2GraphStateKeys.COMPLETION_STATE, assessment.toPayload());
            if (assessment.blocked()) {
                if (shouldForceCodeArtifactDelivery(state, assessment, availableToolNames, codeStatus)) {
                    return forceCodeArtifactDelivery(output, runtimeState, assessment);
                }
                appendBlockedContinuationBridge(runtimeState, output, assessment);
                output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
                output.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.TRUE);
                output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.REASONING_NODE);
                output.put(
                        RuntimeV2GraphStateKeys.LAST_DECISION,
                        Map.of(
                                "type",
                                "final-blocked",
                                "source",
                                "graph-completion-gate",
                                "blockerCount",
                                assessment.blockerCount(),
                                "summary",
                                normalizeText(assessment.firstBlockerSummary())));
                return output;
            }
            output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, decision.answer());
            output.put(RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED, runtimeState.terminalAnswerStreamed());
            output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of(new AssistantMessage(decision.answer())));
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
            output.put(RuntimeV2GraphStateKeys.LAST_DECISION, Map.of("type", "final", "source", "graph-model"));
            return output;
        }

        String requestedToolName = normalizeText(decision.toolName());
        prepareToolCallOutput(
                output,
                requestedToolName,
                decision.arguments(),
                "graph-model",
                normalizeText(decision.userPreambleMessage()));
        String regressionObservation = RuntimeV2ReactDecisionRegressionSupport.buildRegressionObservation(
                runtimeState, requestedToolName, availableToolNames, 4000);
        if (StringUtils.hasText(regressionObservation)) {
            List<Map<String, Object>> nextTrace = appendSyntheticObservation(
                    runtimeState.observationTrace(), requestedToolName, decision.arguments(), regressionObservation);
            output.put(
                    RuntimeV2GraphStateKeys.ITERATION_COUNT,
                    state.value(RuntimeV2GraphStateKeys.ITERATION_COUNT, 0) + 1);
            output.put(RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.copyOf(nextTrace));
            output.put(RuntimeV2GraphStateKeys.LAST_OBSERVATION, regressionObservation);
            output.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.TRUE);
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.REASONING_NODE);
            output.put(
                    RuntimeV2GraphStateKeys.LAST_DECISION,
                    Map.of(
                            "type", "tool-regression",
                            "source", "graph-runtime-observation",
                            "toolName", requestedToolName,
                            "observationClass", "decision-regression"));
            syncObservationTrace(runtimeState, nextTrace);
            return output;
        }
        if (shouldEnterCodeStage(state, requestedToolName, decision.arguments(), availableToolNames, codeStatus)) {
            output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.TRUE);
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE);
            return output;
        }
        output.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.TRUE);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.ACTION_NODE);
        return output;
    }

    private Map<String, Object> forceCodeArtifactDelivery(
            Map<String, Object> output, RuntimeV2State runtimeState, RuntimeV2CompletionAssessment assessment) {
        output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, runtimeState == null ? 0 : runtimeState.llmCallCount());
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        output.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.TRUE);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE);
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of(
                        "type",
                        "code-escalation",
                        "source",
                        "graph-artifact-completion-gate",
                        "summary",
                        normalizeText(assessment == null ? "" : assessment.firstBlockerSummary())));
        return output;
    }

    private Map<String, Object> failClosedUnavailable(
            Map<String, Object> output, int llmCallCount, String runtimeStatus, String finalMessage) {
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, runtimeStatus);
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, finalMessage);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.GRAPH_RUNTIME_UNAVAILABLE.name());
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of("type", "final", "source", "graph-runtime-guard", "status", runtimeStatus));
        output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
        return output;
    }

    private Map<String, Object> failClosedLimitExceeded(
            Map<String, Object> output, int llmCallCount, UsageGuardResult usageGuardResult) {
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, usageGuardResult.status());
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, usageGuardResult.message());
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE);
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.LIMIT_EXCEEDED.name());
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of("type", "final", "source", "graph-usage-guard", "status", usageGuardResult.status()));
        output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
        return output;
    }

    private Map<String, Object> failClosedCodeFailure(
            Map<String, Object> output, int llmCallCount, String codeStatus, Object toolResult) {
        String runtimeStatus = resolveTerminalCodeFailureStatus(codeStatus);
        String finalMessage = buildTerminalCodeFailureMessage(codeStatus, toolResult);
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, runtimeStatus);
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, finalMessage);
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.TOOL_ERROR.name());
        output.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of(
                        "type",
                        "final",
                        "source",
                        "graph-code-terminal-failure",
                        "status",
                        normalizeText(codeStatus),
                        "runtimeStatus",
                        runtimeStatus));
        output.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, llmCallCount);
        return output;
    }

    private String readExistingDraftAnswer(OverAllState state, RuntimeV2State runtimeState) {
        String finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        if (StringUtils.hasText(finalAnswer)) {
            return finalAnswer.trim();
        }
        String draftAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        if (StringUtils.hasText(draftAnswer)) {
            return draftAnswer.trim();
        }
        return runtimeState == null ? "" : normalizeText(runtimeState.finalAnswer());
    }

    private boolean shouldGenerateFinalAnswer(RuntimeV2CompletionAssessment assessment, RuntimeV2State runtimeState) {
        if (assessment == null
                || assessment.openObligationCount() > 0
                || assessment.blockers().isEmpty()) {
            return false;
        }
        boolean onlyMissingFinalAnswer = assessment.blockers().stream()
                .allMatch(blocker -> "FINAL_ANSWER_EMPTY".equalsIgnoreCase(normalizeText(blocker.code())));
        return onlyMissingFinalAnswer && hasMeaningfulExecutionEvidence(runtimeState);
    }

    private boolean hasMeaningfulExecutionEvidence(RuntimeV2State runtimeState) {
        if (runtimeState == null) {
            return false;
        }
        return runtimeState.evidenceLedger().stream()
                .anyMatch(entry -> entry != null
                        && entry.status()
                                == lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceStatus
                                        .SATISFIED
                        && entry.source() != null
                        && entry.source()
                                != lingzhou.agent.backend.capability.agentruntime.v2.completion
                                        .RuntimeV2CompletionEvidenceSource.SKILL_STATE
                        && entry.source()
                                != lingzhou.agent.backend.capability.agentruntime.v2.completion
                                        .RuntimeV2CompletionEvidenceSource.ANSWER);
    }

    private String generateFinalAnswer(
            RuntimeV2GraphExecutionContext executionContext, RuntimeV2State runtimeState, String draftAnswer) {
        if (executionContext == null || runtimeState == null) {
            return "";
        }
        var finalAnswerClient = executionContext.streamChatClient() != null
                ? executionContext.streamChatClient()
                : executionContext.decisionChatClient();
        List<String> deltas = graphModelSupport
                .streamReactFinalAnswer(runtimeState, finalAnswerClient, draftAnswer)
                .doOnNext(delta -> emitTerminalContentDelta(executionContext, runtimeState, delta))
                .collectList()
                .block();
        if (deltas == null || deltas.isEmpty()) {
            return "";
        }
        return String.join("", deltas).trim();
    }

    private void emitTerminalContentDelta(
            RuntimeV2GraphExecutionContext executionContext, RuntimeV2State runtimeState, String delta) {
        if (!StringUtils.hasText(delta)) {
            return;
        }
        if (runtimeState != null) {
            runtimeState.appendTerminalAnswerDelta(delta);
            if (eventPersistenceCapability != null) {
                eventPersistenceCapability.appendTextSegment(runtimeState.timelineSegments(), delta);
            }
        }
        if (executionContext != null && executionContext.eventEmitter() != null) {
            executionContext.eventEmitter().accept(RuntimeV2GraphEvent.contentDelta(delta));
        }
    }

    private void appendBlockedContinuationBridge(
            RuntimeV2State runtimeState, Map<String, Object> output, RuntimeV2CompletionAssessment assessment) {
        String bridgeMessage = buildBlockedContinuationBridge(assessment);
        if (!StringUtils.hasText(bridgeMessage)) {
            return;
        }
        List<Message> nextMessages = new ArrayList<>();
        if (runtimeState != null
                && runtimeState.messages() != null
                && !runtimeState.messages().isEmpty()) {
            nextMessages.addAll(runtimeState.messages());
        }
        nextMessages.add(new AssistantMessage(bridgeMessage));
        if (runtimeState != null) {
            runtimeState.replaceMessages(nextMessages);
        }
        output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of(new AssistantMessage(bridgeMessage)));
    }

    private String buildBlockedContinuationBridge(RuntimeV2CompletionAssessment assessment) {
        if (assessment == null || !assessment.blocked()) {
            return "";
        }
        String summary = normalizeText(assessment.firstBlockerSummary());
        if (!StringUtils.hasText(summary)) {
            summary = "当前回答未通过完成性校验";
        }
        return "[未完成续接] " + summary + "。请继续补齐未闭环项后再收尾。";
    }

    private void syncRuntimeState(RuntimeV2State runtimeState, OverAllState graphState, Map<String, Object> codeState) {
        RuntimeV2GraphStateProjector.syncRuntimeState(
                runtimeState, graphState, RuntimeV2Phase.REASONING, codeState, null);
        ledgerEngine.refresh(runtimeState);
    }

    private void prepareToolCallOutput(
            Map<String, Object> output,
            String toolName,
            Map<String, Object> arguments,
            String source,
            String userPreambleMessage) {
        String normalizedToolName = normalizeText(toolName);
        Map<String, Object> normalizedArguments =
                arguments == null || arguments.isEmpty() ? Map.of() : Map.copyOf(arguments);
        String toolCallId = "tool-call-" + UUID.randomUUID();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(normalizeText(userPreambleMessage))
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        toolCallId, "function", normalizedToolName, JSON.toJSONString(normalizedArguments))))
                .build();
        output.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.TRUE);
        output.put(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID, toolCallId);
        output.put(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, normalizedToolName);
        output.put(RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS, normalizedArguments);
        output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of((Message) assistantMessage));
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.ACTION_NODE);
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of(
                        "type",
                        "tool",
                        "source",
                        normalizeText(source),
                        "toolName",
                        normalizedToolName,
                        "userPreambleMessage",
                        normalizeText(userPreambleMessage)));
    }

    private List<Map<String, Object>> appendSyntheticObservation(
            List<Map<String, Object>> observationTrace,
            String toolName,
            Map<String, Object> toolArguments,
            String observation) {
        List<Map<String, Object>> nextTrace =
                new java.util.ArrayList<>(observationTrace == null ? List.of() : observationTrace);
        Map<String, Object> traceItem = new LinkedHashMap<>();
        traceItem.put("toolName", normalizeText(toolName));
        traceItem.put("arguments", JSON.toJSONString(toolArguments == null ? Map.of() : toolArguments));
        traceItem.put("signature", "");
        traceItem.put("duplicateReadOnly", Boolean.FALSE);
        traceItem.put("resultKind", "decision-regression");
        traceItem.put("observation", observation);
        nextTrace.add(Map.copyOf(traceItem));
        return List.copyOf(nextTrace);
    }

    private void syncObservationTrace(RuntimeV2State runtimeState, List<Map<String, Object>> nextTrace) {
        if (runtimeState == null) {
            return;
        }
        runtimeState.replaceObservationTrace(nextTrace);
    }

    private boolean shouldEnterCodeStage(
            OverAllState state,
            String toolName,
            Map<String, Object> arguments,
            List<String> availableToolNames,
            String codeStatus) {
        if (!RuntimeV2RequestHints.readBooleanFlag(
                state.value(RuntimeV2GraphStateKeys.PARAMS_JSON, ""), "allowCodeExecution")) {
            return false;
        }
        if (!availableToolNames.contains("file_write")) {
            return false;
        }
        if ("file_write".equalsIgnoreCase(toolName)) {
            String targetPath = normalizeText(arguments.getOrDefault("path", arguments.get("arg0")));
            if (!targetPath.startsWith("/workspace/") || !targetPath.endsWith(".py")) {
                return false;
            }
            return !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        if ("run_python".equalsIgnoreCase(toolName)) {
            return !RuntimeV2CodeState.CODE_OUTPUT_READY.equalsIgnoreCase(codeStatus)
                    && !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        if ("write_artifact".equalsIgnoreCase(toolName)) {
            return !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        return false;
    }

    private boolean shouldForceCodeArtifactDelivery(
            OverAllState state,
            RuntimeV2CompletionAssessment assessment,
            List<String> availableToolNames,
            String codeStatus) {
        if (assessment == null || !assessment.blocked() || !hasArtifactPublishBlocker(assessment)) {
            return false;
        }
        if (RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus)) {
            return false;
        }
        if (!RuntimeV2RequestHints.readBooleanFlag(
                state == null ? null : state.value(RuntimeV2GraphStateKeys.PARAMS_JSON, ""), "allowCodeExecution")) {
            return false;
        }
        return availableToolNames != null
                && availableToolNames.contains("file_write")
                && availableToolNames.contains("run_python")
                && availableToolNames.contains("write_artifact");
    }

    private boolean hasArtifactPublishBlocker(RuntimeV2CompletionAssessment assessment) {
        if (assessment == null || assessment.blockers() == null) {
            return false;
        }
        return assessment.blockers().stream()
                .anyMatch(blocker -> "ARTIFACT.PUBLISH.REQUIRED".equalsIgnoreCase(normalizeText(blocker.code())));
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean shouldRetryArtifactWrite(Map<String, Object> codeState) {
        return readRetryCount(codeState, "artifactWriteRetryCount") < 1;
    }

    private boolean isTerminalCodeFailure(
            String codeStatus,
            Object lastToolResult,
            Map<String, Object> codeState,
            List<String> availableToolNames) {
        if (RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            if (availableToolNames == null || !availableToolNames.contains("file_write")) {
                return true;
            }
            return !recoveryPolicy.shouldRetryCodeScriptWrite(
                    lastToolResult, readRetryCount(codeState, "writeRepairCount"));
        }
        if (RuntimeV2CodeState.CODE_RUN_FAILED.equalsIgnoreCase(codeStatus)) {
            if (availableToolNames == null || !availableToolNames.contains("file_write")) {
                return true;
            }
            return !recoveryPolicy.shouldRetryCodeRun(lastToolResult, readRetryCount(codeState, "runRepairCount"));
        }
        if (RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            if (availableToolNames == null || !availableToolNames.contains("write_artifact")) {
                return true;
            }
            return !shouldRetryArtifactWrite(codeState);
        }
        return false;
    }

    private String resolveTerminalCodeFailureStatus(String codeStatus) {
        if (RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            return "graph-code-script-write-failed";
        }
        if (RuntimeV2CodeState.CODE_RUN_FAILED.equalsIgnoreCase(codeStatus)) {
            return "graph-code-run-failed";
        }
        if (RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            return "graph-code-artifact-write-failed";
        }
        return "graph-code-failed";
    }

    private String buildTerminalCodeFailureMessage(String codeStatus, Object toolResult) {
        String detail = extractToolFailureDetail(toolResult);
        String raw = normalizeText(toolResult);
        String combined = (detail + "\n" + raw).trim();
        if (RuntimeV2CodeState.CODE_RUN_FAILED.equalsIgnoreCase(codeStatus)) {
            if (combined.contains("RUN_PYTHON_EXECUTION_FAILED") || combined.contains("Python 环境")) {
                return "文件处理失败：Python 运行环境构建失败，暂时无法执行文件提取脚本。请检查本机 Python 运行环境配置后重试。";
            }
            if (StringUtils.hasText(detail)) {
                return "文件处理失败：Python 脚本执行失败，未生成结果文件。错误：" + detail;
            }
            return "文件处理失败：Python 脚本执行失败，未生成结果文件。";
        }
        if (RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            if (StringUtils.hasText(detail)) {
                return "文件处理失败：处理脚本写入失败，未生成结果文件。错误：" + detail;
            }
            return "文件处理失败：处理脚本写入失败，未生成结果文件。";
        }
        if (RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            if (StringUtils.hasText(detail)) {
                return "文件处理失败：结果文件发布失败，暂时无法返回可下载文件。错误：" + detail;
            }
            return "文件处理失败：结果文件发布失败，暂时无法返回可下载文件。";
        }
        return "文件处理失败：运行时处理链路失败，未生成结果文件。";
    }

    private String extractToolFailureDetail(Object toolResult) {
        Map<String, Object> payload = parseToolPayload(toolResult);
        if (payload.isEmpty()) {
            return "";
        }
        String errorMessage = normalizeText(payload.get("errorMessage"));
        if (StringUtils.hasText(errorMessage)) {
            return errorMessage;
        }
        String errorCode = normalizeText(payload.get("errorCode"));
        if (StringUtils.hasText(errorCode)) {
            return errorCode;
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            String output = normalizeText(dataMap.get("output"));
            if (StringUtils.hasText(output)) {
                return output;
            }
        }
        return "";
    }

    private Map<String, Object> parseToolPayload(Object toolResult) {
        if (toolResult instanceof Map<?, ?> rawMap) {
            return asObject(rawMap);
        }
        String text = normalizeText(toolResult);
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Object parsed = JSON.parse(text);
            return asObject(parsed);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> markCodeRetry(Map<String, Object> codeState, String retryKey) {
        Map<String, Object> next = new LinkedHashMap<>(codeState == null ? Map.of() : codeState);
        next.put(retryKey, readRetryCount(codeState, retryKey) + 1);
        return Map.copyOf(next);
    }

    private int readRetryCount(Map<String, Object> codeState, String key) {
        if (codeState == null || codeState.isEmpty()) {
            return 0;
        }
        Object value = codeState.get(key);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
