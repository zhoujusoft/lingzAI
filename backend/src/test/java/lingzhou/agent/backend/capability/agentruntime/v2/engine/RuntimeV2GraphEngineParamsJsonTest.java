package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class RuntimeV2GraphEngineParamsJsonTest {

    @Test
    void shouldKeepLoadedSkillsWhenBuildingTerminalParamsJson() throws Exception {
        RuntimeV2GraphEngine engine = new RuntimeV2GraphEngine(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new StubRequestScopedSkillRuntimeService());
        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                LingzRuntimeScopeType.GENERAL,
                "session-1",
                null,
                null,
                "message",
                "message",
                "normal",
                "GENERAL_CHAT_V2",
                "{\"foo\":\"bar\"}",
                null,
                List.of(),
                null,
                null,
                null,
                List.of(new RuntimeSkillDescriptor(1L, "expense", "报销助手", "desc")),
                List.of(new RuntimeLoadedSkill(1L, "expense", "报销助手", "desc")),
                null,
                false,
                "");
        RuntimeV2State state = new RuntimeV2State(prepared, 1L, null, List.of(), new StubRequestScopedSkillKit(), null);

        String paramsJson = engine.buildGraphParamsJson(state, prepared, Map.of("cancelled", true), "已终止本次执行。", false);

        Map<String, Object> payload = JSON.parseObject(paramsJson, Map.class);
        assertThat(payload.get("loadedSkills")).isInstanceOf(List.class);
        assertThat((List<Object>) payload.get("loadedSkills")).contains("expense");
        assertThat(payload.get("currentRuntimeSkillName")).isEqualTo("expense");
        assertThat(payload.get("graphRuntime")).isEqualTo(Boolean.TRUE);
    }

    private static final class StubRequestScopedSkillRuntimeService extends RequestScopedSkillRuntimeService {

        private StubRequestScopedSkillRuntimeService() {
            super(null);
        }

        @Override
        public List<RuntimeLoadedSkill> extractLoadedSkills(
                lingzhou.agent.spring.ai.skill.core.SkillKit skillKit, List<RuntimeSkillDescriptor> availableSkills) {
            return List.of(new RuntimeLoadedSkill(1L, "expense", "报销助手", "desc"));
        }

        @Override
        public String resolveCurrentRuntimeSkillName(
                lingzhou.agent.spring.ai.skill.core.SkillKit skillKit, ChatRuntimePreparedRequest prepared) {
            return "expense";
        }

        @Override
        public String mergeSkillStateParams(
                String paramsJson,
                List<RuntimeSkillDescriptor> availableSkills,
                List<RuntimeLoadedSkill> loadedSkills,
                String currentRuntimeSkillName) {
            Map<String, Object> payload = JSON.parseObject(paramsJson, Map.class);
            payload.put(
                    "loadedSkills",
                    loadedSkills == null
                            ? List.of()
                            : loadedSkills.stream()
                                    .map(RuntimeLoadedSkill::runtimeSkillName)
                                    .toList());
            payload.put("currentRuntimeSkillName", currentRuntimeSkillName);
            return JSON.toJSONString(payload);
        }
    }

    private static final class StubRequestScopedSkillKit implements lingzhou.agent.spring.ai.skill.core.SkillKit {

        @Override
        public void register(
                lingzhou.agent.spring.ai.skill.core.SkillMetadata metadata,
                java.util.function.Supplier<lingzhou.agent.spring.ai.skill.core.Skill> loader) {}

        @Override
        public void register(Object instance) {}

        @Override
        public void register(Class<?> skillClass) {}

        @Override
        public boolean exists(String name) {
            return true;
        }

        @Override
        public lingzhou.agent.spring.ai.skill.core.Skill getSkill(String name) {
            return null;
        }

        @Override
        public lingzhou.agent.spring.ai.skill.core.SkillMetadata getMetadata(String name) {
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
            return true;
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
        public java.util.Set<String> getActivatedSkillNames() {
            return java.util.Set.of("expense");
        }
    }
}
