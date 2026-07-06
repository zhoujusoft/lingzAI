package lingzhou.agent.backend.skillstudio.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.api.registry.ConnectorToolRegistryService;
import lingzhou.agent.backend.capability.api.registry.LowcodeToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.DatasetToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.KnowledgeBaseToolRegistryService;
import lingzhou.agent.backend.capability.mcp.registry.McpToolRegistryService;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService.ProjectSettingsState;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService.ProjectToolBindingState;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioToolProfileService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ToolCatalogMapper toolCatalogMapper;
    private final GlobalToolRegistry globalToolRegistry;
    private final McpToolRegistryService mcpToolRegistryService;
    private final LowcodeToolRegistryService lowcodeToolRegistryService;
    private final ConnectorToolRegistryService connectorToolRegistryService;
    private final DatasetToolRegistryService datasetToolRegistryService;
    private final KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService;
    private final ObjectMapper objectMapper;

    public SkillStudioToolProfileService(
            ToolCatalogMapper toolCatalogMapper,
            GlobalToolRegistry globalToolRegistry,
            McpToolRegistryService mcpToolRegistryService,
            LowcodeToolRegistryService lowcodeToolRegistryService,
            ConnectorToolRegistryService connectorToolRegistryService,
            DatasetToolRegistryService datasetToolRegistryService,
            KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService,
            ObjectMapper objectMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.globalToolRegistry = globalToolRegistry;
        this.mcpToolRegistryService = mcpToolRegistryService;
        this.lowcodeToolRegistryService = lowcodeToolRegistryService;
        this.connectorToolRegistryService = connectorToolRegistryService;
        this.datasetToolRegistryService = datasetToolRegistryService;
        this.knowledgeBaseToolRegistryService = knowledgeBaseToolRegistryService;
        this.objectMapper = objectMapper;
    }

    public List<SkillStudioContextInput.ToolProfile> buildProfiles(ProjectSettingsState settingsState) {
        if (settingsState == null
                || settingsState.bindings() == null
                || settingsState.bindings().isEmpty()) {
            return List.of();
        }
        List<String> toolNames = settingsState.bindings().stream()
                .filter(ProjectToolBindingState::enabled)
                .map(ProjectToolBindingState::toolName)
                .toList();
        if (toolNames.isEmpty()) {
            return List.of();
        }
        Map<String, ToolCatalog> toolCatalogMap = new LinkedHashMap<>();
        for (ToolCatalog item : toolCatalogMapper.selectByToolNames(toolNames)) {
            if (item != null && StringUtils.hasText(item.getToolName())) {
                toolCatalogMap.put(item.getToolName(), item);
            }
        }
        Map<String, ProjectToolBindingState> bindingMap = new LinkedHashMap<>();
        for (ProjectToolBindingState binding : settingsState.bindings()) {
            if (binding != null && StringUtils.hasText(binding.toolName()) && binding.enabled()) {
                bindingMap.put(binding.toolName(), binding);
            }
        }
        List<SkillStudioContextInput.ToolProfile> profiles = new ArrayList<>();
        for (String toolName : toolNames) {
            profiles.add(buildProfile(toolName, toolCatalogMap.get(toolName), bindingMap.get(toolName)));
        }
        return List.copyOf(profiles);
    }

    private SkillStudioContextInput.ToolProfile buildProfile(
            String toolName, ToolCatalog toolCatalog, ProjectToolBindingState binding) {
        ToolCallback callback = resolveToolCallback(toolName);
        String inputSchema = callback == null || callback.getToolDefinition() == null
                ? ""
                : normalizeText(callback.getToolDefinition().inputSchema());
        SchemaSummary schemaSummary = summarizeInputSchema(inputSchema);
        String description = toolCatalog != null ? normalizeText(toolCatalog.getDescription()) : "";
        String displayName = toolCatalog != null && StringUtils.hasText(toolCatalog.getDisplayName())
                ? toolCatalog.getDisplayName().trim()
                : toolName;
        String toolType = toolCatalog != null && StringUtils.hasText(toolCatalog.getToolType())
                ? toolCatalog.getToolType().trim()
                : resolveToolType(toolName);
        String source = toolCatalog != null && StringUtils.hasText(toolCatalog.getSource())
                ? toolCatalog.getSource().trim()
                : "runtime";

        List<String> usageHints = new ArrayList<>();
        if (binding != null && StringUtils.hasText(binding.businessPurpose())) {
            usageHints.add("项目用途：" + binding.businessPurpose());
        }
        if (binding != null
                && binding.triggerHints() != null
                && !binding.triggerHints().isEmpty()) {
            usageHints.add("优先触发词：" + String.join("、", binding.triggerHints()));
        }
        usageHints.addAll(defaultUsageHints(toolName, toolType));

        return new SkillStudioContextInput.ToolProfile(
                toolName,
                displayName,
                toolType,
                source,
                buildCapabilitySummary(toolName, toolType, description, binding),
                schemaSummary.requiredParams(),
                schemaSummary.schemaSummary(),
                schemaSummary.keyFields(),
                List.copyOf(new LinkedHashSet<>(usageHints)));
    }

    private ToolCallback resolveToolCallback(String toolName) {
        ToolCallback callback = globalToolRegistry.findBindableByName(toolName);
        if (callback != null) {
            return callback;
        }
        callback = mcpToolRegistryService.findByName(toolName);
        if (callback != null) {
            return callback;
        }
        callback = lowcodeToolRegistryService.findByName(toolName);
        if (callback != null) {
            return callback;
        }
        callback = connectorToolRegistryService.findByName(toolName);
        if (callback != null) {
            return callback;
        }
        callback = knowledgeBaseToolRegistryService.findByName(toolName);
        if (callback != null) {
            return callback;
        }
        return datasetToolRegistryService.findByName(toolName);
    }

    private SchemaSummary summarizeInputSchema(String inputSchema) {
        if (!StringUtils.hasText(inputSchema)) {
            return new SchemaSummary(List.of(), List.of(), List.of());
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(inputSchema, MAP_TYPE);
            List<String> requiredParams = normalizeStringList(payload.get("required"));
            Map<String, Object> properties = payload.get("properties") instanceof Map<?, ?> rawProperties
                    ? normalizeProperties(rawProperties)
                    : Map.of();
            List<String> keyFields = new ArrayList<>();
            List<String> schemaSummary = new ArrayList<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (keyFields.size() < 8) {
                    keyFields.add(entry.getKey());
                }
                if (schemaSummary.size() >= 8) {
                    continue;
                }
                if (entry.getValue() instanceof Map<?, ?> propertyMap) {
                    String type = valueAsText(propertyMap.get("type"));
                    String title = valueAsText(propertyMap.get("title"));
                    String description = valueAsText(propertyMap.get("description"));
                    StringBuilder summary = new StringBuilder(entry.getKey());
                    if (StringUtils.hasText(title) && !Objects.equals(title, entry.getKey())) {
                        summary.append("（").append(title).append("）");
                    }
                    if (StringUtils.hasText(type)) {
                        summary.append(": ").append(type);
                    }
                    if (requiredParams.contains(entry.getKey())) {
                        summary.append(" [必填]");
                    }
                    if (StringUtils.hasText(description)) {
                        summary.append(" - ").append(description);
                    }
                    schemaSummary.add(summary.toString());
                }
            }
            return new SchemaSummary(List.copyOf(requiredParams), List.copyOf(schemaSummary), List.copyOf(keyFields));
        } catch (Exception ex) {
            return new SchemaSummary(List.of(), List.of("输入参数为通用 JSON 对象"), List.of());
        }
    }

    private Map<String, Object> normalizeProperties(Map<?, ?> rawProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawProperties.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            properties.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return properties;
    }

    private List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            String normalized = valueAsText(item);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private String buildCapabilitySummary(
            String toolName, String toolType, String description, ProjectToolBindingState binding) {
        if (StringUtils.hasText(description)) {
            return description;
        }
        if ("LOWCODE_API".equals(toolType)) {
            return "用于调用低代码平台 API，适合查询或提交外部业务数据。";
        }
        if ("CONNECTOR_API".equals(toolType)) {
            return "用于调用自定义外部 API，可按配置自动注入当前用户与角色身份参数。";
        }
        if ("DATASET_TOOL".equals(toolType)) {
            return "用于数据集查询，适合先看 summary，再看 schema，最后执行只读 SQL。";
        }
        if ("KNOWLEDGE_BASE_TOOL".equals(toolType)) {
            return "用于知识库检索，适合问答、制度、流程与规则类场景。";
        }
        if ("MCP_REMOTE".equals(toolType)) {
            return "用于调用远端 MCP 工具，能力边界由远端 server 提供。";
        }
        if (binding != null && StringUtils.hasText(binding.businessPurpose())) {
            return "项目内主要用途：" + binding.businessPurpose();
        }
        return "请按当前工具定义使用，不要虚构额外参数或返回字段。";
    }

    private List<String> defaultUsageHints(String toolName, String toolType) {
        if ("DATASET_TOOL".equals(toolType)) {
            return List.of(
                    "推荐顺序：先 summary，再 schema，最后 SQL。",
                    "SQL 只允许只读查询。",
                    "SQL 返回 rows 后如需 Python 二次处理，先用 file_write 保存为 /workspace/*.json、/workspace/*.jsonl 或 /workspace/*.csv，再让 Python 读取文件；不要把 rows 直接嵌入 .py 源码。");
        }
        if ("LOWCODE_API".equals(toolType)) {
            return List.of("优先遵循工具入参 schema，不要虚构字段名。");
        }
        if ("CONNECTOR_API".equals(toolType)) {
            return List.of("优先遵循工具入参 schema，连接器会按配置自动注入当前用户/角色身份参数。");
        }
        if ("KNOWLEDGE_BASE_TOOL".equals(toolType)) {
            return List.of("适合检索制度、流程、说明类内容。");
        }
        if ("MCP_REMOTE".equals(toolType)) {
            return List.of("入参与返回结构由远端 MCP schema 决定。");
        }
        if (toolName.endsWith(".search_dataset_summary")) {
            return List.of("先用该工具理解候选数据对象。");
        }
        return List.of();
    }

    private String resolveToolType(String toolName) {
        if (toolName == null) {
            return "GLOBAL";
        }
        if (toolName.startsWith("lowcode.")) {
            return "LOWCODE_API";
        }
        if (toolName.startsWith("connector.")) {
            return "CONNECTOR_API";
        }
        if (toolName.endsWith(".search_dataset_summary")
                || toolName.endsWith(".get_dataset_schema")
                || toolName.endsWith(".execute_dataset_sql")) {
            return "DATASET_TOOL";
        }
        return "GLOBAL";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueAsText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record SchemaSummary(List<String> requiredParams, List<String> schemaSummary, List<String> keyFields) {}
}
