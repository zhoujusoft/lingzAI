package lingzhou.agent.backend.business.model.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.model.domain.ModelAdapterType;
import lingzhou.agent.backend.business.model.domain.ModelCapabilityType;
import lingzhou.agent.backend.business.model.domain.ModelDefaultBinding;
import lingzhou.agent.backend.business.model.domain.ModelDefinition;
import lingzhou.agent.backend.business.model.domain.ModelVendor;
import lingzhou.agent.backend.business.model.mapper.ModelDefaultBindingMapper;
import lingzhou.agent.backend.business.model.mapper.ModelDefinitionMapper;
import lingzhou.agent.backend.business.model.mapper.ModelVendorMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ModelLibraryService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DRAFT = "DRAFT";

    private static final Logger logger = LoggerFactory.getLogger(ModelLibraryService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> SUPPORTED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_DRAFT);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModelVendorMapper modelVendorMapper;
    private final ModelDefinitionMapper modelDefinitionMapper;
    private final ModelDefaultBindingMapper modelDefaultBindingMapper;
    private final SysUserMapper sysUserMapper;

    public ModelLibraryService(
            ModelVendorMapper modelVendorMapper,
            ModelDefinitionMapper modelDefinitionMapper,
            ModelDefaultBindingMapper modelDefaultBindingMapper,
            SysUserMapper sysUserMapper) {
        this.modelVendorMapper = modelVendorMapper;
        this.modelDefinitionMapper = modelDefinitionMapper;
        this.modelDefaultBindingMapper = modelDefaultBindingMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public List<VendorView> listVendors(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, Long> modelCountByVendor = buildModelCountByVendor();
        return modelVendorMapper.selectAllOrdered().stream()
                .map(vendor -> toVendorView(vendor, modelCountByVendor.getOrDefault(vendor.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public VendorView createVendor(Long operatorUserId, UpsertVendorRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        NormalizedVendor normalized = normalizeVendorRequest(request);
        ModelVendor existing = modelVendorMapper.selectByVendorCode(normalized.vendorCode());
        if (existing != null) {
            throw new TaskException("模型厂商编码已存在：" + normalized.vendorCode(), TaskException.Code.UNKNOWN);
        }
        ModelVendor entity = new ModelVendor();
        applyVendor(entity, normalized);
        modelVendorMapper.insert(entity);
        logger.info("模型厂商创建成功：vendorCode={}, operatorUserId={}", entity.getVendorCode(), operator.getId());
        return toVendorView(entity, 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public VendorView updateVendor(Long operatorUserId, Long id, UpsertVendorRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        ModelVendor existing = requireVendor(id);
        NormalizedVendor normalized = normalizeVendorRequest(request);
        ModelVendor sameCode = modelVendorMapper.selectByVendorCode(normalized.vendorCode());
        if (sameCode != null && !sameCode.getId().equals(existing.getId())) {
            throw new TaskException("模型厂商编码已存在：" + normalized.vendorCode(), TaskException.Code.UNKNOWN);
        }
        applyVendor(existing, normalized);
        modelVendorMapper.updateById(existing);
        logger.info("模型厂商更新成功：vendorId={}, operatorUserId={}", existing.getId(), operator.getId());
        return toVendorView(existing, countModelsByVendor(existing.getId()));
    }

    public List<ModelView> listModels(Long operatorUserId, String keyword, String capabilityType, Long vendorId, String status)
            throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, ModelVendor> vendorById = buildVendorById();
        Map<String, Long> defaultModelIdByCapability = buildDefaultModelIdByCapability();
        return modelDefinitionMapper.search(keyword, capabilityType, vendorId, status).stream()
                .map(model -> toModelView(model, vendorById.get(model.getVendorId()), defaultModelIdByCapability))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelView createModel(Long operatorUserId, UpsertModelRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        NormalizedModel normalized = normalizeModelRequest(request, null);
        ModelDefinition existing = modelDefinitionMapper.selectByModelCode(normalized.modelCode());
        if (existing != null) {
            throw new TaskException("模型编码已存在：" + normalized.modelCode(), TaskException.Code.UNKNOWN);
        }
        ModelDefinition entity = new ModelDefinition();
        applyModel(entity, normalized);
        modelDefinitionMapper.insert(entity);
        logger.info("模型定义创建成功：modelCode={}, operatorUserId={}", entity.getModelCode(), operator.getId());
        return toModelView(entity, requireVendor(entity.getVendorId()), buildDefaultModelIdByCapability());
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelView updateModel(Long operatorUserId, Long id, UpsertModelRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        ModelDefinition existing = requireModel(id);
        NormalizedModel normalized = normalizeModelRequest(request, existing);
        ModelDefinition sameCode = modelDefinitionMapper.selectByModelCode(normalized.modelCode());
        if (sameCode != null && !sameCode.getId().equals(existing.getId())) {
            throw new TaskException("模型编码已存在：" + normalized.modelCode(), TaskException.Code.UNKNOWN);
        }
        applyModel(existing, normalized);
        modelDefinitionMapper.updateById(existing);
        logger.info("模型定义更新成功：modelId={}, operatorUserId={}", existing.getId(), operator.getId());
        return toModelView(existing, requireVendor(existing.getVendorId()), buildDefaultModelIdByCapability());
    }

    public List<DefaultBindingView> listDefaults(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, ModelDefinition> modelById = buildModelById();
        Map<Long, ModelVendor> vendorById = buildVendorById();
        Map<String, ModelDefaultBinding> bindingByCapability = new LinkedHashMap<>();
        for (ModelDefaultBinding binding : modelDefaultBindingMapper.selectAllOrdered()) {
            bindingByCapability.put(binding.getCapabilityType(), binding);
        }
        return ModelCapabilityType.orderedValues().stream()
                .map(capabilityType -> toDefaultBindingView(
                        capabilityType.name(),
                        bindingByCapability.get(capabilityType.name()),
                        modelById,
                        vendorById))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DefaultBindingView saveDefaultBinding(Long operatorUserId, String capabilityType, DefaultBindingRequest request)
            throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        String normalizedCapabilityType = ModelCapabilityType.normalize(capabilityType);
        Long modelId = request == null ? null : request.modelId();
        ModelDefaultBinding existing = modelDefaultBindingMapper.selectByCapabilityType(normalizedCapabilityType);

        if (modelId == null) {
            if (existing != null) {
                modelDefaultBindingMapper.deleteById(existing.getId());
            }
            logger.info("默认模型已清空：capabilityType={}, operatorUserId={}", normalizedCapabilityType, operator.getId());
            return new DefaultBindingView(normalizedCapabilityType, null, null, null, null, null, null);
        }

        ModelDefinition model = requireModel(modelId);
        if (!normalizedCapabilityType.equalsIgnoreCase(model.getCapabilityType())) {
            throw new TaskException("默认模型能力类型不匹配", TaskException.Code.UNKNOWN);
        }
        if (!STATUS_ACTIVE.equals(normalizeStatus(model.getStatus()))) {
            throw new TaskException("仅启用状态的模型可设为默认模型", TaskException.Code.UNKNOWN);
        }

        if (existing == null) {
            existing = new ModelDefaultBinding();
            existing.setCapabilityType(normalizedCapabilityType);
            existing.setModelId(model.getId());
            modelDefaultBindingMapper.insert(existing);
        } else {
            existing.setModelId(model.getId());
            modelDefaultBindingMapper.updateById(existing);
        }

        ModelVendor vendor = requireVendor(model.getVendorId());
        logger.info(
                "默认模型更新成功：capabilityType={}, modelId={}, operatorUserId={}",
                normalizedCapabilityType,
                model.getId(),
                operator.getId());
        return new DefaultBindingView(
                normalizedCapabilityType,
                model.getId(),
                model.getDisplayName(),
                vendor.getId(),
                vendor.getVendorName(),
                model.getAdapterType(),
                normalizeStatus(model.getStatus()));
    }

    private Map<Long, Long> buildModelCountByVendor() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (ModelDefinition model : modelDefinitionMapper.search(null, null, null, null)) {
            if (model.getVendorId() == null) {
                continue;
            }
            counts.merge(model.getVendorId(), 1L, Long::sum);
        }
        return counts;
    }

    private int countModelsByVendor(Long vendorId) {
        if (vendorId == null) {
            return 0;
        }
        int count = 0;
        for (ModelDefinition model : modelDefinitionMapper.search(null, null, vendorId, null)) {
            count++;
        }
        return count;
    }

    private Map<Long, ModelVendor> buildVendorById() {
        Map<Long, ModelVendor> vendorById = new LinkedHashMap<>();
        for (ModelVendor vendor : modelVendorMapper.selectAllOrdered()) {
            vendorById.put(vendor.getId(), vendor);
        }
        return vendorById;
    }

    private Map<Long, ModelDefinition> buildModelById() {
        Map<Long, ModelDefinition> modelById = new LinkedHashMap<>();
        for (ModelDefinition model : modelDefinitionMapper.search(null, null, null, null)) {
            modelById.put(model.getId(), model);
        }
        return modelById;
    }

    private Map<String, Long> buildDefaultModelIdByCapability() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ModelDefaultBinding binding : modelDefaultBindingMapper.selectAllOrdered()) {
            if (binding.getModelId() != null) {
                result.put(binding.getCapabilityType(), binding.getModelId());
            }
        }
        return result;
    }

    private VendorView toVendorView(ModelVendor vendor, int modelCount) {
        return new VendorView(
                vendor.getId(),
                vendor.getVendorCode(),
                vendor.getVendorName(),
                trimText(vendor.getDescription()),
                normalizeStatus(vendor.getStatus()),
                modelCount,
                vendor.getCreatedAt(),
                vendor.getUpdatedAt());
    }

    private ModelView toModelView(ModelDefinition model, ModelVendor vendor, Map<String, Long> defaultModelIdByCapability) {
        boolean isDefault = model != null
                && defaultModelIdByCapability != null
                && model.getId() != null
                && model.getId().equals(defaultModelIdByCapability.get(model.getCapabilityType()));
        return new ModelView(
                model.getId(),
                model.getModelCode(),
                model.getDisplayName(),
                model.getCapabilityType(),
                model.getVendorId(),
                vendor == null ? "" : vendor.getVendorName(),
                model.getAdapterType(),
                trimText(model.getProtocol()),
                trimText(model.getBaseUrl()),
                trimText(model.getPath()),
                trimText(model.getModelName()),
                model.getTemperature(),
                model.getMaxTokens(),
                trimText(model.getSystemPrompt()),
                model.getEnableThinking(),
                model.getDimensions(),
                model.getTimeoutMs(),
                model.getFallbackRrf(),
                trimText(model.getExtraConfigJson()),
                normalizeStatus(model.getStatus()),
                StringUtils.hasText(model.getApiKey()),
                isDefault,
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    private DefaultBindingView toDefaultBindingView(
            String capabilityType,
            ModelDefaultBinding binding,
            Map<Long, ModelDefinition> modelById,
            Map<Long, ModelVendor> vendorById) {
        if (binding == null || binding.getModelId() == null) {
            return new DefaultBindingView(capabilityType, null, null, null, null, null, null);
        }
        ModelDefinition model = modelById.get(binding.getModelId());
        if (model == null) {
            return new DefaultBindingView(capabilityType, binding.getModelId(), null, null, null, null, "MISSING");
        }
        ModelVendor vendor = vendorById.get(model.getVendorId());
        return new DefaultBindingView(
                capabilityType,
                model.getId(),
                model.getDisplayName(),
                vendor == null ? null : vendor.getId(),
                vendor == null ? "" : vendor.getVendorName(),
                model.getAdapterType(),
                normalizeStatus(model.getStatus()));
    }

    private NormalizedVendor normalizeVendorRequest(UpsertVendorRequest request) throws TaskException {
        if (request == null) {
            throw new TaskException("模型厂商请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        return new NormalizedVendor(
                normalizeCode(request.vendorCode(), "模型厂商编码不能为空"),
                requireText(request.vendorName(), "模型厂商名称不能为空"),
                trimText(request.description()),
                normalizeStatus(request.status()));
    }

    private NormalizedModel normalizeModelRequest(UpsertModelRequest request, ModelDefinition existing) throws TaskException {
        if (request == null) {
            throw new TaskException("模型请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        Long vendorId = request.vendorId();
        if (vendorId == null || vendorId <= 0) {
            throw new TaskException("模型厂商不能为空", TaskException.Code.UNKNOWN);
        }
        requireVendor(vendorId);
        String capabilityType = ModelCapabilityType.normalize(request.capabilityType());
        String adapterType = ModelAdapterType.normalize(request.adapterType());
        String baseUrl = validateUrl(requireText(request.baseUrl(), "Base URL 不能为空"));
        String apiKey = resolveApiKey(request.apiKey(), existing);
        if (!StringUtils.hasText(apiKey)) {
            throw new TaskException("API Key 不能为空", TaskException.Code.UNKNOWN);
        }
        String modelName = requireText(request.modelName(), "模型名不能为空");
        String path = normalizePath(defaultPath(capabilityType, adapterType, request.path()));
        String protocol = normalizeProtocol(capabilityType, adapterType, request.protocol());
        String extraConfigJson = normalizeExtraConfigJson(request.extraConfigJson());

        return new NormalizedModel(
                normalizeCode(request.modelCode(), "模型编码不能为空"),
                requireText(request.displayName(), "模型展示名称不能为空"),
                capabilityType,
                vendorId,
                adapterType,
                protocol,
                baseUrl,
                apiKey,
                path,
                modelName,
                request.temperature(),
                request.maxTokens(),
                trimText(request.systemPrompt()),
                request.enableThinking(),
                request.dimensions(),
                request.timeoutMs(),
                request.fallbackRrf(),
                extraConfigJson,
                normalizeStatus(request.status()));
    }

    private void applyVendor(ModelVendor entity, NormalizedVendor normalized) {
        entity.setVendorCode(normalized.vendorCode());
        entity.setVendorName(normalized.vendorName());
        entity.setDescription(normalized.description());
        entity.setStatus(normalized.status());
    }

    private void applyModel(ModelDefinition entity, NormalizedModel normalized) {
        entity.setModelCode(normalized.modelCode());
        entity.setDisplayName(normalized.displayName());
        entity.setCapabilityType(normalized.capabilityType());
        entity.setVendorId(normalized.vendorId());
        entity.setAdapterType(normalized.adapterType());
        entity.setProtocol(normalized.protocol());
        entity.setBaseUrl(normalized.baseUrl());
        entity.setApiKey(normalized.apiKey());
        entity.setPath(normalized.path());
        entity.setModelName(normalized.modelName());
        entity.setTemperature(normalized.capabilityType().equals(ModelCapabilityType.CHAT.name()) ? normalized.temperature() : null);
        entity.setMaxTokens(normalized.capabilityType().equals(ModelCapabilityType.CHAT.name()) ? normalized.maxTokens() : null);
        entity.setSystemPrompt(normalized.capabilityType().equals(ModelCapabilityType.CHAT.name()) ? normalized.systemPrompt() : null);
        entity.setEnableThinking(normalized.capabilityType().equals(ModelCapabilityType.CHAT.name()) ? normalized.enableThinking() : null);
        entity.setDimensions(normalized.capabilityType().equals(ModelCapabilityType.EMBEDDING.name()) ? normalized.dimensions() : null);
        entity.setTimeoutMs(normalized.capabilityType().equals(ModelCapabilityType.RERANK.name()) ? normalized.timeoutMs() : null);
        entity.setFallbackRrf(normalized.capabilityType().equals(ModelCapabilityType.RERANK.name()) ? normalized.fallbackRrf() : null);
        entity.setExtraConfigJson(normalized.extraConfigJson());
        entity.setStatus(normalized.status());
    }

    private String resolveApiKey(String rawApiKey, ModelDefinition existing) {
        if (StringUtils.hasText(rawApiKey)) {
            return rawApiKey.trim();
        }
        if (existing == null) {
            return "";
        }
        return trimText(existing.getApiKey());
    }

    private SysUserModel requireAdmin(Long operatorUserId) throws TaskException {
        if (operatorUserId == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        if (operator.getUserType() == null || !operator.getUserType().equals(UserType.admin.getValue())) {
            throw new TaskException("普通用户不可管理模型库", TaskException.Code.UNKNOWN);
        }
        return operator;
    }

    private ModelVendor requireVendor(Long id) throws TaskException {
        if (id == null || id <= 0) {
            throw new TaskException("模型厂商 id 不能为空", TaskException.Code.UNKNOWN);
        }
        ModelVendor vendor = modelVendorMapper.selectById(id);
        if (vendor == null) {
            throw new TaskException("模型厂商不存在：" + id, TaskException.Code.UNKNOWN);
        }
        return vendor;
    }

    private ModelDefinition requireModel(Long id) throws TaskException {
        if (id == null || id <= 0) {
            throw new TaskException("模型 id 不能为空", TaskException.Code.UNKNOWN);
        }
        ModelDefinition model = modelDefinitionMapper.selectById(id);
        if (model == null) {
            throw new TaskException("模型不存在：" + id, TaskException.Code.UNKNOWN);
        }
        return model;
    }

    private String normalizeCode(String value, String message) throws TaskException {
        String normalized = requireText(value, message);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new TaskException("编码仅支持字母、数字、点、下划线和中划线", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : STATUS_ACTIVE;
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            return STATUS_ACTIVE;
        }
        return normalized;
    }

    private String normalizeProtocol(String capabilityType, String adapterType, String protocol) {
        if (!ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return trimText(protocol);
        }
        if (StringUtils.hasText(protocol)) {
            return protocol.trim();
        }
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

    private String defaultPath(String capabilityType, String adapterType, String rawPath) {
        if (StringUtils.hasText(rawPath)) {
            return rawPath;
        }
        if (ModelCapabilityType.CHAT.name().equals(capabilityType)) {
            return ModelAdapterType.QWEN_ONLINE.name().equals(adapterType) ? "/v1/chat/completions" : "";
        }
        if (ModelCapabilityType.EMBEDDING.name().equals(capabilityType)) {
            return "/v1/embeddings";
        }
        if (ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return ModelAdapterType.VLLM.name().equals(adapterType)
                    ? "/v1/rerank"
                    : "/api/v1/services/rerank/text-rerank/text-rerank";
        }
        return "";
    }

    private String normalizeExtraConfigJson(String value) throws TaskException {
        String normalized = trimText(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        try {
            JSON.readTree(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new TaskException("额外配置必须是合法 JSON", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String validateUrl(String value) throws TaskException {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new TaskException("Base URL 格式不正确", TaskException.Code.UNKNOWN, ex);
        }
        if (!StringUtils.hasText(uri.getScheme())
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new TaskException("Base URL 仅支持 http 或 https", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new TaskException("Base URL 必须包含合法主机名", TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String requireText(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String trimText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record VendorView(
            Long id,
            String vendorCode,
            String vendorName,
            String description,
            String status,
            Integer modelCount,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record UpsertVendorRequest(
            String vendorCode,
            String vendorName,
            String description,
            String status) {}

    public record ModelView(
            Long id,
            String modelCode,
            String displayName,
            String capabilityType,
            Long vendorId,
            String vendorName,
            String adapterType,
            String protocol,
            String baseUrl,
            String path,
            String modelName,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking,
            Integer dimensions,
            Integer timeoutMs,
            Boolean fallbackRrf,
            String extraConfigJson,
            String status,
            Boolean apiKeyConfigured,
            Boolean defaultModel,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record UpsertModelRequest(
            String modelCode,
            String displayName,
            String capabilityType,
            Long vendorId,
            String adapterType,
            String protocol,
            String baseUrl,
            String apiKey,
            String path,
            String modelName,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking,
            Integer dimensions,
            Integer timeoutMs,
            Boolean fallbackRrf,
            String extraConfigJson,
            String status) {}

    public record DefaultBindingRequest(Long modelId) {}

    public record DefaultBindingView(
            String capabilityType,
            Long modelId,
            String modelDisplayName,
            Long vendorId,
            String vendorName,
            String adapterType,
            String modelStatus) {}

    private record NormalizedVendor(
            String vendorCode,
            String vendorName,
            String description,
            String status) {}

    private record NormalizedModel(
            String modelCode,
            String displayName,
            String capabilityType,
            Long vendorId,
            String adapterType,
            String protocol,
            String baseUrl,
            String apiKey,
            String path,
            String modelName,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking,
            Integer dimensions,
            Integer timeoutMs,
            Boolean fallbackRrf,
            String extraConfigJson,
            String status) {}
}
