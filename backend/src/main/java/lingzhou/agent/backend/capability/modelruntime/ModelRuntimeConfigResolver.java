package lingzhou.agent.backend.capability.modelruntime;

import lingzhou.agent.backend.app.ChatModelProperties;
import lingzhou.agent.backend.app.EmbeddingModelProperties;
import lingzhou.agent.backend.app.RagRerankProperties;
import lingzhou.agent.backend.business.model.domain.ModelAdapterType;
import lingzhou.agent.backend.business.model.domain.ModelCapabilityType;
import lingzhou.agent.backend.business.model.domain.ModelDefaultBinding;
import lingzhou.agent.backend.business.model.domain.ModelDefinition;
import lingzhou.agent.backend.business.model.mapper.ModelDefaultBindingMapper;
import lingzhou.agent.backend.business.model.mapper.ModelDefinitionMapper;
import lingzhou.agent.backend.business.model.service.ModelLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelRuntimeConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(ModelRuntimeConfigResolver.class);

    private final ModelDefaultBindingMapper modelDefaultBindingMapper;
    private final ModelDefinitionMapper modelDefinitionMapper;
    private final ChatModelProperties chatProperties;
    private final EmbeddingModelProperties embeddingProperties;
    private final RagRerankProperties rerankProperties;

    public ModelRuntimeConfigResolver(
            ModelDefaultBindingMapper modelDefaultBindingMapper,
            ModelDefinitionMapper modelDefinitionMapper,
            ChatModelProperties chatProperties,
            EmbeddingModelProperties embeddingProperties,
            RagRerankProperties rerankProperties) {
        this.modelDefaultBindingMapper = modelDefaultBindingMapper;
        this.modelDefinitionMapper = modelDefinitionMapper;
        this.chatProperties = chatProperties;
        this.embeddingProperties = embeddingProperties;
        this.rerankProperties = rerankProperties;
    }

    public ResolvedChatModelConfig resolveChatConfig() {
        try {
            ModelDefinition model = loadActiveDefaultModel(ModelCapabilityType.CHAT);
            if (model != null) {
                return toChatConfig(model);
            }
        } catch (Exception ex) {
            logFallback(ModelCapabilityType.CHAT, ex);
        }
        return fallbackChatConfig();
    }

    public ResolvedEmbeddingModelConfig resolveEmbeddingConfig() {
        try {
            ModelDefinition model = loadActiveDefaultModel(ModelCapabilityType.EMBEDDING);
            if (model != null) {
                return toEmbeddingConfig(model);
            }
        } catch (Exception ex) {
            logFallback(ModelCapabilityType.EMBEDDING, ex);
        }
        return fallbackEmbeddingConfig();
    }

    public ResolvedRerankModelConfig resolveRerankConfig() {
        try {
            ModelDefinition model = loadActiveDefaultModel(ModelCapabilityType.RERANK);
            if (model != null) {
                return toRerankConfig(model);
            }
        } catch (Exception ex) {
            logFallback(ModelCapabilityType.RERANK, ex);
        }
        return fallbackRerankConfig();
    }

    private ModelDefinition loadActiveDefaultModel(ModelCapabilityType capabilityType) {
        ModelDefaultBinding binding = modelDefaultBindingMapper.selectByCapabilityType(capabilityType.name());
        if (binding == null || binding.getModelId() == null) {
            return null;
        }
        ModelDefinition model = modelDefinitionMapper.selectById(binding.getModelId());
        if (model == null) {
            throw new IllegalStateException("默认模型不存在：" + binding.getModelId());
        }
        if (!capabilityType.name().equalsIgnoreCase(model.getCapabilityType())) {
            throw new IllegalStateException("默认模型能力类型不匹配：" + capabilityType.name());
        }
        if (!ModelLibraryService.STATUS_ACTIVE.equalsIgnoreCase(model.getStatus())) {
            throw new IllegalStateException("默认模型未启用：" + model.getId());
        }
        return model;
    }

    private ResolvedChatModelConfig toChatConfig(ModelDefinition model) {
        requireRuntimeFields(model, "对话模型");
        String adapterType = normalizeAdapterType(model.getAdapterType());
        return new ResolvedChatModelConfig(
                "DATABASE",
                ModelAdapterType.toChatProvider(adapterType),
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                model.getBaseUrl(),
                model.getApiKey(),
                normalizePath(defaultChatPath(adapterType, model.getPath())),
                model.getModelName(),
                model.getTemperature() != null ? model.getTemperature() : chatProperties.getTemperature(),
                model.getMaxTokens() != null ? model.getMaxTokens() : chatProperties.getMaxTokens(),
                StringUtils.hasText(model.getSystemPrompt()) ? model.getSystemPrompt().trim() : chatProperties.getSystemPrompt(),
                model.getEnableThinking() != null ? model.getEnableThinking() : chatProperties.getEnableThinking());
    }

    private ResolvedEmbeddingModelConfig toEmbeddingConfig(ModelDefinition model) {
        requireRuntimeFields(model, "向量模型");
        String adapterType = normalizeAdapterType(model.getAdapterType());
        return new ResolvedEmbeddingModelConfig(
                "DATABASE",
                adapterType,
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                model.getBaseUrl(),
                model.getApiKey(),
                normalizePath(defaultEmbeddingsPath(model.getPath())),
                model.getModelName(),
                model.getDimensions() != null ? model.getDimensions() : embeddingProperties.getDimensions());
    }

    private ResolvedRerankModelConfig toRerankConfig(ModelDefinition model) {
        requireRuntimeFields(model, "重排序模型");
        String adapterType = normalizeAdapterType(model.getAdapterType());
        return new ResolvedRerankModelConfig(
                "DATABASE",
                adapterType,
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                Boolean.TRUE,
                model.getBaseUrl(),
                model.getApiKey(),
                normalizePath(defaultRerankPath(adapterType, model.getPath())),
                model.getModelName(),
                StringUtils.hasText(model.getProtocol())
                        ? model.getProtocol().trim()
                        : (ModelAdapterType.VLLM.name().equals(adapterType) ? "vllm" : "dashscope"),
                model.getTimeoutMs() != null ? model.getTimeoutMs() : rerankProperties.getTimeoutMs(),
                model.getFallbackRrf() != null ? model.getFallbackRrf() : rerankProperties.getFallbackRrf());
    }

    private ResolvedChatModelConfig fallbackChatConfig() {
        return new ResolvedChatModelConfig(
                "YAML",
                chatProperties.getProvider(),
                "YAML Chat",
                null,
                chatProperties.getBaseUrl(),
                chatProperties.getApiKey(),
                chatProperties.getCompletionsPath(),
                chatProperties.getModel(),
                chatProperties.getTemperature(),
                chatProperties.getMaxTokens(),
                chatProperties.getSystemPrompt(),
                chatProperties.getEnableThinking());
    }

    private ResolvedEmbeddingModelConfig fallbackEmbeddingConfig() {
        String baseUrl = StringUtils.hasText(embeddingProperties.getBaseUrl())
                ? embeddingProperties.getBaseUrl()
                : chatProperties.getBaseUrl();
        String apiKey = StringUtils.hasText(embeddingProperties.getApiKey())
                ? embeddingProperties.getApiKey()
                : chatProperties.getApiKey();
        String model = StringUtils.hasText(embeddingProperties.getModel())
                ? embeddingProperties.getModel()
                : chatProperties.getModel();
        return new ResolvedEmbeddingModelConfig(
                "YAML",
                chatProperties.getProvider(),
                "YAML Embedding",
                null,
                baseUrl,
                apiKey,
                normalizePath(defaultEmbeddingsPath(embeddingProperties.getEmbeddingsPath())),
                model,
                embeddingProperties.getDimensions());
    }

    private ResolvedRerankModelConfig fallbackRerankConfig() {
        return new ResolvedRerankModelConfig(
                "YAML",
                chatProperties.getProvider(),
                "YAML Rerank",
                null,
                rerankProperties.getEnabled(),
                rerankProperties.getBaseUrl(),
                rerankProperties.getApiKey(),
                normalizePath(defaultRerankPath(chatProperties.getProvider(), rerankProperties.getPath())),
                rerankProperties.getModel(),
                rerankProperties.getProtocol(),
                rerankProperties.getTimeoutMs(),
                rerankProperties.getFallbackRrf());
    }

    private void requireRuntimeFields(ModelDefinition model, String label) {
        if (!StringUtils.hasText(model.getBaseUrl())
                || !StringUtils.hasText(model.getApiKey())
                || !StringUtils.hasText(model.getModelName())) {
            throw new IllegalStateException(label + "配置不完整");
        }
    }

    private void logFallback(ModelCapabilityType capabilityType, Exception ex) {
        logger.warn(
                "默认模型解析失败，回退 YAML：capabilityType={}, error={}",
                capabilityType.name(),
                ex.getMessage());
    }

    private String defaultChatPath(String adapterType, String path) {
        if (StringUtils.hasText(path)) {
            return path;
        }
        return ModelAdapterType.QWEN_ONLINE.name().equalsIgnoreCase(adapterType) ? "/v1/chat/completions" : "";
    }

    private String defaultEmbeddingsPath(String path) {
        return StringUtils.hasText(path) ? path : "/v1/embeddings";
    }

    private String defaultRerankPath(String adapterType, String path) {
        if (StringUtils.hasText(path)) {
            return path;
        }
        return ModelAdapterType.VLLM.name().equalsIgnoreCase(adapterType)
                ? "/v1/rerank"
                : "/api/v1/services/rerank/text-rerank/text-rerank";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : "";
    }

    private String normalizeAdapterType(String value) {
        if (!StringUtils.hasText(value)) {
            return ModelAdapterType.QWEN_ONLINE.name();
        }
        return value.trim().toUpperCase().replace('-', '_');
    }

    public record ResolvedChatModelConfig(
            String source,
            String provider,
            String displayName,
            Long modelId,
            String baseUrl,
            String apiKey,
            String completionsPath,
            String model,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking) {}

    public record ResolvedEmbeddingModelConfig(
            String source,
            String adapterType,
            String displayName,
            Long modelId,
            String baseUrl,
            String apiKey,
            String embeddingsPath,
            String model,
            Integer dimensions) {}

    public record ResolvedRerankModelConfig(
            String source,
            String adapterType,
            String displayName,
            Long modelId,
            Boolean enabled,
            String baseUrl,
            String apiKey,
            String path,
            String model,
            String protocol,
            Integer timeoutMs,
            Boolean fallbackRrf) {}
}
