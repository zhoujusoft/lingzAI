package lingzhou.agent.backend.business.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContractSupport;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

class RequestScopedSkillRuntimeServiceTest {

    @Test
    void shouldKeepOnlyCoreSkillGuidanceWhenBuildingLoadableSkillContent() throws Exception {
        RequestScopedSkillRuntimeService service = new RequestScopedSkillRuntimeService(null);
        Method method = RequestScopedSkillRuntimeService.class.getDeclaredMethod(
                "buildLoadableSkillContent", RuntimeSkillDescriptor.class, String.class);
        method.setAccessible(true);

        RuntimeSkillDescriptor descriptor = new RuntimeSkillDescriptor(1L, "business-metrics", "经营指标", "经营指标分析");
        String rawSkillContent = """
                ## 技能使用说明

                先查询数据，再生成右侧预览。
                需要时可读取 referenceKey。
                """;

        String content = (String) method.invoke(service, descriptor, rawSkillContent);

        assertThat(content).contains("## 技能核心说明");
        assertThat(content).contains("先查询数据，再生成右侧预览");
        assertThat(content).contains("需要时可读取 referenceKey");
        assertThat(content).contains("中间查询成功不等于任务完成");
        assertThat(content).doesNotContain("## Skill Execution Contract");
        assertThat(content).doesNotContain("## 当前技能可用工具");
        assertThat(content).doesNotContain("## 当前轮执行要求");
    }

    @Test
    void shouldExposeOnlyCurrentSkillToolsWhileKeepingHistoricalLoadedSkills() throws Exception {
        RequestScopedSkillRuntimeService service = new RequestScopedSkillRuntimeService(null);
        SkillKit delegate = DefaultSkillKit.builder()
                .skillBox(new SimpleSkillBox())
                .poolManager(new DefaultSkillPoolManager())
                .build();
        registerSkill(delegate, "meeting-assistant", List.of(ToolCallbacks.from(new MeetingToolProvider())));
        registerSkill(delegate, "business-metrics", List.of(ToolCallbacks.from(new MetricsToolProvider())));

        SkillKit trackingSkillKit = newTrackingSkillKit(
                delegate,
                List.of(
                        new RuntimeSkillDescriptor(1L, "meeting-assistant", "会议助手", "会议查询"),
                        new RuntimeSkillDescriptor(2L, "business-metrics", "经营指标", "经营分析")));
        trackingSkillKit.activateSkill("meeting-assistant");
        trackingSkillKit.activateSkill("business-metrics");

        assertThat(trackingSkillKit.getAllActiveTools())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("queryMetricsDetail");
        assertThat(service.extractLoadedSkills(
                        trackingSkillKit,
                        List.of(
                                new RuntimeSkillDescriptor(1L, "meeting-assistant", "会议助手", "会议查询"),
                                new RuntimeSkillDescriptor(2L, "business-metrics", "经营指标", "经营分析"))))
                .extracting(RuntimeLoadedSkill::runtimeSkillName)
                .containsExactly("meeting-assistant", "business-metrics");
    }

    @Test
    void shouldFallbackToRuntimeSkillWhenPublishedContextResolutionFails() {
        RequestScopedSkillRuntimeService service =
                new RequestScopedSkillRuntimeService(new FallbackSkillCatalogService(new StaticSkill(
                        "business-metrics",
                        "# 经营指标技能",
                        List.of(ToolCallbacks.from(new MetricsToolProvider())))));

        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType.GENERAL_CHAT,
                LingzRuntimeScopeType.GENERAL,
                "session-1",
                null,
                null,
                "今年各销售完成情况明细",
                "今年各销售完成情况明细",
                "normal",
                "GENERAL_CHAT",
                "{\"currentRuntimeSkillName\":\"business-metrics\"}",
                null,
                List.of(),
                null,
                null,
                "business-metrics",
                List.of(new RuntimeSkillDescriptor(1L, "business-metrics", "经营指标", "经营指标分析")),
                List.of(new RuntimeLoadedSkill(1L, "business-metrics", "经营指标", "经营指标分析")),
                null,
                false,
                "");

