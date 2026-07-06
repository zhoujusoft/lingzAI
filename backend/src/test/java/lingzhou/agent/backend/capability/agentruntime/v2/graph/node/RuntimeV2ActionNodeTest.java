package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.capability.agentruntime.approval.DefaultRuntimeToolApprovalAnalyzer;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApproval;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApprovalService;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeToolApprovalAnalysis;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractResolver;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContractEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphEvent;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ToolCallExecutor;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class RuntimeV2ActionNodeTest {

    @Test
    void shouldAppendToolResponseMessageUsingExistingToolCallId() {
        RuntimeV2State runtimeState = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        null,
                        "session-1",
                        null,
                        null,
                        "查询武汉报销标准",
                        "查询武汉报销标准",
                        "normal",
                        "general",
                        "{}",
                        "[]",
                        List.of(),
                        "",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        null,
                        false,
                        ""),
                1L,
                null,
                List.of(),
                null,
                null);
        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        runtimeRegistry.register(
                "runtime-context-action",
                runtimeState,
                null,
                null,
                Map.of("parse_file", callback("parse_file", "{\"success\":true,\"status\":\"SUCCESS\"}")),
                null,
                null);
        RuntimeV2ActionNode node = new RuntimeV2ActionNode(
                new RuntimeV2CodeExecutionSupport(),
                new RuntimeV2ToolCallExecutor(),
                runtimeRegistry,
                new RuntimeV2LedgerEngine(
                        new FakeRequestScopedSkillRuntimeService(),
                        new RuntimeV2TaskContractEngine(new FakeSkillContractResolver())),
                null,
                null);

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.TOOL_CALL_COUNT,
                0,
                RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID,
                "call_parse_file_1",
                RuntimeV2GraphStateKeys.LAST_TOOL_NAME,
                "parse_file",
                RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS,
                Map.of("arg0", "fanyi.docx", "arg1", "text"),
                RuntimeV2GraphStateKeys.CODE_STATE,
                Map.of(),
                RuntimeV2GraphStateKeys.TOOL_STATE,
                Map.of(),
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY,
                "runtime-context-action")));

        @SuppressWarnings("unchecked")
        List<Message> appendedMessages = (List<Message>) output.get(RuntimeV2GraphStateKeys.MESSAGES);
        assertThat(appendedMessages).hasSize(1);
        assertThat(appendedMessages.get(0)).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) appendedMessages.get(0);
        assertThat(toolResponseMessage.getResponses()).hasSize(1);
        assertThat(toolResponseMessage.getResponses().get(0).id()).isEqualTo("call_parse_file_1");
        assertThat(toolResponseMessage.getResponses().get(0).name()).isEqualTo("parse_file");
        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.OBSERVATION_NODE);
    }

    @Test
    void shouldPauseBeforeApprovalRequiredToolExecution() {
        RuntimeV2State runtimeState = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        null,
                        "session-1",
                        null,
                        null,
                        "写入 Python 脚本",
                        "写入 Python 脚本",
                        "normal",
                        "general",
                        "{}",
                        "[]",
                        List.of(),
                        "",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        null,
                        false,
                        ""),
                1L,
                null,
                List.of(),
                null,
                null);
        runtimeState.setRunId(10L);
        runtimeState.setRunCode("run-approval-1");
        RuntimeV2GraphRuntimeRegistry runtimeRegistry = new RuntimeV2GraphRuntimeRegistry();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicReference<RuntimeV2GraphEvent> emittedEvent = new AtomicReference<>();
        runtimeRegistry.register(
                "runtime-context-approval",
                runtimeState,
                null,
                null,
                Map.of("file_write", callback("file_write", "{\"success\":true}", callbackInvoked)),
                null,
                emittedEvent::set);
        RuntimeApprovalService approvalService = new FakeRuntimeApprovalService();
        RuntimeV2ActionNode node = new RuntimeV2ActionNode(
                new RuntimeV2CodeExecutionSupport(),
                new RuntimeV2ToolCallExecutor(),
                runtimeRegistry,
                new RuntimeV2LedgerEngine(
                        new FakeRequestScopedSkillRuntimeService(),
                        new RuntimeV2TaskContractEngine(new FakeSkillContractResolver())),
                null,
                approvalService);

        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.TOOL_CALL_COUNT,
                0,
                RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID,
                "call_file_write_1",
                RuntimeV2GraphStateKeys.LAST_TOOL_NAME,
                "file_write",
                RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS,
                Map.of("path", "/tmp/process.py", "content", "import subprocess\nsubprocess.run(['ls'])"),
                RuntimeV2GraphStateKeys.CODE_STATE,
                Map.of(),
                RuntimeV2GraphStateKeys.TOOL_STATE,
                Map.of(),
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY,
                "runtime-context-approval")));

        assertThat(callbackInvoked).isFalse();
        assertThat(output.get(RuntimeV2GraphStateKeys.FINISH_REASON))
                .isEqualTo(RuntimeV2FinishReason.WAITING_APPROVAL.name());
        assertThat(output.get(RuntimeV2GraphStateKeys.ROUTE)).isEqualTo(RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        assertThat(emittedEvent.get()).isNotNull();
        assertThat(emittedEvent.get().eventName()).isEqualTo("approval_required");
    }

    @Test
    void shouldAnalyzePythonScriptRiskForFileWrite() {
        RuntimeToolApprovalAnalysis analysis = new DefaultRuntimeToolApprovalAnalyzer()
                .analyze(
                        "file_write",
                        Map.of("path", "/tmp/process.py", "content", "import subprocess\nos.system('ls')"));

        assertThat(analysis.riskLevel()).isEqualTo("HIGH");
        assertThat(analysis.riskItems())
                .extracting(RuntimeToolApprovalAnalysis.RiskItem::code)
                .contains("SUBPROCESS", "OS_SYSTEM");
        assertThat(analysis.preview()).containsEntry("language", "python");
    }

    private ToolCallback callback(String name, String result) {
        return callback(name, result, new AtomicBoolean(false));
    }

    private ToolCallback callback(String name, String result, AtomicBoolean invoked) {
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
                invoked.set(true);
                return result;
            }
        };
    }

    private static final class FakeRuntimeApprovalService extends RuntimeApprovalService {

        private FakeRuntimeApprovalService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean requiresApproval(String toolName) {
            return "file_write".equals(toolName);
        }

        @Override
        public RuntimeApproval createPendingApproval(
                RuntimeV2State runtimeState,
                String toolCallId,
                String toolName,
                String toolDisplayName,
                Map<String, Object> arguments) {
            RuntimeApproval approval = new RuntimeApproval();
            approval.setApprovalCode("approval-1");
            approval.setRunId(runtimeState.runId());
            approval.setRunCode(runtimeState.runCode());
            approval.setToolCallId(toolCallId);
            approval.setToolName(toolName);
            approval.setToolDisplayName(toolDisplayName);
            approval.setToolArgumentsJson("{}");
            approval.setApprovalStatus("PENDING");
            approval.setExecutionStatus("NOT_STARTED");
            approval.setRiskLevel("HIGH");
            approval.setAnalysisJson("{}");
            return approval;
        }

        @Override
        public void markRunWaitingApproval(RuntimeV2State runtimeState, RuntimeApproval approval) {}

        @Override
        public Map<String, Object> buildApprovalPayload(RuntimeApproval approval) {
            return Map.of("approvalCode", approval.getApprovalCode(), "toolName", approval.getToolName());
        }
    }

    private static final class FakeRequestScopedSkillRuntimeService extends RequestScopedSkillRuntimeService {

        private FakeRequestScopedSkillRuntimeService() {
            super(null);
        }

        @Override
        public List<RuntimeLoadedSkill> extractLoadedSkills(
                lingzhou.agent.spring.ai.skill.core.SkillKit skillKit, List<RuntimeSkillDescriptor> availableSkills) {
            return List.of();
        }
    }

    private static final class FakeSkillContractResolver extends RuntimeV2SkillContractResolver {

        private FakeSkillContractResolver() {
            super(null, null, null, null);
        }

        @Override
        public List<RuntimeV2SkillContract> resolveActiveContracts(RuntimeV2State state) {
            return List.of();
        }
    }
}
