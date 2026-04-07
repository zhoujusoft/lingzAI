package lingzhou.agent.backend.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatModelPropertiesTests {

    @Test
    void defaultsToQwenOnlineWhenProviderIsBlank() {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setProvider(null);
        properties.getQwenOnline().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");

        assertThat(properties.getProvider()).isEqualTo("qwen-online");
        assertThat(properties.getBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode");
    }

    @Test
    void treatsQwenProfileAliasAsQwenOnlineProvider() {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setProvider("qwen");
        properties.getQwenOnline().setModel("qwen3-max");

        assertThat(properties.getProvider()).isEqualTo("qwen-online");
        assertThat(properties.getModel()).isEqualTo("qwen3-max");
    }
}
