package lingzhou.agent.backend.capability.api.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class LowcodeApiSchemaResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String GENERIC_OBJECT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": true
            }
            """;

    private LowcodeApiSchemaResolver() {}

    public static ResolvedSchema resolve(String remoteSchemaJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(remoteSchemaJson) || objectMapper == null) {
            return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
        }
        try {
            Map<String, Object> root = objectMapper.readValue(remoteSchemaJson, MAP_TYPE);
            Object inputParams = root == null ? null : root.get("inputParams");
            List<FieldDefinition> fields = buildFields(inputParams);
            if (fields.isEmpty()) {
                return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
            }

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("additionalProperties", true);

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (FieldDefinition field : fields) {
                Map<String, Object> property = new LinkedHashMap<>();
                property.put("type", field.jsonType());
                if (StringUtils.hasText(field.label())) {
                    property.put("title", field.label());
                }
                if (StringUtils.hasText(field.description())) {
                    property.put("description", field.description());
                }
                if ("array".equals(field.jsonType())) {
                    property.put("items", Map.of("type", "object"));
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
            return new ResolvedSchema(objectMapper.writeValueAsString(schema), List.copyOf(fields));
        } catch (Exception ex) {
            return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
        }
    }

    private static List<FieldDefinition> buildFields(Object inputParams) {
        if (inputParams instanceof List<?> rows) {
            List<FieldDefinition> fields = new ArrayList<>();
            for (Object row : rows) {
                FieldDefinition field = toFieldDefinition(row, "");
                if (field != null) {
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
        if (inputParams instanceof Map<?, ?> map) {
            List<FieldDefinition> fields = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fallbackKey = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
                if (!StringUtils.hasText(fallbackKey)) {
                    continue;
                }
                FieldDefinition field = toFieldDefinition(entry.getValue(), fallbackKey);
                if (field != null) {
                    fields.add(field);
                    continue;
                }
                String jsonType = resolveJsonType(entry.getValue());
                fields.add(new FieldDefinition(fallbackKey, fallbackKey, jsonType, false, "", defaultValue(jsonType)));
            }
            return List.copyOf(fields);
        }
        return List.of();
    }

    private static FieldDefinition toFieldDefinition(Object raw, String fallbackKey) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        String key = firstText(
                map.get("name"),
                map.get("field"),
                map.get("code"),
                map.get("paramName"),
                map.get("key"),
                fallbackKey);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String label = firstText(
                map.get("label"),
                map.get("title"),
                map.get("displayName"),
                map.get("displayLabel"),
                map.get("text"),
                map.get("fieldLabel"),
                map.get("fieldName"),
                map.get("fieldCnName"),
                map.get("paramLabel"),
                key);
        String jsonType = resolveJsonType(
                firstText(map.get("type"), map.get("dataType"), map.get("valueType"), map.get("paramType")));
        boolean required = resolveRequired(map.get("required"), map.get("isRequired"), map.get("must"));
        String description = firstText(
                map.get("description"),
                map.get("desc"),
                map.get("remark"),
                map.get("comment"),
                map.get("memo"),
                map.get("helpText"),
                map.get("tips"),
                map.get("fieldDesc"),
                map.get("paramDesc"),
                map.get("placeholder"));
        return new FieldDefinition(key, label, jsonType, required, description, defaultValue(jsonType));
    }

    private static String resolveJsonType(Object rawType) {
        if (rawType == null) {
            return "string";
        }
        String normalized = String.valueOf(rawType).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "2", "3", "number", "double", "float", "int", "integer", "long", "decimal" -> "number";
            case "4", "object", "map" -> "object";
            case "5", "6", "array", "objectarray", "list" -> "array";
            case "7", "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static boolean resolveRequired(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if ("1".equals(text) || "true".equals(text) || "yes".equals(text) || "y".equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static Object defaultValue(String jsonType) {
        return switch (jsonType) {
            case "object" -> Map.of();
            case "array" -> List.of();
            case "boolean" -> Boolean.FALSE;
            default -> "";
        };
    }

    public record FieldDefinition(
            String key,
            String label,
            String jsonType,
            boolean required,
            String description,
            Object defaultValue) {}

    public record ResolvedSchema(String jsonSchema, List<FieldDefinition> fields) {}
}
