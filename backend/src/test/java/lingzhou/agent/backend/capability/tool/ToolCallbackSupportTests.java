package lingzhou.agent.backend.capability.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolCallbackSupportTests {

    @Test
    void shouldDeduplicateCallbacksByToolNameAndKeepFirstOccurrenceOrder() {
        ToolCallback runtimeToolFirst = callback("runtime_tool", "first");
        ToolCallback datasetTool = callback("dataset.demo.execute_dataset_sql", "dataset");
        ToolCallback runtimeToolSecond = callback("runtime_tool", "second");
        ToolCallback parseFile = callback("parse_file", "parse");

        List<ToolCallback> deduplicated = ToolCallbackSupport.deduplicateByName(
                List.of(runtimeToolFirst, datasetTool, runtimeToolSecond, parseFile));

        assertThat(deduplicated)
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("runtime_tool", "dataset.demo.execute_dataset_sql", "parse_file");
        assertThat(deduplicated.get(0)).isSameAs(runtimeToolFirst);
    }

    @Test
    void shouldTreatListActiveSkillsAsZeroArgumentTool() {
        assertThat(ToolCallbackSupport.acceptsEmptyArguments(callback(
                        "listActiveSkills", "ok", "{\"type\":\"object\",\"properties\":{},\"required\":[]}")))
                .isTrue();
    }

    @Test
    void shouldTreatEmptyObjectSchemaAsZeroArgumentTool() {
        assertThat(ToolCallbackSupport.acceptsEmptyArguments(callback(
                        "ping_tool", "pong", "{\"type\":\"object\",\"properties\":{},\"required\":[]}")))
                .isTrue();
    }

    @Test
    void shouldRequireArgumentsWhenSchemaDeclaresProperties() {
        assertThat(ToolCallbackSupport.acceptsEmptyArguments(callback(
                        "file_read",
                        "content",
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}")))
                .isFalse();
    }

    private ToolCallback callback(String name, String result) {
        return callback(name, result, "{\"type\":\"object\"}");
    }

    private ToolCallback callback(String name, String result, String inputSchema) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(name + " description")
                .inputSchema(inputSchema)
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
