package lingzhou.agent.backend.capability.agentruntime.v2.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodePlanProtocol.CodeExecutionPlan;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;

class RuntimeV2CodeExecutionSupportTest {

    private final RuntimeV2CodeExecutionSupport support = new RuntimeV2CodeExecutionSupport();

    @Test
    void shouldResolveSuggestedInputPathsFromParseFileObservationAndAttachments() {
        RuntimeV2State state = newState(
                """
                [
                  {"name":"销售数据.xlsx","path":"chat-upload://chat-files/1/a.xlsx"},
                  {"name":"补充数据.csv","path":"supplement.csv"}
                ]
                """);
        state.observationTrace()
                .add(Map.of(
                        "toolName", "parse_file",
                        "arguments", "{\"arg0\":\"chat-upload://chat-files/1/a.xlsx\",\"arg1\":\"structured\"}",
                        "observation", "status: SUCCESS"));

        List<String> inputPaths = support.resolveSuggestedInputPaths(state);

        assertThat(inputPaths).containsExactly("/uploads/销售数据.xlsx", "/uploads/supplement.csv");
    }

    @Test
    void shouldBuildCodeFileWriteObservation() {
        CodeExecutionPlan plan = new CodeExecutionPlan(
                "/workspace/task.py",
                List.of("/uploads/a.xlsx"),
                "/outputs/report.html",
                "report.html",
                "text/html",
                "/workspace",
                120,
                "生成报告");

        String observation = support.buildCodeFileWriteObservation(plan, "{\"success\":true}", true, 4000);

        assertThat(observation).contains("CODE_SCRIPT_READY");
        assertThat(observation).contains("codeStage: file-write");
        assertThat(observation).contains("scriptPath: /workspace/task.py");
        assertThat(observation).contains("stageOutcome: script-ready");
        assertThat(observation).contains("outputReady: false");
        assertThat(observation).contains("artifactPublished: false");
    }

    @Test
    void shouldBuildCodeRunFailureObservationWithFailureKind() {
        CodeExecutionPlan plan = new CodeExecutionPlan(
                "/workspace/task.py",
                List.of("/uploads/a.zip"),
                "/outputs/result.zip",
                "result.zip",
                "application/zip",
                "/workspace",
                180,
                "提取 PDF");

        String observation = support.buildCodeRunObservation(
                plan, "errorCode: RUN_PYTHON_EXIT_NON_ZERO\nfailureKind: no-matching-pdf-found", false, 4000);

        assertThat(observation).contains("codeStage: run-python");
        assertThat(observation).contains("stageOutcome: run-failed");
        assertThat(observation).contains("failureKind: no-matching-pdf-found");
        assertThat(observation).contains("outputReady: false");
    }

    @Test
    void shouldBuildSuggestedPlanForPreview() {
        CodeExecutionPlan plan = support.buildSuggestedPlan(
                "筛选出所有湖北的企业并输出",
                """
                [
                  {"name":"销售数据.xlsx","path":"chat-upload://chat-files/1/a.xlsx"}
                ]
                """,
                List.of(Map.of(
                        "toolName", "parse_file",
                        "arguments", "{\"arg0\":\"chat-upload://chat-files/1/a.xlsx\",\"arg1\":\"structured\"}")));

        assertThat(plan.scriptPath()).isEqualTo("/workspace/runtime_v2_task.py");
        assertThat(plan.inputPaths()).containsExactly("/uploads/销售数据.xlsx");
        assertThat(plan.outputPath()).isEqualTo("/outputs/runtime_v2_output.bin");
        assertThat(plan.outputMimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldBuildZipSuggestedPlanForArchiveExtractionTask() {
        CodeExecutionPlan plan = support.buildSuggestedPlan(
                "帮我提取其中 PDF 发票文件 然后给我压缩一下",
                """
                [
                  {"name":"20260506_通行费电子发票_4张.zip","path":"chat-upload://chat-files/1/invoices.zip"}
                ]
                """,
                List.of(Map.of(
                        "toolName", "parse_file",
                        "arguments",
                                "{\"arg0\":\"chat-upload://chat-files/1/invoices.zip\",\"arg1\":\"structured\"}")));

        assertThat(plan.scriptPath()).isEqualTo("/workspace/runtime_v2_task.py");
        assertThat(plan.inputPaths()).containsExactly("/uploads/20260506_通行费电子发票_4张.zip");
        assertThat(plan.outputPath()).isEqualTo("/outputs/runtime_v2_output.zip");
        assertThat(plan.outputMimeType()).isEqualTo("application/zip");
    }

    @Test
    void shouldRoundTripCodeStatePlan() {
        CodeExecutionPlan plan = new CodeExecutionPlan(
                "/workspace/task.py",
                List.of("/uploads/a.xlsx"),
                "/outputs/report.html",
                "report.html",
                "text/html",
                "/workspace",
                120,
                "生成报告");

        Map<String, Object> codeState =
                support.buildCodeState(plan, "", "- a.xlsx (/uploads/a.xlsx)", RuntimeV2CodeState.CODE_PLAN_PREPARED);

        CodeExecutionPlan restored = support.readPlan(codeState, "fallback-goal");

        assertThat(restored).isNotNull();
        assertThat(restored.scriptPath()).isEqualTo(plan.scriptPath());
        assertThat(restored.inputPaths()).containsExactlyElementsOf(plan.inputPaths());
        assertThat(restored.outputPath()).isEqualTo(plan.outputPath());
        assertThat(restored.goal()).isEqualTo(plan.goal());
    }

    private RuntimeV2State newState(String fileListJson) {
        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                LingzRuntimeScopeType.GENERAL,
                "session-code",
                null,
                null,
                "message",
                "message",
                "normal",
                "GENERAL_CHAT_V2",
                "{}",
                fileListJson,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                false,
                "");
        return new RuntimeV2State(prepared, 1L, null, List.of(), null, null);
    }
}
