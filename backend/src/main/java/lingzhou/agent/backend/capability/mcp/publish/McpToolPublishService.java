package lingzhou.agent.backend.capability.mcp.publish;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.mcp.naming.McpToolNaming;
import lingzhou.agent.backend.common.permission.ResourcePermissionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class McpToolPublishService {

    private final ToolCatalogMapper toolCatalogMapper;

    public McpToolPublishService(ToolCatalogMapper toolCatalogMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
    }

    public Set<String> listEnabledToolNamesBySources(List<String> sources) {
        return toolCatalogMapper.selectToolNamesBySources(sources);
    }

    public List<String> listToolNamesBySource(String source) {
        return toolCatalogMapper.selectBySource(source).stream()
                .map(ToolCatalog::getToolName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    public void deleteBySource(String source) {
        toolCatalogMapper.deleteBySource(source);
    }

    public void syncRemoteTools(
            String serverKey,
            String source,
            List<McpSchema.Tool> remoteTools,
            Long ownerUserId,
            Integer permissionScope) {
        Map<String, ToolCatalog> existingByName = new LinkedHashMap<>();
        for (ToolCatalog catalog : toolCatalogMapper.selectBySource(source)) {
            existingByName.put(catalog.getToolName(), catalog);
        }
        Integer normalizedPermissionScope = normalizePermissionScope(permissionScope);
        Set<String> nextNames = new LinkedHashSet<>();
        int index = 0;
        for (McpSchema.Tool tool : remoteTools) {
            String internalName = McpToolNaming.internalToolName(serverKey, tool.name());
            nextNames.add(internalName);
            ToolCatalog catalog = existingByName.getOrDefault(internalName, new ToolCatalog());
            boolean newRow = catalog.getId() == null;
            catalog.setToolName(internalName);
            catalog.setDisplayName(
                    StringUtils.hasText(tool.title()) ? tool.title().trim() : tool.name());
            catalog.setDescription(
                    StringUtils.hasText(tool.description()) ? tool.description().trim() : "");
            catalog.setToolType("MCP_REMOTE");
            catalog.setBindable(1);
            if (catalog.getEnabledGlobal() == null) {
                catalog.setEnabledGlobal(0);
            }
            catalog.setOwnerSkillName(null);
            catalog.setSource(source);
            catalog.setOwnerUserId(ownerUserId);
            catalog.setPermissionScope(normalizedPermissionScope);
            if (catalog.getSortOrder() == null || catalog.getSortOrder() <= 0) {
                catalog.setSortOrder(50000 + index);
            }
            if (newRow) {
                toolCatalogMapper.insert(catalog);
            } else {
                toolCatalogMapper.updateById(catalog);
            }
            index++;
        }
        for (ToolCatalog catalog : existingByName.values()) {
            if (!nextNames.contains(catalog.getToolName())) {
                toolCatalogMapper.deleteById(catalog.getId());
            }
        }
    }

    public void syncPermissions(String source, Long ownerUserId, Integer permissionScope) {
        List<ToolCatalog> tools = toolCatalogMapper.selectBySource(source);
        if (tools.isEmpty()) {
            return;
        }
        Integer normalizedPermissionScope = normalizePermissionScope(permissionScope);
        for (ToolCatalog tool : tools) {
            tool.setOwnerUserId(ownerUserId);
            tool.setPermissionScope(normalizedPermissionScope);
            toolCatalogMapper.updateById(tool);
        }
    }

    public Map<String, List<McpToolView>> loadToolViewsBySources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return Map.of();
        }
        Map<String, List<McpToolView>> grouped = new LinkedHashMap<>();
        for (ToolCatalog catalog : toolCatalogMapper.selectBySources(sources)) {
            grouped.computeIfAbsent(catalog.getSource(), ignored -> new ArrayList<>())
                    .add(toToolView(catalog));
        }
        return grouped;
    }

    public List<McpToolView> loadToolViews(String source) {
        return toolCatalogMapper.selectBySource(source).stream()
                .map(this::toToolView)
                .toList();
    }

    private McpToolView toToolView(ToolCatalog catalog) {
        String remoteToolName = McpToolNaming.extractRemoteToolName(catalog.getToolName());
        String displayName = StringUtils.hasText(catalog.getDisplayName())
                ? catalog.getDisplayName().trim()
                : (StringUtils.hasText(remoteToolName) ? remoteToolName : catalog.getToolName());
        return new McpToolView(
                catalog.getId(),
                catalog.getToolName(),
                remoteToolName,
                displayName,
                StringUtils.hasText(catalog.getDescription())
                        ? catalog.getDescription().trim()
                        : "",
                catalog.getBindable() != null && catalog.getBindable() == 1,
                catalog.getUpdatedAt());
    }

    private Integer normalizePermissionScope(Integer value) {
        return ResourcePermissionSupport.normalizeScope(value);
    }

    public record McpToolView(
            Long id,
            String toolName,
            String remoteToolName,
            String displayName,
            String description,
            boolean bindable,
            Date updatedAt) {}
}
