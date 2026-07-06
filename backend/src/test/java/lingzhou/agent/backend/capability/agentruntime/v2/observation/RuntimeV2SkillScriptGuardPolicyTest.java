package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class RuntimeV2SkillScriptGuardPolicyTest {

    private final RuntimeV2SkillScriptGuardPolicy policy = new RuntimeV2SkillScriptGuardPolicy();

    @Test
    void shouldBlockWorkspacePythonRewriteWhenFixedSkillScriptAlreadyFailed() {
        RuntimeV2State state = newState(new FixedScriptSkillKit());
        state.observationTrace()
                .add(
                        Map.of(
                                "toolName", "run_python",
                                "observation",
                                        """
                        success: false
                        scriptPath: /skill/scripts/extract_invoice.py
                        errorCode: RUN_PYTHON_EXIT_NON_ZERO
                        """));

        String observation = policy.buildBlockedRewriteObservation(
                state, "file_write", Map.of("path", "/workspace/extract_invoice_retry.py"), 4000);

        assertThat(observation).contains("SKILL_FIXED_SCRIPT_REWRITE_BLOCKED");
        assertThat(observation).contains("fixedSkillScripts: /skill/scripts/extract_invoice.py");
        assertThat(observation).contains("failureKind: fixed-skill-script-rewrite-blocked");
        assertThat(observation).doesNotContain("nextActionHint");
    }

    @Test
    void shouldAllowNonPythonWorkspaceWrite() {
        RuntimeV2State state = newState(new FixedScriptSkillKit());
        state.observationTrace()
                .add(
                        Map.of(
                                "toolName", "run_python",
                                "observation",
                                        """
                        success: false
                        scriptPath: /skill/scripts/extract_invoice.py
                        errorCode: RUN_PYTHON_EXIT_NON_ZERO
                        """));

        String observation = policy.buildBlockedRewriteObservation(
                state, "file_write", Map.of("path", "/workspace/result.json"), 4000);

        assertThat(observation).isEmpty();
    }

    private RuntimeV2State newState(SkillKit skillKit) {
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
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                false,
                "");
        return new RuntimeV2State(prepared, 1L, null, List.of(), skillKit, null);
    }

    private static final class FixedScriptSkillKit implements SkillKit {

        private final Skill skill = new Skill() {
            @Override
            public SkillMetadata getMetadata() {
                return new SkillMetadata("invoice-skill", "invoice skill", "test");
            }

            @Override
            public String getContent() {
                return "执行脚本路径：/skill/scripts/extract_invoice.py";
            }
        };

        @Override
        public void register(SkillMetadata metadata, Supplier<Skill> loader) {}

        @Override
        public void register(Object instance) {}

        @Override
        public void register(Class<?> skillClass) {}

        @Override
        public boolean exists(String name) {
            return "invoice-skill".equals(name);
        }

        @Override
        public Skill getSkill(String name) {
            return exists(name) ? skill : null;
        }

        @Override
        public SkillMetadata getMetadata(String name) {
            return exists(name) ? skill.getMetadata() : null;
        }

        @Override
        public void activateSkill(String name) {}

        @Override
        public void deactivateSkill(String name) {}

        @Override
        public void deactivateAllSkills() {}

        @Override
        public boolean isActivated(String name) {
            return exists(name);
        }

        @Override
        public List<ToolCallback> getSkillLoaderTools() {
            return List.of();
        }

        @Override
        public List<ToolCallback> getAllActiveTools() {
            return List.of();
        }

        @Override
        public String getSkillSystemPrompt() {
            return "";
        }

        @Override
        public Set<String> getActivatedSkillNames() {
            return Set.of("invoice-skill");
        }
    }
}
