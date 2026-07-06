package lingzhou.agent.backend.capability.agentruntime.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class RuntimeV2ActiveToolRegistryTest {

    @Test
    void shouldMergeDynamicSkillToolsIntoRuntimeState() {
        RuntimeV2ActiveToolRegistry registry =
                new RuntimeV2ActiveToolRegistry(new RuntimeExecutionCapabilityAdapter(null, null) {
                    @Override
                    public List<ToolCallback> bindToolCallbacks(
                            List<ToolCallback> callbacks,
                            ChatRuntimePreparedRequest prepared,
                            lingzhou.agent.backend.business.chat.service.ConversationHistoryService.ConversationContext
                                    context,
                            SkillKit requestSkillKit) {
                        return callbacks == null ? List.of() : List.copyOf(callbacks);
                    }
                });
        ToolCallback baseTool = callback("parse_file");
        ToolCallback dynamicTool = callback("custom_skill_tool");
        SkillKit skillKit = new SkillKit() {
            @Override
            public void register(SkillMetadata metadata, Supplier<Skill> loader) {}

            @Override
            public void register(Object instance) {}

            @Override
            public void register(Class<?> skillClass) {}

            @Override
            public boolean exists(String name) {
                return false;
            }

            @Override
            public Skill getSkill(String name) {
                return null;
            }

            @Override
            public SkillMetadata getMetadata(String name) {
                return null;
            }

            @Override
            public void activateSkill(String name) {}

            @Override
            public void deactivateSkill(String name) {}

            @Override
            public void deactivateAllSkills() {}

            @Override
            public boolean isActivated(String name) {
                return false;
            }

            @Override
            public List<ToolCallback> getSkillLoaderTools() {
                return List.of();
            }

            @Override
            public List<ToolCallback> getAllActiveTools() {
                return List.of(dynamicTool);
            }

            @Override
            public String getSkillSystemPrompt() {
                return "";
            }

            @Override
            public Set<String> getActivatedSkillNames() {
                return Set.of("custom-skill");
            }
        };
        RuntimeV2State state = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        null,
                        null,
                        "session-id",
                        null,
                        null,
                        "message",
                        "user message",
                        "normal",
                        "",
                        "{}",
                        null,
                        List.of(baseTool),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        false,
                        ""),
                1L,
                null,
                List.of(baseTool),
                skillKit,
                null);

        Map<String, ToolCallback> toolIndex = registry.refresh(state);

        assertThat(toolIndex.keySet()).containsExactlyInAnyOrder("parse_file", "custom_skill_tool");
        assertThat(state.toolCallbacks()).hasSize(2);
    }

    private ToolCallback callback(String name) {
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
}
