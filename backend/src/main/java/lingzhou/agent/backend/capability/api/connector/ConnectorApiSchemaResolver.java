package lingzhou.agent.backend.capability.api.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class ConnectorApiSchemaResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String GENERIC_OBJECT_SCHEMA =
            """
            {
              "type": "object",
              "additionalProperties": true
            }
            """;

    private ConnectorApiSchemaResolver() {}

    public static ResolvedSchema resolve(String inputSchemaJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(inputSchemaJson) || objectMapper == null) {
            return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
        }
        try {
            Object raw = objectMapper.readValue(inputSchemaJson, Object.class);
            if (raw instanceof List<?> list) {
                return resolveFromParamList(list, objectMapper);
            }
            Map<String, Object> root = normalizeProperties(raw);
            if (root == null || !"object".equals(String.valueOf(root.getOrDefault("type", "object")).trim())) {
                return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
            }
            Map<String, Object> properties = root.get("properties") instanceof Map<?, ?> rawProperties
                    ? normalizeProperties(rawProperties)
                    : Map.of();
            List<String> required = normalizeStringList(root.get("required"));
            List<FieldDefinition> fields = new ArrayList<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> propertyMap)) {
                    continue;
                }
                String type = valueAsText(propertyMap.get("type"));
                String title = valueAsText(propertyMap.get("title"));
                String description = valueAsText(propertyMap.get("description"));
                fields.add(new FieldDefinition(
                        entry.getKey(),
                        StringUtils.hasText(title) ? title : entry.getKey(),
                        StringUtils.hasText(type) ? type : "string",
                        required.contains(entry.getKey()),
                        description));
            }
            return new ResolvedSchema(objectMapper.writeValueAsString(root), List.copyOf(fields));
        } catch (Exception ex) {
            return new ResolvedSchema(GENERIC_OBJECT_SCHEMA, List.of());
        }
    }

    private static ResolvedSchema resolveFromParamList(List<?> list, ObjectMapper objectMapper) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<FieldDefinition> fields = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> row = normalizeProperties(item);
            String name = valueAsText(row.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String type = normalizeJsonType(valueAsText(row.get("paramType")));
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", type);
            property.put("title", name);
            String description = valueAsText(firstNonNull(row.get("desc"), row.get("description")));
            if (StringUtils.hasText(description)) {
                property.put("description", description);
            }
            properties.put(name, property);
            fields.add(new FieldDefinition(name, name, type, false, description));
        }
        root.put("properties", properties);
        root.put("required", required);
        return new ResolvedSchema(objectMapper.writeValueAsString(root), List.copyOf(fields));
    }

    private static Map<String, Object> normalizeProperties(Object rawProperties) {
        if (!(rawProperties instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            properties.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return properties;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeJsonType(String type) {
        String normalized = valueAsText(type).toLowerCase();
        return switch (normalized) {
            case "number", "integer", "long", "double" -> "number";
            case "boolean", "bool" -> "boolean";
            case "object" -> "object";
            case "array", "objectarray", "list" -> "array";
            default -> "string";
        };
    }

    private static List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            String text = valueAsText(value);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static String valueAsText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record FieldDefinition(String key, String label, String jsonType, boolean required, String description) {}

    public record ResolvedSchema(String jsonSchema, List<FieldDefinition> fields) {}
}
