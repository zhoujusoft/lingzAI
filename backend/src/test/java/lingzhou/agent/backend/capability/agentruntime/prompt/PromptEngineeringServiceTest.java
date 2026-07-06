package lingzhou.agent.backend.capability.agentruntime.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.app.ChatModelProperties;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntime;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimePipeline;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeProfile;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeProfileResolution;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContractSupport;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class PromptEngineeringServiceTest {

    private final PromptEngineeringService promptEngineeringService =
            new PromptEngineeringService(
                    new ChatModelProperties(),
                    null,
                    new RequestScopedSkillRuntimeService(null, new RuntimeSkillStateContractSupport(new ObjectMapper())),
                    Clock.fixed(Instant.parse("2026-06-09T02:11:12Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void shouldIncludeCurrentTimeInSystemPrompt() {
        ChatRuntimePreparedRequest prepared = prepared("{}", List.of());

        RuntimePromptPack promptPack = promptEngineeringService.resolvePromptPack(executionContext(prepared), null);

        assertThat(promptPack.systemPrompt())
                .contains("## 当前运行状态")
                .contains("- 当前日期：2026-06-09")
                .contains("- 当前时间：2026-06-09 10:11:12")
                .contains("- 当前时区：Asia/Shanghai");
    }

    @Test
    void shouldIncludeSkillRoutingStateWhenLoadedSkillsAreRestored() {
        ChatRuntimePreparedRequest prepared = prepared(
                "{\"currentRuntimeSkillName\":\"business-metrics\"}",
                List.of(new RuntimeLoadedSkill(1L, "business-metrics", "经营指标", "经营指标分析")));

        RuntimePromptPack promptPack = promptEngineeringService.resolvePromptPack(executionContext(prepared), null);

        assertThat(promptPack.systemPrompt()).contains("## 技能路由状态");
        assertThat(promptPack.systemPrompt()).contains("当前会话已加载技能：`business-metrics`");
        assertThat(promptPack.systemPrompt()).contains("当前最近一次使用的技能：`business-metrics`");
        assertThat(promptPack.systemPrompt()).contains("每轮都应基于当前用户问题重新判断");
        assertThat(promptPack.systemPrompt()).contains("先调用 `loadSkillContent(skillName)` 获取当前轮说明");
        assertThat(promptPack.systemPrompt()).contains("本轮应重新查询或生成，不要直接复用上一轮结果");
    }

    @Test
    void shouldSkipSkillRoutingStateWhenNoLoadedSkillStateExists() {
        ChatRuntimePreparedRequest prepared = prepared("{}", List.of());

        RuntimePromptPack promptPack = promptEngineeringService.resolvePromptPack(executionContext(prepared), null);

        assertThat(promptPack.systemPrompt()).doesNotContain("## 技能路由状态");
    }

    @Test
    void shouldSkipActiveSkillContentInjectionForGeneralFollowUp() {
        ChatRuntimePreparedRequest prepared = prepared(
                "{\"currentRuntimeSkillName\":\"business-metrics\"}",
                List.of(
                        new RuntimeLoadedSkill(1L, "meeting-assistant", "会议助手", "会议查询"),
                        new RuntimeLoadedSkill(2L, "business-metrics", "经营指标", "经营指标分析")));

        RuntimePromptPack promptPack = promptEngineeringService.resolvePromptPack(
                new AgentRuntimeExecutionContext(
                        prepared,
                        7L,
                        new ConversationHistoryService.ConversationContext(
                                1L,
                                "session-1",
                                "GENERAL_CHAT",
                                null,
                                11L,
                                12L,
                                1,
                                2,
                                7L,
                                null,
                                prepared.message(),
                                false),
                        new AgentRuntimeProfileResolution(
                                AgentRuntimeProfile.GENERAL_CHAT, AgentRuntimePipeline.TOOL_AWARE),
                        AgentRuntime.builder(AgentRuntimeProfile.GENERAL_CHAT).build(),
                        new StubSkillKit(
                                Set.of("meeting-assistant", "business-metrics"),
                                Map.of(
                                        "meeting-assistant", new StubSkill("meeting-assistant", "# 会议技能内容"),
                                        "business-metrics", new StubSkill("business-metrics", "# 经营指标技能内容"))),
                        new RequestScopedSkillRuntimeService(
                                null, new RuntimeSkillStateContractSupport(new ObjectMapper()))),
                null);

        assertThat(promptPack.systemPrompt()).doesNotContain("### 技能：business-metrics");
        assertThat(promptPack.systemPrompt()).doesNotContain("# 经营指标技能内容");
        assertThat(promptPack.systemPrompt()).doesNotContain("### 技能：meeting-assistant");
        assertThat(promptPack.systemPrompt()).contains("先调用 `loadSkillContent(skillName)` 获取当前轮说明");
        assertThat(promptPack.systemPrompt()).contains("不表示本轮问题已经自动绑定到其中某个 Skill");
    }

    @Test
    void shouldInjectOnlyCoreSkillGuidanceForSkillSessionActiveContent() {
        ChatRuntimePreparedRequest prepared = prepared(
                ConversationSessionType.SKILL_CHAT,
                "{\"currentRuntimeSkillName\":\"business-metrics\"}",
                List.of(new RuntimeLoadedSkill(1L, "business-metrics", "经营指标", "经营指标分析")));

        RuntimePromptPack promptPack = promptEngineeringService.resolvePromptPack(
                new AgentRuntimeExecutionContext(
                        prepared,
                        7L,
                        new ConversationHistoryService.ConversationContext(
                                1L,
                                "session-1",
                                "SKILL_CHAT",
                                null,
                                11L,
                                12L,
                                1,
                                2,
                                7L,
                                null,
                                prepared.message(),
                                false),
                        new AgentRuntimeProfileResolution(
                                AgentRuntimeProfile.SKILL_CHAT, AgentRuntimePipeline.TOOL_AWARE),
                        AgentRuntime.builder(AgentRuntimeProfile.SKILL_CHAT).build(),
                        new StubSkillKit(
                                Set.of("business-metrics"),
                                Map.of(
                                        "business-metrics",
                                        new StubSkill(
                                                "business-metrics",
                                                """
                                                # 技能：经营指标

                                                - 运行时技能名：`business-metrics`
                                                - 说明：该技能已按需加载。

                                                ## 技能核心说明

                                                先查询数据，再生成右侧预览。
                                                """))),
                        new RequestScopedSkillRuntimeService(
                                null, new RuntimeSkillStateContractSupport(new ObjectMapper()))),
                null);

        assertThat(promptPack.systemPrompt()).contains("先查询数据，再生成右侧预览");
        assertThat(promptPack.systemPrompt()).doesNotContain("运行时技能名：`business-metrics`");
        assertThat(promptPack.systemPrompt()).doesNotContain("该技能已按需加载");
        assertThat(promptPack.systemPrompt()).contains("必须先重新调用 `loadSkillContent(skillName)`");
    }

    private AgentRuntimeExecutionContext executionContext(ChatRuntimePreparedRequest prepared) {
        return new AgentRuntimeExecutionContext(
                prepared,
                7L,
                new ConversationHistoryService.ConversationContext(
                        1L, "session-1", "GENERAL_CHAT", null, 11L, 12L, 1, 2, 7L, null, prepared.message(), false),
                new AgentRuntimeProfileResolution(AgentRuntimeProfile.GENERAL_CHAT, AgentRuntimePipeline.TOOL_AWARE),
                AgentRuntime.builder(AgentRuntimeProfile.GENERAL_CHAT).build(),
                null,
                new RequestScopedSkillRuntimeService(null, new RuntimeSkillStateContractSupport(new ObjectMapper())));
    }

    private ChatRuntimePreparedRequest prepared(String paramsJson, List<RuntimeLoadedSkill> loadedSkills) {
        return prepared(ConversationSessionType.GENERAL_CHAT, paramsJson, loadedSkills);
    }

    private ChatRuntimePreparedRequest prepared(
            ConversationSessionType sessionType, String paramsJson, List<RuntimeLoadedSkill> loadedSkills) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                LingzRuntimeScopeType.GENERAL,
                "session-1",
                null,
                null,
                "尹传旗的今年的指标趋势",
                "尹传旗的今年的指标趋势",
                "normal",
                "GENERAL_CHAT",
                paramsJson,
                null,
                List.of(),
                "## Scene Prompt",
                null,
                null,
                List.of(new RuntimeSkillDescriptor(1L, "business-metrics", "经营指标", "经营指标分析")),
                loadedSkills,
                null,
                false,
                "");
    }

    private record StubSkill(String name, String content) implements Skill {

        @Override
        public SkillMetadata getMetadata() {
            return SkillMetadata.builder(name, name, "test").build();
        }

        @Override
        public String getContent() {
            return content;
        }
    }

    private record StubSkillKit(Set<String> activatedSkills, Map<String, Skill> skills) implements SkillKit {

        @Override
        public void register(SkillMetadata metadata, java.util.function.Supplier<Skill> loader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(Class<?> skillClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String name) {
            return skills.containsKey(name);
        }

        @Override
        public Skill getSkill(String name) {
            return skills.get(name);
        }

        @Override
        public SkillMetadata getMetadata(String name) {
            Skill skill = skills.get(name);
            return skill == null ? null : skill.getMetadata();
        }

        @Override
        public void activateSkill(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deactivateSkill(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deactivateAllSkills() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isActivated(String name) {
            return activatedSkills.contains(name);
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
            return activatedSkills;
        }
    }
}
