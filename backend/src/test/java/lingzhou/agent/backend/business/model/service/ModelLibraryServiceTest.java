package lingzhou.agent.backend.business.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import lingzhou.agent.backend.business.model.domain.ModelVendor;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import org.junit.jupiter.api.Test;

class ModelLibraryServiceTest {

    @Test
    void shouldReuseChatRuntimeClientFactoryForVllmChatValidation() throws Exception {
        RecordingModelRuntimeClientFactory factory = new RecordingModelRuntimeClientFactory();
        ModelLibraryService service = new ModelLibraryService(null, null, null, null, factory);
        ModelVendor vendor = new ModelVendor();
        vendor.setVendorCode(ModelLibraryService.VENDOR_VLLM);
        vendor.setVendorName("vLLM");

        Class<?> normalizedClass = null;
        for (Class<?> declaredClass : ModelLibraryService.class.getDeclaredClasses()) {
            if ("NormalizedModel".equals(declaredClass.getSimpleName())) {
                normalizedClass = declaredClass;
                break;
            }
        }
        assertThat(normalizedClass).isNotNull();
        Constructor<?> constructor = normalizedClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object normalized = constructor.newInstance(
                "model-code",
                "模型",
                "CHAT",
                1L,
                "VLLM",
                null,
                "http://59.1.100.19:8080/v1",
                "sk-test",
                "/v1/chat/completions",
                "qwen3.5-397b",
                0.3D,
                512,
                "",
                false,
                null,
                null,
                null,
                "ACTIVE");
        Method method = ModelLibraryService.class.getDeclaredMethod(
                "performModelValidation",
                ModelVendor.class,
                normalizedClass,
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        method.invoke(service, vendor, normalized, "http://59.1.100.19:8080/v1", "sk-test", "模型保存前连通性校验");

        assertThat(factory.capturedUserMessage).isEqualTo("ping");
        assertThat(factory.capturedConfig).isNotNull();
        assertThat(factory.capturedConfig.adapterType()).isEqualTo("VLLM");
        assertThat(factory.capturedConfig.baseUrl()).isEqualTo("http://59.1.100.19:8080");
        assertThat(factory.capturedConfig.completionsPath()).isEqualTo("/v1/chat/completions");
        assertThat(factory.capturedConfig.model()).isEqualTo("qwen3.5-397b");
        assertThat(factory.capturedConfig.maxTokens()).isEqualTo(1);
        assertThat(factory.capturedConfig.temperature()).isEqualTo(0.3D);
    }

    private static final class RecordingModelRuntimeClientFactory extends ModelRuntimeClientFactory {

        private ModelRuntimeConfigResolver.ResolvedChatModelConfig capturedConfig;
        private String capturedUserMessage;

        private RecordingModelRuntimeClientFactory() {
            super(null, null, null, null);
        }

        @Override
        public void validateChatConnectivity(
                ModelRuntimeConfigResolver.ResolvedChatModelConfig config, String userMessage) {
            this.capturedConfig = config;
            this.capturedUserMessage = userMessage;
        }
    }
}
