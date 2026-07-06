package lingzhou.agent.backend.business.skill.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCatalogServicePromptTest {

    @Test
    void shouldRequireFreshExecutionAndShowResolvedDatasetToolOrder() throws Exception {
        SkillCatalogService service =
                new SkillCatalogService(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        Method method = SkillCatalogService.class.getDeclaredMethod(
                "buildSkillSystemPrompt", String.class, String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String systemPrompt = (String) method.invoke(
                service,
                "经营指标",
                "business-metrics",
                "经营指标分析",
                "# 技能内容",
                List.of(
                        resolvedTool("dataset.demo.search_dataset_summary", "search_dataset_summary", "DATASET_TOOL"),
                        resolvedTool("dataset.demo.get_dataset_schema", "get_dataset_schema", "DATASET_TOOL"),
                        resolvedTool("dataset.demo.execute_dataset_sql", "execute_dataset_sql", "DATASET_TOOL")));

        assertThat(systemPrompt).contains("Current skill available tools:");
        assertThat(systemPrompt).contains("数据集工具使用顺序必须遵守：先 search_dataset_summary，再按需要 get_dataset_schema，最后才 execute_dataset_sql。");
        assertThat(systemPrompt).contains("这个顺序对追问同样生效");
        assertThat(systemPrompt).contains("必须把本轮视为新的技能执行轮");
        assertThat(systemPrompt).contains("本轮必须重新生成新的产物并确认成功后");
        assertThat(systemPrompt).contains("不要把上一轮工具结果、预览或产物当作本轮已完成");
    }

    private Object resolvedTool(String name, String displayName, String toolType) throws Exception {
        Class<?> resolvedToolClass = Arrays.stream(SkillCatalogService.class.getDeclaredClasses())
                .filter(candidate -> "ResolvedSkillTool".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = resolvedToolClass.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, displayName, "", toolType, "runtime", false);
    }
}
