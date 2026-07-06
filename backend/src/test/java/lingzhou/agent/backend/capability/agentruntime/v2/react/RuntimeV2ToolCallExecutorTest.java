package lingzhou.agent.backend.capability.agentruntime.v2.react;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class RuntimeV2ToolCallExecutorTest {

    private final RuntimeV2ToolCallExecutor executor = new RuntimeV2ToolCallExecutor();

    @Test
    void shouldMapNamedRunPythonArgumentsToPositionalArguments() {
        String toolInput = executor.execute(
                "run_python",
                echoTool(),
                Map.of(
                        "scriptPath",
                        "/workspace/task.py",
                        "args",
                        List.of("/uploads/a.zip", "/outputs/b.zip"),
                        "workDir",
                        "/workspace",
                        "timeoutSeconds",
                        180));

        Map<String, Object> payload = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
        assertThat(payload).containsEntry("arg0", "/workspace/task.py");
        assertThat(payload).containsEntry("arg2", "/workspace");
        assertThat(payload).containsEntry("arg3", 180);
        assertThat(payload.get("arg1")).isEqualTo(List.of("/uploads/a.zip", "/outputs/b.zip"));
    }

    @Test
    void shouldNormalizeSingleStringRunPythonArgAndPreserveSecondScriptArgument() {
        String toolInput = executor.execute(
                "run_python",
                echoTool(),
                Map.of(
                        "arg0", "/workspace/filter_hubei_companies.py",
                        "arg1", "/uploads/企业名单.xlsx",
                        "arg2", "/outputs/hubei_companies.xlsx",
                        "arg3", 600));

        Map<String, Object> payload = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
        assertThat(payload).containsEntry("arg0", "/workspace/filter_hubei_companies.py");
        assertThat(payload.get("arg1")).isEqualTo(List.of("/uploads/企业名单.xlsx", "/outputs/hubei_companies.xlsx"));
        assertThat(payload).doesNotContainKey("arg2");
        assertThat(payload).containsEntry("arg3", 600);
    }

    @Test
    void shouldNormalizeMixedRunPythonPositionalArgumentsIntoCanonicalArgs() {
        String toolInput = executor.execute(
                "run_python",
                echoTool(),
                Map.of(
                        "arg0", "/workspace/filter_hubei_companies.py",
                        "arg1", "/uploads/企业名单.xlsx",
                        "arg2", "/outputs/hubei_companies.xlsx",
                        "timeoutSeconds", 600));

        Map<String, Object> payload = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
        assertThat(payload).containsEntry("arg0", "/workspace/filter_hubei_companies.py");
        assertThat(payload.get("arg1"))
                .isEqualTo(List.of("/uploads/企业名单.xlsx", "/outputs/hubei_companies.xlsx"));
        assertThat(payload).doesNotContainKey("arg2");
        assertThat(payload).containsEntry("arg3", 600);
    }

    @Test
    void shouldMapNamedWriteArtifactArgumentsToPositionalArguments() {
        String toolInput = executor.execute(
                "write_artifact",
                echoTool(),
                Map.of(
                        "folder", "runtime_v2",
                        "fileName", "result.zip",
                        "content", "",
                        "sourcePath", "/outputs/result.zip",
                        "contentType", "application/zip"));

        Map<String, Object> payload = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
        assertThat(payload).containsEntry("arg0", "runtime_v2");
        assertThat(payload).containsEntry("arg1", "result.zip");
        assertThat(payload).containsEntry("arg2", "");
        assertThat(payload).containsEntry("arg3", "/outputs/result.zip");
        assertThat(payload).containsEntry("arg4", "application/zip");
    }

    @Test
    void shouldMapNamedParseFileArgumentsToPositionalArguments() {
        String toolInput = executor.execute(
                "parse_file",
                echoTool(),
                Map.of(
                        "path", "/uploads/test.xlsx",
                        "mode", "text"));

        Map<String, Object> payload = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
        assertThat(payload).containsEntry("arg0", "/uploads/test.xlsx");
        assertThat(payload).containsEntry("arg1", "text");
    }

    private ToolCallback echoTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return null;
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return null;
            }

            @Override
            public String call(String toolInput) {
                return toolInput;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return toolInput;
            }
        };
    }
}
