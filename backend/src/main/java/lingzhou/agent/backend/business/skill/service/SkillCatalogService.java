package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lingzhou.agent.backend.business.datasets.service.KnowledgeBasePermissionService;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.domain.SkillPackageInstall;
import lingzhou.agent.backend.business.skill.domain.SkillPublishBinding;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPackageInstallMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPublishBindingMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.tool.ToolCallbackSupport;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.capability.tool.registry.ToolLibraryCallbackResolver;
import lingzhou.agent.backend.common.enums.ResourcePermissionScope;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillCatalogService {

    private static final String BINDING_TYPE_NATIVE = "NATIVE";
    private static final String BINDING_TYPE_MANUAL = "MANUAL";
    private static final String TOOL_BINDING_STATUS_READY = "READY";
    private static final String TOOL_TYPE_KNOWLEDGE_BASE = "KNOWLEDGE_BASE_TOOL";
    private static final String TOOL_TYPE_MCP_REMOTE = "MCP_REMOTE";
    private static final String TOOL_TYPE_DATASET = "DATASET_TOOL";
    private static final String TOOL_TYPE_CONNECTOR_API = "CONNECTOR_API";
    private static final ZoneId PROMPT_TIME_ZONE = ZoneId.systemDefault();
    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillPackageInstallMapper skillPackageInstallMapper;
    private final SkillPublishBindingMapper skillPublishBindingMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final ToolCatalogMapper toolCatalogMapper;
    private final SkillKit skillKit;
    private final SimpleSkillBox skillBox;
    private final GlobalToolRegistry globalToolRegistry;
    private final ToolLibraryCallbackResolver toolLibraryCallbackResolver;
    private final SkillRecommendationService skillRecommendationService;
    private final KnowledgeBasePermissionService knowledgeBasePermissionService;
    private final RoleResourcePermissionService roleResourcePermissionService;
    private final ObjectMapper objectMapper;
    private final RuntimeV2ContractSupport runtimeV2ContractSupport;

    public SkillCatalogService(
            SkillCatalogMapper skillCatalogMapper,
            SkillPackageInstallMapper skillPackageInstallMapper,
            SkillPublishBindingMapper skillPublishBindingMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            ToolCatalogMapper toolCatalogMapper,
            SkillKit skillKit,
            SimpleSkillBox skillBox,
            GlobalToolRegistry globalToolRegistry,
            ToolLibraryCallbackResolver toolLibraryCallbackResolver,
            SkillRecommendationService skillRecommendationService,
            KnowledgeBasePermissionService knowledgeBasePermissionService,
            RoleResourcePermissionService roleResourcePermissionService,
            ObjectMapper objectMapper,
            RuntimeV2ContractSupport runtimeV2ContractSupport) {
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillPackageInstallMapper = skillPackageInstallMapper;
        this.skillPublishBindingMapper = skillPublishBindingMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.toolCatalogMapper = toolCatalogMapper;
        this.skillKit = skillKit;
        this.skillBox = skillBox;
        this.globalToolRegistry = globalToolRegistry;
        this.toolLibraryCallbackResolver = toolLibraryCallbackResolver;
        this.skillRecommendationService = skillRecommendationService;
        this.knowledgeBasePermissionService = knowledgeBasePermissionService;
        this.roleResourcePermissionService = roleResourcePermissionService;
        this.objectMapper = objectMapper;
        this.runtimeV2ContractSupport = runtimeV2ContractSupport;
    }

    public List<RuntimeSkillSummary> listRuntimeSkills() {
        return loadRuntimeMetadata().values().stream()
                .sorted(Comparator.comparing(SkillMetadata::getName))
                .map(metadata -> {
                    SkillCatalogLocalization.SkillLabel label =
                            SkillCatalogLocalization.resolveSkill(metadata.getName(), metadata.getDescription());
                    return new RuntimeSkillSummary(
                            metadata.getName(),
                            label.displayName(),
                            label.description(),
                            metadata.getSource(),
                            skillKit.isActivated(metadata.getName()),
                            metadata.getExtensions());
                })
                .toList();
    }

    public List<SkillCatalogView> listCatalogs(Long userId, boolean visibleOnly) {
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
        List<SkillCatalog> rows =
                visibleOnly ? skillCatalogMapper.selectVisibleOrdered() : skillCatalogMapper.selectAllOrdered();
        rows = rows.stream()
                .filter(row -> runtimeMetadata.containsKey(row.getRuntimeSkillName()))
                .toList();
        Map<Long, List<String>> bindingMap =
                loadManualBindingMap(rows.stream().map(SkillCatalog::getId).toList());
        Map<Long, SkillPublishBinding> publishBindingMap =
                loadPublishBindingMap(rows.stream().map(SkillCatalog::getId).toList());
        Map<Long, List<ToolLibraryItem>> nativeToolMap =
                loadNativeToolMap(rows.stream().map(SkillCatalog::getId).toList());
        Map<Long, SkillRecommendationService.RecommendationProfile> recommendationMap =
                skillRecommendationService.buildRecommendationMap(userId, rows);
        return rows.stream()
                .map(row -> {
                    List<ToolLibraryItem> runtimeTools = nativeToolMap.getOrDefault(row.getId(), List.of());
                    if (runtimeTools.isEmpty()) {
                        runtimeTools = loadRuntimeToolItems(row.getRuntimeSkillName());
                    }
                    boolean canViewDetail = canAccessSkillDetail(row, operator);
                    boolean canDelete = canDeleteSkill(row, operator);
                    return toCatalogView(
                            row,
                            bindingMap.getOrDefault(row.getId(), List.of()),
                            publishBindingMap.get(row.getId()),
                            runtimeTools,
                            recommendationMap.get(row.getId()),
                            canViewDetail,
                            canDelete);
                })
                .toList();
    }

    public boolean canAccessSkillDetail(Long userId, SkillCatalog catalog) {
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        return canAccessSkillDetail(catalog, operator);
    }

    public void assertCanAccessSkillDetail(Long userId, SkillCatalog catalog) throws TaskException {
        if (!canAccessSkillDetail(userId, catalog)) {
            throw new TaskException("仅创建人或系统管理员可查看技能详情或导出技能包", TaskException.Code.UNKNOWN);
        }
    }

    public boolean canDeleteSkill(Long userId, SkillCatalog catalog) {
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        return canDeleteSkill(catalog, operator);
    }

    public void assertCanDeleteSkill(Long userId, SkillCatalog catalog) throws TaskException {
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        if (!canDeleteSkill(userId, catalog)) {
            throw new TaskException("仅创建人或系统管理员可删除技能", TaskException.Code.UNKNOWN);
        }
    }

    public List<ToolLibraryItem> listToolLibrary(Long userId) {
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        boolean isSuperAdmin = isAdminUserType(operator);

        // 获取用户的工具权限
        final List<Long> permittedToolIds;
        if (isSuperAdmin) {
            permittedToolIds = List.of();
        } else if (operator != null && operator.getRoleId() != null) {
            permittedToolIds = roleResourcePermissionService.getRoleToolIds(operator.getRoleId());
        } else {
            permittedToolIds = List.of();
        }

        Set<String> runtimeToolNames = loadRuntimeToolNames(loadRuntimeMetadata());
        Map<String, ToolLibraryItem> items = new LinkedHashMap<>();
        toolCatalogMapper.selectAllOrdered().stream()
                .filter(row -> runtimeToolNames.contains(row.getToolName())
                        || Objects.equals(row.getToolType(), "GLOBAL")
                        || Objects.equals(row.getToolType(), "RUNTIME")
                        || Objects.equals(row.getToolType(), TOOL_TYPE_MCP_REMOTE)
                        || Objects.equals(row.getToolType(), "LOWCODE_API")
                        || Objects.equals(row.getToolType(), TOOL_TYPE_CONNECTOR_API)
                        || Objects.equals(row.getToolType(), "DATASET_TOOL")
                        || Objects.equals(row.getToolType(), TOOL_TYPE_KNOWLEDGE_BASE))
                .filter(row -> canViewInToolLibrary(row, operator))
                // 权限过滤：管理员看全部，普通用户只看有权限的
                .filter(row -> isSuperAdmin
                        || (row.getEnabledGlobal() != null && row.getEnabledGlobal() == 1)
                        || permittedToolIds.contains(row.getId()))
                .map(row -> toToolLibraryItemForLibrary(row, operator))
                .forEach(item -> items.put(item.name(), item));
        for (GlobalToolRegistry.ToolDescriptor descriptor : globalToolRegistry.getDescriptors()) {
            if (!StringUtils.hasText(descriptor.name())) {
                continue;
            }
            items.putIfAbsent(descriptor.name(), toGlobalRegistryToolItem(descriptor));
        }
        return List.copyOf(items.values());
    }

    @Transactional(rollbackFor = Exception.class)
    public ToolLibraryItem updateToolGlobalAvailability(String toolName, Boolean enabledGlobal, Long operatorUserId)
            throws TaskException {
        String normalizedToolName = normalizeRequired(toolName, "工具名称不能为空");
        if (enabledGlobal == null) {
            throw new TaskException("全局可用开关不能为空", TaskException.Code.UNKNOWN);
        }
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(operatorUserId);
        if (!isAdminUserType(operator)) {
            throw new TaskException("仅管理员用户可修改全局可用状态", TaskException.Code.UNKNOWN);
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(normalizedToolName);
        if (catalog == null) {
            throw new TaskException("工具不存在：" + normalizedToolName, TaskException.Code.UNKNOWN);
        }
        if (!toolLibraryCallbackResolver.isGlobalAvailabilityEditable(catalog)) {
            throw new TaskException("当前工具不支持修改全局可用状态", TaskException.Code.UNKNOWN);
        }
        catalog.setEnabledGlobal(Boolean.TRUE.equals(enabledGlobal) ? 1 : 0);
        toolCatalogMapper.updateById(catalog);
        return toToolLibraryItem(catalog);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ToolLibraryItem> batchUpdateToolGlobalAvailability(
            List<String> toolNames, Boolean enabledGlobal, Long operatorUserId) throws TaskException {
        if (toolNames == null || toolNames.isEmpty()) {
            throw new TaskException("工具名称列表不能为空", TaskException.Code.UNKNOWN);
        }
        if (enabledGlobal == null) {
            throw new TaskException("全局可用开关不能为空", TaskException.Code.UNKNOWN);
        }
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(operatorUserId);
        if (!isAdminUserType(operator)) {
            throw new TaskException("仅管理员用户可修改全局可用状态", TaskException.Code.UNKNOWN);
        }
        int enabledValue = Boolean.TRUE.equals(enabledGlobal) ? 1 : 0;
        List<ToolLibraryItem> results = new ArrayList<>();
        for (String toolName : toolNames) {
            String normalizedToolName = normalizeRequired(toolName, "工具名称不能为空");
            ToolCatalog catalog = toolCatalogMapper.selectByToolName(normalizedToolName);
            if (catalog == null) {
                throw new TaskException("工具不存在：" + normalizedToolName, TaskException.Code.UNKNOWN);
            }
            if (!toolLibraryCallbackResolver.isGlobalAvailabilityEditable(catalog)) {
                throw new TaskException(
                        "当前工具不支持修改全局可用状态：" + normalizedToolName, TaskException.Code.UNKNOWN);
            }
            catalog.setEnabledGlobal(enabledValue);
            toolCatalogMapper.updateById(catalog);
            results.add(toToolLibraryItem(catalog));
        }
        return results;
    }

    @Transactional(rollbackFor = Exception.class)
    public SkillCatalogView updateCatalog(Long skillId, SkillCatalogUpdateCommand command) throws TaskException {
        Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, runtimeMetadata);
        String displayName = normalizeRequired(command.displayName(), "展示名称不能为空");
        String description = normalizeRequired(command.description(), "技能描述不能为空");
        String category = normalizeRequired(command.category(), "业务能力分类不能为空");
        if (Boolean.TRUE.equals(command.visible()) && !isToolBindingReady(catalog)) {
            throw new TaskException("当前技能存在未恢复的工具绑定，暂不允许发布到前台使用", TaskException.Code.UNKNOWN);
        }
        catalog.setDisplayName(displayName);
        catalog.setDescription(description);
        catalog.setCategory(category);
        if (StringUtils.hasText(command.icon())) {
            catalog.setIcon(command.icon().trim());
        }
        if (StringUtils.hasText(command.iconColor())) {
            catalog.setIconColor(command.iconColor().trim());
        }
        catalog.setVisible(Boolean.TRUE.equals(command.visible()) ? 1 : 0);
        catalog.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        skillCatalogMapper.updateById(catalog);
        List<ToolLibraryItem> runtimeTools = loadNativeToolMap(List.of(skillId)).getOrDefault(skillId, List.of());
        if (runtimeTools.isEmpty()) {
            runtimeTools = loadRuntimeToolItems(catalog.getRuntimeSkillName());
        }
        return toCatalogView(
                catalog,
                loadManualBindingMap(List.of(skillId)).getOrDefault(skillId, List.of()),
                skillPublishBindingMapper.selectBySkillId(skillId),
                runtimeTools,
                null,
                true,
                false);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> updateBindings(Long skillId, List<String> toolNames, Long userId) throws TaskException {
        Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, runtimeMetadata);
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        List<String> normalizedNames = normalizeToolNames(toolNames);
        for (String toolName : normalizedNames) {
            if (!isBindableToolName(toolName)) {
                throw new TaskException("仅支持绑定可追加工具：" + toolName, TaskException.Code.UNKNOWN);
            }
            assertCanBindTool(toolName, operator);
        }
        skillToolBindingMapper.deleteBySkillIdAndBindingType(skillId, BINDING_TYPE_MANUAL);
        for (String toolName : normalizedNames) {
            SkillToolBinding binding = new SkillToolBinding();
            binding.setSkillId(skillId);
            binding.setToolName(toolName);
            binding.setBindingType(BINDING_TYPE_MANUAL);
            skillToolBindingMapper.insert(binding);
        }
        catalog.setToolBindingStatus(TOOL_BINDING_STATUS_READY);
        catalog.setToolBindingMessage(null);
        catalog.setToolBindingDetails(null);
        skillCatalogMapper.updateById(catalog);
        return normalizedNames;
    }

    public SkillChatContext resolveSkillChatContext(Long skillId) throws TaskException {
        return resolveSkillChatContextInternal(skillId, null, null, true);
    }

    public SkillChatContext resolveSkillChatContextForPublished(
            Long skillId, String preferredDisplayName, String preferredDescription) throws TaskException {
        return resolveSkillChatContextInternal(skillId, preferredDisplayName, preferredDescription, false);
    }

    public Skill getRuntimeSkill(Long skillId) throws TaskException {
        Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, runtimeMetadata);
        Skill skill = skillKit.getSkill(catalog.getRuntimeSkillName());
        if (skill == null) {
            throw new TaskException("运行时技能不存在：" + catalog.getRuntimeSkillName(), TaskException.Code.UNKNOWN);
        }
        return skill;
    }

    public SkillChatContext buildAdHocSkillChatContext(
            Long scopeId,
            String runtimeSkillName,
            String displayName,
            String description,
            String skillContent,
            List<String> boundToolNames)
            throws TaskException {
        String normalizedRuntimeSkillName = normalizeRequired(runtimeSkillName, "运行时技能名不能为空");
        String normalizedContent = normalizeRequired(skillContent, "技能说明不能为空");
        String normalizedDisplayName =
                StringUtils.hasText(displayName) ? displayName.trim() : normalizedRuntimeSkillName;
        String normalizedDescription = StringUtils.hasText(description) ? description.trim() : "";
        List<String> normalizedBoundToolNames = normalizeToolNames(boundToolNames);
        List<ToolCallback> mergedToolCallbacks = mergeToolCallbacks(List.of(), normalizedBoundToolNames);
        List<ResolvedSkillTool> resolvedTools = resolveSkillTools(mergedToolCallbacks, normalizedBoundToolNames);
        RuntimeV2SkillContract runtimeContract = null;
        return new SkillChatContext(
                scopeId,
                normalizedRuntimeSkillName,
                normalizedDisplayName,
                normalizedDescription,
                buildSkillSystemPrompt(
                        normalizedDisplayName,
                        normalizedRuntimeSkillName,
                        normalizedDescription,
                        normalizedContent,
                        resolvedTools),
                mergedToolCallbacks,
                hasFileReadCallback(mergedToolCallbacks),
                runtimeContract);
    }

    private SkillChatContext resolveSkillChatContextInternal(
            Long skillId, String preferredDisplayName, String preferredDescription, boolean requireVisible)
            throws TaskException {
        Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, runtimeMetadata);
        if (requireVisible && !isVisible(catalog)) {
            throw new TaskException("技能未上架或不可用", TaskException.Code.UNKNOWN);
        }
        if (!isToolBindingReady(catalog)) {
            throw new TaskException(
                    StringUtils.hasText(catalog.getToolBindingMessage())
                            ? "技能工具绑定未就绪：" + catalog.getToolBindingMessage().trim()
                            : "技能工具绑定未就绪，请先在后台处理缺失工具或重新绑定",
                    TaskException.Code.UNKNOWN);
        }
        Skill skill = skillKit.getSkill(catalog.getRuntimeSkillName());
        if (skill == null) {
            throw new TaskException("运行时技能不存在：" + catalog.getRuntimeSkillName(), TaskException.Code.UNKNOWN);
        }
        List<String> boundToolNames = loadManualBindingMap(List.of(skillId)).getOrDefault(skillId, List.of());
        List<ToolCallback> mergedToolCallbacks = mergeToolCallbacks(skill.getTools(), boundToolNames);
        List<ResolvedSkillTool> resolvedTools = resolveSkillTools(mergedToolCallbacks, boundToolNames);
        String displayName =
                StringUtils.hasText(preferredDisplayName) ? preferredDisplayName.trim() : catalog.getDisplayName();
        String description =
                StringUtils.hasText(preferredDescription) ? preferredDescription.trim() : catalog.getDescription();
        RuntimeV2SkillContract runtimeContract = resolveInstalledRuntimeContract(catalog.getRuntimeSkillName());
        return new SkillChatContext(
                catalog.getId(),
                catalog.getRuntimeSkillName(),
                displayName,
                description,
                buildSkillSystemPrompt(catalog, skill, resolvedTools),
                mergedToolCallbacks,
                hasFileReadCallback(mergedToolCallbacks),
                runtimeContract);
    }

    private RuntimeV2SkillContract resolveInstalledRuntimeContract(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return null;
        }
        SkillPackageInstall install =
                skillPackageInstallMapper.selectLatestSuccessfulByRuntimeSkillName(runtimeSkillName.trim());
        if (install == null || !StringUtils.hasText(install.getSummaryJson())) {
            return null;
        }
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(install.getSummaryJson(), new TypeReference<Map<String, Object>>() {});
            return runtimeV2ContractSupport.readSkillContract(payload.get(RuntimeV2ContractSupport.EXTENSION_KEY));
        } catch (Exception ignored) {
            return null;
        }
    }

    public String resolveToolDisplayName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "";
        }
        String normalizedToolName = toolName.trim();
        SkillCatalogLocalization.ToolLabel label = SkillCatalogLocalization.resolveTool(normalizedToolName, "");
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog != null && StringUtils.hasText(catalog.getDisplayName())) {
            String displayName = catalog.getDisplayName().trim();
            if (!displayName.equals(normalizedToolName)) {
                return displayName;
            }
        }
        return label.displayName();
    }

    public String resolveSkillDisplayName(Long skillId) {
        if (skillId == null || skillId <= 0) {
            return "";
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            return "";
        }
        if (StringUtils.hasText(catalog.getDisplayName())) {
            return catalog.getDisplayName().trim();
        }
        return SkillCatalogLocalization.resolveSkill(catalog.getRuntimeSkillName(), catalog.getDescription())
                .displayName();
    }

    public SkillVisual resolveSkillVisual(Long skillId) {
        if (skillId == null || skillId <= 0) {
            return SkillVisual.empty();
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            return SkillVisual.empty();
        }
        return new SkillVisual(
                StringUtils.hasText(catalog.getIcon()) ? catalog.getIcon().trim() : "",
                StringUtils.hasText(catalog.getIconColor()) ? catalog.getIconColor().trim() : "");
    }

    private Map<String, SkillMetadata> loadRuntimeMetadata() {
        return new LinkedHashMap<>(skillBox.getAllMetadata());
    }

    private Set<String> loadRuntimeToolNames(Map<String, SkillMetadata> runtimeMetadata) {
        Set<String> runtimeToolNames = new LinkedHashSet<>();
        for (GlobalToolRegistry.ToolDescriptor descriptor : globalToolRegistry.getDescriptors()) {
            if (StringUtils.hasText(descriptor.name()) && descriptor.systemRuntime()) {
                runtimeToolNames.add(descriptor.name());
            }
        }
        for (SkillMetadata metadata : runtimeMetadata.values().stream()
                .sorted(Comparator.comparing(SkillMetadata::getName))
                .toList()) {
            Skill skill = skillKit.getSkill(metadata.getName());
            if (skill == null) {
                continue;
            }
            for (ToolCallback callback : safeToolCallbacks(skill.getTools())) {
                if (callback != null
                        && callback.getToolDefinition() != null
                        && StringUtils.hasText(callback.getToolDefinition().name())) {
                    runtimeToolNames.add(callback.getToolDefinition().name());
                }
            }
        }
        return runtimeToolNames;
    }

    private SkillCatalog requireCatalog(Long skillId) throws TaskException {
        if (skillId == null || skillId <= 0) {
            throw new TaskException("技能ID无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        return catalog;
    }

    private void ensureRuntimeSkillExists(SkillCatalog catalog, Map<String, SkillMetadata> runtimeMetadata)
            throws TaskException {
        if (catalog == null) {
            return;
        }
        if (runtimeMetadata == null || !runtimeMetadata.containsKey(catalog.getRuntimeSkillName())) {
            throw new TaskException("运行时技能不存在：" + catalog.getRuntimeSkillName(), TaskException.Code.UNKNOWN);
        }
    }

    private Map<Long, List<String>> loadManualBindingMap(Collection<Long> skillIds) {
        Map<Long, List<String>> bindingMap = new LinkedHashMap<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return bindingMap;
        }
        for (SkillToolBinding binding :
                skillToolBindingMapper.selectBySkillIdsAndBindingType(skillIds, BINDING_TYPE_MANUAL)) {
            bindingMap
                    .computeIfAbsent(binding.getSkillId(), ignored -> new ArrayList<>())
                    .add(binding.getToolName());
        }
        return bindingMap;
    }

    private Map<Long, SkillPublishBinding> loadPublishBindingMap(Collection<Long> skillIds) {
        Map<Long, SkillPublishBinding> publishMap = new LinkedHashMap<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return publishMap;
        }
        for (SkillPublishBinding binding : skillPublishBindingMapper.selectBySkillIds(skillIds)) {
            publishMap.put(binding.getSkillId(), binding);
        }
        return publishMap;
    }

    private List<ResolvedSkillTool> resolveSkillTools(
            List<ToolCallback> mergedToolCallbacks, List<String> boundToolNames) {
        if (mergedToolCallbacks == null || mergedToolCallbacks.isEmpty()) {
            return List.of();
        }
        Set<String> manualBoundNames = boundToolNames == null
                ? Set.of()
                : boundToolNames.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> toolNames = mergedToolCallbacks.stream()
                .map(callback -> callback.getToolDefinition() == null
                        ? null
                        : callback.getToolDefinition().name())
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, ToolCatalog> toolCatalogMap = new LinkedHashMap<>();
        for (ToolCatalog toolCatalog : toolCatalogMapper.selectByToolNames(toolNames)) {
            toolCatalogMap.put(toolCatalog.getToolName(), toolCatalog);
        }
        List<ResolvedSkillTool> resolvedTools = new ArrayList<>();
        for (ToolCallback callback : mergedToolCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String toolName = callback.getToolDefinition().name();
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            ToolCatalog toolCatalog = toolCatalogMap.get(toolName);
            SkillCatalogLocalization.ToolLabel label = SkillCatalogLocalization.resolveTool(
                    toolName, callback.getToolDefinition().description());
            String displayName = toolCatalog != null && StringUtils.hasText(toolCatalog.getDisplayName())
                    ? toolCatalog.getDisplayName().trim()
                    : label.displayName();
            String description = toolCatalog != null && StringUtils.hasText(toolCatalog.getDescription())
                    ? toolCatalog.getDescription().trim()
                    : label.description();
            String toolType = toolCatalog == null ? "" : toolCatalog.getToolType();
            String source = toolCatalog == null ? "" : toolCatalog.getSource();
            resolvedTools.add(new ResolvedSkillTool(
                    toolName, displayName, description, toolType, source, manualBoundNames.contains(toolName)));
        }
        return List.copyOf(resolvedTools);
    }

    private Map<Long, List<ToolLibraryItem>> loadNativeToolMap(Collection<Long> skillIds) {
        Map<Long, List<ToolLibraryItem>> nativeToolMap = new LinkedHashMap<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return nativeToolMap;
        }
        List<SkillToolBinding> nativeBindings =
                skillToolBindingMapper.selectBySkillIdsAndBindingType(skillIds, BINDING_TYPE_NATIVE);
        if (nativeBindings.isEmpty()) {
            return nativeToolMap;
        }

        Set<String> toolNames = nativeBindings.stream()
                .map(SkillToolBinding::getToolName)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, ToolCatalog> toolCatalogMap = new LinkedHashMap<>();
        for (ToolCatalog toolCatalog : toolCatalogMapper.selectByToolNames(toolNames)) {
            toolCatalogMap.put(toolCatalog.getToolName(), toolCatalog);
        }

        for (SkillToolBinding binding : nativeBindings) {
            ToolCatalog toolCatalog = toolCatalogMap.get(binding.getToolName());
            if (toolCatalog == null) {
                continue;
            }
            ToolLibraryItem item = toToolLibraryItem(toolCatalog);
            if (!isRuntimeVisibleTool(item)) {
                continue;
            }
            nativeToolMap
                    .computeIfAbsent(binding.getSkillId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        return nativeToolMap;
    }

    private SkillCatalogView toCatalogView(
            SkillCatalog catalog,
            List<String> boundGlobalToolNames,
            SkillPublishBinding publishBinding,
            List<ToolLibraryItem> runtimeTools,
            SkillRecommendationService.RecommendationProfile recommendationProfile,
            boolean canViewDetail,
            boolean canDelete) {
        boolean recommended = recommendationProfile != null && recommendationProfile.recommended();
        int recommendationScore = recommendationProfile == null ? 0 : recommendationProfile.recommendationScore();
        int usageCount = recommendationProfile == null ? 0 : recommendationProfile.usageCount();
        String recommendationReason = recommendationProfile == null ? "" : recommendationProfile.recommendationReason();
        String publishStatus = publishBinding == null || !StringUtils.hasText(publishBinding.getPublishStatus())
                ? "DISABLED"
                : publishBinding.getPublishStatus().trim();
        String appCode = publishBinding == null || !StringUtils.hasText(publishBinding.getAppCode())
                ? ""
                : publishBinding.getAppCode().trim();
        String publishAppName = publishBinding == null || !StringUtils.hasText(publishBinding.getAppName())
                ? ""
                : publishBinding.getAppName().trim();
        String publishAppDescription =
                publishBinding == null || !StringUtils.hasText(publishBinding.getAppDescription())
                        ? ""
                        : publishBinding.getAppDescription().trim();
        String chatbotUrl = "PUBLISHED".equalsIgnoreCase(publishStatus) && StringUtils.hasText(appCode)
                ? "/chatbot/" + appCode
                : "";
        return new SkillCatalogView(
                catalog.getId(),
                catalog.getRuntimeSkillName(),
                catalog.getDisplayName(),
                catalog.getDescription(),
                catalog.getCategory(),
                catalog.getSource(),
                catalog.getOwnerUserId(),
                isVisible(catalog),
                canViewDetail,
                canDelete,
                catalog.getSortOrder() == null ? 0 : catalog.getSortOrder(),
                SkillCatalogMetadataDefaults.resolveVersion(catalog.getVersion()),
                SkillCatalogMetadataDefaults.resolveAuthor(catalog.getAuthor()),
                SkillCatalogMetadataDefaults.resolveIcon(catalog.getRuntimeSkillName(), catalog.getIcon()),
                StringUtils.hasText(catalog.getIconColor())
                        ? catalog.getIconColor().trim()
                        : null,
                normalizeToolBindingStatus(catalog.getToolBindingStatus()),
                StringUtils.hasText(catalog.getToolBindingMessage())
                        ? catalog.getToolBindingMessage().trim()
                        : null,
                parseToolBindingIssues(catalog.getToolBindingDetails()),
                runtimeTools,
                List.copyOf(boundGlobalToolNames),
                recommended,
                recommendationScore,
                usageCount,
                recommendationReason,
                publishStatus,
                appCode,
                publishAppName,
                publishAppDescription,
                chatbotUrl);
    }

    private ToolLibraryItem toToolLibraryItem(ToolCatalog catalog) {
        SkillCatalogLocalization.ToolLabel label =
                SkillCatalogLocalization.resolveTool(catalog.getToolName(), catalog.getDescription());
        String displayName =
                StringUtils.hasText(catalog.getDisplayName()) ? catalog.getDisplayName() : label.displayName();
        String description =
                StringUtils.hasText(catalog.getDescription()) ? catalog.getDescription() : label.description();
        String ownerSkillDisplayName = null;
        if (StringUtils.hasText(catalog.getOwnerSkillName())) {
            ownerSkillDisplayName = SkillCatalogLocalization.resolveSkill(catalog.getOwnerSkillName(), "")
                    .displayName();
        }
        return new ToolLibraryItem(
                catalog.getId(),
                catalog.getToolName(),
                displayName,
                description,
                catalog.getToolType(),
                isBindable(catalog),
                isEnabledGlobal(catalog),
                toolLibraryCallbackResolver.isGlobalAvailabilityEditable(catalog),
                catalog.getOwnerSkillName(),
                ownerSkillDisplayName,
                catalog.getSource(),
                catalog.getPermissionScope());
    }

    private ToolLibraryItem toToolLibraryItemForLibrary(ToolCatalog catalog, SysUserModel operator) {
        ToolLibraryItem item = toToolLibraryItem(catalog);
        if (catalog == null || item == null || !isPermissionScopedTool(catalog)) {
            return item;
        }
        boolean bindableInSkillBinding = canBindInSkillBinding(catalog, operator);
        return new ToolLibraryItem(
                item.id(),
                item.name(),
                item.displayName(),
                item.description(),
                item.type(),
                bindableInSkillBinding,
                item.enabledGlobal(),
                item.globalAvailabilityEditable() && isAdminUserType(operator),
                item.ownerSkillName(),
                item.ownerSkillDisplayName(),
                item.source(),
                item.permissionScope());
    }

    private ToolLibraryItem toGlobalRegistryToolItem(GlobalToolRegistry.ToolDescriptor descriptor) {
        SkillCatalogLocalization.ToolLabel label =
                SkillCatalogLocalization.resolveTool(descriptor.name(), descriptor.description());
        return new ToolLibraryItem(
                null, // 全局注册的工具没有数据库 ID
                descriptor.name(),
                label.displayName(),
                label.description(),
                descriptor.systemRuntime() ? "RUNTIME" : "GLOBAL",
                descriptor.bindable() && !descriptor.systemRuntime(),
                descriptor.systemRuntime(),
                false,
                null,
                null,
                "runtime",
                null);
    }

    private List<ToolLibraryItem> loadRuntimeToolItems(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return List.of();
        }
        Skill skill = skillKit.getSkill(runtimeSkillName);
        if (skill == null) {
            return List.of();
        }
        List<ToolLibraryItem> items = new ArrayList<>();
        for (ToolCallback callback : safeToolCallbacks(skill.getTools())) {
            ToolLibraryItem item = toRuntimeToolLibraryItem(runtimeSkillName, callback);
            if (item != null && isRuntimeVisibleTool(item)) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private ToolLibraryItem toRuntimeToolLibraryItem(String runtimeSkillName, ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) {
            return null;
        }
        String toolName = callback.getToolDefinition().name();
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName);
        if (catalog != null) {
            return toToolLibraryItem(catalog);
        }
        SkillCatalogLocalization.ToolLabel label = SkillCatalogLocalization.resolveTool(
                toolName, callback.getToolDefinition().description());
        String ownerSkillDisplayName =
                SkillCatalogLocalization.resolveSkill(runtimeSkillName, "").displayName();
        return new ToolLibraryItem(
                catalog != null ? catalog.getId() : null, // 使用数据库 ID，如果没有则为 null
                toolName,
                label.displayName(),
                label.description(),
                globalToolRegistry.containsSystemRuntime(toolName)
                        ? "RUNTIME"
                        : (globalToolRegistry.contains(toolName) ? "GLOBAL" : "SKILL_NATIVE"),
                globalToolRegistry.containsBindable(toolName) && !globalToolRegistry.containsSystemRuntime(toolName),
                globalToolRegistry.containsSystemRuntime(toolName),
                false,
                globalToolRegistry.contains(toolName) ? null : runtimeSkillName,
                globalToolRegistry.contains(toolName) ? null : ownerSkillDisplayName,
                globalToolRegistry.contains(toolName) ? "runtime" : runtimeSkillName,
                null);
    }

    private List<ToolCallback> mergeToolCallbacks(List<ToolCallback> runtimeCallbacks, List<String> boundToolNames) {
        Map<String, ToolCallback> merged = new LinkedHashMap<>();
        for (ToolCallback callback : globalToolRegistry.getSystemRuntimeToolCallbacks()) {
            String toolName = ToolCallbackSupport.resolveToolName(callback);
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            merged.putIfAbsent(toolName, callback);
        }
        for (ToolCallback callback : toolLibraryCallbackResolver.listAllEnabledGlobalCallbacks()) {
            String toolName = ToolCallbackSupport.resolveToolName(callback);
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            merged.putIfAbsent(toolName, callback);
        }
        for (ToolCallback callback : safeToolCallbacks(runtimeCallbacks)) {
            String toolName = ToolCallbackSupport.resolveToolName(callback);
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            merged.putIfAbsent(toolName, callback);
        }
        for (String toolName : boundToolNames) {
            ToolCallback callback = resolveBindableToolCallback(toolName);
            String resolvedToolName = ToolCallbackSupport.resolveToolName(callback);
            if (StringUtils.hasText(resolvedToolName)) {
                merged.putIfAbsent(resolvedToolName, callback);
            }
        }
        return List.copyOf(merged.values());
    }

    public List<ToolCallback> mergeToolCallbacksForExport(
            List<ToolCallback> runtimeCallbacks, List<String> boundToolNames) {
        return mergeToolCallbacks(runtimeCallbacks, boundToolNames);
    }

    private boolean isBindableToolName(String toolName) {
        if (globalToolRegistry.containsBindable(toolName)) {
            return true;
        }
        ToolCatalog catalog = toolCatalogMapper.selectBindableByToolName(toolName);
        return catalog != null
                && (Objects.equals(catalog.getToolType(), "MCP_REMOTE")
                        || Objects.equals(catalog.getToolType(), "LOWCODE_API")
                        || Objects.equals(catalog.getToolType(), TOOL_TYPE_CONNECTOR_API)
                        || Objects.equals(catalog.getToolType(), "DATASET_TOOL")
                        || Objects.equals(catalog.getToolType(), TOOL_TYPE_KNOWLEDGE_BASE));
    }

    private boolean canViewInToolLibrary(ToolCatalog catalog, SysUserModel operator) {
        if (catalog == null || !isPermissionScopedTool(catalog)) {
            return true;
        }
        if (knowledgeBasePermissionService.isAdmin(operator)) {
            return true;
        }
        int scope = knowledgeBasePermissionService.normalizePermissionScope(catalog.getPermissionScope());
        if (scope == ResourcePermissionScope.OWNER_ONLY.code()) {
            Long operatorUserId = operator == null ? null : operator.getId();
            return catalog.getOwnerUserId() != null && catalog.getOwnerUserId().equals(operatorUserId);
        }
        return true;
    }

    private void assertCanBindTool(String toolName, SysUserModel operator) throws TaskException {
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName);
        if (catalog == null || !isPermissionScopedTool(catalog)) {
            return;
        }
        if (!canBindInSkillBinding(catalog, operator)) {
            throw new TaskException("no permission to bind this scoped tool: " + toolName, TaskException.Code.UNKNOWN);
        }
    }

    private boolean canBindInSkillBinding(ToolCatalog catalog, SysUserModel operator) {
        if (catalog == null) {
            return false;
        }
        if (!isBindable(catalog)) {
            return false;
        }
        if (!isPermissionScopedTool(catalog)) {
            return true;
        }
        return knowledgeBasePermissionService.canBindKnowledgeBaseTool(
                catalog.getOwnerUserId(), catalog.getPermissionScope(), operator);
    }

    private boolean isPermissionScopedTool(ToolCatalog catalog) {
        if (catalog == null) {
            return false;
        }
        return Objects.equals(catalog.getToolType(), TOOL_TYPE_KNOWLEDGE_BASE)
                || Objects.equals(catalog.getToolType(), TOOL_TYPE_MCP_REMOTE)
                || Objects.equals(catalog.getToolType(), TOOL_TYPE_DATASET)
                || Objects.equals(catalog.getToolType(), TOOL_TYPE_CONNECTOR_API);
    }

    private ToolCallback resolveBindableToolCallback(String toolName) {
        ToolCallback callback = globalToolRegistry.findBindableByName(toolName);
        if (callback != null) {
            return callback;
        }
        return toolLibraryCallbackResolver.findByName(toolName);
    }

    private boolean isRuntimeVisibleTool(ToolLibraryItem item) {
        if (item == null) {
            return false;
        }
        return Objects.equals(item.type(), "RUNTIME") || Objects.equals(item.type(), "SKILL_NATIVE");
    }

    private List<ToolCallback> safeToolCallbacks(List<ToolCallback> callbacks) {
        return callbacks == null ? List.of() : callbacks;
    }

    private boolean hasFileReadCallback(List<ToolCallback> callbacks) {
        return safeToolCallbacks(callbacks).stream()
                .map(callback -> callback.getToolDefinition() == null
                        ? null
                        : callback.getToolDefinition().name())
                .filter(Objects::nonNull)
                .anyMatch("file_read"::equals);
    }

    private List<String> normalizeToolNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            String normalized = StringUtils.hasText(toolName) ? toolName.trim() : "";
            if (!normalized.isEmpty()) {
                dedup.add(normalized);
            }
        }
        return List.copyOf(dedup);
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private boolean isVisible(SkillCatalog catalog) {
        return catalog != null && catalog.getVisible() != null && catalog.getVisible() == 1;
    }

    private boolean canAccessSkillDetail(SkillCatalog catalog, SysUserModel operator) {
        if (catalog == null) {
            return false;
        }
        if (knowledgeBasePermissionService.isAdmin(operator)) {
            return true;
        }
        Long ownerUserId = catalog.getOwnerUserId();
        Long operatorUserId = operator == null ? null : operator.getId();
        return ownerUserId != null && ownerUserId.equals(operatorUserId);
    }

    private boolean canDeleteSkill(SkillCatalog catalog, SysUserModel operator) {
        if (catalog == null) {
            return false;
        }
        if (knowledgeBasePermissionService.isAdmin(operator)) {
            return true;
        }
        Long ownerUserId = catalog.getOwnerUserId();
        Long operatorUserId = operator == null ? null : operator.getId();
        return ownerUserId != null && ownerUserId.equals(operatorUserId);
    }

    private boolean isEnabledGlobal(ToolCatalog catalog) {
        return catalog != null && catalog.getEnabledGlobal() != null && catalog.getEnabledGlobal() == 1;
    }

    private boolean isToolBindingReady(SkillCatalog catalog) {
        return catalog != null
                && Objects.equals(
                        normalizeToolBindingStatus(catalog.getToolBindingStatus()), TOOL_BINDING_STATUS_READY);
    }

    private String normalizeToolBindingStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : TOOL_BINDING_STATUS_READY;
    }

    private List<ToolBindingIssueView> parseToolBindingIssues(String details) {
        if (!StringUtils.hasText(details)) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(details, new TypeReference<List<ToolBindingIssueView>>() {}));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private boolean isBindable(ToolCatalog catalog) {
        return catalog != null && catalog.getBindable() != null && catalog.getBindable() == 1;
    }

    private String buildSkillSystemPrompt(SkillCatalog catalog, Skill skill, List<ResolvedSkillTool> resolvedTools) {
        return buildSkillSystemPrompt(
                catalog.getDisplayName(),
                catalog.getRuntimeSkillName(),
                catalog.getDescription(),
                skill.getContent(),
                resolvedTools);
    }

    private String buildSkillSystemPrompt(
            String displayName,
            String runtimeSkillName,
            String description,
            String skillContent,
            List<ResolvedSkillTool> resolvedTools) {
        StringBuilder builder = new StringBuilder();

        appendSkillExecutionContract(builder);
        builder.append("You are operating in skill mode.\n");
        builder.append("The current skill is already resolved and active.\n");
        builder.append("Current skill display name: ").append(displayName).append("\n");
        builder.append("Current skill runtime name: ").append(runtimeSkillName).append("\n");
        if (StringUtils.hasText(description)) {
            builder.append("Skill description: ").append(description.trim()).append("\n");
        }
        builder.append(buildTimeContextPrompt()).append("\n");
        builder.append(
                        "If you call any SkillBox or skill-reference loading tool, use the runtime skill name as the tool parameter. ")
                .append("This parameter rule does not mean the display name is invalid.\n");
        appendResolvedToolPrompt(builder, resolvedTools);

        builder.append("\nFollow the skill instructions below. Use available tools only when needed.\n\n");
        builder.append(skillContent);
        return builder.toString();
    }

    private void appendSkillExecutionContract(StringBuilder builder) {
        builder.append("\n## Skill Execution Contract\n");
        builder.append("- 回答必须基于当前轮产生的工具结果，而不是历史轮工具结果、历史附件结果、上轮 OCR 输出、缓存或模型记忆。\n");
        builder.append("- 当前轮包含附件，并且当前 skill 定义了工具执行流程时，必须重新执行该工具流程；禁止用历史结果或推理替代。\n");
        builder.append("- Skill active 只表示技能已加载，不表示工具已执行，不表示处理结果已生成。\n");
        builder.append("- 如果 skill 中出现 MUST / REQUIRED / 必须执行 / 强制执行 / 必须先执行工具 等要求，必须严格遵守，不得降级为“优先”。\n");
        builder.append("- 如果任务要求执行工具，禁止提前给结论；工具未完成时，只允许报告当前状态或继续执行工具。\n");
        builder.append("- 如果本轮问题仍属于当前已加载 skill，即使只是对上一轮的追问，也必须把本轮视为新的技能执行轮。\n");
        builder.append(
                "- 如果本轮的指标、维度、筛选条件、对象、人员、项目、会议室、时间范围或展示粒度发生变化，必须重新执行该 skill 规定的前置查询与处理步骤，不能把上一轮结果当作本轮已完成。\n");
        builder.append("- 当前轮发现新附件时，历史 OCR 结果、提取字段、输出文件、缓存和解析状态立即失效。\n");
        builder.append("- 只有当前轮工具执行成功，并读取当前轮输出后，才能生成业务字段答案。\n");
        builder.append(
                "- 对要求生成右侧预览、HTML artifact、报表或其他结构化产物的 skill，本轮必须重新生成新的产物并确认成功后，才能回复完成。\n");
        builder.append("- 如果未检测到当前轮有效处理结果，只能输出：未检测到当前轮有效处理结果，无法继续生成业务字段。禁止继续推断。\n");
    }

    private String buildTimeContextPrompt() {
        LocalDate currentDate = LocalDate.now(PROMPT_TIME_ZONE);
        return """
                Current date: %s
                Timezone: %s
                When interpreting relative time expressions such as today, yesterday, tomorrow, this week, this month, this quarter, this year, last year, or next year, you must use this date and timezone instead of guessing.
                If the user wording conflicts with this date context, prefer this date context and, when needed, clarify with absolute dates.
                """
                .formatted(currentDate, PROMPT_TIME_ZONE.getId())
                .trim();
    }

    private void appendResolvedToolPrompt(StringBuilder builder, List<ResolvedSkillTool> resolvedTools) {
        if (resolvedTools == null || resolvedTools.isEmpty()) {
            return;
        }
        builder.append("\nCurrent skill available tools:\n");
        for (ResolvedSkillTool tool : resolvedTools) {
            builder.append("- ").append(tool.name());
            if (StringUtils.hasText(tool.displayName()) && !tool.displayName().equals(tool.name())) {
                builder.append(" (").append(tool.displayName()).append(")");
            }
            builder.append(": ").append(resolveToolUsageHint(tool));
            if (tool.manualBound()) {
                builder.append(" [manual binding]");
            }
            builder.append("\n");
        }
        if (resolvedTools.stream().anyMatch(tool -> Objects.equals(tool.toolType(), "DATASET_TOOL"))) {
            builder.append("数据集工具使用顺序必须遵守：先 search_dataset_summary，再按需要 get_dataset_schema，最后才 execute_dataset_sql。\n");
            builder.append("这个顺序对追问同样生效；不要因为当前 skill 已经加载、或者上一问刚查过一次数据，就在下一问里直接跳到 execute_dataset_sql。\n");
            builder.append(
                    "对于“市场部的报销总额是多少”“最近三个月趋势如何”这类业务问题，不要一开始就调用 get_dataset_schema；应先用 summary 确认候选对象、objectCode、关联方向和大致字段语义。\n");
            builder.append("只有当 summary 已经给出候选对象，但字段、过滤条件或关联关系仍不够明确时，才调用 get_dataset_schema。\n");
            builder.append(
                    "如果用户追问的是新的指标、维度、过滤条件或统计口径，例如把“总额”改成“人数”“最高金额”“平均金额”，必须重新检查这些新字段是否已经被 schema 明确确认；没有确认前，不得直接写 SQL 猜字段名。\n");
        }
        builder.append("如果本轮继续沿用当前 skill 处理追问，仍需发起当前轮必要的工具调用；不要把上一轮工具结果、预览或产物当作本轮已完成。\n");
    }

    private String resolveToolUsageHint(ResolvedSkillTool tool) {
        String toolName = tool.name();
        if (toolName.endsWith(".search_dataset_summary")) {
            return "必须优先使用这个工具来确认候选对象、objectCode、关联方向和字段语义。objectCode 才是 SQL 里可直接使用的真实表名，objectName 只是中文说明。追问时如果只是延续同一对象，可复用已经确认过的 objectCode；但如果对象是否变化仍不确定，先再次调用这个工具。示例参数：{\"question\":\"近三个月报销趋势\"}。";
        }
        if (toolName.endsWith(".get_dataset_schema")) {
            return "只能在 search_dataset_summary 之后使用，用来补充表、字段和关联细节。SQL 表名必须使用 objectCode，不能使用中文 objectName；SQL 字段必须来自这里返回的 schema，不能自行猜。若你要查询的新问题涉及新的指标、维度、过滤字段或聚合字段，必须先在这里确认这些字段。若用中文 objectName 返回 objects=[]，应立即改用 summary 里拿到的 objectCode 重试，而不是直接去写 SQL。参数只允许传 objectCode/objectName，或用 {} 请求完整结构。";
        }
        if (toolName.endsWith(".execute_dataset_sql")) {
            return "只有在 summary/schema 已经足够支撑 SQL 时才能使用。只允许执行只读 SELECT/WITH；SQL 表名必须使用 objectCode；SQL 字段必须来自 get_dataset_schema 结果。不要把上一问中成功或失败时猜过的字段名，当成这一问已确认的 schema 事实。若返回 success=false，先读取 nextActionHint、suggestedSchemaRequest、schemaHints、fieldCandidates，再回到 schema 或修正 SQL，不要直接结束。示例参数：{\"sql\":\"select ...\",\"limit\":50}。";
        }
        if (Objects.equals(tool.toolType(), "KNOWLEDGE_BASE_TOOL") || toolName.endsWith(".search")) {
            return "Use this for reimbursement policy, process, invoice, and rules lookup based on the knowledge base.";
        }
        if (StringUtils.hasText(tool.description())) {
            return tool.description();
        }
        return "Use according to the tool definition available in the current environment.";
    }

    private boolean isAdminUserType(SysUserModel operator) {
        if (operator == null || operator.getUserType() == null) {
            return false;
        }
        return operator.getUserType().intValue() == UserType.admin.getValue();
    }

    public record RuntimeSkillSummary(
            String name,
            String displayName,
            String description,
            String source,
            boolean activated,
            Map<String, Object> extensions) {}

    public record ToolLibraryItem(
            Long id,
            String name,
            String displayName,
            String description,
            String type,
            boolean bindable,
            boolean enabledGlobal,
            boolean globalAvailabilityEditable,
            String ownerSkillName,
            String ownerSkillDisplayName,
            String source,
            Integer permissionScope) {}

    public record SkillCatalogView(
            Long id,
            String runtimeSkillName,
            String displayName,
            String description,
            String category,
            String source,
            Long ownerUserId,
            boolean visible,
            boolean canViewDetail,
            boolean canDelete,
            int sortOrder,
            String version,
            String author,
            String icon,
            String iconColor,
            String toolBindingStatus,
            String toolBindingMessage,
            List<ToolBindingIssueView> toolBindingIssues,
            List<ToolLibraryItem> runtimeTools,
            List<String> boundGlobalToolNames,
            boolean recommended,
            int recommendationScore,
            int usageCount,
            String recommendationReason,
            String publishStatus,
            String appCode,
            String publishAppName,
            String publishAppDescription,
            String chatbotUrl) {}

    public record ToolBindingIssueView(
            String toolName, String resolvedToolName, String toolSourceType, String restoreStatus, String message) {}

    public record SkillCatalogUpdateCommand(
            String displayName,
            String description,
            String category,
            Integer sortOrder,
            Boolean visible,
            String icon,
            String iconColor) {}

    public record SkillVisual(String icon, String iconColor) {
        static SkillVisual empty() {
            return new SkillVisual("", "");
        }
    }

    public record SkillChatContext(
            Long skillId,
            String runtimeSkillName,
            String displayName,
            String description,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            boolean readFileAvailable,
            RuntimeV2SkillContract runtimeContract) {}

    private record ResolvedSkillTool(
            String name, String displayName, String description, String toolType, String source, boolean manualBound) {}
}
