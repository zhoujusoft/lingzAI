package lingzhou.agent.backend.capability.modelruntime;

import lingzhou.agent.backend.app.ChatModelProperties;
import lingzhou.agent.backend.app.EmbeddingModelProperties;
import lingzhou.agent.backend.app.ModelProviderProperties;
import lingzhou.agent.backend.app.RagRerankProperties;
import lingzhou.agent.backend.business.model.domain.ModelAdapterType;
import lingzhou.agent.backend.business.model.domain.ModelCapabilityType;
import lingzhou.agent.backend.business.model.domain.ModelDefaultBinding;
import lingzhou.agent.backend.business.model.domain.ModelDefinition;
import lingzhou.agent.backend.business.model.domain.ModelVendor;
import lingzhou.agent.backend.business.model.mapper.ModelDefaultBindingMapper;
import lingzhou.agent.backend.business.model.mapper.ModelDefinitionMapper;
import lingzhou.agent.backend.business.model.mapper.ModelVendorMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelRuntimeConfigResolver {

    private final ModelDefaultBindingMapper modelDefaultBindingMapper;
    private final ModelDefinitionMapper modelDefinitionMapper;
    private final ModelVendorMapper modelVendorMapper;
    private final ChatModelProperties chatProperties;
    private final EmbeddingModelProperties embeddingProperties;
    private final ModelProviderProperties modelProviderProperties;
    private final RagRerankProperties rerankProperties;

    public ModelRuntimeConfigResolver(
            ModelDefaultBindingMapper modelDefaultBindingMapper,
            ModelDefinitionMapper modelDefinitionMapper,
            ModelVendorMapper modelVendorMapper,
            ChatModelProperties chatProperties,
            EmbeddingModelProperties embeddingProperties,
            ModelProviderProperties modelProviderProperties,
            RagRerankProperties rerankProperties) {
        this.modelDefaultBindingMapper = modelDefaultBindingMapper;
        this.modelDefinitionMapper = modelDefinitionMapper;
        this.modelVendorMapper = modelVendorMapper;
        this.chatProperties = chatProperties;
        this.embeddingProperties = embeddingProperties;
        this.modelProviderProperties = modelProviderProperties;
        this.rerankProperties = rerankProperties;
    }

    public ResolvedChatModelConfig resolveChatConfig() {
        return toChatConfig(requireActiveDefaultModel(ModelCapabilityType.CHAT));
    }

    public ResolvedEmbeddingModelConfig resolveEmbeddingConfig() {
        return toEmbeddingConfig(requireActiveDefaultModel(ModelCapabilityType.EMBEDDING));
    }

    public ResolvedRerankModelConfig resolveRerankConfig() {
        return toRerankConfig(requireActiveDefaultModel(ModelCapabilityType.RERANK));
    }

    private ModelDefinition requireActiveDefaultModel(ModelCapabilityType capabilityType) {
        ModelDefaultBinding binding = modelDefaultBindingMapper.selectByCapabilityType(capabilityType.name());
        if (binding == null || binding.getModelId() == null) {
            throw new IllegalStateException("未设置默认" + capabilityLabel(capabilityType) + "模型");
        }
        ModelDefinition model = modelDefinitionMapper.selectById(binding.getModelId());
        if (model == null) {
            throw new IllegalStateException("默认" + capabilityLabel(capabilityType) + "模型不存在：" + binding.getModelId());
        }
        if (!capabilityType.name().equalsIgnoreCase(model.getCapabilityType())) {
            throw new IllegalStateException("默认" + capabilityLabel(capabilityType) + "模型能力类型不匹配");
        }
        if (!"ACTIVE".equalsIgnoreCase(model.getStatus())) {
            throw new IllegalStateException("默认" + capabilityLabel(capabilityType) + "模型未启用：" + model.getId());
        }
        return model;
    }

    private ResolvedChatModelConfig toChatConfig(ModelDefinition model) {
        ModelVendor vendor = requireVendor(model);
        String adapterType = resolveAdapterType(model, vendor);
        ModelProviderProperties.VendorProperties providerProperties = modelProviderProperties.resolve(adapterType);
        ModelProviderProperties.ChatProperties providerChat = providerProperties.getChat();
        String baseUrl = resolveBaseUrl(model, vendor);
        String apiKey = resolveApiKey(model, vendor);
        requireRuntimeFields(baseUrl, apiKey, model.getModelName(), adapterType, "对话模型");
        return new ResolvedChatModelConfig(
                "DATABASE",
                ModelAdapterType.toChatProvider(adapterType),
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                baseUrl,
                apiKey,
                normalizePath(defaultChatPath(adapterType, model.getPath())),
                model.getModelName(),
                providerChat.getTemperature(),
                providerChat.getMaxTokens(),
                trimToEmpty(providerChat.getSystemPrompt()),
                providerChat.getEnableThinking());
    }

    private ResolvedEmbeddingModelConfig toEmbeddingConfig(ModelDefinition model) {
        ModelVendor vendor = requireVendor(model);
        String adapterType = resolveAdapterType(model, vendor);
        ModelProviderProperties.VendorProperties providerProperties = modelProviderProperties.resolve(adapterType);
        String baseUrl = resolveBaseUrl(model, vendor);
        String apiKey = resolveApiKey(model, vendor);
        requireRuntimeFields(baseUrl, apiKey, model.getModelName(), adapterType, "向量模型");
        return new ResolvedEmbeddingModelConfig(
                "DATABASE",
                adapterType,
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                baseUrl,
                apiKey,
                normalizePath(defaultEmbeddingsPath(model.getPath())),
                model.getModelName(),
                providerProperties.getEmbedding().getDimensions() != null
                        ? providerProperties.getEmbedding().getDimensions()
                        : embeddingProperties.getDimensions());
    }

    private ResolvedRerankModelConfig toRerankConfig(ModelDefinition model) {
        ModelVendor vendor = requireVendor(model);
        String adapterType = resolveAdapterType(model, vendor);
        ModelProviderProperties.VendorProperties providerProperties = modelProviderProperties.resolve(adapterType);
        ModelProviderProperties.RerankProperties providerRerank = providerProperties.getRerank();
        String baseUrl = resolveBaseUrl(model, vendor);
        String apiKey = resolveApiKey(model, vendor);
        requireRuntimeFields(baseUrl, apiKey, model.getModelName(), adapterType, "重排序模型");
        return new ResolvedRerankModelConfig(
                "DATABASE",
                adapterType,
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                model.getId(),
                Boolean.TRUE,
                baseUrl,
                apiKey,
                normalizePath(defaultRerankPath(adapterType, model.getPath())),
                model.getModelName(),
                StringUtils.hasText(providerRerank.getProtocol())
                        ? providerRerank.getProtocol().trim()
                        : defaultRerankProtocol(adapterType),
                providerRerank.getTimeoutMs() != null ? providerRerank.getTimeoutMs() : rerankProperties.getTimeoutMs(),
                providerRerank.getFallbackRrf() != null ? providerRerank.getFallbackRrf() : rerankProperties.getFallbackRrf());
    }

    private void requireRuntimeFields(String baseUrl, String apiKey, String modelName, String adapterType, String label) {
        boolean apiKeyRequired = !ModelAdapterType.VLLM.name().equalsIgnoreCase(trimToEmpty(adapterType));
        if (!StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(modelName)
                || (apiKeyRequired && !StringUtils.hasText(apiKey))) {
            throw new IllegalStateException(label + "配置不完整");
        }
    }

    private ModelVendor requireVendor(ModelDefinition model) {
        if (model == null || model.getVendorId() == null) {
            throw new IllegalStateException("模型厂商不存在");
        }
        ModelVendor vendor = modelVendorMapper.selectById(model.getVendorId());
        if (vendor == null) {
            throw new IllegalStateException("模型厂商不存在：" + model.getVendorId());
        }
        if (!"ACTIVE".equalsIgnoreCase(vendor.getStatus())) {
            throw new IllegalStateException("模型厂商未启用：" + model.getVendorId());
        }
        return vendor;
    }

    private String resolveBaseUrl(ModelDefinition model, ModelVendor vendor) {
        return firstNonBlank(model.getBaseUrl(), vendor == null ? "" : vendor.getDefaultBaseUrl());
    }

    private String resolveApiKey(ModelDefinition model, ModelVendor vendor) {
        return firstNonBlank(model.getApiKey(), vendor == null ? "" : vendor.getDefaultApiKey());
    }

    private String resolveAdapterType(ModelDefinition model, ModelVendor vendor) {
        if (StringUtils.hasText(model.getAdapterType())) {
            return normalizeAdapterType(model.getAdapterType());
        }
        if (vendor != null && "VLLM".equalsIgnoreCase(vendor.getVendorCode())) {
            return ModelAdapterType.VLLM.name();
        }
        return ModelAdapterType.QWEN_ONLINE.name();
    }

    private String capabilityLabel(ModelCapabilityType capabilityType) {
        if (capabilityType == null) {
            return "模型";
        }
        return switch (capabilityType) {
            case CHAT -> "对话";
            case EMBEDDING -> "向量";
            case RERANK -> "重排序";
        };
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

    private String defaultRerankProtocol(String adapterType) {
        return ModelAdapterType.VLLM.name().equalsIgnoreCase(adapterType) ? "vllm" : "dashscope";
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

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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
