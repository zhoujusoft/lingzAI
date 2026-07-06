package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RequestHints;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphStateProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProcessor;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ReadOnlyToolGuard;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

public class RuntimeV2ObservationNode implements NodeAction {

    private final RuntimeV2ObservationSummaryProtocol observationSummaryProtocol;
    private final RuntimeV2ReadOnlyToolGuard readOnlyToolGuard;
    private final RuntimeV2ObservationProcessor observationProcessor;
    private final RuntimeV2ObservationProjector observationProjector;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;

    public RuntimeV2ObservationNode(
            RuntimeV2ObservationSummaryProtocol observationSummaryProtocol,
            RuntimeV2ReadOnlyToolGuard readOnlyToolGuard,
            RuntimeV2ObservationProcessor observationProcessor,
            RuntimeV2ObservationProjector observationProjector,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry) {
        this.observationSummaryProtocol = observationSummaryProtocol;
        this.readOnlyToolGuard = readOnlyToolGuard;
        this.observationProcessor = observationProcessor;
        this.observationProjector = observationProjector;
        this.runtimeRegistry = runtimeRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        int iterationCount = state.value(RuntimeV2GraphStateKeys.ITERATION_COUNT, 0);
        String toolName = state.value(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, "");
        Object toolArguments = state.value(RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS, Map.of());
        Object toolResult = state.value(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, Map.of());
        String paramsJson = state.value(RuntimeV2GraphStateKeys.PARAMS_JSON, "");
        List<Message> messages =
                state.<List<Message>>value(RuntimeV2GraphStateKeys.MESSAGES).orElse(List.of());
        List<Map<String, Object>> observationTrace = state.<List<Map<String, Object>>>value(
                        RuntimeV2GraphStateKeys.OBSERVATION_TRACE)
                .orElse(List.of());
        Map<String, Object> toolArgumentsMap = asObject(toolArguments);
        boolean duplicateReadOnly =
                readOnlyToolGuard.isDuplicateReadOnlyTool(observationTrace, toolName, toolArgumentsMap);
        String toolSignature = readOnlyToolGuard.buildToolCallSignature(toolName, toolArgumentsMap);
        Map<String, Object> projection = observationProjector.project(toolName, toolArgumentsMap, toolResult);
        Map<String, Object> toolState = new LinkedHashMap<>(asObject(projection.get("toolState")));
        if (StringUtils.hasText(toolSignature)) {
            toolState.put("signature", toolSignature);
        }
        toolState.put("duplicateReadOnly", duplicateReadOnly);

        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.OBSERVATION.name());
        output.put(RuntimeV2GraphStateKeys.ITERATION_COUNT, iterationCount + 1);
        output.put(RuntimeV2GraphStateKeys.TOOL_STATE, Map.copyOf(toolState));
        output.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.FALSE);
        output.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);

        String observationSummary = duplicateReadOnly
                ? readOnlyToolGuard.buildDuplicateObservation(observationTrace, toolName, toolArgumentsMap, 4000)
                : observationSummaryProtocol.summarize(
                        toolName,
                        stringify(toolResult),
                        new ObservationSummaryOptions(
                                RuntimeV2RequestHints.readBooleanFlag(paramsJson, "allowCodeExecution"),
                                4000,
                                state.value(RuntimeV2GraphStateKeys.USER_REQUEST, "")));
        if (StringUtils.hasText(observationSummary)) {
            List<Map<String, Object>> nextTrace = new ArrayList<>(observationTrace);
            Map<String, Object> traceItem = new LinkedHashMap<>();
            traceItem.put("toolName", toolName);
            traceItem.put("arguments", stringify(toolArgumentsMap));
            traceItem.put("signature", toolSignature);
            traceItem.put("duplicateReadOnly", duplicateReadOnly);
            traceItem.put("resultKind", normalizeText(toolState.get("resultKind")));
            traceItem.put("observation", observationSummary);
            nextTrace.add(Map.copyOf(traceItem));
            output.put(RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.copyOf(nextTrace));
            output.put(RuntimeV2GraphStateKeys.LAST_OBSERVATION, observationSummary);
            Map<String, Object> documentState = new LinkedHashMap<>(asObject(projection.get("documentState")));
            documentState.put("lastToolName", toolName);
            documentState.put("lastObservation", observationSummary);
            documentState.put("observationCount", nextTrace.size());
            Map<String, Object> observationState = observationProcessor.evaluate(nextTrace, messages.size());
            documentState.put("summaryPressure", observationState.getOrDefault("shouldSummarize", Boolean.FALSE));
            documentState.put("summaryReason", normalizeText(observationState.get("summaryReason")));
            documentState.put("messageCount", observationState.getOrDefault("messageCount", messages.size()));
            output.put(RuntimeV2GraphStateKeys.OBSERVATION_STATE, observationState);
            output.put(RuntimeV2GraphStateKeys.DOCUMENT_STATE, Map.copyOf(documentState));
            syncRuntimeState(state, nextTrace, output);
            if (Boolean.TRUE.equals(observationState.get("loopTerminated"))) {
                output.put(
                        RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS,
                        RuntimeV2UsageGuardSupport.OBSERVATION_LOOP_DETECTED_STATUS);
                output.put(
                        RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT,
                        RuntimeV2UsageGuardSupport.OBSERVATION_LOOP_DETECTED_MESSAGE);
                output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.LIMIT_EXCEEDED.name());
                output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE);
                return output;
            }
        }

        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.REASONING_NODE);
        return output;
    }

    private Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return JSON.toJSONString(value);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void syncRuntimeState(
            OverAllState graphState, List<Map<String, Object>> nextTrace, Map<String, Object> output) {
        String runtimeContextKey = graphState.value(RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "");
        RuntimeV2GraphExecutionContext context = runtimeRegistry.resolveExecutionContext(runtimeContextKey);
        if (context == null || context.runtimeState() == null) {
            return;
        }
        Map<String, Object> codeState = output.containsKey(RuntimeV2GraphStateKeys.CODE_STATE)
                ? asObject(output.get(RuntimeV2GraphStateKeys.CODE_STATE))
                : graphState
                        .<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                        .orElse(Map.of());
        RuntimeV2GraphStateProjector.syncRuntimeState(
                context.runtimeState(), graphState, RuntimeV2Phase.OBSERVATION, codeState, nextTrace);
    }
}
