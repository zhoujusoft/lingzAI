package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2ActiveToolRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionGate;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractBuilder;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractResolver;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContractEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphSeed;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProcessor;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ReadOnlyToolGuard;
import lingzhou.agent.backend.capability.agentruntime.v2.prompt.RuntimeV2PromptAssembler;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ToolCallExecutor;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class RuntimeV2GraphBuilderTest {

    @Test
    void shouldSimulateCodePathToArtifactReady() throws Exception {
        GraphFixture fixture = buildGraphFixture();
        fixture.runtimeRegistry()
                .register(
                        "runtime-context-test",
                        Map.of(
                                "parse_file",
                                callback(
                                        "parse_file",
                                        """
                        {
                          "success": true,
                          "status": "SUCCESS",
                          "fileName": "测试销售数据_豆包AI生成.xlsx",
                          "fileType": "xlsx",
                          "contentView": {
                            "text": "",
                            "markdown": "",
                            "sections": [
                              {
                                "type": "sheet",
                                "name": "销售明细",
                                "rowCount": 1000,
                                "columnCount": 13
                              }
                            ],
                            "entities": {
                              "sheetNames": ["销售明细", "销售汇总"]
                            }
                          }
                        }
                        """)),
                        null);
        RuntimeV2GraphSeed seed = new RuntimeV2GraphSeed(
                "session-id",
                1L,
                "需要先解析用户上传的销售数据Excel文件，获取结构化数据以便后续按月份统计各产品线的销售额和利润。",
                "{\"executionModeHint\":\"TOOL\",\"codeEscalationCandidate\":true,\"allowCodeExecution\":true}",
                """
                [{"name":"测试销售数据_豆包AI生成.xlsx","path":"chat-upload://chat-files/1/a.xlsx"}]
                """,
                "TOOL",
                RuntimeV2Mode.REACT,
                6,
                List.of(),
                List.of("parse_file", "file_write", "run_python", "write_artifact"),
                "runtime-context-test");

        Optional<OverAllState> result = fixture.graph().invoke(seed.toStateMap());

        assertThat(result).isPresent();
        OverAllState state = result.orElseThrow();
        assertThat(state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "")).contains("当前 graph 主链未接入真实模型推理上下文");
        assertThat(state.value(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, ""))
                .isEqualTo("graph-model-context-missing");
        Map<String, Object> codeState = state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                .orElse(Map.of());
        assertThat(codeState).isEmpty();
        assertThat(state.<List<Map<String, Object>>>value(RuntimeV2GraphStateKeys.OBSERVATION_TRACE)
                        .orElse(List.of()))
                .isEmpty();
    }

    @Test
    void shouldClearTransientStateWhenReusingSameGraphThread() throws Exception {
        GraphFixture fixture = buildGraphFixture();
        RunnableConfig config =
                RunnableConfig.builder().threadId("session-reuse-test").build();
        RuntimeV2GraphSeed initialSeed = new RuntimeV2GraphSeed(
                "session-reuse-test",
                1L,
                "第一次请求",
                "{}",
                "[]",
                "TOOL",
                RuntimeV2Mode.REACT,
                6,
                List.of(),
                List.of(),
                "runtime-context-initial");
        fixture.graph().invoke(initialSeed.toStateMap(), config);
        fixture.graph()
                .updateState(
                        config,
                        Map.ofEntries(
                                Map.entry(RuntimeV2GraphStateKeys.FINAL_ANSWER, "上一轮回答"),
                                Map.entry(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "上一轮草稿"),
                                Map.entry(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.TRUE),
                                Map.entry(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.TRUE),
                                Map.entry(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, "run_python"),
                                Map.entry(RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS, Map.of("arg0", "old")),
                                Map.entry(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, Map.of("status", "old")),
                                Map.entry(RuntimeV2GraphStateKeys.LAST_OBSERVATION, "上一轮观察"),
                                Map.entry(RuntimeV2GraphStateKeys.TOOL_STATE, Map.of("toolName", "run_python")),
                                Map.entry(RuntimeV2GraphStateKeys.DOCUMENT_STATE, Map.of("summaryReason", "old")),
                                Map.entry(RuntimeV2GraphStateKeys.CODE_STATE, Map.of("status", "CODE_OUTPUT_READY")),
                                Map.entry(
                                        RuntimeV2GraphStateKeys.COMPLETION_STATE, Map.of("completionConfirmed", true)),
                                Map.entry(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, "stale-status")));
        RuntimeV2GraphSeed seed = new RuntimeV2GraphSeed(
                "session-reuse-test",
                1L,
                "新的用户问题",
                "{}",
                "[]",
                "TOOL",
                RuntimeV2Mode.REACT,
                6,
                List.of(),
                List.of("parse_file"),
                "runtime-context-next");

        Map<String, Object> initialState = fixture.graph().getInitialState(seed.toStateMap(), config);

        assertThat(initialState.get(RuntimeV2GraphStateKeys.FINAL_ANSWER)).isEqualTo("");
        assertThat(initialState.get(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT)).isEqualTo("");
        assertThat(initialState.get(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL)).isEqualTo(Boolean.FALSE);
        assertThat(initialState.get(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION))
                .isEqualTo(Boolean.FALSE);
        assertThat(initialState.get(RuntimeV2GraphStateKeys.LAST_TOOL_NAME)).isEqualTo("");
        assertThat(initialState.get(RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS))
                .isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT)).isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.LAST_OBSERVATION)).isEqualTo("");
        assertThat(initialState.get(RuntimeV2GraphStateKeys.TOOL_STATE)).isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.DOCUMENT_STATE)).isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.CODE_STATE)).isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.COMPLETION_STATE)).isEqualTo(Map.of());
        assertThat(initialState.get(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS))
                .isEqualTo("");
    }

    private GraphFixture buildGraphFixture() throws Exception {
        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        RequestScopedSkillRuntimeService requestScopedSkillRuntimeService = new RequestScopedSkillRuntimeService(null);
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                requestScopedSkillRuntimeService,
                new RuntimeV2TaskContractEngine(new RuntimeV2SkillContractResolver(
                        requestScopedSkillRuntimeService,
                        null,
                        new RuntimeV2ContractSupport(new ObjectMapper()),
                        new RuntimeV2SkillContractBuilder())));
        RuntimeV2ActiveToolRegistry activeToolRegistry =
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {
                    @Override
                    public List<org.springframework.ai.tool.ToolCallback> bindToolCallbacks(
                            List<org.springframework.ai.tool.ToolCallback> callbacks,
                            lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest prepared,
                            lingzhou.agent.backend.business.chat.service.ConversationHistoryService.ConversationContext
                                    context,
                            lingzhou.agent.spring.ai.skill.core.SkillKit requestSkillKit) {
                        return callbacks == null ? List.of() : List.copyOf(callbacks);
                    }
                });
        RuntimeV2GraphBuilder graphBuilder = new RuntimeV2GraphBuilder(
                new RuntimeV2ObservationSummaryProtocol(),
                new RuntimeV2CodeExecutionSupport(),
                new RuntimeV2ReadOnlyToolGuard(),
                new RuntimeV2ObservationProcessor(),
                new RuntimeV2ObservationProjector(),
                new RuntimeV2ToolCallExecutor(),
                new RuntimeV2CodeStageService(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    lingzhou.agent.backend.business.chat.service.ConversationHistoryService
                                                    .ConversationContext
                                            context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodePlanProtocol(
                                new RuntimeV2ReactDecisionProtocol()),
                        new RuntimeV2CodeExecutionSupport(),
                        new RuntimeV2ReactDecisionProtocol()),
                runtimeRegistry,
                new RuntimeV2GraphModelSupport(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    lingzhou.agent.backend.business.chat.service.ConversationHistoryService
                                                    .ConversationContext
                                            context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new RuntimeV2ReactDecisionProtocol()),
                new RuntimeV2CompletionGate(),
                ledgerEngine,
                activeToolRegistry,
                new RuntimeV2RecoveryPolicy(),
                new EventPersistenceCapabilityAdapter(null, null, null, null, null, null, null, null),
                new RuntimeExecutionProperties(),
                null);
        return new GraphFixture(graphBuilder.buildGraph(), runtimeRegistry);
    }

    private record GraphFixture(CompiledGraph graph, RuntimeV2GraphRuntimeRegistry runtimeRegistry) {}

    private ToolCallback callback(String name, String result) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(name + " description")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return result;
            }
        };
    }
}
