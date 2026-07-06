package lingzhou.agent.backend.business.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class ModelLibraryService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DRAFT = "DRAFT";

    public static final String VENDOR_QWEN = "QWEN_ONLINE";
    public static final String VENDOR_VLLM = "VLLM";

    private static final Logger logger = LoggerFactory.getLogger(ModelLibraryService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> SUPPORTED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_DRAFT);
    private static final List<String> BUILTIN_VENDOR_CODES = List.of(VENDOR_QWEN, VENDOR_VLLM);
    private static final Set<String> BUILTIN_MODEL_CODES = Set.of(
            "qwen-chat-default",
            "qwen-chat-plus",
            "qwen-chat-turbo",
            "qwen-chat-long",
            "qwen-embedding-default",
            "qwen-rerank-default");

    private final ModelVendorMapper modelVendorMapper;
    private final ModelDefinitionMapper modelDefinitionMapper;
    private final ModelDefaultBindingMapper modelDefaultBindingMapper;
    private final SysUserMapper sysUserMapper;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;

    public ModelLibraryService(
            ModelVendorMapper modelVendorMapper,
            ModelDefinitionMapper modelDefinitionMapper,
            ModelDefaultBindingMapper modelDefaultBindingMapper,
            SysUserMapper sysUserMapper,
            ModelRuntimeClientFactory modelRuntimeClientFactory) {
        this.modelVendorMapper = modelVendorMapper;
        this.modelDefinitionMapper = modelDefinitionMapper;
        this.modelDefaultBindingMapper = modelDefaultBindingMapper;
        this.sysUserMapper = sysUserMapper;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
    }

    public List<VendorView> listVendors(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, Integer> modelCountByVendor = buildModelCountByVendor();
        return listBuiltinVendors().stream()
                .map(vendor -> toVendorView(vendor, modelCountByVendor.getOrDefault(vendor.getId(), 0)))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public VendorView updateVendor(Long operatorUserId, Long id, UpsertVendorRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        ModelVendor vendor = requireBuiltinVendor(id);
        NormalizedVendor normalized = normalizeVendorRequest(request, vendor);
        vendor.setDefaultBaseUrl(normalized.defaultBaseUrl());
        vendor.setDefaultApiKey(normalized.defaultApiKey());
        vendor.setStatus(normalized.status());
        modelVendorMapper.updateById(vendor);
        logger.info("模型厂商配置已更新：vendorCode={}, operatorUserId={}", vendor.getVendorCode(), operator.getId());
        return toVendorView(vendor, countModelsByVendor(vendor.getId()));
    }

    public VendorValidationView validateVendor(Long operatorUserId, Long id, UpsertVendorRequest request)
            throws TaskException {
        requireAdmin(operatorUserId);
        ModelVendor vendor = requireBuiltinVendor(id);
        NormalizedVendor normalized = normalizeVendorRequest(request, vendor);
        String effectiveApiKey = trimText(normalized.defaultApiKey());
        if (!StringUtils.hasText(effectiveApiKey) && !VENDOR_VLLM.equals(trimText(vendor.getVendorCode()))) {
            throw new TaskException("请先配置可用的默认 API Key", TaskException.Code.UNKNOWN);
        }
        String effectiveBaseUrl = resolveVendorValidationBaseUrl(vendor, normalized);
        performVendorValidation(vendor, effectiveBaseUrl, effectiveApiKey, "厂商连通性校验");
        return new VendorValidationView(
                vendor.getId(),
                vendor.getVendorCode(),
                effectiveBaseUrl,
                StringUtils.hasText(effectiveApiKey) ? "API Key 校验通过" : "连接校验通过");
    }

    public List<ModelView> listModels(
            Long operatorUserId, String keyword, String capabilityType, Long vendorId, String status)
            throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, ModelVendor> vendorById = buildBuiltinVendorById();
        Map<String, Long> defaultModelIdByCapability = buildDefaultModelIdByCapability();
        return modelDefinitionMapper.search(keyword, capabilityType, vendorId, status).stream()
                .filter(model -> vendorById.containsKey(model.getVendorId()))
                .map(model -> toModelView(model, vendorById.get(model.getVendorId()), defaultModelIdByCapability))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelView createModel(Long operatorUserId, UpsertModelRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        NormalizedModel normalized = normalizeModelRequest(request, null);
        validateModelConnectivity(normalized);
        ModelDefinition entity = new ModelDefinition();
        applyModel(entity, normalized);
        modelDefinitionMapper.insert(entity);
        ModelVendor vendor = requireBuiltinVendor(entity.getVendorId());
        logger.info(
                "模型定义创建成功：modelCode={}, vendorCode={}, operatorUserId={}",
                entity.getModelCode(),
                vendor.getVendorCode(),
                operator.getId());
        return toModelView(entity, vendor, buildDefaultModelIdByCapability());
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelView updateModel(Long operatorUserId, Long id, UpsertModelRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        ModelDefinition existing = requireModel(id);
        NormalizedModel normalized = normalizeModelRequest(request, existing);
        validateModelConnectivity(normalized);
        applyModel(existing, normalized);
        modelDefinitionMapper.updateById(existing);
        ModelVendor vendor = requireBuiltinVendor(existing.getVendorId());
        logger.info(
                "模型定义更新成功：modelId={}, vendorCode={}, operatorUserId={}",
                existing.getId(),
                vendor.getVendorCode(),
                operator.getId());
        return toModelView(existing, vendor, buildDefaultModelIdByCapability());
    }

    public ValidationResult validateModel(Long operatorUserId, UpsertModelRequest request) throws TaskException {
        requireAdmin(operatorUserId);
        ModelDefinition existing = request != null && request.modelId() != null && request.modelId() > 0
                ? requireModel(request.modelId())
                : null;
        NormalizedModel normalized = normalizeModelRequest(request, existing);
        validateModelConnectivity(normalized);
        ModelVendor vendor = requireBuiltinVendor(normalized.vendorId());
        String effectiveBaseUrl = firstNonBlank(normalized.baseUrl(), vendor.getDefaultBaseUrl());
        return new ValidationResult(vendor.getId(), vendor.getVendorCode(), effectiveBaseUrl, "模型连通性校验通过");
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long operatorUserId, Long id) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        ModelDefinition model = requireModel(id);
        if (BUILTIN_MODEL_CODES.contains(trimText(model.getModelCode()))) {
            throw new TaskException("系统内置模型不支持删除", TaskException.Code.UNKNOWN);
        }
        ModelDefaultBinding defaultBinding =
                modelDefaultBindingMapper.selectByCapabilityType(model.getCapabilityType());
        if (defaultBinding != null && model.getId().equals(defaultBinding.getModelId())) {
            throw new TaskException("当前模型已被设为默认模型，请先取消默认绑定后再删除", TaskException.Code.UNKNOWN);
        }
        modelDefinitionMapper.deleteById(model.getId());
        logger.info("模型定义删除成功：modelId={}, operatorUserId={}", model.getId(), operator.getId());
    }

    public List<DefaultBindingView> listDefaults(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        Map<Long, ModelDefinition> modelById = buildBuiltinModelById();
        Map<Long, ModelVendor> vendorById = buildBuiltinVendorById();
        Map<String, ModelDefaultBinding> bindingByCapability = new LinkedHashMap<>();
        for (ModelDefaultBinding binding : modelDefaultBindingMapper.selectAllOrdered()) {
            bindingByCapability.put(binding.getCapabilityType(), binding);
        }
        return ModelCapabilityType.orderedValues().stream()
                .map(capabilityType -> toDefaultBindingView(
                        capabilityType.name(), bindingByCapability.get(capabilityType.name()), modelById, vendorById))
                .toList();
    }

    public List<ChatModelOptionView> listAvailableChatModels() {
        Map<Long, ModelVendor> vendorById = buildBuiltinVendorById();
        Map<String, Long> defaultModelIdByCapability = buildDefaultModelIdByCapability();
        Long defaultChatModelId = defaultModelIdByCapability.get(ModelCapabilityType.CHAT.name());
        return modelDefinitionMapper.search(null, ModelCapabilityType.CHAT.name(), null, STATUS_ACTIVE).stream()
                .filter(model -> isAvailableChatModel(model, vendorById.get(model.getVendorId())))
                .map(model -> toChatModelOptionView(model, vendorById.get(model.getVendorId()), defaultChatModelId))
                .toList();
    }

    public ChatModelReferenceView resolveChatModelReference(Long modelId) {
        Long defaultChatModelId = buildDefaultModelIdByCapability().get(ModelCapabilityType.CHAT.name());
        Map<Long, ModelVendor> vendorById = buildBuiltinVendorById();
        if (modelId == null || modelId <= 0) {
            if (defaultChatModelId == null) {
                return new ChatModelReferenceView(null, "", false, true);
            }
            return resolveChatModelReference(defaultChatModelId);
        }
        ModelDefinition model = modelDefinitionMapper.selectById(modelId);
        if (model == null) {
            return new ChatModelReferenceView(modelId, "当前模型不可用", false, false);
        }
        ModelVendor vendor = vendorById.get(model.getVendorId());
        boolean available = isAvailableChatModel(model, vendor);
        return new ChatModelReferenceView(
                model.getId(),
                firstNonBlank(model.getDisplayName(), model.getModelCode()),
                available,
                defaultChatModelId != null && defaultChatModelId.equals(model.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public DefaultBindingView saveDefaultBinding(
            Long operatorUserId, String capabilityType, DefaultBindingRequest request) throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        String normalizedCapabilityType = ModelCapabilityType.normalize(capabilityType);
        Long modelId = request == null ? null : request.modelId();
        ModelDefaultBinding existing = modelDefaultBindingMapper.selectByCapabilityType(normalizedCapabilityType);

        if (modelId == null) {
            if (existing != null) {
                modelDefaultBindingMapper.deleteById(existing.getId());
            }
            logger.info("默认模型已清空：capabilityType={}, operatorUserId={}", normalizedCapabilityType, operator.getId());
            return new DefaultBindingView(normalizedCapabilityType, null, null, null, null, null);
        }

        ModelDefinition model = requireModel(modelId);
        ModelVendor vendor = requireBuiltinVendor(model.getVendorId());
        if (!normalizedCapabilityType.equalsIgnoreCase(model.getCapabilityType())) {
            throw new TaskException("默认模型能力类型不匹配", TaskException.Code.UNKNOWN);
        }
        if (!STATUS_ACTIVE.equals(normalizeStatus(model.getStatus()))) {
            throw new TaskException("仅启用状态的模型可设为默认模型", TaskException.Code.UNKNOWN);
        }
        if (!STATUS_ACTIVE.equals(normalizeStatus(vendor.getStatus()))) {
            throw new TaskException("厂商未启用，无法设置默认模型", TaskException.Code.UNKNOWN);
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

        logger.info(
                "默认模型更新成功：capabilityType={}, modelId={}, operatorUserId={}",
                normalizedCapabilityType,
                model.getId(),
                operator.getId());
        return toDefaultBindingView(
                normalizedCapabilityType, existing, buildBuiltinModelById(), buildBuiltinVendorById());
    }

    private Map<Long, Integer> buildModelCountByVendor() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        Set<Long> builtinVendorIds = buildBuiltinVendorById().keySet();
        for (ModelDefinition model : modelDefinitionMapper.search(null, null, null, null)) {
            if (model.getVendorId() == null || !builtinVendorIds.contains(model.getVendorId())) {
                continue;
            }
            result.merge(model.getVendorId(), 1, Integer::sum);
        }
        return result;
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

    private List<ModelVendor> listBuiltinVendors() {
        return modelVendorMapper
                .selectList(new QueryWrapper<ModelVendor>().in("vendor_code", BUILTIN_VENDOR_CODES))
                .stream()
                .sorted(Comparator.comparingInt(vendor -> vendorOrder(vendor.getVendorCode())))
                .toList();
    }

    private Map<Long, ModelVendor> buildBuiltinVendorById() {
        Map<Long, ModelVendor> vendorById = new LinkedHashMap<>();
        for (ModelVendor vendor : listBuiltinVendors()) {
            vendorById.put(vendor.getId(), vendor);
        }
        return vendorById;
    }

    private Map<Long, ModelDefinition> buildBuiltinModelById() {
        Map<Long, ModelVendor> vendorById = buildBuiltinVendorById();
        Map<Long, ModelDefinition> modelById = new LinkedHashMap<>();
        for (ModelDefinition model : modelDefinitionMapper.search(null, null, null, null)) {
            if (vendorById.containsKey(model.getVendorId())) {
                modelById.put(model.getId(), model);
            }
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
                trimText(vendor.getDefaultBaseUrl()),
                StringUtils.hasText(vendor.getDefaultApiKey()),
                vendorMode(vendor.getVendorCode()),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt());
    }

    private ModelView toModelView(
            ModelDefinition model, ModelVendor vendor, Map<String, Long> defaultModelIdByCapability) {
        boolean defaultModel = model != null
                && defaultModelIdByCapability != null
                && model.getId() != null
                && model.getId().equals(defaultModelIdByCapability.get(model.getCapabilityType()));
        boolean modelBaseUrlConfigured = StringUtils.hasText(model.getBaseUrl());
        boolean vendorBaseUrlConfigured = vendor != null && StringUtils.hasText(vendor.getDefaultBaseUrl());
        boolean modelApiKeyConfigured = StringUtils.hasText(model.getApiKey());
        boolean vendorApiKeyConfigured = vendor != null && StringUtils.hasText(vendor.getDefaultApiKey());
        return new ModelView(
                model.getId(),
                model.getModelCode(),
                model.getDisplayName(),
                model.getCapabilityType(),
                model.getVendorId(),
                vendor == null ? "" : vendor.getVendorCode(),
                vendor == null ? "" : vendor.getVendorName(),
                trimText(model.getBaseUrl()),
                firstNonBlank(model.getBaseUrl(), vendor == null ? "" : vendor.getDefaultBaseUrl()),
                !modelBaseUrlConfigured && vendorBaseUrlConfigured,
                trimText(model.getModelName()),
                normalizeStatus(model.getStatus()),
                modelApiKeyConfigured || vendorApiKeyConfigured,
                !modelApiKeyConfigured && vendorApiKeyConfigured,
                trimText(model.getPath()),
                trimText(model.getProtocol()),
                model.getTemperature(),
                model.getMaxTokens(),
                trimText(model.getSystemPrompt()),
                model.getEnableThinking(),
                model.getDimensions(),
                model.getTimeoutMs(),
                model.getFallbackRrf(),
                defaultModel,
                BUILTIN_MODEL_CODES.contains(trimText(model.getModelCode())),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    private DefaultBindingView toDefaultBindingView(
            String capabilityType,
            ModelDefaultBinding binding,
            Map<Long, ModelDefinition> modelById,
            Map<Long, ModelVendor> vendorById) {
        if (binding == null || binding.getModelId() == null) {
            return new DefaultBindingView(capabilityType, null, null, null, null, null);
        }
        ModelDefinition model = modelById.get(binding.getModelId());
        if (model == null) {
            return new DefaultBindingView(capabilityType, binding.getModelId(), null, null, null, "MISSING");
        }
        ModelVendor vendor = vendorById.get(model.getVendorId());
        return new DefaultBindingView(
                capabilityType,
                model.getId(),
                model.getDisplayName(),
                vendor == null ? null : vendor.getId(),
                vendor == null ? "" : vendor.getVendorName(),
                normalizeStatus(model.getStatus()));
    }

    private ChatModelOptionView toChatModelOptionView(
            ModelDefinition model, ModelVendor vendor, Long defaultChatModelId) {
        String displayName = firstNonBlank(model.getDisplayName(), model.getModelCode());
        String vendorName = vendor == null ? "" : vendor.getVendorName();
        String modelName = trimText(model.getModelName());
        String description = vendorName;
        if (StringUtils.hasText(modelName)) {
            description = StringUtils.hasText(description) ? description + " · " + modelName : modelName;
        }
        return new ChatModelOptionView(
                model.getId(),
                displayName,
                description,
                vendor == null ? null : vendor.getId(),
                vendorName,
                modelName,
                defaultChatModelId != null && defaultChatModelId.equals(model.getId()));
    }

    private boolean isAvailableChatModel(ModelDefinition model, ModelVendor vendor) {
        if (model == null || vendor == null || model.getId() == null) {
            return false;
        }
        if (!ModelCapabilityType.CHAT.name().equalsIgnoreCase(trimText(model.getCapabilityType()))) {
            return false;
        }
        return STATUS_ACTIVE.equals(normalizeStatus(model.getStatus()))
                && STATUS_ACTIVE.equals(normalizeStatus(vendor.getStatus()));
    }

    private NormalizedVendor normalizeVendorRequest(UpsertVendorRequest request, ModelVendor existing)
            throws TaskException {
        if (request == null) {
            throw new TaskException("厂商配置请求不能为空", TaskException.Code.UNKNOWN);
        }
        String defaultBaseUrl = trimText(request.defaultBaseUrl());
        if (StringUtils.hasText(defaultBaseUrl)) {
            defaultBaseUrl = validateUrl(defaultBaseUrl);
        }
        String defaultApiKey = trimText(request.apiKey());
        if (!StringUtils.hasText(defaultApiKey) && existing != null) {
            defaultApiKey = trimText(existing.getDefaultApiKey());
        }
        return new NormalizedVendor(defaultBaseUrl, defaultApiKey, normalizeStatus(request.status()));
    }

    private NormalizedModel normalizeModelRequest(UpsertModelRequest request, ModelDefinition existing)
            throws TaskException {
        if (request == null) {
            throw new TaskException("模型请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        Long vendorId = request.vendorId();
        if (vendorId == null || vendorId <= 0) {
            throw new TaskException("模型厂商不能为空", TaskException.Code.UNKNOWN);
        }
        ModelVendor vendor = requireBuiltinVendor(vendorId);
        String capabilityType = ModelCapabilityType.normalize(request.capabilityType());
        String displayName = requireText(request.displayName(), "模型名称不能为空");
        String modelName = requireText(request.modelName(), "模型名不能为空");
        String baseUrl = trimText(request.baseUrl());
        if (StringUtils.hasText(baseUrl)) {
            baseUrl = validateUrl(baseUrl);
        }
        String apiKey = resolveApiKey(request.apiKey(), existing);
        String modelCode =
                resolveModelCode(request.modelCode(), vendor.getVendorCode(), capabilityType, modelName, existing);
        String protocol = normalizeProtocol(capabilityType, vendor.getVendorCode(), request.protocol());
        String path = normalizePath(defaultPath(capabilityType, vendor.getVendorCode(), request.path()));
        Double temperature = normalizeTemperature(capabilityType, request.temperature());
        Integer maxTokens = normalizePositiveInteger(request.maxTokens(), "maxTokens 必须大于 0");
        String systemPrompt = normalizeOptionalText(request.systemPrompt());
        Boolean enableThinking = normalizeEnableThinking(capabilityType, request.enableThinking());
        Integer dimensions = normalizeDimensions(capabilityType, request.dimensions());
        Integer timeoutMs = normalizeTimeoutMs(capabilityType, request.timeoutMs());
        Boolean fallbackRrf = normalizeFallbackRrf(capabilityType, request.fallbackRrf());

        return new NormalizedModel(
                modelCode,
                displayName,
                capabilityType,
                vendor.getId(),
                resolveAdapterType(vendor.getVendorCode()),
                protocol,
                baseUrl,
                apiKey,
                path,
                modelName,
                temperature,
                maxTokens,
                systemPrompt,
                enableThinking,
                dimensions,
                timeoutMs,
                fallbackRrf,
                normalizeStatus(request.status()));
    }

    private void validateModelConnectivity(NormalizedModel normalized) throws TaskException {
        ModelVendor vendor = requireBuiltinVendor(normalized.vendorId());
        String vendorCode = trimText(vendor.getVendorCode());
        String vendorBaseUrl = trimText(vendor.getDefaultBaseUrl());
        String effectiveBaseUrl = firstNonBlank(normalized.baseUrl(), vendorBaseUrl);
        if (!StringUtils.hasText(effectiveBaseUrl) && !VENDOR_VLLM.equals(vendorCode)) {
            throw new TaskException(
                    "请先为厂商「" + vendor.getVendorName() + "」配置默认 Base URL，保存厂商后再添加或编辑模型", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(effectiveBaseUrl) && VENDOR_VLLM.equals(vendorCode)) {
            throw new TaskException("请填写模型 Base URL，或先为 vLLM 厂商配置默认 Base URL", TaskException.Code.UNKNOWN);
        }
        String effectiveApiKey = firstNonBlank(normalized.apiKey(), vendor.getDefaultApiKey());
        if (!StringUtils.hasText(effectiveApiKey) && !VENDOR_VLLM.equals(vendorCode)) {
            throw new TaskException(
                    "请先为厂商「" + vendor.getVendorName() + "」配置可用的默认 API Key，或在模型中填写专属 API Key",
                    TaskException.Code.UNKNOWN);
        }
        if (ModelCapabilityType.CHAT.name().equalsIgnoreCase(normalized.capabilityType())
                && VENDOR_VLLM.equals(vendorCode)) {
            performModelValidation(vendor, normalized, effectiveBaseUrl, effectiveApiKey, "模型保存前连通性校验");
            return;
        }
        Object catalogPayload = performVendorValidation(vendor, effectiveBaseUrl, effectiveApiKey, "模型保存前连通性校验");
        ensureModelExistsInCatalog(normalized.modelName(), catalogPayload);
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
        entity.setTemperature(normalized.temperature());
        entity.setMaxTokens(normalized.maxTokens());
        entity.setSystemPrompt(normalized.systemPrompt());
        entity.setEnableThinking(normalized.enableThinking());
        entity.setDimensions(normalized.dimensions());
        entity.setTimeoutMs(normalized.timeoutMs());
        entity.setFallbackRrf(normalized.fallbackRrf());
        entity.setExtraConfigJson("");
        entity.setStatus(normalized.status());
    }

    private String resolveModelCode(
            String rawModelCode, String vendorCode, String capabilityType, String modelName, ModelDefinition existing)
            throws TaskException {
        if (StringUtils.hasText(rawModelCode)) {
            String normalized = normalizeCode(rawModelCode, "模型编码不能为空");
            ModelDefinition sameCode = modelDefinitionMapper.selectByModelCode(normalized);
            if (sameCode != null && (existing == null || !sameCode.getId().equals(existing.getId()))) {
                throw new TaskException("模型编码已存在：" + normalized, TaskException.Code.UNKNOWN);
            }
            return normalized;
        }

        String baseCode = normalizeCode(
                (vendorCode + "-" + capabilityType + "-" + slugify(modelName)).toLowerCase(Locale.ROOT), "模型编码不能为空");
        String candidate = baseCode;
        int suffix = 2;
        while (true) {
            ModelDefinition sameCode = modelDefinitionMapper.selectByModelCode(candidate);
            if (sameCode == null || (existing != null && sameCode.getId().equals(existing.getId()))) {
                return candidate;
            }
            candidate = baseCode + "-" + suffix;
            suffix++;
        }
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

    private ModelVendor requireBuiltinVendor(Long id) throws TaskException {
        if (id == null || id <= 0) {
            throw new TaskException("模型厂商 id 不能为空", TaskException.Code.UNKNOWN);
        }
        ModelVendor vendor = modelVendorMapper.selectById(id);
        if (vendor == null || !BUILTIN_VENDOR_CODES.contains(trimText(vendor.getVendorCode()))) {
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
        requireBuiltinVendor(model.getVendorId());
        return model;
    }

    private String resolveAdapterType(String vendorCode) {
        return VENDOR_VLLM.equals(vendorCode) ? VENDOR_VLLM : VENDOR_QWEN;
    }

    private String normalizeProtocol(String capabilityType, String vendorCode, String protocol) {
        if (!ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return trimText(protocol);
        }
        if (StringUtils.hasText(protocol)) {
            return protocol.trim();
        }
        return VENDOR_VLLM.equals(vendorCode) ? "vllm" : "dashscope";
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

    private String defaultPath(String capabilityType, String vendorCode, String rawPath) {
        if (StringUtils.hasText(rawPath)) {
            return rawPath;
        }
        if (ModelCapabilityType.CHAT.name().equals(capabilityType)) {
            return "/v1/chat/completions";
        }
        if (ModelCapabilityType.EMBEDDING.name().equals(capabilityType)) {
            return "/v1/embeddings";
        }
        if (ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return VENDOR_VLLM.equals(vendorCode) ? "/v1/rerank" : "/api/v1/services/rerank/text-rerank/text-rerank";
        }
        return "";
    }

    private Double normalizeTemperature(String capabilityType, Double temperature) throws TaskException {
        if (!ModelCapabilityType.CHAT.name().equals(capabilityType)) {
            return null;
        }
        if (temperature == null) {
            return null;
        }
        if (temperature < 0D || temperature > 2D) {
            throw new TaskException("temperature 需在 0 到 2 之间", TaskException.Code.UNKNOWN);
        }
        return temperature;
    }

    private Boolean normalizeEnableThinking(String capabilityType, Boolean enableThinking) {
        if (!ModelCapabilityType.CHAT.name().equals(capabilityType)) {
            return null;
        }
        return enableThinking;
    }

    private Integer normalizeDimensions(String capabilityType, Integer dimensions) throws TaskException {
        if (!ModelCapabilityType.EMBEDDING.name().equals(capabilityType)) {
            return null;
        }
        return normalizePositiveInteger(dimensions, "dimensions 必须大于 0");
    }

    private Integer normalizeTimeoutMs(String capabilityType, Integer timeoutMs) throws TaskException {
        if (!ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return null;
        }
        return normalizePositiveInteger(timeoutMs, "timeoutMs 必须大于 0");
    }

    private Boolean normalizeFallbackRrf(String capabilityType, Boolean fallbackRrf) {
        if (!ModelCapabilityType.RERANK.name().equals(capabilityType)) {
            return null;
        }
        return fallbackRrf;
    }

    private Integer normalizePositiveInteger(Integer value, String errorMessage) throws TaskException {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new TaskException(errorMessage, TaskException.Code.UNKNOWN);
        }
        return value;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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

    private String resolveVendorValidationBaseUrl(ModelVendor vendor, NormalizedVendor normalized)
            throws TaskException {
        String effectiveBaseUrl = firstNonBlank(
                normalized == null ? "" : normalized.defaultBaseUrl(),
                vendor == null ? "" : vendor.getDefaultBaseUrl());
        if (StringUtils.hasText(effectiveBaseUrl)) {
            return effectiveBaseUrl;
        }
        if (vendor != null && VENDOR_QWEN.equals(trimText(vendor.getVendorCode()))) {
            return "https://dashscope.aliyuncs.com/compatible-mode";
        }
        throw new TaskException("请先配置厂商 Base URL", TaskException.Code.UNKNOWN);
    }

    private Object performVendorValidation(ModelVendor vendor, String baseUrl, String apiKey, String actionLabel)
            throws TaskException {
        String normalizedBaseUrl = normalizeValidationBaseUrl(vendor == null ? "" : vendor.getVendorCode(), baseUrl);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder =
                RestClient.builder().baseUrl(normalizedBaseUrl).requestFactory(requestFactory);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        RestClient restClient = builder.build();
        try {
            return restClient.get().uri("/v1/models").retrieve().body(Object.class);
        } catch (RestClientResponseException ex) {
            throw new TaskException(
                    buildHttpValidationErrorMessage(vendor, normalizedBaseUrl, actionLabel, ex),
                    TaskException.Code.UNKNOWN,
                    ex);
        } catch (Exception ex) {
            throw new TaskException(
                    buildGenericValidationErrorMessage(vendor, normalizedBaseUrl, actionLabel, ex),
                    TaskException.Code.UNKNOWN,
                    ex);
        }
    }

    private void performModelValidation(
            ModelVendor vendor, NormalizedModel normalized, String baseUrl, String apiKey, String actionLabel)
            throws TaskException {
        ModelRuntimeConfigResolver.ResolvedChatModelConfig validationConfig =
                buildValidationChatConfig(vendor, normalized, baseUrl, apiKey);
        if (!StringUtils.hasText(validationConfig.baseUrl())) {
            throw new TaskException("模型保存前连通性校验失败：Base URL 不能为空", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(validationConfig.model())) {
            throw new TaskException("模型保存前连通性校验失败：模型名不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            modelRuntimeClientFactory.validateChatConnectivity(validationConfig, "ping");
        } catch (RestClientResponseException ex) {
            throw new TaskException(
                    buildHttpValidationErrorMessage(vendor, validationConfig.baseUrl(), actionLabel, ex),
                    TaskException.Code.UNKNOWN,
                    ex);
        } catch (WebClientResponseException ex) {
            throw new TaskException(
                    buildHttpValidationErrorMessage(vendor, validationConfig.baseUrl(), actionLabel, ex),
                    TaskException.Code.UNKNOWN,
                    ex);
        } catch (Exception ex) {
            RestClientResponseException restClientEx = findCause(ex, RestClientResponseException.class);
            if (restClientEx != null) {
                throw new TaskException(
                        buildHttpValidationErrorMessage(vendor, validationConfig.baseUrl(), actionLabel, restClientEx),
                        TaskException.Code.UNKNOWN,
                        ex);
            }
            WebClientResponseException webClientEx = findCause(ex, WebClientResponseException.class);
            if (webClientEx != null) {
                throw new TaskException(
                        buildHttpValidationErrorMessage(vendor, validationConfig.baseUrl(), actionLabel, webClientEx),
                        TaskException.Code.UNKNOWN,
                        ex);
            }
            throw new TaskException(
                    buildGenericValidationErrorMessage(vendor, validationConfig.baseUrl(), actionLabel, ex),
                    TaskException.Code.UNKNOWN,
                    ex);
        }
    }

    private ModelRuntimeConfigResolver.ResolvedChatModelConfig buildValidationChatConfig(
            ModelVendor vendor, NormalizedModel normalized, String baseUrl, String apiKey) {
        String vendorCode = vendor == null ? "" : trimText(vendor.getVendorCode());
        String normalizedBaseUrl = normalizeValidationBaseUrl(vendorCode, baseUrl);
        String resolvedPath = resolveModelValidationPath(vendor, normalized);
        return new ModelRuntimeConfigResolver.ResolvedChatModelConfig(
                "MODEL_LIBRARY_VALIDATION",
                normalized.adapterType(),
                ModelAdapterType.toChatProvider(normalized.adapterType()),
                normalized.displayName(),
                null,
                normalizedBaseUrl,
                trimText(apiKey),
                resolvedPath,
                normalized.modelName(),
                normalized.temperature() == null ? 0D : normalized.temperature(),
                1,
                "",
                normalized.enableThinking());
    }

    private String resolveModelValidationPath(ModelVendor vendor, NormalizedModel normalized) {
        String path = trimText(normalized.path());
        if (StringUtils.hasText(path)) {
            return normalizePath(path);
        }
        String vendorCode = vendor == null ? "" : trimText(vendor.getVendorCode());
        return VENDOR_VLLM.equals(vendorCode) ? "/v1/chat/completions" : "/v1/chat/completions";
    }

    private void ensureModelExistsInCatalog(String modelName, Object catalogPayload) throws TaskException {
        String normalizedModelName = trimText(modelName);
        if (!StringUtils.hasText(normalizedModelName)) {
            throw new TaskException("模型名不能为空", TaskException.Code.UNKNOWN);
        }
        if (containsModelName(catalogPayload, normalizedModelName)) {
            return;
        }
        throw new TaskException(
                "模型保存前校验失败：模型标识「" + normalizedModelName + "」不在厂商返回的模型列表中，请确认模型名填写正确", TaskException.Code.UNKNOWN);
    }

    @SuppressWarnings("unchecked")
    private boolean containsModelName(Object payload, String modelName) {
        if (payload == null) {
            return false;
        }
        if (payload instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (modelName.equals(trimText(id == null ? null : String.valueOf(id)))) {
                return true;
            }
            Object model = map.get("model");
            if (modelName.equals(trimText(model == null ? null : String.valueOf(model)))) {
                return true;
            }
            Object name = map.get("name");
            if (modelName.equals(trimText(name == null ? null : String.valueOf(name)))) {
                return true;
            }
            for (Object value : map.values()) {
                if (containsModelName(value, modelName)) {
                    return true;
                }
            }
            return false;
        }
        if (payload instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (containsModelName(item, modelName)) {
                    return true;
                }
            }
            return false;
        }
        if (payload.getClass().isArray()) {
            Object[] array = (Object[]) payload;
            for (Object item : array) {
                if (containsModelName(item, modelName)) {
                    return true;
                }
            }
            return false;
        }
        return modelName.equals(trimText(String.valueOf(payload)));
    }

    private String buildHttpValidationErrorMessage(
            ModelVendor vendor, String normalizedBaseUrl, String actionLabel, RestClientResponseException ex) {
        return buildHttpValidationErrorMessage(
                vendor, normalizedBaseUrl, actionLabel, ex.getStatusCode().value(), ex.getResponseBodyAsString());
    }

    private String buildHttpValidationErrorMessage(
            ModelVendor vendor, String normalizedBaseUrl, String actionLabel, WebClientResponseException ex) {
        return buildHttpValidationErrorMessage(
                vendor, normalizedBaseUrl, actionLabel, ex.getStatusCode().value(), ex.getResponseBodyAsString());
    }

    private String buildHttpValidationErrorMessage(
            ModelVendor vendor, String normalizedBaseUrl, String actionLabel, int status, String responseBodyRaw) {
        String vendorName = vendor == null ? "当前厂商" : vendor.getVendorName();
        if (status == 401 || status == 403) {
            return actionLabel + "失败：" + vendorName + " 返回 HTTP " + status + "，请检查 API Key 是否正确或是否仍然有效";
        }
        if (status == 404) {
            return actionLabel + "失败：" + vendorName + " 返回 HTTP 404，请检查厂商 Base URL 是否正确，当前请求地址前缀为 " + normalizedBaseUrl;
        }
        if (status >= 500) {
            return actionLabel + "失败：" + vendorName + " 服务暂时不可用（HTTP " + status + "），请稍后重试";
        }
        String responseBody = shorten(responseBodyRaw, 160);
        if (StringUtils.hasText(responseBody)) {
            return actionLabel + "失败：" + vendorName + " 返回 HTTP " + status + "，" + responseBody;
        }
        return actionLabel + "失败：" + vendorName + " 返回 HTTP " + status + "，请检查厂商连接配置";
    }

    private String buildGenericValidationErrorMessage(
            ModelVendor vendor, String normalizedBaseUrl, String actionLabel, Exception ex) {
        String vendorName = vendor == null ? "当前厂商" : vendor.getVendorName();
        Throwable root = rootCause(ex);
        if (root instanceof UnknownHostException) {
            return actionLabel + "失败：无法解析主机，请检查厂商 Base URL 是否填写正确，当前请求地址前缀为 " + normalizedBaseUrl;
        }
        if (root instanceof ConnectException) {
            return actionLabel + "失败：无法连接到模型服务，请检查厂商 Base URL 是否可访问，当前请求地址前缀为 " + normalizedBaseUrl;
        }
        if (root instanceof HttpConnectTimeoutException || root instanceof SocketTimeoutException) {
            return actionLabel + "失败：" + vendorName + " 连接超时，请检查服务状态或网络连通性";
        }
        String detail = shorten(root == null ? ex.getMessage() : root.getMessage(), 160);
        if (StringUtils.hasText(detail)) {
            return actionLabel + "失败：" + vendorName + " 连接异常，" + detail;
        }
        return actionLabel + "失败：" + vendorName + " 连接异常，请检查厂商 URL、API Key 与服务状态";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return targetType.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private String normalizeValidationBaseUrl(String vendorCode, String baseUrl) {
        String normalized = trimText(baseUrl).replaceAll("/+$", "");
        if (VENDOR_VLLM.equals(trimText(vendorCode)) && normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private String shorten(String value, int maxLength) {
        String text = trimText(value);
        if (!StringUtils.hasText(text) || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String normalizeCode(String value, String message) throws TaskException {
        String normalized = requireText(value, message);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new TaskException("编码仅支持字母、数字、点、下划线和中划线", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String requireText(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : STATUS_ACTIVE;
        return SUPPORTED_STATUSES.contains(normalized) ? normalized : STATUS_ACTIVE;
    }

    private String trimText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return trimText(second);
    }

    private int vendorOrder(String vendorCode) {
        if (VENDOR_QWEN.equals(vendorCode)) {
            return 0;
        }
        if (VENDOR_VLLM.equals(vendorCode)) {
            return 1;
        }
        return 9;
    }

    private String vendorMode(String vendorCode) {
        return VENDOR_QWEN.equals(vendorCode) ? "PREDEFINED" : "CUSTOMIZABLE";
    }

    private String slugify(String value) {
        String normalized = trimText(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(normalized) ? normalized : "model";
    }

    public record VendorView(
            Long id,
            String vendorCode,
            String vendorName,
            String description,
            String status,
            Integer modelCount,
            String defaultBaseUrl,
            Boolean apiKeyConfigured,
            String mode,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record VendorValidationView(Long id, String vendorCode, String baseUrl, String message) {}

    public record ValidationResult(Long vendorId, String vendorCode, String baseUrl, String message) {}

    public record UpsertVendorRequest(String defaultBaseUrl, String apiKey, String status) {}

    public record ModelView(
            Long id,
            String modelCode,
            String displayName,
            String capabilityType,
            Long vendorId,
            String vendorCode,
            String vendorName,
            String baseUrl,
            String effectiveBaseUrl,
            Boolean baseUrlInherited,
            String modelName,
            String status,
            Boolean apiKeyConfigured,
            Boolean apiKeyInherited,
            String path,
            String protocol,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking,
            Integer dimensions,
            Integer timeoutMs,
            Boolean fallbackRrf,
            Boolean defaultModel,
            Boolean builtin,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record UpsertModelRequest(
            Long modelId,
            String modelCode,
            String displayName,
            String capabilityType,
            Long vendorId,
            String baseUrl,
            String apiKey,
            String modelName,
            String status,
            String protocol,
            String path,
            Double temperature,
            Integer maxTokens,
            String systemPrompt,
            Boolean enableThinking,
            Integer dimensions,
            Integer timeoutMs,
            Boolean fallbackRrf) {}

    public record DefaultBindingRequest(Long modelId) {}

    public record DefaultBindingView(
            String capabilityType,
            Long modelId,
            String modelDisplayName,
            Long vendorId,
            String vendorName,
            String modelStatus) {}

    public record ChatModelOptionView(
            Long id,
            String displayName,
            String description,
            Long vendorId,
            String vendorName,
            String modelName,
            Boolean defaultModel) {}

    public record ChatModelReferenceView(Long id, String displayName, Boolean available, Boolean defaultModel) {}

    private record NormalizedVendor(String defaultBaseUrl, String defaultApiKey, String status) {}

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
            String status) {}
}
