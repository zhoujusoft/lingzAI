package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApproval;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApprovalService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeState;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphEvent;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ToolCallExecutor;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

public class RuntimeV2ActionNode implements NodeAction {

    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2ToolCallExecutor toolCallExecutor;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeV2LedgerEngine ledgerEngine;
    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final RuntimeApprovalService runtimeApprovalService;

    public RuntimeV2ActionNode(
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2ToolCallExecutor toolCallExecutor,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeV2LedgerEngine ledgerEngine,
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            RuntimeApprovalService runtimeApprovalService) {
        this.codeExecutionSupport = codeExecutionSupport;
        this.toolCallExecutor = toolCallExecutor;
        this.runtimeRegistry = runtimeRegistry;
        this.ledgerEngine = ledgerEngine;
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.runtimeApprovalService = runtimeApprovalService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        int toolCallCount = state.value(RuntimeV2GraphStateKeys.TOOL_CALL_COUNT, 0);
        String toolName = state.value(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, "");
        Object existingToolResult =
                state.value(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT).orElse(null);
        Map<String, Object> toolState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.TOOL_STATE)
                .orElse(Map.of());
        Map<String, Object> codeState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                .orElse(Map.of());
        String runtimeContextKey = state.value(RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "");
        RuntimeV2GraphExecutionContext executionContext = runtimeRegistry.resolveExecutionContext(runtimeContextKey);
        Map<String, ToolCallback> toolCallbackIndex = runtimeRegistry.resolveToolCallbackIndex(runtimeContextKey);
        String previousObservedToolName = toolState.get("toolName") == null
                ? ""
                : String.valueOf(toolState.get("toolName")).trim();

        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.ACTION.name());
        output.put(RuntimeV2GraphStateKeys.TOOL_CALL_COUNT, toolCallCount + 1);
        ToolCallback callback = toolCallbackIndex.get(toolName);
        if (callback != null) {
            Map<String, Object> arguments = state.<Map<String, Object>>value(
                            RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS)
                    .orElse(Map.of());
            String toolId = state.value(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID, "");
            if (!StringUtils.hasText(toolId)) {
                toolId = UUID.randomUUID().toString();
            }
            String toolArgumentsJson = JSON.toJSONString(arguments == null ? Map.of() : arguments);
            RuntimeV2State runtimeState = executionContext == null ? null : executionContext.runtimeState();
            Map<String, Object> toolPayload = new LinkedHashMap<>();
            toolPayload.put("id", toolId);
            toolPayload.put("name", toolName);
            toolPayload.put("displayName", toolName);
            toolPayload.put("arguments", toolArgumentsJson);
            if (requiresApproval(toolName)) {
                RuntimeApproval approval = runtimeApprovalService.createPendingApproval(
                        runtimeState, toolId, toolName, toolName, arguments);
                runtimeApprovalService.markRunWaitingApproval(runtimeState, approval);
                Map<String, Object> approvalPayload = runtimeApprovalService.buildApprovalPayload(approval);
                emitRealtimeEvent(executionContext, RuntimeV2GraphEvent.approvalRequired(approvalPayload));
                output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.WAITING_APPROVAL.name());
                output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "需要人工审批后继续执行。");
                output.put(
                        RuntimeV2GraphStateKeys.LAST_DECISION,
                        Map.of(
                                "type", "approval",
                                "source", "runtime-approval",
                                "status", "waiting",
                                "approvalCode", approval.getApprovalCode(),
                                "toolName", normalizeToolName(toolName)));
                output.put(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, approvalPayload);
                output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
                return output;
            }
            emitRealtimeEvent(executionContext, RuntimeV2GraphEvent.toolCallStarted(toolPayload));
            String toolResult;
            try {
                toolResult = toolCallExecutor.execute(toolName, callback, arguments);
            } catch (Exception ex) {
                toolResult = "工具执行失败：" + ex.getMessage();
                Map<String, Object> resultPayload = new LinkedHashMap<>();
                resultPayload.put("id", toolId);
                resultPayload.put("name", toolName);
                resultPayload.put("arguments", toolArgumentsJson);
                resultPayload.put("response", toolResult);
                emitRealtimeEvent(executionContext, RuntimeV2GraphEvent.toolCallCompleted(resultPayload));
                if (runtimeState != null) {
                    runtimeState.incrementToolCallCount();
                    runtimeState.setCodeState(advanceCodeState(codeState, toolName, toolResult));
                    recordToolPersistence(runtimeState, toolId, toolName, toolArgumentsJson, toolResult);
                    ledgerEngine.recordToolFailure(runtimeState, toolName, toolResult);
                }
                throw ex;
            }
            output.put(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, toolResult);
            output.put(RuntimeV2GraphStateKeys.CODE_STATE, advanceCodeState(codeState, toolName, toolResult));
            output.put(RuntimeV2GraphStateKeys.MESSAGES, List.of((Message) ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(toolId, toolName, toolResult)))
                    .build()));
            if (runtimeState != null) {
                runtimeState.incrementToolCallCount();
                runtimeState.setCodeState(
                        output.containsKey(RuntimeV2GraphStateKeys.CODE_STATE)
                                ? castMap(output.get(RuntimeV2GraphStateKeys.CODE_STATE))
                                : codeState);
                recordToolPersistence(runtimeState, toolId, toolName, toolArgumentsJson, toolResult);
                if (isSuccessfulToolResult(toolResult)) {
                    ledgerEngine.recordToolSuccess(runtimeState, toolName, toolResult);
                } else {
                    ledgerEngine.recordToolFailure(runtimeState, toolName, toolResult);
                }
            }
            emitRealtimeEvent(
                    executionContext,
                    RuntimeV2GraphEvent.toolCallCompleted(Map.of(
                            "id", toolId,
                            "name", toolName,
                            "arguments", toolArgumentsJson,
                            "response", toolResult == null ? "" : toolResult)));
            output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.OBSERVATION_NODE);
            return output;
        }
        return failClosedMissingToolCallback(output, toolName, existingToolResult, previousObservedToolName);
    }

    private void recordToolPersistence(
            RuntimeV2State runtimeState, String toolId, String toolName, String toolArgumentsJson, String toolResult) {
        if (runtimeState == null) {
            return;
        }
        Map<String, Object> toolPayload = new LinkedHashMap<>();
        toolPayload.put("id", toolId);
        toolPayload.put("name", toolName);
        toolPayload.put("displayName", toolName);
        toolPayload.put("arguments", toolArgumentsJson);
        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("id", toolId);
        resultPayload.put("name", toolName);
        resultPayload.put("arguments", toolArgumentsJson);
        resultPayload.put("response", toolResult == null ? "" : toolResult);
        runtimeState.promptToolEvents().add(Map.of("type", "tool", "content", Map.copyOf(toolPayload)));
        runtimeState.promptToolEvents().add(Map.of("type", "result", "content", Map.copyOf(resultPayload)));
        runtimeState.toolEvents().add(Map.of("type", "tool", "content", Map.copyOf(toolPayload)));
        runtimeState.toolEvents().add(Map.of("type", "result", "content", Map.copyOf(resultPayload)));
        if (eventPersistenceCapability != null) {
            eventPersistenceCapability.recordTimelineToolEvent(
                    runtimeState.timelineSegments(),
                    "tool",
                    JSON.toJSONString(Map.of("type", "tool", "content", toolPayload)));
            eventPersistenceCapability.recordTimelineToolEvent(
                    runtimeState.timelineSegments(),
                    "result",
                    JSON.toJSONString(Map.of("type", "result", "content", resultPayload)));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> advanceCodeState(Map<String, Object> codeState, String toolName, String toolResult) {
        if (codeState == null
                || codeState.isEmpty()
                || !codeExecutionSupport.isToolExecutionSuccess(toolResult) && !isKnownCodeTool(toolName)) {
            return codeState;
        }
        Map<String, Object> next = new LinkedHashMap<>(codeState);
        String status =
                switch (toolName == null ? "" : toolName.trim()) {
                    case "file_write" -> codeExecutionSupport.isToolExecutionSuccess(toolResult)
                            ? RuntimeV2CodeState.CODE_SCRIPT_READY
                            : RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED;
                    case "run_python" -> codeExecutionSupport.isToolExecutionSuccess(toolResult)
                            ? RuntimeV2CodeState.CODE_OUTPUT_READY
                            : RuntimeV2CodeState.CODE_RUN_FAILED;
                    case "write_artifact" -> codeExecutionSupport.isToolExecutionSuccess(toolResult)
                            ? RuntimeV2CodeState.CODE_ARTIFACT_READY
                            : RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED;
                    default -> "";
                };
        if (!status.isEmpty()) {
            next.put("status", status);
        }
        return Map.copyOf(next);
    }

    private boolean isKnownCodeTool(String toolName) {
        return "file_write".equalsIgnoreCase(toolName)
                || "run_python".equalsIgnoreCase(toolName)
                || "write_artifact".equalsIgnoreCase(toolName);
    }

    private boolean requiresApproval(String toolName) {
        return runtimeApprovalService != null && runtimeApprovalService.requiresApproval(toolName);
    }

    private boolean isSuccessfulToolResult(String toolResult) {
        if (!StringUtils.hasText(toolResult)) {
            return false;
        }
        if (codeExecutionSupport.isToolExecutionSuccess(toolResult)) {
            return true;
        }
        try {
            Object parsed = JSON.parse(toolResult);
            if (parsed instanceof Map<?, ?> payload && payload.containsKey("success")) {
                Object success = payload.get("success");
                return success != null
                        && "true".equalsIgnoreCase(String.valueOf(success).trim());
            }
        } catch (Exception ignored) {
            // 非 JSON 结果默认视作成功，由 observation 再决定下一步。
        }
        return true;
    }

    private Map<String, Object> failClosedMissingToolCallback(
            Map<String, Object> output, String toolName, Object existingToolResult, String previousObservedToolName) {
        output.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, "graph-tool-callback-missing");
        output.put(
                RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT,
                "当前 graph 主链缺少工具 `" + normalizeToolName(toolName) + "` 的真实执行回调，已停止本次运行。");
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.GRAPH_RUNTIME_UNAVAILABLE.name());
        output.put(
                RuntimeV2GraphStateKeys.LAST_DECISION,
                Map.of(
                        "type", "final",
                        "source", "graph-runtime-guard",
                        "status", "graph-tool-callback-missing",
                        "toolName", normalizeToolName(toolName)));
        if (existingToolResult != null && toolName.equalsIgnoreCase(previousObservedToolName)) {
            output.put(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, existingToolResult);
        } else {
            output.put(
                    RuntimeV2GraphStateKeys.LAST_TOOL_RESULT,
                    Map.of("status", "TOOL_CALLBACK_MISSING", "toolName", normalizeToolName(toolName)));
        }
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        return output;
    }

    private String normalizeToolName(String toolName) {
        return toolName == null ? "" : toolName.trim();
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void emitRealtimeEvent(RuntimeV2GraphExecutionContext executionContext, RuntimeV2GraphEvent event) {
        if (executionContext == null || executionContext.eventEmitter() == null || event == null) {
            return;
        }
        executionContext.eventEmitter().accept(event);
    }
}
