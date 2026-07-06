package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApprovalService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2ActiveToolRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionGate;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.edge.RuntimeV2ActionDispatcher;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.edge.RuntimeV2ObservationDispatcher;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.edge.RuntimeV2ReasoningDispatcher;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.edge.RuntimeV2TriageDispatcher;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2ActionNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2CodeEscalationNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2FinalAnswerNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2LimitExceededNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2ObservationNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2ReasoningNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.node.RuntimeV2TriageNode;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProcessor;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ReadOnlyToolGuard;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ToolCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class RuntimeV2GraphBuilder {

    private static final List<String> REGISTERED_STATE_KEYS = List.of(
            RuntimeV2GraphStateKeys.ROUTE,
            RuntimeV2GraphStateKeys.SESSION_ID,
            RuntimeV2GraphStateKeys.USER_ID,
            RuntimeV2GraphStateKeys.USER_REQUEST,
            RuntimeV2GraphStateKeys.PARAMS_JSON,
            RuntimeV2GraphStateKeys.FILE_LIST_JSON,
            RuntimeV2GraphStateKeys.EXECUTION_MODE_HINT,
            RuntimeV2GraphStateKeys.MODE,
            RuntimeV2GraphStateKeys.MESSAGES,
            RuntimeV2GraphStateKeys.PHASE,
            RuntimeV2GraphStateKeys.ITERATION_COUNT,
            RuntimeV2GraphStateKeys.MAX_ITERATIONS,
            RuntimeV2GraphStateKeys.LLM_CALL_COUNT,
            RuntimeV2GraphStateKeys.TOOL_CALL_COUNT,
            RuntimeV2GraphStateKeys.FINISH_REASON,
            RuntimeV2GraphStateKeys.FINAL_ANSWER,
            RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT,
            RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED,
            RuntimeV2GraphStateKeys.CONTINUE_REASONING,
            RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL,
            RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION,
            RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES,
            RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY,
            RuntimeV2GraphStateKeys.LAST_DECISION,
            RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID,
            RuntimeV2GraphStateKeys.LAST_TOOL_NAME,
            RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS,
            RuntimeV2GraphStateKeys.LAST_TOOL_RESULT,
            RuntimeV2GraphStateKeys.LAST_OBSERVATION,
            RuntimeV2GraphStateKeys.OBSERVATION_TRACE,
            RuntimeV2GraphStateKeys.OBSERVATION_STATE,
            RuntimeV2GraphStateKeys.TOOL_STATE,
            RuntimeV2GraphStateKeys.WORKING_MEMORY,
            RuntimeV2GraphStateKeys.DOCUMENT_STATE,
            RuntimeV2GraphStateKeys.CODE_STATE,
            RuntimeV2GraphStateKeys.COMPLETION_STATE,
            RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS,
            RuntimeV2GraphStateKeys.GRAPH_READY);

    private final RuntimeV2ObservationSummaryProtocol observationSummaryProtocol;
    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2ReadOnlyToolGuard readOnlyToolGuard;
    private final RuntimeV2ObservationProcessor observationProcessor;
    private final RuntimeV2ObservationProjector observationProjector;
    private final RuntimeV2ToolCallExecutor toolCallExecutor;
    private final RuntimeV2CodeStageService codeStageService;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeV2GraphModelSupport graphModelSupport;
    private final RuntimeV2CompletionGate completionGate;
    private final RuntimeV2LedgerEngine ledgerEngine;
    private final RuntimeV2ActiveToolRegistry activeToolRegistry;
    private final RuntimeV2RecoveryPolicy recoveryPolicy;
    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final RuntimeApprovalService runtimeApprovalService;

    public RuntimeV2GraphBuilder(
            RuntimeV2ObservationSummaryProtocol observationSummaryProtocol,
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2ReadOnlyToolGuard readOnlyToolGuard,
            RuntimeV2ObservationProcessor observationProcessor,
            RuntimeV2ObservationProjector observationProjector,
            RuntimeV2ToolCallExecutor toolCallExecutor,
            RuntimeV2CodeStageService codeStageService,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeV2GraphModelSupport graphModelSupport,
            RuntimeV2CompletionGate completionGate,
            RuntimeV2LedgerEngine ledgerEngine,
            RuntimeV2ActiveToolRegistry activeToolRegistry,
            RuntimeV2RecoveryPolicy recoveryPolicy,
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            RuntimeExecutionProperties runtimeExecutionProperties,
            RuntimeApprovalService runtimeApprovalService) {
        this.observationSummaryProtocol = observationSummaryProtocol;
        this.codeExecutionSupport = codeExecutionSupport;
        this.readOnlyToolGuard = readOnlyToolGuard;
        this.observationProcessor = observationProcessor;
        this.observationProjector = observationProjector;
        this.toolCallExecutor = toolCallExecutor;
        this.codeStageService = codeStageService;
        this.runtimeRegistry = runtimeRegistry;
        this.graphModelSupport = graphModelSupport;
        this.completionGate = completionGate;
        this.ledgerEngine = ledgerEngine;
        this.activeToolRegistry = activeToolRegistry;
        this.recoveryPolicy = recoveryPolicy;
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.runtimeApprovalService = runtimeApprovalService;
    }

    public CompiledGraph buildGraph() throws Exception {
        KeyStrategyFactory keyStrategyFactory = buildKeyStrategyFactory();

        StateGraph graph = new StateGraph("runtime-v2-graph", keyStrategyFactory)
                .addNode(RuntimeV2GraphStateKeys.TRIAGE_NODE, AsyncNodeAction.node_async(new RuntimeV2TriageNode()))
                .addNode(
                        RuntimeV2GraphStateKeys.REASONING_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2ReasoningNode(
                                codeExecutionSupport,
                                runtimeRegistry,
                                graphModelSupport,
                                completionGate,
                                ledgerEngine,
                                activeToolRegistry,
                                recoveryPolicy,
                                eventPersistenceCapability,
                                runtimeExecutionProperties)))
                .addNode(
                        RuntimeV2GraphStateKeys.ACTION_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2ActionNode(
                                codeExecutionSupport,
                                toolCallExecutor,
                                runtimeRegistry,
                                ledgerEngine,
                                eventPersistenceCapability,
                                runtimeApprovalService)))
                .addNode(
                        RuntimeV2GraphStateKeys.OBSERVATION_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2ObservationNode(
                                observationSummaryProtocol,
                                readOnlyToolGuard,
                                observationProcessor,
                                observationProjector,
                                runtimeRegistry)))
                .addNode(
                        RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2CodeEscalationNode(
                                codeExecutionSupport, codeStageService, runtimeRegistry, runtimeExecutionProperties)))
                .addNode(
                        RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2FinalAnswerNode()))
                .addNode(
                        RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE,
                        AsyncNodeAction.node_async(new RuntimeV2LimitExceededNode()))
                .addEdge(StateGraph.START, RuntimeV2GraphStateKeys.TRIAGE_NODE)
                .addConditionalEdges(
                        RuntimeV2GraphStateKeys.TRIAGE_NODE,
                        AsyncEdgeAction.edge_async(new RuntimeV2TriageDispatcher()),
                        java.util.Map.of(
                                RuntimeV2GraphStateKeys.REASONING_NODE, RuntimeV2GraphStateKeys.REASONING_NODE,
                                RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE))
                .addConditionalEdges(
                        RuntimeV2GraphStateKeys.REASONING_NODE,
                        AsyncEdgeAction.edge_async(new RuntimeV2ReasoningDispatcher()),
                        java.util.Map.of(
                                RuntimeV2GraphStateKeys.REASONING_NODE, RuntimeV2GraphStateKeys.REASONING_NODE,
                                RuntimeV2GraphStateKeys.ACTION_NODE, RuntimeV2GraphStateKeys.ACTION_NODE,
                                RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE,
                                        RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE,
                                RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE,
                                RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE,
                                        RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE))
                .addConditionalEdges(
                        RuntimeV2GraphStateKeys.ACTION_NODE,
                        AsyncEdgeAction.edge_async(new RuntimeV2ActionDispatcher()),
                        java.util.Map.of(
                                RuntimeV2GraphStateKeys.OBSERVATION_NODE, RuntimeV2GraphStateKeys.OBSERVATION_NODE,
                                RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE))
                .addConditionalEdges(
                        RuntimeV2GraphStateKeys.OBSERVATION_NODE,
                        AsyncEdgeAction.edge_async(new RuntimeV2ObservationDispatcher()),
                        java.util.Map.of(
                                RuntimeV2GraphStateKeys.REASONING_NODE, RuntimeV2GraphStateKeys.REASONING_NODE,
                                RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE,
                                RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE,
                                        RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE))
                .addEdge(RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE, RuntimeV2GraphStateKeys.ACTION_NODE)
                .addEdge(RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE)
                .addEdge(RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE, StateGraph.END);

        return graph.compile(CompileConfig.builder().recursionLimit(100).build());
    }

    @Deprecated
    public CompiledGraph buildSkeletonGraph() throws Exception {
        return buildGraph();
    }

    public List<String> nodeNames() {
        return List.of(
                RuntimeV2GraphStateKeys.TRIAGE_NODE,
                RuntimeV2GraphStateKeys.REASONING_NODE,
                RuntimeV2GraphStateKeys.ACTION_NODE,
                RuntimeV2GraphStateKeys.OBSERVATION_NODE,
                RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE,
                RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE,
                RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE);
    }

    static List<String> registeredStateKeys() {
        return new ArrayList<>(REGISTERED_STATE_KEYS);
    }

    private KeyStrategyFactory buildKeyStrategyFactory() {
        KeyStrategyFactoryBuilder builder = new KeyStrategyFactoryBuilder();
        for (String key : REGISTERED_STATE_KEYS) {
            builder.addStrategy(
                    key, RuntimeV2GraphStateKeys.MESSAGES.equals(key) ? KeyStrategy.APPEND : KeyStrategy.REPLACE);
        }
        return builder.build();
    }
}
