package lingzhou.agent.backend.capability.tool.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.api.registry.LowcodeApiSchemaResolver;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import lingzhou.agent.backend.business.skill.service.LowcodeApiCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FrontendRenderToolService {

    private static final String GET_TEMPLATE_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "templateCode": {
                  "type": "string",
                  "description": "前端渲染模板编码，例如 expense_form_fill_v1"
                },
                "targetToolName": {
                  "type": "string",
                  "description": "可选，目标 API 工具名；若模板来源为 api_input，则会基于它生成有效 dataSchema"
                }
              },
              "required": ["templateCode"],
              "additionalProperties": false
            }
            """;

    private static final String BUILD_PAYLOAD_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "templateCode": {
                  "type": "string",
                  "description": "前端渲染模板编码，例如 expense_form_fill_v1"
                },
                "targetToolName": {
                  "type": "string",
                  "description": "可选，目标 API 工具名；若模板来源为 api_input，则会基于它补齐字段结构"
                },
                "data": {
                  "type": "object",
                  "description": "技能从自然语言中提取出的结构化业务数据",
                  "additionalProperties": true
                },
                "componentProps": {
                  "type": "object",
                  "description": "可选，组件渲染配置",
                  "additionalProperties": true
                },
                "notes": {
                  "type": "string",
                  "description": "可选，对当前渲染结果的补充说明"
                }
              },
              "required": ["templateCode", "data"],
              "additionalProperties": false
            }
            """;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LowcodeApiCatalogService lowcodeApiCatalogService;
    private final RenderTemplateRegistryService renderTemplateRegistryService;
    private final ObjectMapper objectMapper;

    public FrontendRenderToolService(
            LowcodeApiCatalogService lowcodeApiCatalogService,
            RenderTemplateRegistryService renderTemplateRegistryService,
            ObjectMapper objectMapper) {
        this.lowcodeApiCatalogService = lowcodeApiCatalogService;
        this.renderTemplateRegistryService = renderTemplateRegistryService;
        this.objectMapper = objectMapper;
    }

    public String getTemplateInputSchema() {
        return GET_TEMPLATE_INPUT_SCHEMA;
    }

    public String buildPayloadInputSchema() {
        return BUILD_PAYLOAD_INPUT_SCHEMA;
    }

    public Map<String, Object> getRenderTemplate(Map<String, Object> arguments) {
        Map<String, Object> request = arguments == null ? Map.of() : arguments;
        String templateCode = normalizeText(request.get("templateCode"));
        String targetToolName = normalizeText(request.get("targetToolName"));
        TemplateContext templateContext = resolveTemplateContext(templateCode, targetToolName);
        return toTemplateView(templateContext);
    }

    public Map<String, Object> buildRenderPayload(Map<String, Object> arguments) {
        Map<String, Object> request = arguments == null ? Map.of() : arguments;
        String templateCode = normalizeText(request.get("templateCode"));
        String targetToolName = normalizeText(request.get("targetToolName"));
        String notes = normalizeText(request.get("notes"));
        Map<String, Object> data = asMap(request.get("data"));
        Map<String, Object> componentProps = asMap(request.get("componentProps"));
        if (!StringUtils.hasText(templateCode)) {
            throw new IllegalArgumentException("templateCode 不能为空");
        }
        if (data.isEmpty()) {
            throw new IllegalArgumentException("data 不能为空");
        }

        TemplateContext templateContext = resolveTemplateContext(templateCode, targetToolName);
        Map<String, Object> normalizedData = deepMerge(templateContext.defaultData(), data);
        Map<String, Object> normalizedComponentProps =
                deepMerge(templateContext.defaultComponentProps(), componentProps);

        List<String> missingFields = collectMissingFields(templateContext.fields(), normalizedData);
        List<String> unknownFields = collectUnknownFields(templateContext.fields(), data);
        List<String> messages = new ArrayList<>();
        if (!missingFields.isEmpty()) {
            messages.add("存在待补充字段");
        }

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", missingFields.isEmpty());
        validation.put("missingFields", missingFields);
        validation.put("unknownFields", unknownFields);
        validation.put("messages", messages);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sourceType", templateContext.sourceType());
        if (StringUtils.hasText(targetToolName)) {
            meta.put("sourceRef", targetToolName);
        } else if (StringUtils.hasText(templateContext.sourceRef())) {
            meta.put("sourceRef", templateContext.sourceRef());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "frontend_render");
        result.put("renderId", resolveRenderId(request));
        result.put("templateCode", templateContext.templateCode());
        result.put("componentCode", templateContext.componentCode());
        result.put("componentType", templateContext.componentCode());
        result.put("componentProps", normalizedComponentProps);
        result.put("actions", templateContext.actions());
        result.put("data", normalizedData);
        result.put("validation", validation);
        result.put("state", buildInitialState(templateContext.componentCode(), normalizedComponentProps));
        result.put("meta", meta);
        result.put("schema", buildLegacySchemaView(templateContext));
        result.put("title", normalizeText(normalizedComponentProps.get("title")));
        if (StringUtils.hasText(notes)) {
            result.put("notes", notes);
        } else if (StringUtils.hasText(normalizeText(normalizedComponentProps.get("subtitle")))) {
            result.put("notes", normalizeText(normalizedComponentProps.get("subtitle")));
        }
        if (StringUtils.hasText(targetToolName)) {
            result.put("targetToolName", targetToolName);
        }
        return result;
    }

    private String resolveRenderId(Map<String, Object> request) {
        String renderId = normalizeText(request.get("renderId"));
        return StringUtils.hasText(renderId) ? renderId : UlidGenerator.next();
    }

    private Map<String, Object> buildInitialState(String componentCode, Map<String, Object> componentProps) {
        Map<String, Object> state = new LinkedHashMap<>();
        boolean editable = "StructuredFormCard".equals(componentCode)
                && !Boolean.FALSE.equals(componentProps.get("editable"));
        state.put("stage", "draft");
        state.put("editable", editable);

        Map<String, Object> actionStates = new LinkedHashMap<>();
        if ("StructuredFormCard".equals(componentCode)) {
            actionStates.put("cancel", buildActionState(true));
            actionStates.put("confirm", buildActionState(true));
        }
        state.put("actionStates", actionStates);
        return state;
    }

    private Map<String, Object> buildActionState(boolean enabled) {
        Map<String, Object> actionState = new LinkedHashMap<>();
        actionState.put("enabled", enabled);
        actionState.put("loading", false);
        actionState.put("visible", true);
        return actionState;
    }

    public Map<String, Object> generate(Map<String, Object> arguments) {
        return buildRenderPayload(arguments);
    }

    private TemplateContext resolveTemplateContext(String templateCode, String targetToolName) {
        RenderTemplateRegistryService.RenderTemplateDefinition template =
                renderTemplateRegistryService.requireTemplate(templateCode);
        Map<String, Object> effectiveDataSchema = mutableCopy(template.dataSchema());
        Map<String, Object> effectiveComponentSchema = mutableCopy(template.componentSchema());
        Map<String, Object> effectiveDefaultData = mutableCopy(template.defaultData());
        Map<String, Object> effectiveDefaultComponentProps = mutableCopy(template.defaultComponentProps());
        List<LowcodeApiSchemaResolver.FieldDefinition> effectiveFields = new ArrayList<>();

        String sourceType = normalizeText(template.templateConfig().get("sourceType"));
        String sourceRef = normalizeText(template.templateConfig().get("sourceRef"));
        if ("api_input".equals(sourceType) && StringUtils.hasText(targetToolName)) {
            SchemaContext schemaContext = resolveSchemaContext(targetToolName);
            if (!schemaContext.fields().isEmpty()) {
                effectiveFields.addAll(schemaContext.fields());
                effectiveDataSchema = buildDataSchema(schemaContext.fields());
                effectiveDefaultData = deepMerge(buildDefaultData(schemaContext.fields()), effectiveDefaultData);
                effectiveDefaultComponentProps = deepMerge(
                        effectiveDefaultComponentProps,
                        Map.of("fields", toComponentFields(schemaContext.fields())));
            }
            if (StringUtils.hasText(schemaContext.targetDisplayName())
                    && !StringUtils.hasText(normalizeText(effectiveDefaultComponentProps.get("title")))) {
                effectiveDefaultComponentProps = deepMerge(
                        effectiveDefaultComponentProps,
                        Map.of("title", schemaContext.targetDisplayName() + " 草稿"));
            }
        } else {
            effectiveFields.addAll(extractFieldDefinitionsFromTemplate(effectiveDataSchema, effectiveDefaultComponentProps));
        }

        return new TemplateContext(
                template.templateCode(),
                template.templateName(),
                template.componentCode(),
                effectiveDataSchema,
                effectiveComponentSchema,
                effectiveDefaultData,
                effectiveDefaultComponentProps,
                template.actions(),
                effectiveFields,
                sourceType,
                sourceRef);
    }

    private Map<String, Object> toTemplateView(TemplateContext templateContext) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateCode", templateContext.templateCode());
        result.put("templateName", templateContext.templateName());
        result.put("componentCode", templateContext.componentCode());
        result.put("dataSchema", templateContext.dataSchema());
        result.put("componentSchema", templateContext.componentSchema());
        result.put("defaultData", templateContext.defaultData());
        result.put("defaultComponentProps", templateContext.defaultComponentProps());
        result.put("actions", templateContext.actions());
        result.put("templateConfig", Map.of(
                "sourceType", templateContext.sourceType(),
                "sourceRef", templateContext.sourceRef()));
        return result;
    }

    private SchemaContext resolveSchemaContext(String targetToolName) {
        if (!StringUtils.hasText(targetToolName)) {
            return new SchemaContext("", List.of(), "");
        }
        LowcodeApiCatalog catalog = lowcodeApiCatalogService.findEnabledByToolName(targetToolName);
        if (catalog == null) {
            throw new IllegalArgumentException("未找到目标 API 工具: " + targetToolName);
        }
        LowcodeApiSchemaResolver.ResolvedSchema resolved =
                LowcodeApiSchemaResolver.resolve(catalog.getRemoteSchemaJson(), objectMapper);
        String displayName = StringUtils.hasText(catalog.getApiName())
                ? catalog.getApiName().trim()
                : targetToolName.trim();
        return new SchemaContext(resolved.jsonSchema(), resolved.fields(), displayName);
    }

    private Map<String, Object> buildDataSchema(List<LowcodeApiSchemaResolver.FieldDefinition> fields) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (LowcodeApiSchemaResolver.FieldDefinition field : fields) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", field.jsonType());
            if (StringUtils.hasText(field.label())) {
                property.put("title", field.label());
            }
            if (StringUtils.hasText(field.description())) {
                property.put("description", field.description());
            }
            properties.put(field.key(), property);
            if (field.required()) {
                required.add(field.key());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> buildDefaultData(List<LowcodeApiSchemaResolver.FieldDefinition> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (LowcodeApiSchemaResolver.FieldDefinition field : fields) {
            result.put(field.key(), field.defaultValue());
        }
        return result;
    }

    private List<Map<String, Object>> toComponentFields(List<LowcodeApiSchemaResolver.FieldDefinition> fields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LowcodeApiSchemaResolver.FieldDefinition field : fields) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", field.key());
            row.put("label", StringUtils.hasText(field.label()) ? field.label() : field.key());
            row.put(
                    "description",
                    StringUtils.hasText(field.description()) ? field.description() : (StringUtils.hasText(field.label()) ? field.label() : field.key()));
            row.put("required", field.required());
            row.put("jsonType", field.jsonType());
            result.add(row);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<LowcodeApiSchemaResolver.FieldDefinition> extractFieldDefinitionsFromTemplate(
            Map<String, Object> dataSchema, Map<String, Object> defaultComponentProps) {
        Object fieldsObject = defaultComponentProps.get("fields");
        if (fieldsObject instanceof List<?> rows) {
            List<LowcodeApiSchemaResolver.FieldDefinition> result = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                String key = normalizeText(rawMap.get("key"));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String label = normalizeText(rawMap.get("label"));
                String description = normalizeText(rawMap.get("description"));
                boolean required = rawMap.get("required") instanceof Boolean requiredFlag && requiredFlag;
                String jsonType = normalizeText(rawMap.get("jsonType"));
                result.add(new LowcodeApiSchemaResolver.FieldDefinition(
                        key,
                        StringUtils.hasText(label) ? label : key,
                        StringUtils.hasText(jsonType) ? jsonType : "string",
                        required,
                        description,
                        ""));
            }
            return result;
        }

        Map<String, Object> properties = asMap(dataSchema.get("properties"));
        List<String> requiredKeys = new ArrayList<>();
        Object required = dataSchema.get("required");
        if (required instanceof List<?> rows) {
            for (Object row : rows) {
                if (row != null) {
                    requiredKeys.add(String.valueOf(row));
                }
            }
        }
        List<LowcodeApiSchemaResolver.FieldDefinition> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> property = asMap(entry.getValue());
            String key = entry.getKey();
            String label = normalizeText(property.get("title"));
            String description = normalizeText(property.get("description"));
            String jsonType = normalizeText(property.get("type"));
            result.add(new LowcodeApiSchemaResolver.FieldDefinition(
                    key,
                    StringUtils.hasText(label) ? label : key,
                    StringUtils.hasText(jsonType) ? jsonType : "string",
                    requiredKeys.contains(key),
                    description,
                    ""));
        }
        return result;
    }

    private List<String> collectMissingFields(
            List<LowcodeApiSchemaResolver.FieldDefinition> fields, Map<String, Object> normalizedData) {
        List<String> missingFields = new ArrayList<>();
        for (LowcodeApiSchemaResolver.FieldDefinition field : fields) {
            Object rawValue = normalizedData.get(field.key());
            if (field.required() && isEmptyValue(rawValue)) {
                missingFields.add(field.key());
            }
        }
        return missingFields;
    }

    private List<String> collectUnknownFields(
            List<LowcodeApiSchemaResolver.FieldDefinition> fields, Map<String, Object> inputData) {
        if (fields.isEmpty()) {
            return List.of();
        }
        List<String> unknownFields = new ArrayList<>();
        List<String> knownFields = fields.stream().map(LowcodeApiSchemaResolver.FieldDefinition::key).toList();
        for (String key : inputData.keySet()) {
            if (!knownFields.contains(key)) {
                unknownFields.add(key);
            }
        }
        return unknownFields;
    }

    private Map<String, Object> buildLegacySchemaView(TemplateContext templateContext) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("fields", toComponentFields(templateContext.fields()));
        schema.put("dataSchema", templateContext.dataSchema());
        schema.put("componentSchema", templateContext.componentSchema());
        try {
            schema.put("inputSchema", objectMapper.writeValueAsString(templateContext.dataSchema()));
        } catch (Exception ignored) {
            schema.put("inputSchema", "");
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = mutableCopy(base);
        if (override == null || override.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object current = result.get(entry.getKey());
            Object incoming = entry.getValue();
            if (current instanceof Map<?, ?> currentMap && incoming instanceof Map<?, ?> incomingMap) {
                result.put(
                        entry.getKey(),
                        deepMerge((Map<String, Object>) currentMap, (Map<String, Object>) incomingMap));
                continue;
            }
            result.put(entry.getKey(), incoming);
        }
        return result;
    }

    private Map<String, Object> mutableCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> childMap) {
                result.put(String.valueOf(entry.getKey()), mutableCopy(asMap(childMap)));
                continue;
            }
            if (value instanceof List<?> list) {
                result.put(String.valueOf(entry.getKey()), new ArrayList<>(list));
                continue;
            }
            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private record SchemaContext(
            String jsonSchema,
            List<LowcodeApiSchemaResolver.FieldDefinition> fields,
            String targetDisplayName) {}

    private record TemplateContext(
            String templateCode,
            String templateName,
            String componentCode,
            Map<String, Object> dataSchema,
            Map<String, Object> componentSchema,
            Map<String, Object> defaultData,
            Map<String, Object> defaultComponentProps,
            List<Map<String, Object>> actions,
            List<LowcodeApiSchemaResolver.FieldDefinition> fields,
            String sourceType,
            String sourceRef) {}
}
