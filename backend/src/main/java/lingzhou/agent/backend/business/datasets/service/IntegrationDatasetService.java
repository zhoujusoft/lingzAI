package lingzhou.agent.backend.business.datasets.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import lingzhou.agent.backend.capability.tool.publish.DatasetToolPublishService;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetFieldBinding;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetObjectBinding;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetPublishBinding;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetRelationBinding;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetFieldBindingMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetObjectBindingMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetPublishBindingMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetRelationBindingMapper;
import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.integration.service.lowcode.LowcodeDatasetBrowseService;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDatasetService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationDatasetService.class);
    private static final DateTimeFormatter DATASET_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final IntegrationDatasetMapper integrationDatasetMapper;
    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final IntegrationDatasetObjectBindingMapper objectBindingMapper;
    private final IntegrationDatasetFieldBindingMapper fieldBindingMapper;
    private final IntegrationDatasetRelationBindingMapper relationBindingMapper;
    private final IntegrationDatasetPublishBindingMapper publishBindingMapper;
    private final DatasetToolPublishService datasetToolPublishService;
    private final LowcodeDatasetBrowseService lowcodeDatasetBrowseService;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final ObjectMapper objectMapper;

    public IntegrationDatasetService(
            IntegrationDatasetMapper integrationDatasetMapper,
            IntegrationDataSourceMapper integrationDataSourceMapper,
            IntegrationDatasetObjectBindingMapper objectBindingMapper,
            IntegrationDatasetFieldBindingMapper fieldBindingMapper,
            IntegrationDatasetRelationBindingMapper relationBindingMapper,
            IntegrationDatasetPublishBindingMapper publishBindingMapper,
            DatasetToolPublishService datasetToolPublishService,
            LowcodeDatasetBrowseService lowcodeDatasetBrowseService,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            ObjectMapper objectMapper) {
        this.integrationDatasetMapper = integrationDatasetMapper;
        this.integrationDataSourceMapper = integrationDataSourceMapper;
        this.objectBindingMapper = objectBindingMapper;
        this.fieldBindingMapper = fieldBindingMapper;
        this.relationBindingMapper = relationBindingMapper;
        this.publishBindingMapper = publishBindingMapper;
        this.datasetToolPublishService = datasetToolPublishService;
        this.lowcodeDatasetBrowseService = lowcodeDatasetBrowseService;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.objectMapper = objectMapper;
    }

    public List<DatasetSummary> listDatasets(String keyword, String sourceKind, Long aiDataSourceId, String lowcodePlatformKey) {
        List<IntegrationDataset> datasets =
                integrationDatasetMapper.search(keyword, sourceKind, aiDataSourceId, lowcodePlatformKey);
        Map<Long, IntegrationDatasetPublishBinding> publishBindingMap = publishBindingMapper
                .selectByDatasetIds(datasets.stream().map(IntegrationDataset::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        IntegrationDatasetPublishBinding::getDatasetId,
                        item -> item,
                        (left, right) -> right,
                        LinkedHashMap::new));
        return datasets.stream()
                .map(dataset -> toSummary(dataset, publishBindingMap.get(dataset.getId())))
                .toList();
    }

    public DatasetDetail getDataset(Long id) throws TaskException {
        IntegrationDataset dataset = requireDataset(id);
        return toDetail(dataset);
    }

    public DatasetDetail getDatasetByCode(String datasetCode) throws TaskException {
        if (!StringUtils.hasText(datasetCode)) {
            throw new TaskException("数据集编码不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationDataset dataset = integrationDatasetMapper.selectByDatasetCode(datasetCode.trim());
        if (dataset == null) {
            throw new TaskException("数据集不存在：" + datasetCode, TaskException.Code.UNKNOWN);
        }
        return toDetail(dataset);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetDetail create(UpsertDatasetRequest request) throws TaskException {
        NormalizedDataset normalized = normalizeRequest(request, null);
        if (integrationDatasetMapper.selectByName(normalized.name()) != null) {
            throw new TaskException("数据集名称已存在：" + normalized.name(), TaskException.Code.UNKNOWN);
        }
        IntegrationDataset dataset = new IntegrationDataset();
        dataset.setDatasetCode(generateUniqueDatasetCode());
        applyDataset(dataset, normalized);
        integrationDatasetMapper.insert(dataset);
        replaceBindings(dataset.getId(), normalized);
        return getDataset(dataset.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetDetail update(Long id, UpsertDatasetRequest request) throws TaskException {
        IntegrationDataset dataset = requireDataset(id);
        NormalizedDataset normalized = normalizeRequest(request, dataset);
        IntegrationDataset sameName = integrationDatasetMapper.selectByName(normalized.name());
        if (sameName != null && !sameName.getId().equals(dataset.getId())) {
            throw new TaskException("数据集名称已存在：" + normalized.name(), TaskException.Code.UNKNOWN);
        }
        applyDataset(dataset, normalized);
        integrationDatasetMapper.updateById(dataset);
        replaceBindings(dataset.getId(), normalized);
        return getDataset(dataset.getId());
    }

    private DatasetSummary toSummary(IntegrationDataset dataset) {
        return toSummary(dataset, publishBindingMapper.selectByDatasetId(dataset.getId()));
    }

    private DatasetSummary toSummary(IntegrationDataset dataset, IntegrationDatasetPublishBinding publishBinding) {
        IntegrationDataSource dataSource = dataset.getAiDataSourceId() == null
                ? null
                : integrationDataSourceMapper.selectById(dataset.getAiDataSourceId());
        List<IntegrationDatasetObjectBinding> objectBindings = objectBindingMapper.selectByDatasetId(dataset.getId());
        List<IntegrationDatasetFieldBinding> fieldBindings = fieldBindingMapper.selectByDatasetId(dataset.getId());
        return new DatasetSummary(
                dataset.getId(),
                dataset.getDatasetCode(),
                dataset.getName(),
                dataset.getSourceKind(),
                dataset.getAiDataSourceId(),
                dataSource == null ? "" : dataSource.getName(),
                dataset.getLowcodePlatformKey(),
                dataset.getLowcodeAppId(),
                dataset.getLowcodeAppName(),
                dataset.getDescription(),
                dataset.getBusinessLogic(),
                dataset.getStatus(),
                normalizePublishStatus(publishBinding),
                publishBinding == null ? 0 : defaultNumber(publishBinding.getPublishedVersion()),
                publishBinding == null ? null : publishBinding.getPublishedAt(),
                publishBinding == null ? null : publishBinding.getLastCompiledAt(),
                publishBinding == null ? "" : trimText(publishBinding.getLastPublishMessage()),
                objectBindings.size(),
                fieldBindings.size(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }

    private DatasetDetail toDetail(IntegrationDataset dataset) throws TaskException {
        DatasetSummary summary = toSummary(dataset);
        List<IntegrationDatasetObjectBinding> objectBindings = objectBindingMapper.selectByDatasetId(dataset.getId());
        List<IntegrationDatasetFieldBinding> fieldBindings = fieldBindingMapper.selectByDatasetId(dataset.getId());
        if ("LOWCODE_APP".equals(dataset.getSourceKind())) {
            enrichLowcodeFieldBindings(dataset, objectBindings, fieldBindings);
        }
        return new DatasetDetail(
                summary.id(),
                summary.datasetCode(),
                summary.name(),
                summary.sourceKind(),
                summary.aiDataSourceId(),
                summary.aiDataSourceName(),
                summary.lowcodePlatformKey(),
                summary.lowcodeAppId(),
                summary.lowcodeAppName(),
                summary.description(),
                summary.businessLogic(),
                summary.status(),
                summary.publishStatus(),
                summary.publishedVersion(),
                summary.publishedAt(),
                summary.lastCompiledAt(),
                summary.lastPublishMessage(),
                summary.objectCount(),
                summary.fieldCount(),
                objectBindings.stream()
                        .map(item -> new ObjectBindingView(
                                item.getId(),
                                item.getObjectCode(),
                                item.getFormCode(),
                                item.getObjectName(),
                                item.getObjectSource(),
                                item.getSelected(),
                                item.getSortOrder()))
                        .toList(),
                fieldBindings.stream()
                        .map(item -> new FieldBindingView(
                                item.getId(),
                                item.getObjectCode(),
                                item.getFormCode(),
                                item.getFieldName(),
                                item.getFieldAlias(),
                                item.getFieldType(),
                                item.getFieldScope(),
                                item.getSubObjectCode(),
                                item.getSubObjectName(),
                                item.getObjectName(),
                                item.getSelected(),
                                item.getSortOrder()))
                        .toList(),
                relationBindingMapper.selectByDatasetId(dataset.getId()).stream()
                        .map(item -> new RelationBindingView(
                                item.getId(),
                                item.getLeftObjectCode(),
                                item.getLeftFieldName(),
                                item.getRightObjectCode(),
                                item.getRightFieldName(),
                                item.getRelationSource()))
                        .toList(),
                summary.createdAt(),
                summary.updatedAt());
    }

    private String normalizePublishStatus(IntegrationDatasetPublishBinding publishBinding) {
        if (publishBinding == null || !StringUtils.hasText(publishBinding.getPublishStatus())) {
            return "DRAFT";
        }
        return publishBinding.getPublishStatus().trim().toUpperCase(Locale.ROOT);
    }

    private Integer defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private String generateUniqueDatasetCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "DS" + LocalDateTime.now().format(DATASET_CODE_FORMATTER)
                    + randomAlphaNumeric(4);
            if (integrationDatasetMapper.selectByDatasetCode(candidate) == null) {
                return candidate;
            }
        }
        return "DS" + System.currentTimeMillis() + randomAlphaNumeric(6);
    }

    private String randomAlphaNumeric(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return builder.toString();
    }

    private void enrichLowcodeFieldBindings(
            IntegrationDataset dataset,
            List<IntegrationDatasetObjectBinding> objectBindings,
            List<IntegrationDatasetFieldBinding> fieldBindings)
            throws TaskException {
        if (!StringUtils.hasText(dataset.getLowcodePlatformKey()) || fieldBindings.isEmpty()) {
            return;
        }
        boolean needsEnrichment = fieldBindings.stream().anyMatch(item ->
                !StringUtils.hasText(item.getFieldScope())
                        || !StringUtils.hasText(item.getObjectName())
                        || (isLowcodeSubtableBinding(item, objectBindings) && !StringUtils.hasText(item.getSubObjectCode())));
        if (!needsEnrichment) {
            return;
        }
        Map<String, String> objectSourceMap = new LinkedHashMap<>();
        Map<String, String> rootObjectNameMap = new LinkedHashMap<>();
        for (IntegrationDatasetObjectBinding item : objectBindings) {
            if (StringUtils.hasText(item.getObjectCode())) {
                objectSourceMap.put(item.getObjectCode().trim(), item.getObjectSource());
            }
        }
        Map<String, LowcodeDatasetBrowseService.FieldView> fieldLookup = new LinkedHashMap<>();
        for (String appId : splitCommaSeparated(dataset.getLowcodeAppId())) {
            List<LowcodeDatasetBrowseService.ObjectView> appObjects =
                    lowcodeDatasetBrowseService.listObjects(dataset.getLowcodePlatformKey(), appId);
            for (LowcodeDatasetBrowseService.ObjectView object : appObjects) {
                if (object.folder()) {
                    continue;
                }
                rootObjectNameMap.putIfAbsent(trimText(object.objectCode()), trimText(object.objectName()));
                List<LowcodeDatasetBrowseService.FieldView> browseFields =
                        lowcodeDatasetBrowseService.listFields(
                                dataset.getLowcodePlatformKey(),
                                appId,
                                object.objectCode(),
                                object.formCode());
                for (LowcodeDatasetBrowseService.FieldView field : browseFields) {
                    fieldLookup.putIfAbsent(buildFieldLookupKey(field.objectCode(), field.subObjectCode(), field.fieldName()), field);
                }
            }
        }
        for (IntegrationDatasetFieldBinding fieldBinding : fieldBindings) {
            LowcodeDatasetBrowseService.FieldView matchedField = fieldLookup.get(buildFieldLookupKey(
                    findMenuObjectCode(fieldBinding, objectSourceMap, fieldLookup),
                    fieldBinding.getObjectCode(),
                    fieldBinding.getFieldName()));
            if (matchedField == null) {
                matchedField = fieldLookup.get(buildFieldLookupKey(fieldBinding.getObjectCode(), "", fieldBinding.getFieldName()));
            }
            if (matchedField == null) {
                matchedField = fieldLookup.get(buildFieldLookupKey("", fieldBinding.getObjectCode(), fieldBinding.getFieldName()));
            }
            if (matchedField == null) {
                continue;
            }
            fieldBinding.setObjectCode(trimText(matchedField.objectCode()));
            if (!StringUtils.hasText(fieldBinding.getFormCode())) {
                fieldBinding.setFormCode(trimText(matchedField.formCode()));
            }
            if (!StringUtils.hasText(fieldBinding.getFieldScope())) {
                fieldBinding.setFieldScope(matchedField.fieldScope());
            }
            if (!StringUtils.hasText(fieldBinding.getSubObjectCode())) {
                fieldBinding.setSubObjectCode(trimText(matchedField.subObjectCode()));
            }
            if (!StringUtils.hasText(fieldBinding.getSubObjectName())) {
                fieldBinding.setSubObjectName(trimText(matchedField.subObjectName()));
            }
            if (!StringUtils.hasText(fieldBinding.getObjectName())) {
                fieldBinding.setObjectName(firstNonBlank(
                        rootObjectNameMap.get(trimText(matchedField.objectCode())),
                        matchedField.objectCode(),
                        fieldBinding.getObjectCode()));
            }
        }
    }

    private String findMenuObjectCode(
            IntegrationDatasetFieldBinding fieldBinding,
            Map<String, String> objectSourceMap,
            Map<String, LowcodeDatasetBrowseService.FieldView> fieldLookup) {
        if (!isLowcodeSubtableBinding(fieldBinding, objectSourceMap)) {
            return trimText(fieldBinding.getObjectCode());
        }
        for (LowcodeDatasetBrowseService.FieldView fieldView : fieldLookup.values()) {
            if (sameIgnoreCase(fieldView.subObjectCode(), fieldBinding.getObjectCode())
                    && sameIgnoreCase(fieldView.fieldName(), fieldBinding.getFieldName())) {
                return trimText(fieldView.objectCode());
            }
        }
        return "";
    }

    private boolean isLowcodeSubtableBinding(
            IntegrationDatasetFieldBinding fieldBinding,
            List<IntegrationDatasetObjectBinding> objectBindings) {
        Map<String, String> objectSourceMap = new LinkedHashMap<>();
        for (IntegrationDatasetObjectBinding item : objectBindings) {
            if (StringUtils.hasText(item.getObjectCode())) {
                objectSourceMap.put(item.getObjectCode().trim(), item.getObjectSource());
            }
        }
        return isLowcodeSubtableBinding(fieldBinding, objectSourceMap);
    }

    private boolean isLowcodeSubtableBinding(
            IntegrationDatasetFieldBinding fieldBinding,
            Map<String, String> objectSourceMap) {
        return "LOWCODE_SUBTABLE".equalsIgnoreCase(trimText(objectSourceMap.get(fieldBinding.getObjectCode())));
    }

    private List<String> splitCommaSeparated(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String buildFieldLookupKey(String objectCode, String subObjectCode, String fieldName) {
        return normalizeCode(objectCode) + "|" + normalizeCode(subObjectCode) + "|" + normalizeCode(fieldName);
    }

    private boolean sameIgnoreCase(String left, String right) {
        return normalizeCode(left).equals(normalizeCode(right));
    }

    private String normalizeCode(String value) {
        return trimText(value).toLowerCase(Locale.ROOT);
    }

    private IntegrationDataset requireDataset(Long id) throws TaskException {
        if (id == null) {
            throw new TaskException("数据集 id 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationDataset dataset = integrationDatasetMapper.selectById(id);
        if (dataset == null) {
            throw new TaskException("数据集不存在：" + id, TaskException.Code.UNKNOWN);
        }
        return dataset;
    }

    private NormalizedDataset normalizeRequest(UpsertDatasetRequest request, IntegrationDataset existing) throws TaskException {
        if (request == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        String name = requireText(request.name(), "数据集名称不能为空");
        String sourceKind = normalizeSourceKind(request.sourceKind());
        String businessLogic = trimText(request.businessLogic());
        List<ObjectBindingInput> objectBindings = request.objectBindings() == null ? List.of() : request.objectBindings();
        List<FieldBindingInput> fieldBindings = request.fieldBindings() == null ? List.of() : request.fieldBindings();
        List<RelationBindingInput> relationBindings = request.relationBindings() == null ? List.of() : request.relationBindings();
        if (fieldBindings.isEmpty()) {
            throw new TaskException("至少选择一个字段", TaskException.Code.UNKNOWN);
        }
        Long aiDataSourceId = request.aiDataSourceId();
        String lowcodePlatformKey = trimText(request.lowcodePlatformKey());
        String lowcodeAppId = trimText(request.lowcodeAppId());
        String lowcodeAppName = trimText(request.lowcodeAppName());
        if ("AI_SOURCE".equals(sourceKind)) {
            if (aiDataSourceId == null) {
                throw new TaskException("AI 平台数据源不能为空", TaskException.Code.UNKNOWN);
            }
            if (integrationDataSourceMapper.selectById(aiDataSourceId) == null) {
                throw new TaskException("AI 平台数据源不存在：" + aiDataSourceId, TaskException.Code.UNKNOWN);
            }
        }
        if ("LOWCODE_APP".equals(sourceKind)) {
            if (!StringUtils.hasText(lowcodePlatformKey)) {
                throw new TaskException("低代码平台不能为空", TaskException.Code.UNKNOWN);
            }
            NormalizedLowcodeBindings normalizedLowcodeBindings =
                    normalizeLowcodeBindings(objectBindings, fieldBindings, relationBindings);
            objectBindings = normalizedLowcodeBindings.objectBindings();
            fieldBindings = normalizedLowcodeBindings.fieldBindings();
            relationBindings = normalizedLowcodeBindings.relationBindings();
            if (objectBindings.isEmpty()) {
                throw new TaskException("低代码链路至少选择一个菜单或对象", TaskException.Code.UNKNOWN);
            }
        }
        return new NormalizedDataset(
                name,
                sourceKind,
                aiDataSourceId,
                lowcodePlatformKey,
                lowcodeAppId,
                lowcodeAppName,
                trimText(request.description()),
                businessLogic,
                normalizeStatus(request.status(), existing == null ? "ACTIVE" : existing.getStatus()),
                objectBindings,
                fieldBindings,
                relationBindings);
    }

    private NormalizedLowcodeBindings normalizeLowcodeBindings(
            List<ObjectBindingInput> objectBindings,
            List<FieldBindingInput> fieldBindings,
            List<RelationBindingInput> relationBindings)
            throws TaskException {
        Map<String, ObjectBindingInput> objectsByCode = new LinkedHashMap<>();
        int objectSort = 0;
        for (ObjectBindingInput item : objectBindings) {
            if (!StringUtils.hasText(item.objectCode())) {
                continue;
            }
            String objectCode = item.objectCode().trim();
            objectsByCode.putIfAbsent(
                    objectCode,
                    new ObjectBindingInput(
                            objectCode,
                            trimText(item.formCode()),
                            StringUtils.hasText(item.objectName()) ? item.objectName().trim() : objectCode,
                            trimText(item.objectSource()),
                            item.selected() == null ? 1 : item.selected(),
                            item.sortOrder() == null ? objectSort++ : item.sortOrder()));
        }

        List<FieldBindingInput> normalizedFields = new java.util.ArrayList<>();
        Set<String> seenFieldKeys = new LinkedHashSet<>();
        int fieldSort = 0;
        for (FieldBindingInput item : fieldBindings) {
            String ownerObjectCode = resolveFieldOwnerObjectCode(item);
            String fieldName = trimText(item.fieldName());
            if (!StringUtils.hasText(ownerObjectCode) || !StringUtils.hasText(fieldName)) {
                continue;
            }
            String fieldKey = (ownerObjectCode + "|" + fieldName).toLowerCase(Locale.ROOT);
            if (!seenFieldKeys.add(fieldKey)) {
                continue;
            }
            objectsByCode.putIfAbsent(
                    ownerObjectCode,
                    new ObjectBindingInput(
                            ownerObjectCode,
                            trimText(item.formCode()),
                            firstNonBlank(item.objectName(), item.subObjectName(), ownerObjectCode),
                            deriveObjectSource(item),
                            1,
                            objectSort++));
            normalizedFields.add(new FieldBindingInput(
                    ownerObjectCode,
                    trimText(item.formCode()),
                    fieldName,
                    trimText(item.fieldAlias()),
                    trimText(item.fieldType()),
                    item.selected() == null ? 1 : item.selected(),
                    item.sortOrder() == null ? fieldSort++ : item.sortOrder(),
                    trimText(item.fieldScope()),
                    trimText(item.subObjectCode()),
                    trimText(item.subObjectName()),
                    trimText(item.objectName())));
        }
        if (normalizedFields.isEmpty()) {
            throw new TaskException("低代码链路至少选择一个字段", TaskException.Code.UNKNOWN);
        }

        return new NormalizedLowcodeBindings(List.copyOf(objectsByCode.values()), List.copyOf(normalizedFields), List.of());
    }

    private String resolveFieldOwnerObjectCode(FieldBindingInput item) {
        if (item == null) {
            return "";
        }
        String subObjectCode = trimText(item.subObjectCode());
        if (StringUtils.hasText(subObjectCode)) {
            return subObjectCode;
        }
        return trimText(item.objectCode());
    }

    private String deriveObjectSource(FieldBindingInput item) {
        String fieldScope = trimText(item.fieldScope()).toUpperCase(Locale.ROOT);
        if (fieldScope.startsWith("SUB")) {
            return "LOWCODE_SUBTABLE";
        }
        return "LOWCODE_MAIN";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void applyDataset(IntegrationDataset dataset, NormalizedDataset normalized) {
        dataset.setName(normalized.name());
        dataset.setSourceKind(normalized.sourceKind());
        dataset.setAiDataSourceId("AI_SOURCE".equals(normalized.sourceKind()) ? normalized.aiDataSourceId() : null);
        dataset.setLowcodePlatformKey("LOWCODE_APP".equals(normalized.sourceKind()) ? normalized.lowcodePlatformKey() : "");
        dataset.setLowcodeAppId("LOWCODE_APP".equals(normalized.sourceKind()) ? normalized.lowcodeAppId() : "");
        dataset.setLowcodeAppName("LOWCODE_APP".equals(normalized.sourceKind()) ? normalized.lowcodeAppName() : "");
        dataset.setDescription(normalized.description());
        dataset.setBusinessLogic(normalized.businessLogic());
        dataset.setStatus(normalized.status());
    }

    private void replaceBindings(Long datasetId, NormalizedDataset normalized) {
        objectBindingMapper.deleteByDatasetId(datasetId);
        fieldBindingMapper.deleteByDatasetId(datasetId);
        relationBindingMapper.deleteByDatasetId(datasetId);
        int objectSort = 0;
        for (ObjectBindingInput item : normalized.objectBindings()) {
            if (!StringUtils.hasText(item.objectCode())) {
                continue;
            }
            IntegrationDatasetObjectBinding binding = new IntegrationDatasetObjectBinding();
            binding.setDatasetId(datasetId);
            binding.setObjectCode(item.objectCode().trim());
            binding.setFormCode(trimText(item.formCode()));
            binding.setObjectName(trimText(item.objectName()));
            binding.setObjectSource(trimText(item.objectSource()));
            binding.setSelected(item.selected() == null ? 1 : item.selected());
            binding.setSortOrder(item.sortOrder() == null ? objectSort++ : item.sortOrder());
            objectBindingMapper.insert(binding);
        }
        int fieldSort = 0;
        for (FieldBindingInput item : normalized.fieldBindings()) {
            if (!StringUtils.hasText(item.objectCode()) || !StringUtils.hasText(item.fieldName())) {
                continue;
            }
            IntegrationDatasetFieldBinding binding = new IntegrationDatasetFieldBinding();
            binding.setDatasetId(datasetId);
            binding.setObjectCode(item.objectCode().trim());
            binding.setFormCode(trimText(item.formCode()));
            binding.setFieldName(item.fieldName().trim());
            binding.setFieldAlias(trimText(item.fieldAlias()));
            binding.setFieldType(trimText(item.fieldType()));
            binding.setFieldScope(trimText(item.fieldScope()));
            binding.setSubObjectCode(trimText(item.subObjectCode()));
            binding.setSubObjectName(trimText(item.subObjectName()));
            binding.setObjectName(trimText(item.objectName()));
            binding.setSelected(item.selected() == null ? 1 : item.selected());
            binding.setSortOrder(item.sortOrder() == null ? fieldSort++ : item.sortOrder());
            fieldBindingMapper.insert(binding);
        }
        for (RelationBindingInput item : normalized.relationBindings()) {
            if (!StringUtils.hasText(item.leftObjectCode())
                    || !StringUtils.hasText(item.leftFieldName())
                    || !StringUtils.hasText(item.rightObjectCode())
                    || !StringUtils.hasText(item.rightFieldName())) {
                continue;
            }
            IntegrationDatasetRelationBinding binding = new IntegrationDatasetRelationBinding();
            binding.setDatasetId(datasetId);
            binding.setLeftObjectCode(item.leftObjectCode().trim());
            binding.setLeftFieldName(item.leftFieldName().trim());
            binding.setRightObjectCode(item.rightObjectCode().trim());
            binding.setRightFieldName(item.rightFieldName().trim());
            binding.setRelationSource(
                    StringUtils.hasText(item.relationSource()) ? item.relationSource().trim().toUpperCase(Locale.ROOT) : "MANUAL");
            relationBindingMapper.insert(binding);
        }
    }

    private String normalizeSourceKind(String sourceKind) throws TaskException {
        String normalized = requireText(sourceKind, "来源类型不能为空").toUpperCase(Locale.ROOT);
        if (!List.of("AI_SOURCE", "LOWCODE_APP").contains(normalized)) {
            throw new TaskException("不支持的来源类型：" + normalized, TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeStatus(String status, String defaultValue) {
        if (!StringUtils.hasText(status)) {
            return StringUtils.hasText(defaultValue) ? defaultValue.trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String trimText(String value) {
        return value == null ? "" : value.trim();
    }

    private record NormalizedDataset(
            String name,
            String sourceKind,
            Long aiDataSourceId,
            String lowcodePlatformKey,
            String lowcodeAppId,
            String lowcodeAppName,
            String description,
            String businessLogic,
            String status,
            List<ObjectBindingInput> objectBindings,
            List<FieldBindingInput> fieldBindings,
            List<RelationBindingInput> relationBindings) {}

    private record NormalizedLowcodeBindings(
            List<ObjectBindingInput> objectBindings,
            List<FieldBindingInput> fieldBindings,
            List<RelationBindingInput> relationBindings) {}

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) throws TaskException {
        IntegrationDataset dataset = requireDataset(id);
        if (StringUtils.hasText(dataset.getDatasetCode())) {
            datasetToolPublishService.disable(dataset.getDatasetCode());
        }
        publishBindingMapper.deleteByDatasetId(dataset.getId());
        objectBindingMapper.deleteByDatasetId(dataset.getId());
        fieldBindingMapper.deleteByDatasetId(dataset.getId());
        relationBindingMapper.deleteByDatasetId(dataset.getId());
        integrationDatasetMapper.deleteById(dataset.getId());
    }

    public DescriptionGenerateResult generateDescription(DescriptionGenerateRequest request) throws TaskException {
        if (request == null) {
            throw new TaskException("生成说明参数不能为空", TaskException.Code.UNKNOWN);
        }
        String sourceKind = normalizeSourceKind(request.sourceKind());
        List<ObjectBindingInput> objectBindings = request.objectBindings() == null ? List.of() : request.objectBindings();
        List<FieldBindingInput> fieldBindings = request.fieldBindings() == null ? List.of() : request.fieldBindings();
        if (objectBindings.isEmpty() && fieldBindings.isEmpty()) {
            throw new TaskException("请先选择对象和字段后再生成说明", TaskException.Code.UNKNOWN);
        }

        String datasetName = trimText(request.datasetName());
        String businessLogic = trimText(request.businessLogic());
        String promptHint = trimText(request.promptHint());

        Map<String, String> objectNames = new LinkedHashMap<>();
        for (ObjectBindingInput item : objectBindings) {
            if (StringUtils.hasText(item.objectCode())) {
                objectNames.putIfAbsent(trimText(item.objectCode()), firstNonBlank(item.objectName(), item.objectCode()));
            }
        }
        for (FieldBindingInput item : fieldBindings) {
            String objectCode = trimText(item.objectCode());
            if (StringUtils.hasText(objectCode)) {
                objectNames.putIfAbsent(objectCode, firstNonBlank(item.objectName(), objectCode));
            }
        }

        DescriptionGenerateResult aiGenerated =
                tryGenerateDescriptionByAi(sourceKind, datasetName, businessLogic, promptHint, objectNames, fieldBindings);
        String summary = aiGenerated != null && StringUtils.hasText(aiGenerated.summary())
                ? aiGenerated.summary()
                : buildSummary(sourceKind, datasetName, businessLogic, promptHint, objectNames);
        String relationDescription = aiGenerated != null && StringUtils.hasText(aiGenerated.relationDescription())
                ? aiGenerated.relationDescription()
                : buildRelationDescription(sourceKind, objectNames, fieldBindings, promptHint);
        return new DescriptionGenerateResult(summary, relationDescription);
    }

    private DescriptionGenerateResult tryGenerateDescriptionByAi(
            String sourceKind,
            String datasetName,
            String businessLogic,
            String promptHint,
            Map<String, String> objectNames,
            List<FieldBindingInput> fieldBindings) {
        try {
            String prompt = buildDescriptionPrompt(sourceKind, datasetName, businessLogic, promptHint, objectNames, fieldBindings);
            String content = modelRuntimeClientFactory
                    .createChatBundle()
                    .chatClient()
                    .prompt()
                    .system("""
                            你是数据集建模助手，负责为 AI 查询场景生成“数据集摘要”和“关系说明”。
                            你必须严格输出 JSON，不要输出 markdown 代码块，不要输出解释。
                            JSON 结构固定为：
                            {
                              "summary": "...",
                              "relationDescription": "..."
                            }
                            要求：
                            1. summary 用中文，概括数据集用途、适用问题范围、推荐查询方向。
                            2. relationDescription 用中文，描述对象关系、主表/子表逻辑、关键字段作用。
                            3. 若存在枚举值、状态码、时间口径等仍需业务补充的信息，直接写进 relationDescription。
                            4. 不要输出 TODO 字段，不要输出多余键。
                            """)
                    .user(prompt)
                    .call()
                    .content();
            if (!StringUtils.hasText(content)) {
                return null;
            }
            String normalizedJson = extractJsonObject(content);
            Map<String, String> result = objectMapper.readValue(normalizedJson, new TypeReference<>() {});
            return new DescriptionGenerateResult(
                    trimText(result.get("summary")),
                    trimText(result.get("relationDescription")));
        } catch (Exception ex) {
            logger.warn("AI 生成数据集说明失败，回退规则生成：error={}", ex.getMessage(), ex);
            return null;
        }
    }

    private String buildDescriptionPrompt(
            String sourceKind,
            String datasetName,
            String businessLogic,
            String promptHint,
            Map<String, String> objectNames,
            List<FieldBindingInput> fieldBindings) {
        StringBuilder builder = new StringBuilder();
        builder.append("来源类型：").append(sourceKind).append('\n');
        builder.append("数据集名称：").append(firstNonBlank(datasetName, "未命名数据集")).append('\n');
        builder.append("当前业务逻辑说明：").append(firstNonBlank(businessLogic, "无")).append('\n');
        builder.append("用户补充描述：").append(firstNonBlank(promptHint, "无")).append('\n');
        builder.append("已选对象：").append(objectNames.isEmpty() ? "无" : String.join("、", objectNames.values())).append('\n');
        builder.append("已选字段：\n");
        if (fieldBindings.isEmpty()) {
            builder.append("- 无\n");
        } else {
            for (FieldBindingInput item : fieldBindings) {
                String objectName = firstNonBlank(
                        item.subObjectName(),
                        item.objectName(),
                        objectNames.get(trimText(item.objectCode())),
                        item.objectCode());
                builder.append("- 对象：").append(firstNonBlank(objectName, "未命名对象"))
                        .append("，字段：").append(firstNonBlank(item.fieldAlias(), item.fieldName()))
                        .append("，原始字段：").append(firstNonBlank(item.fieldName(), "无"))
                        .append("，字段类型：").append(firstNonBlank(item.fieldType(), "未知"))
                        .append("，字段范围：").append(firstNonBlank(item.fieldScope(), "MAIN"));
                if (StringUtils.hasText(item.subObjectCode())) {
                    builder.append("，子表编码：").append(item.subObjectCode());
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String extractJsonObject(String text) {
        String normalized = trimText(text);
        int fencedStart = normalized.indexOf('{');
        int fencedEnd = normalized.lastIndexOf('}');
        if (fencedStart >= 0 && fencedEnd > fencedStart) {
            return normalized.substring(fencedStart, fencedEnd + 1);
        }
        return normalized;
    }

    private String buildSummary(
            String sourceKind,
            String datasetName,
            String businessLogic,
            String promptHint,
            Map<String, String> objectNames) {
        String sourceText = "LOWCODE_APP".equals(sourceKind) ? "低代码对象结构" : "AI 平台数据源结构";
        String objectsText = objectNames.values().stream().limit(5).reduce((a, b) -> a + "、" + b).orElse("当前已选对象");
        List<String> parts = new ArrayList<>();
        parts.add(StringUtils.hasText(datasetName)
                ? "该数据集“" + datasetName + "”基于" + sourceText + "构建，适用于围绕" + objectsText + "开展查询与分析。"
                : "该数据集基于" + sourceText + "构建，适用于围绕" + objectsText + "开展查询与分析。");
        if (StringUtils.hasText(businessLogic)) {
            parts.add("现有业务说明显示，该数据集重点关注：" + businessLogic + "。");
        }
        if (StringUtils.hasText(promptHint)) {
            parts.add("结合用户补充描述，建议优先支持以下问题方向：" + promptHint + "。");
        }
        if (objectNames.size() > 5) {
            parts.add("除上述核心对象外，数据集中还包含其他辅助对象，可用于补充明细字段和上下文信息。");
        }
        return String.join("", parts);
    }

    private String buildRelationDescription(
            String sourceKind,
            Map<String, String> objectNames,
            List<FieldBindingInput> fieldBindings,
            String promptHint) {
        List<String> parts = new ArrayList<>();
        if ("LOWCODE_APP".equals(sourceKind)) {
            Map<String, Set<String>> subtableFields = new LinkedHashMap<>();
            Set<String> mainFields = new LinkedHashSet<>();
            for (FieldBindingInput item : fieldBindings) {
                if (StringUtils.hasText(item.subObjectCode())) {
                    subtableFields.computeIfAbsent(
                                    firstNonBlank(item.subObjectName(), item.subObjectCode()),
                                    key -> new LinkedHashSet<>())
                            .add(firstNonBlank(item.fieldAlias(), item.fieldName()));
                } else {
                    mainFields.add(firstNonBlank(item.fieldAlias(), item.fieldName()));
                }
            }
            if (!objectNames.isEmpty()) {
                parts.add("当前数据集主要由" + String.join("、", objectNames.values()) + "等菜单对象组成。");
            }
            if (!mainFields.isEmpty()) {
                parts.add("主表字段重点包括：" + joinLimited(mainFields, 6) + "。");
            }
            if (!subtableFields.isEmpty()) {
                List<String> subtableDescriptions = new ArrayList<>();
                for (Map.Entry<String, Set<String>> entry : subtableFields.entrySet()) {
                    subtableDescriptions.add(entry.getKey() + "包含字段：" + joinLimited(entry.getValue(), 5));
                }
                parts.add("子表结构方面，" + String.join("；", subtableDescriptions) + "。");
                parts.add("低代码链路下主表与子表默认按照系统结构理解，若存在业务上的状态码、枚举值或时间口径差异，建议用户进一步补充字段含义。");
            } else {
                parts.add("当前选择结果以主表字段为主，若存在状态码、枚举值、时间口径等业务约束，建议在正式使用前补充说明。");
            }
        } else {
            if (!objectNames.isEmpty()) {
                parts.add("当前数据集涉及的核心表包括：" + String.join("、", objectNames.values()) + "。");
            }
            Map<String, Set<String>> fieldsByObject = new LinkedHashMap<>();
            for (FieldBindingInput item : fieldBindings) {
                String objectName = firstNonBlank(item.objectName(), objectNames.get(trimText(item.objectCode())), item.objectCode());
                fieldsByObject.computeIfAbsent(objectName, key -> new LinkedHashSet<>())
                        .add(firstNonBlank(item.fieldAlias(), item.fieldName()));
            }
            List<String> objectDescriptions = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : fieldsByObject.entrySet()) {
                objectDescriptions.add(entry.getKey() + "可重点关注字段：" + joinLimited(entry.getValue(), 5));
            }
            if (!objectDescriptions.isEmpty()) {
                parts.add(String.join("；", objectDescriptions) + "。");
            }
            parts.add("若表中存在状态字段、类型字段或枚举值字段，建议进一步补充各取值的业务含义，以便后续生成 SQL 时正确理解筛选口径。");
        }
        if (StringUtils.hasText(promptHint)) {
            parts.add("结合用户补充描述，后续说明应特别关注：" + promptHint + "。");
        }
        return String.join("", parts);
    }

    private String joinLimited(Set<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().filter(StringUtils::hasText).limit(limit).reduce((a, b) -> a + "、" + b).orElse("");
    }

    public record UpsertDatasetRequest(
            String name,
            String sourceKind,
            Long aiDataSourceId,
            String lowcodePlatformKey,
            String lowcodeAppId,
            String lowcodeAppName,
            String description,
            String businessLogic,
            String status,
            List<ObjectBindingInput> objectBindings,
            List<FieldBindingInput> fieldBindings,
            List<RelationBindingInput> relationBindings) {}

    public record DescriptionGenerateRequest(
            String sourceKind,
            String datasetName,
            String businessLogic,
            String promptHint,
            List<ObjectBindingInput> objectBindings,
            List<FieldBindingInput> fieldBindings) {}

    public record DescriptionGenerateResult(String summary, String relationDescription) {}

    public record DatasetSummary(
            Long id,
            String datasetCode,
            String name,
            String sourceKind,
            Long aiDataSourceId,
            String aiDataSourceName,
            String lowcodePlatformKey,
            String lowcodeAppId,
            String lowcodeAppName,
            String description,
            String businessLogic,
            String status,
            String publishStatus,
            Integer publishedVersion,
            java.util.Date publishedAt,
            java.util.Date lastCompiledAt,
            String lastPublishMessage,
            int objectCount,
            int fieldCount,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record DatasetDetail(
            Long id,
            String datasetCode,
            String name,
            String sourceKind,
            Long aiDataSourceId,
            String aiDataSourceName,
            String lowcodePlatformKey,
            String lowcodeAppId,
            String lowcodeAppName,
            String description,
            String businessLogic,
            String status,
            String publishStatus,
            Integer publishedVersion,
            java.util.Date publishedAt,
            java.util.Date lastCompiledAt,
            String lastPublishMessage,
            int objectCount,
            int fieldCount,
            List<ObjectBindingView> objectBindings,
            List<FieldBindingView> fieldBindings,
            List<RelationBindingView> relationBindings,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ObjectBindingInput(
            String objectCode,
            String formCode,
            String objectName,
            String objectSource,
            Integer selected,
            Integer sortOrder) {}

    public record FieldBindingInput(
            String objectCode,
            String formCode,
            String fieldName,
            String fieldAlias,
            String fieldType,
            Integer selected,
            Integer sortOrder,
            String fieldScope,
            String subObjectCode,
            String subObjectName,
            String objectName) {}

    public record RelationBindingInput(
            String leftObjectCode,
            String leftFieldName,
            String rightObjectCode,
            String rightFieldName,
            String relationSource) {}

    public record ObjectBindingView(
            Long id,
            String objectCode,
            String formCode,
            String objectName,
            String objectSource,
            Integer selected,
            Integer sortOrder) {}

    public record FieldBindingView(
            Long id,
            String objectCode,
            String formCode,
            String fieldName,
            String fieldAlias,
            String fieldType,
            String fieldScope,
            String subObjectCode,
            String subObjectName,
            String objectName,
            Integer selected,
            Integer sortOrder) {}

    public record RelationBindingView(
            Long id,
            String leftObjectCode,
            String leftFieldName,
            String rightObjectCode,
            String rightFieldName,
            String relationSource) {}
}
