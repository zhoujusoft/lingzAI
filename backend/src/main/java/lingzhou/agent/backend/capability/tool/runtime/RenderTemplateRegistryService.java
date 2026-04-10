package lingzhou.agent.backend.capability.tool.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RenderTemplateRegistryService {

    private static final String TEMPLATE_RESOURCE_PATH = "render-templates/templates.json";
    private static final TypeReference<List<RenderTemplateDefinition>> TEMPLATE_LIST_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;
    private volatile Map<String, RenderTemplateDefinition> templateIndex = Map.of();

    public RenderTemplateRegistryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderTemplateDefinition requireTemplate(String templateCode) {
        String normalizedCode = normalize(templateCode);
        if (!StringUtils.hasText(normalizedCode)) {
            throw new IllegalArgumentException("templateCode 不能为空");
        }
        RenderTemplateDefinition definition = templates().get(normalizedCode);
        if (definition == null) {
            throw new IllegalArgumentException("未找到前端渲染模板: " + normalizedCode);
        }
        return definition;
    }

    private Map<String, RenderTemplateDefinition> templates() {
        Map<String, RenderTemplateDefinition> snapshot = this.templateIndex;
        if (!snapshot.isEmpty()) {
            return snapshot;
        }
        synchronized (this) {
            if (!this.templateIndex.isEmpty()) {
                return this.templateIndex;
            }
            this.templateIndex = loadTemplates();
            return this.templateIndex;
        }
    }

    private Map<String, RenderTemplateDefinition> loadTemplates() {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_RESOURCE_PATH);
            try (InputStream inputStream = resource.getInputStream()) {
                List<RenderTemplateDefinition> definitions = objectMapper.readValue(inputStream, TEMPLATE_LIST_TYPE);
                Map<String, RenderTemplateDefinition> index = new LinkedHashMap<>();
                for (RenderTemplateDefinition definition : definitions) {
                    if (definition == null || !StringUtils.hasText(definition.templateCode())) {
                        continue;
                    }
                    index.put(definition.templateCode().trim(), definition.normalized());
                }
                return Map.copyOf(index);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("加载前端渲染模板失败", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record RenderTemplateDefinition(
            String templateCode,
            String templateName,
            String componentCode,
            Map<String, Object> dataSchema,
            Map<String, Object> componentSchema,
            Map<String, Object> defaultData,
            Map<String, Object> defaultComponentProps,
            Map<String, Object> templateConfig,
            List<Map<String, Object>> actions) {

        RenderTemplateDefinition normalized() {
            return new RenderTemplateDefinition(
                    normalizeText(templateCode),
                    normalizeText(templateName),
                    normalizeText(componentCode),
                    copyMap(dataSchema),
                    copyMap(componentSchema),
                    copyMap(defaultData),
                    copyMap(defaultComponentProps),
                    copyMap(templateConfig),
                    copyList(actions));
        }

        private static String normalizeText(String value) {
            return value == null ? "" : value.trim();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> copyMap(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> childMap) {
                    result.put(String.valueOf(entry.getKey()), copyMap((Map<String, Object>) childMap));
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), value);
            }
            return Map.copyOf(result);
        }

        private static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
            if (source == null || source.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : source) {
                result.add(copyMap(item));
            }
            return List.copyOf(result);
        }
    }
}
