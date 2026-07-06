package lingzhou.agent.backend.capability.tool.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.api.registry.ConnectorToolRegistryService;
import lingzhou.agent.backend.capability.api.registry.LowcodeToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.DatasetToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.KnowledgeBaseToolRegistryService;
import lingzhou.agent.backend.capability.mcp.registry.McpToolRegistryService;
import lingzhou.agent.backend.capability.tool.ToolCallbackSupport;
import lingzhou.agent.backend.common.enums.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolLibraryCallbackResolver {

    private static final Logger logger = LoggerFactory.getLogger(ToolLibraryCallbackResolver.class);

    private final ToolCatalogMapper toolCatalogMapper;
    private final GlobalToolRegistry globalToolRegistry;
    private final McpToolRegistryService mcpToolRegistryService;
    private final LowcodeToolRegistryService lowcodeToolRegistryService;
    private final ConnectorToolRegistryService connectorToolRegistryService;
    private final DatasetToolRegistryService datasetToolRegistryService;
    private final KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService;
    private final SysUserMapper sysUserMapper;
    private final RoleResourcePermissionService roleResourcePermissionService;

    public ToolLibraryCallbackResolver(
            ToolCatalogMapper toolCatalogMapper,
            GlobalToolRegistry globalToolRegistry,
            McpToolRegistryService mcpToolRegistryService,
            LowcodeToolRegistryService lowcodeToolRegistryService,
            ConnectorToolRegistryService connectorToolRegistryService,
            DatasetToolRegistryService datasetToolRegistryService,
            KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService,
            SysUserMapper sysUserMapper,
            RoleResourcePermissionService roleResourcePermissionService) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.globalToolRegistry = globalToolRegistry;
        this.mcpToolRegistryService = mcpToolRegistryService;
        this.lowcodeToolRegistryService = lowcodeToolRegistryService;
        this.connectorToolRegistryService = connectorToolRegistryService;
        this.datasetToolRegistryService = datasetToolRegistryService;
        this.knowledgeBaseToolRegistryService = knowledgeBaseToolRegistryService;
        this.sysUserMapper = sysUserMapper;
        this.roleResourcePermissionService = roleResourcePermissionService;
    }

    public ToolCallback findByName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        String normalized = toolName.trim();
        ToolCallback callback = globalToolRegistry.findByName(normalized);
        if (callback != null) {
            return callback;
        }
        callback = mcpToolRegistryService.findByName(normalized);
        if (callback != null) {
            return callback;
        }
        callback = lowcodeToolRegistryService.findByName(normalized);
        if (callback != null) {
            return callback;
        }
        callback = connectorToolRegistryService.findByName(normalized);
        if (callback != null) {
            return callback;
        }
        callback = knowledgeBaseToolRegistryService.findByName(normalized);
        if (callback != null) {
            return callback;
        }
        return datasetToolRegistryService.findByName(normalized);
    }

    public List<ToolCallback> listAllEnabledGlobalCallbacks() {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolCatalog catalog : toolCatalogMapper.selectGlobalEnabledOrdered()) {
            if (!isDialogGlobalAvailable(catalog)) {
                continue;
            }
            ToolCallback callback = findByName(catalog.getToolName());
            String resolvedToolName = ToolCallbackSupport.resolveToolName(callback);
            if (!StringUtils.hasText(resolvedToolName)) {
                logger.warn(
                        "全局可用工具无法解析为 ToolCallback：toolName={}, toolType={}, source={}",
                        catalog.getToolName(),
                        catalog.getToolType(),
                        catalog.getSource());
                continue;
            }
            callbacks.putIfAbsent(resolvedToolName, callback);
        }
        return List.copyOf(callbacks.values());
    }

    public List<ToolCallback> listEnabledGlobalCallbacks(Long userId) {
        if (userId == null) {
            return List.of();
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            return List.of();
        }
        boolean isSuperAdmin = user.getUserType() != null && user.getUserType() == UserType.admin.getValue();
        if (!isSuperAdmin && StringUtils.hasText(user.getCode()) && "admin".equalsIgnoreCase(user.getCode().trim())) {
            isSuperAdmin = true;
        }

        List<Long> permittedToolIds = new ArrayList<>();
        if (!isSuperAdmin && user.getRoleId() != null) {
            permittedToolIds = roleResourcePermissionService.getRoleToolIds(user.getRoleId());
        }

        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();

        List<ToolCatalog> catalogs = isSuperAdmin
                ? toolCatalogMapper.selectGlobalEnabledOrdered()
                : toolCatalogMapper.selectGlobalEnabledOrPermittedOrdered(permittedToolIds);

        for (ToolCatalog catalog : catalogs) {
            if (!isUserDialogAvailable(catalog, isSuperAdmin, permittedToolIds)) {
                continue;
            }

            ToolCallback callback = findByName(catalog.getToolName());
            String resolvedToolName = ToolCallbackSupport.resolveToolName(callback);
            if (!StringUtils.hasText(resolvedToolName)) {
                logger.warn(
                        "全局可用工具无法解析为 ToolCallback：toolName={}, toolType={}, source={}",
                        catalog.getToolName(),
                        catalog.getToolType(),
                        catalog.getSource());
                continue;
            }
            callbacks.putIfAbsent(resolvedToolName, callback);
        }
        return List.copyOf(callbacks.values());
    }

    public boolean isGlobalAvailabilityEditable(ToolCatalog catalog) {
        if (catalog == null || !StringUtils.hasText(catalog.getToolType())) {
            return false;
        }
        String toolType = catalog.getToolType().trim();
        // GLOBAL 和 RUNTIME 始终全局可用，不允许编辑
        // SKILL_NATIVE 由技能管理，不允许编辑
        return !Objects.equals(toolType, "GLOBAL")
                && !Objects.equals(toolType, "RUNTIME")
                && !Objects.equals(toolType, "SKILL_NATIVE");
    }

    private boolean isDialogGlobalAvailable(ToolCatalog catalog) {
        return catalog != null
                && catalog.getEnabledGlobal() != null
                && catalog.getEnabledGlobal() == 1
                && isGlobalAvailabilityEditable(catalog);
    }

    private boolean isUserDialogAvailable(
            ToolCatalog catalog, boolean isSuperAdmin, List<Long> permittedToolIds) {
        if (catalog == null || !isGlobalAvailabilityEditable(catalog)) {
            return false;
        }
        if (catalog.getEnabledGlobal() != null && catalog.getEnabledGlobal() == 1) {
            return true;
        }
        if (isSuperAdmin) {
            return false;
        }
        return catalog.getId() != null && permittedToolIds != null && permittedToolIds.contains(catalog.getId());
    }
}
