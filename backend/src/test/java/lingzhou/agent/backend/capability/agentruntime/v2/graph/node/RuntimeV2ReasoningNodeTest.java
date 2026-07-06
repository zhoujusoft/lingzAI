package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2ActiveToolRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeState;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionGate;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractCapability;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2EvidenceMatchMode;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ExecutionRequirement;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractResolver;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContractEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskIntent;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphModelSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.prompt.RuntimeV2PromptAssembler;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

class RuntimeV2ReasoningNodeTest {

    @Test
    void shouldGenerateFinalAnswerBeforeResolvingAnotherToolDecisionWhenObligationsAreSatisfied() {
        RuntimeLoadedSkill loadedSkill = new RuntimeLoadedSkill(23L, "expense-assistant", "报销助手", "报销技能");
        RuntimeV2State runtimeState = newState(
                "先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多",
                List.of(loadedSkill),
                List.of(callback("dataset.DS20260420103211J78Q.execute_dataset_sql")));
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of(loadedSkill)),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of(datasetContract()))));
        ledgerEngine.recordToolSuccess(
                runtimeState, "dataset.DS20260420103211J78Q.search_dataset_summary", "{\"success\":true}");
        ledgerEngine.recordToolSuccess(
                runtimeState, "dataset.DS20260420103211J78Q.get_dataset_schema", "{\"success\":true}");
        ledgerEngine.recordToolSuccess(
                runtimeState,
                "dataset.DS20260420103211J78Q.execute_dataset_sql",
                "{\"success\":true,\"rows\":[{\"employee_name\":\"张三\",\"total_reimbursed\":6690.0}]}");

        AtomicReference<Prompt> finalPrompt = new AtomicReference<>();
        AtomicReference<Prompt> decisionPrompt = new AtomicReference<>();
        ChatClient streamChatClient = ChatClient.create(
                new StubStreamingChatModel(finalPrompt, List.of(response(message("市场部报销金额最多的是张三，累计报销 6690 元。")))));
        ChatClient decisionChatClient =
                ChatClient.create(new StubStreamingChatModel(decisionPrompt, List.of(response(message("我先继续调用工具")))));

        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        runtimeRegistry.register(
                "runtime-context-test",
                runtimeState,
                streamChatClient,
                decisionChatClient,
                Map.of(
                        "dataset.DS20260420103211J78Q.execute_dataset_sql",
                        callback("dataset.DS20260420103211J78Q.execute_dataset_sql")),
                null,
                null);
        RuntimeV2ReasoningNode node = new RuntimeV2ReasoningNode(
                null,
                runtimeRegistry,
                new RuntimeV2GraphModelSupport(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    ConversationHistoryService.ConversationContext context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new RuntimeV2ReactDecisionProtocol()),
                new RuntimeV2CompletionGate(),
                ledgerEngine,
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {}),
                new RuntimeV2RecoveryPolicy(),
                null,
                new RuntimeExecutionProperties());

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name(),
                RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0,
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "runtime-context-test",
                RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES,
                        List.of("dataset.DS20260420103211J78Q.execute_dataset_sql"),
                RuntimeV2GraphStateKeys.CODE_STATE, Map.of(),
                RuntimeV2GraphStateKeys.TOOL_STATE, Map.of(),
                RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.of(),
                RuntimeV2GraphStateKeys.PARAMS_JSON, "{}",
                RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "")));

        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        assertThat(output.get(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT)).isEqualTo("市场部报销金额最多的是张三，累计报销 6690 元。");
        assertThat(output.get(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL)).isEqualTo(Boolean.FALSE);
        assertThat(output.get(RuntimeV2GraphStateKeys.LLM_CALL_COUNT)).isEqualTo(1);
        assertThat(decisionPrompt.get()).as("不应再进入 tool decision 模型调用").isNull();
        assertThat(finalPrompt.get()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> completionState =
                (Map<String, Object>) output.get(RuntimeV2GraphStateKeys.COMPLETION_STATE);
        assertThat(completionState).containsEntry("completionConfirmed", true);
        assertThat(completionState).containsEntry("openObligationCount", 0);
    }

    @Test
    void shouldNotGenerateFinalAnswerEarlyWhenNoExecutionEvidenceExists() {
        RuntimeV2State runtimeState =
                newState("先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多", List.of(), List.of(callback("parse_file")));
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of()),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of())));
        ledgerEngine.refresh(runtimeState);

        AtomicReference<Prompt> finalPrompt = new AtomicReference<>();
        AtomicReference<Prompt> decisionPrompt = new AtomicReference<>();
        ChatClient streamChatClient =
                ChatClient.create(new StubStreamingChatModel(finalPrompt, List.of(response(message("这不应该被调用")))));
        ChatClient decisionChatClient = ChatClient.create(new StubStreamingChatModel(
                decisionPrompt,
                List.of(response(message(
                        "我先处理文档翻译",
                        new AssistantMessage.ToolCall(
                                "call_parse_file_1",
                                "function",
                                "parse_file",
                                "{\"arg0\":\"fanyi.docx\",\"arg1\":\"text\"}"))))));

        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        runtimeRegistry.register(
                "runtime-context-empty",
                runtimeState,
                streamChatClient,
                decisionChatClient,
                Map.of("parse_file", callback("parse_file")),
                null,
                null);
        RuntimeV2ReasoningNode node = new RuntimeV2ReasoningNode(
                null,
                runtimeRegistry,
                new RuntimeV2GraphModelSupport(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    ConversationHistoryService.ConversationContext context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new RuntimeV2ReactDecisionProtocol()),
                new RuntimeV2CompletionGate(),
                ledgerEngine,
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {}),
                new RuntimeV2RecoveryPolicy(),
                null,
                new RuntimeExecutionProperties());

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name(),
                RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0,
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "runtime-context-empty",
                RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES, List.of("parse_file"),
                RuntimeV2GraphStateKeys.CODE_STATE, Map.of(),
                RuntimeV2GraphStateKeys.TOOL_STATE, Map.of(),
                RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.of(),
                RuntimeV2GraphStateKeys.PARAMS_JSON, "{}",
                RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "")));

        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.ACTION_NODE);
        assertThat(output.get(RuntimeV2GraphStateKeys.LAST_TOOL_NAME)).isEqualTo("parse_file");
        assertThat(output.get(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID)).isInstanceOf(String.class);
        assertThat((String) output.get(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID))
                .isNotBlank();
        @SuppressWarnings("unchecked")
        List<Message> appendedMessages = (List<Message>) output.get(RuntimeV2GraphStateKeys.MESSAGES);
        assertThat(appendedMessages).hasSize(1);
        assertThat(appendedMessages.get(0)).isInstanceOf(AssistantMessage.class);
        AssistantMessage assistantMessage = (AssistantMessage) appendedMessages.get(0);
        assertThat(assistantMessage.getToolCalls()).hasSize(1);
        assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("parse_file");
        assertThat(assistantMessage.getToolCalls().get(0).id())
                .isEqualTo(output.get(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID));
        assertThat(decisionPrompt.get()).isNotNull();
        assertThat(finalPrompt.get()).isNull();
    }

    @Test
    void shouldAppendContinuationBridgeMessageWhenFinalAnswerIsBlocked() {
        RuntimeV2State runtimeState = newState("请继续生成报告并导出 html", List.of(), List.of(callback("parse_file")));
        runtimeState.replaceMessages(List.of(new AssistantMessage("上一步已经解析了附件。")));

        AtomicReference<Prompt> decisionPrompt = new AtomicReference<>();
        ChatClient decisionChatClient =
                ChatClient.create(new StubStreamingChatModel(decisionPrompt, List.of(response(message("我已经完成了")))));
        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        runtimeRegistry.register(
                "runtime-context-blocked-final",
                runtimeState,
                decisionChatClient,
                decisionChatClient,
                Map.of("parse_file", callback("parse_file")),
                null,
                null);
        RuntimeV2ReasoningNode node = new RuntimeV2ReasoningNode(
                null,
                runtimeRegistry,
                new RuntimeV2GraphModelSupport(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    ConversationHistoryService.ConversationContext context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new RuntimeV2ReactDecisionProtocol()),
                new RuntimeV2CompletionGate(),
                new RuntimeV2LedgerEngine(
                        new FakeRequestScopedSkillRuntimeService(List.of()),
                        new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of(datasetContract())))),
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {}),
                new RuntimeV2RecoveryPolicy(),
                null,
                new RuntimeExecutionProperties());

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name(),
                RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0,
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "runtime-context-blocked-final",
                RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES, List.of("parse_file"),
                RuntimeV2GraphStateKeys.CODE_STATE, Map.of(),
                RuntimeV2GraphStateKeys.TOOL_STATE, Map.of(),
                RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.of(),
                RuntimeV2GraphStateKeys.PARAMS_JSON, "{}",
                RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "",
                RuntimeV2GraphStateKeys.MESSAGES, runtimeState.messages())));

        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.REASONING_NODE);
        assertThat(output.get(RuntimeV2GraphStateKeys.CONTINUE_REASONING)).isEqualTo(Boolean.TRUE);
        @SuppressWarnings("unchecked")
        List<Message> appendedMessages = (List<Message>) output.get(RuntimeV2GraphStateKeys.MESSAGES);
        assertThat(appendedMessages).hasSize(1);
        assertThat(appendedMessages.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(appendedMessages.get(0).getText()).contains("[未完成续接]");
        assertThat(runtimeState.messages()).hasSize(1);
        assertThat(runtimeState.messages().get(0).getText()).contains("[未完成续接]");
    }

    @Test
    void shouldStopWhenRunPythonEnvironmentFailureIsNotRecoverable() {
        RuntimeV2State runtimeState = newState(
                "读取刚才上传的文件，提取内容，生成 txt 文件并返回",
                List.of(),
                List.of(callback("file_write"), callback("run_python"), callback("write_artifact")));
        AtomicReference<Prompt> decisionPrompt = new AtomicReference<>();
        ChatClient decisionChatClient =
                ChatClient.create(new StubStreamingChatModel(decisionPrompt, List.of(response(message("不应被调用")))));
        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        runtimeRegistry.register(
                "runtime-context-python-env-failed",
                runtimeState,
                decisionChatClient,
                decisionChatClient,
                Map.of(
                        "file_write", callback("file_write"),
                        "run_python", callback("run_python"),
                        "write_artifact", callback("write_artifact")),
                null,
                null);
        RuntimeV2ReasoningNode node = new RuntimeV2ReasoningNode(
                null,
                runtimeRegistry,
                new RuntimeV2GraphModelSupport(
                        new ContextEngineeringService(null, null) {
                            @Override
                            public List<Message> buildHistoryMessages(
                                    ConversationHistoryService.ConversationContext context) {
                                return List.of();
                            }
                        },
                        new RuntimeV2PromptAssembler(),
                        new RuntimeV2ReactDecisionProtocol()),
                new RuntimeV2CompletionGate(),
                new RuntimeV2LedgerEngine(
                        new FakeRequestScopedSkillRuntimeService(List.of()),
                        new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of()))),
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {}),
                new RuntimeV2RecoveryPolicy(),
                null,
                new RuntimeExecutionProperties());

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.REACT.name(),
                RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 3,
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, "runtime-context-python-env-failed",
                RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES, List.of("file_write", "run_python", "write_artifact"),
                RuntimeV2GraphStateKeys.CODE_STATE,
                        Map.of(
                                "status", RuntimeV2CodeState.CODE_RUN_FAILED,
                                "runRepairCount", 0),
                RuntimeV2GraphStateKeys.TOOL_STATE, Map.of(),
                RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.of(),
                RuntimeV2GraphStateKeys.PARAMS_JSON, "{\"allowCodeExecution\":true}",
                RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "",
                RuntimeV2GraphStateKeys.LAST_TOOL_RESULT,
                        """
                        {"success":false,"action":"RUN_PYTHON","errorCode":"RUN_PYTHON_EXECUTION_FAILED","errorMessage":"构建 Python 环境失败: D:\\\\项目代码\\\\lingzhou-agent\\\\workspaces\\\\users\\\\1\\\\runtime-envs\\\\python\\\\general-code"}
                        """)));

        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        assertThat(output.get(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION)).isEqualTo(Boolean.FALSE);
        assertThat(output.get(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL)).isEqualTo(Boolean.FALSE);
        assertThat(output.get(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT).toString())
                .contains("Python 运行环境构建失败");
        assertThat(decisionPrompt.get()).as("不可恢复的 Python 环境失败不应再进入模型决策").isNull();
    }

    private RuntimeV2State newState(
            String userMessage, List<RuntimeLoadedSkill> loadedSkills, List<ToolCallback> toolCallbacks) {
        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                null,
                "session-1",
                null,
                null,
                userMessage,
                userMessage,
                "normal",
                "general",
                "{}",
                "[]",
                toolCallbacks,
                "",
                "",
                loadedSkills.isEmpty() ? "" : loadedSkills.get(0).runtimeSkillName(),
                loadedSkills.stream()
                        .map(skill -> new RuntimeSkillDescriptor(
                                skill.skillId(), skill.runtimeSkillName(), skill.displayName(), skill.description()))
                        .toList(),
                loadedSkills,
                null,
                false,
                "");
        return new RuntimeV2State(prepared, 1L, null, toolCallbacks, null, null);
    }

    private RuntimeV2SkillContract datasetContract() {
        List<String> datasetTools = List.of(
                "dataset.DS20260420103211J78Q.search_dataset_summary",
                "dataset.DS20260420103211J78Q.get_dataset_schema",
                "dataset.DS20260420103211J78Q.execute_dataset_sql");
        return new RuntimeV2SkillContract(
                "expense-assistant",
                "报销助手",
                datasetTools,
                List.of(RuntimeV2ContractCapability.DATASET_QUERY),
                List.of(
                        new RuntimeV2ExecutionRequirement(
                                "dataset.summary.required",
                                "数据集摘要闭环",
                                RuntimeV2CompletionBlockerSource.SKILL,
                                "当前技能包含数据集查询路径，写 SQL 前应先查看数据集摘要，确认候选表和对象编码。",
                                "先查看数据集 summary，再继续 schema 和 SQL。",
                                List.of("dataset.summary.known"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY)),
                        new RuntimeV2ExecutionRequirement(
                                "dataset.schema.required",
                                "结构确认闭环",
                                RuntimeV2CompletionBlockerSource.SKILL,
                                "当前技能包含数据集查询路径，执行 SQL 前应先确认数据集 schema 和字段。",
                                "先查看数据集 summary/schema，再执行 SQL。",
                                List.of("dataset.schema.known"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY)),
                        new RuntimeV2ExecutionRequirement(
                                "dataset.result.required",
                                "数据结果闭环",
                                RuntimeV2CompletionBlockerSource.EVIDENCE,
                                "当前技能命中数据集查询路径时，最终答复需要成功的数据查询结果支撑。",
                                "继续执行数据集查询，拿到有效结果后再结束。",
                                List.of("dataset.query.success"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY))));
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static AssistantMessage message(String text) {
        return AssistantMessage.builder().content(text).build();
    }

    private static AssistantMessage message(String text, AssistantMessage.ToolCall toolCall) {
        return AssistantMessage.builder()
                .content(text)
                .toolCalls(List.of(toolCall))
                .build();
    }

    private static ToolCallback callback(String name) {
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
                return "";
            }
        };
    }

    private static final class FakeRequestScopedSkillRuntimeService extends RequestScopedSkillRuntimeService {

        private final List<RuntimeLoadedSkill> loadedSkills;

        private FakeRequestScopedSkillRuntimeService(List<RuntimeLoadedSkill> loadedSkills) {
            super(null);
            this.loadedSkills = loadedSkills == null ? List.of() : List.copyOf(loadedSkills);
        }

        @Override
        public List<RuntimeLoadedSkill> extractLoadedSkills(
                lingzhou.agent.spring.ai.skill.core.SkillKit skillKit, List<RuntimeSkillDescriptor> availableSkills) {
            return loadedSkills;
        }
    }

    private static final class FakeSkillContractResolver extends RuntimeV2SkillContractResolver {

        private final List<RuntimeV2SkillContract> contracts;

        private FakeSkillContractResolver(List<RuntimeV2SkillContract> contracts) {
            super(null, null, null, null);
            this.contracts = contracts == null ? List.of() : List.copyOf(contracts);
        }

        @Override
        public List<RuntimeV2SkillContract> resolveActiveContracts(RuntimeV2State state) {
            return contracts;
        }
    }

    private static final class StubStreamingChatModel implements ChatModel {

        private final AtomicReference<Prompt> capturedPrompt;
        private final List<ChatResponse> streamResponses;

        private StubStreamingChatModel(AtomicReference<Prompt> capturedPrompt, List<ChatResponse> streamResponses) {
            this.capturedPrompt = capturedPrompt;
            this.streamResponses = streamResponses;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.capturedPrompt.set(prompt);
            return streamResponses.isEmpty() ? response(message("")) : streamResponses.get(streamResponses.size() - 1);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.capturedPrompt.set(prompt);
            return Flux.fromIterable(streamResponses);
        }
    }
}
