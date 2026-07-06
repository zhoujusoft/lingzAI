package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport.UsageGuardResult;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodePlanProtocol.CodeExecutionPlan;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageRuntime;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService.CodeStagePreparation;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeState;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;

public class RuntimeV2CodeEscalationNode implements NodeAction {

    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2CodeStageService codeStageService;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeExecutionProperties runtimeExecutionProperties;

    public RuntimeV2CodeEscalationNode(
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2CodeStageService codeStageService,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeExecutionProperties runtimeExecutionProperties) {
        this.codeExecutionSupport = codeExecutionSupport;
        this.codeStageService = codeStageService;
        this.runtimeRegistry = runtimeRegistry;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        String fileListJson = state.value(RuntimeV2GraphStateKeys.FILE_LIST_JSON, "");
        Map<String, Object> existingCodeState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                .orElse(Map.of());
        String attachmentSummary = codeExecutionSupport.buildAttachmentSummary(fileListJson);
        String runtimeContextKey = state.value(RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "");
        RuntimeV2CodeStageRuntime codeStageRuntime = runtimeRegistry.resolveCodeStageRuntime(runtimeContextKey);
        if (codeStageRuntime == null) {
            return failClosedUnavailable(
                    output,
                    state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name()),
                    "graph-code-stage-runtime-missing",
                    "当前 graph 主链未接入真实 CODE 执行上下文，已停止本次运行。");
        }
        CodeStagePreparation preparation =
                codeStageService.prepare(codeStageRuntime.state(), codeStageRuntime.chatClient());
        if (!preparation.valid()) {
            return failClosedUnavailable(
                    output,
                    state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name()),
                    "graph-code-stage-prepare-failed",
                    "当前 graph 主链无法建立真实 CODE 执行计划，已停止本次运行。");
        }
        UsageGuardResult usageGuardResult = RuntimeV2UsageGuardSupport.resolveTokenBudgetExceeded(
                codeStageRuntime.state(), runtimeExecutionProperties);
        if (usageGuardResult != null) {
            return failClosedLimitExceeded(
                    output, state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name()), usageGuardResult);
        }
        CodeExecutionPlan plan = preparation.plan();
        Map<String, Object> codeState = mergeRetryState(
                existingCodeState,
                codeExecutionSupport.buildCodeState(
                        plan, preparation.scriptContent(), attachmentSummary, RuntimeV2CodeState.CODE_PLAN_PREPARED));

        output.put(RuntimeV2GraphStateKeys.MODE, state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name()));
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.ACTION.name());
        output.put(RuntimeV2GraphStateKeys.CODE_STATE, codeState);
        output.put(RuntimeV2GraphStateKeys.TOOL_STATE, Map.of("toolName", "file_write", "resultKind", "code-plan"));
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, "file_write");
        output.put(
                RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS,
                codeExecutionSupport.buildFileWriteArguments(plan, preparation.scriptContent()));
        output.put(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, null);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.ACTION_NODE);
        return output;
    }

    private Map<String, Object> failClosedUnavailable(
            Map<String, Object> output, String mode, String runtimeStatus, String finalMessage) {
        output.put(RuntimeV2GraphStateKeys.MODE, mode);
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.REASONING.name());
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, runtimeStatus);
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, finalMessage);
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.GRAPH_RUNTIME_UNAVAILABLE.name());
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        return output;
    }

    private Map<String, Object> failClosedLimitExceeded(
            Map<String, Object> output, String mode, UsageGuardResult usageGuardResult) {
        output.put(RuntimeV2GraphStateKeys.MODE, mode);
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.REASONING.name());
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, usageGuardResult.status());
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, usageGuardResult.message());
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.LIMIT_EXCEEDED.name());
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE);
        return output;
    }

    private Map<String, Object> mergeRetryState(
            Map<String, Object> existingCodeState, Map<String, Object> nextCodeState) {
        if ((existingCodeState == null || existingCodeState.isEmpty())
                && (nextCodeState == null || nextCodeState.isEmpty())) {
            return Map.of();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (nextCodeState != null && !nextCodeState.isEmpty()) {
            merged.putAll(nextCodeState);
        }
        copyRetryKey(existingCodeState, merged, "writeRepairCount");
        copyRetryKey(existingCodeState, merged, "runRepairCount");
        copyRetryKey(existingCodeState, merged, "artifactWriteRetryCount");
        return Map.copyOf(merged);
    }

    private void copyRetryKey(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source == null || source.isEmpty() || target == null || key == null || key.isBlank()) {
            return;
        }
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }
}
