package lingzhou.agent.backend.capability.agentruntime.v2.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptLoaderTest {

    private final PromptLoader promptLoader = new PromptLoader();

    @BeforeEach
    void clearCache() {
        PromptLoader.clearCache();
    }

    @Test
    void shouldLoadPromptFromClasspathAndCacheIt() {
        String prompt = promptLoader.loadPrompt("react/direct-system");

        assertThat(prompt).contains("当前模式：DIRECT");
        assertThat(PromptLoader.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldRenderPromptVariables() {
        String prompt = promptLoader.renderPrompt("react/direct-system", Map.of("baseSystemPrompt", "你是测试助手。"));

        assertThat(prompt).contains("你是测试助手。");
        assertThat(prompt).contains("当前模式：DIRECT");
        assertThat(prompt).doesNotContain("{{baseSystemPrompt}}");
    }

    @Test
    void shouldFailWhenPromptVariablesRemainUnresolved() {
        assertThatThrownBy(() -> promptLoader.renderPrompt("react/direct-system", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少变量");
    }
}