        SkillKit skillKit = service.buildSkillKit(prepared);

        assertThat(skillKit.getActivatedSkillNames()).isEmpty();
        assertThat(skillKit.getAllActiveTools()).isEmpty();
        assertThat(skillKit.getSkill("business-metrics").getContent()).contains("## 技能核心说明");
        assertThat(skillKit.getSkill("business-metrics").getContent()).doesNotContain("## 当前轮执行要求");
    }

    @Test
    void shouldNotAutoActivateRestoredLoadedSkillUntilLoadSkillContentIsCalled() throws Exception {
        RequestScopedSkillRuntimeService service =
                new RequestScopedSkillRuntimeService(new FallbackSkillCatalogService(new StaticSkill(
                        "business-metrics",
                        "# 经营指标技能",
                        List.of(ToolCallbacks.from(new MetricsToolProvider())))));

        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType.GENERAL_CHAT,
                LingzRuntimeScopeType.GENERAL,
                "session-1",
                null,
                null,
                "继续看经营指标",
                "继续看经营指标",
                "normal",
                "GENERAL_CHAT",
                "{\"currentRuntimeSkillName\":\"business-metrics\",\"loadedSkills\":[\"business-metrics\"]}",
                null,
                List.of(),
                null,
                null,
                null,
                List.of(new RuntimeSkillDescriptor(1L, "business-metrics", "经营指标", "经营指标分析")),
                List.of(new RuntimeLoadedSkill(1L, "business-metrics", "经营指标", "经营指标分析")),
                null,
                false,
                "");

        SkillKit skillKit = service.buildSkillKit(prepared);

        assertThat(skillKit.getActivatedSkillNames()).isEmpty();
        assertThat(skillKit.getAllActiveTools()).isEmpty();

        Class<?> loaderToolsClass = Arrays.stream(RequestScopedSkillRuntimeService.class.getDeclaredClasses())
                .filter(candidate -> "TrackingSkillLoaderTools".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> loaderConstructor = loaderToolsClass.getDeclaredConstructor(skillKit.getClass());
        loaderConstructor.setAccessible(true);
        Object loaderTools = loaderConstructor.newInstance(skillKit);
        Method loadSkillContent = loaderToolsClass.getDeclaredMethod("loadSkillContent", String.class);
        loadSkillContent.setAccessible(true);
        String content = (String) loadSkillContent.invoke(loaderTools, "business-metrics");

        assertThat(content).contains("# 经营指标");
        assertThat(skillKit.getActivatedSkillNames()).containsExactly("business-metrics");
        assertThat(skillKit.getAllActiveTools())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("queryMetricsDetail");
    }

    @Test
    void shouldExtractSkillReadFactsOnlyFromSuccessfulLoadSkillContentEvents() {
        RequestScopedSkillRuntimeService service = new RequestScopedSkillRuntimeService(null);

        List<RequestScopedSkillRuntimeService.SkillReadFact> facts = service.extractSkillReadFacts(
                List.of(
                        java.util.Map.of(
                                "type",
                                "tool",
                                "content",
                                java.util.Map.of(
                                        "id",
                                        "call-1",
                                        "name",
                                        "loadSkillContent",
                                        "arguments",
                                        "{\"skillName\":\"business-metrics\"}")),
                        java.util.Map.of(
                                "type",
                                "result",
                                "content",
                                java.util.Map.of(
                                        "id", "call-1",
                                        "name", "loadSkillContent",
                                        "arguments", "{\"skillName\":\"business-metrics\"}",
                                        "response", "# 技能：经营指标"))),
                List.of(new RuntimeSkillDescriptor(1L, "business-metrics", "经营指标", "经营指标分析")));

        assertThat(facts)
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.skillName()).isEqualTo("business-metrics");
                    assertThat(fact.displayName()).isEqualTo("经营指标");
                    assertThat(fact.message()).contains("上一轮已读取技能 `business-metrics`");
                    assertThat(fact.toolCallId()).isEqualTo("call-1");
                });
    }

    @Test
    void shouldResolveSkillReadFactsFromParamsJson() {
        RequestScopedSkillRuntimeService service =
                new RequestScopedSkillRuntimeService(null, new RuntimeSkillStateContractSupport(new ObjectMapper()));

        List<RequestScopedSkillRuntimeService.SkillReadFact> facts = service.resolveSkillReadFactsFromParams(
                """
                {"runtimeSkillState":{"skillReadFacts":[{"skillName":"business-metrics","displayName":"经营指标","message":"上一轮已读取技能 `business-metrics`。","toolCallId":"call-1"}]}}
                """);

        assertThat(facts)
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.skillName()).isEqualTo("business-metrics");
                    assertThat(fact.displayName()).isEqualTo("经营指标");
                    assertThat(fact.message()).contains("上一轮已读取技能");
                    assertThat(fact.toolCallId()).isEqualTo("call-1");
                });
    }

    @Test
    void shouldResolveLegacyFlatSkillReadFactsFromParamsJson() {
        RequestScopedSkillRuntimeService service =
                new RequestScopedSkillRuntimeService(null, new RuntimeSkillStateContractSupport(new ObjectMapper()));

        List<RequestScopedSkillRuntimeService.SkillReadFact> facts = service.resolveSkillReadFactsFromParams(
                """
                {"skillReadFacts":[{"skillName":"business-metrics","displayName":"经营指标","message":"上一轮已读取技能 `business-metrics`。","toolCallId":"call-1"}]}
                """);

        assertThat(facts)
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.skillName()).isEqualTo("business-metrics");
                    assertThat(fact.displayName()).isEqualTo("经营指标");
                    assertThat(fact.message()).contains("上一轮已读取技能");
                    assertThat(fact.toolCallId()).isEqualTo("call-1");
                });
    }

    private SkillKit newTrackingSkillKit(SkillKit delegate, List<RuntimeSkillDescriptor> availableSkills) throws Exception {
        Class<?> trackingClass = Arrays.stream(RequestScopedSkillRuntimeService.class.getDeclaredClasses())
                .filter(candidate -> "TrackingSkillKit".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = trackingClass.getDeclaredConstructor(SkillKit.class, List.class, String.class);
        constructor.setAccessible(true);
        return (SkillKit) constructor.newInstance(delegate, availableSkills, "");
    }

    private void registerSkill(SkillKit skillKit, String name, List<ToolCallback> tools) {
        skillKit.register(
                SkillMetadata.builder(name, name, "test").build(),
                () -> new StaticSkill(name, "# " + name, tools));
    }

    private static final class MeetingToolProvider {

        @Tool(description = "查询会议详情")
        public String queryMeetingDetail() {
            return "";
        }
    }

    private static final class MetricsToolProvider {

        @Tool(description = "查询经营指标明细")
        public String queryMetricsDetail() {
            return "";
        }
    }

    private record StaticSkill(String name, String content, List<ToolCallback> tools) implements Skill {

        @Override
        public SkillMetadata getMetadata() {
            return SkillMetadata.builder(name, name, "test").build();
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public List<ToolCallback> getTools() {
            return tools;
        }
    }

    private static final class FallbackSkillCatalogService extends SkillCatalogService {

        private final Skill runtimeSkill;

        private FallbackSkillCatalogService(Skill runtimeSkill) {
            super(null, null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
            this.runtimeSkill = runtimeSkill;
        }

        @Override
        public SkillChatContext resolveSkillChatContextForPublished(
                Long skillId, String preferredDisplayName, String preferredDescription) throws TaskException {
            throw new TaskException("模拟 skill 上下文恢复失败", TaskException.Code.UNKNOWN);
        }

        @Override
        public Skill getRuntimeSkill(Long skillId) {
            return runtimeSkill;
        }
    }
}
