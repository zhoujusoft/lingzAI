package lingzhou.agent.backend.business.skill.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lingzhou.agent.backend.capability.api.registry.LowcodeToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.DatasetToolRegistryService;
import lingzhou.agent.backend.capability.dataset.registry.KnowledgeBaseToolRegistryService;
import lingzhou.agent.backend.capability.mcp.registry.McpToolRegistryService;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillCatalogService {

    private static final String BINDING_TYPE_NATIVE = "NATIVE";
    private static final String BINDING_TYPE_MANUAL = "MANUAL";

    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final ToolCatalogMapper toolCatalogMapper;
    private final SkillKit skillKit;
    private final SimpleSkillBox skillBox;
    private final GlobalToolRegistry globalToolRegistry;
    private final McpToolRegistryService mcpToolRegistryService;
    private final LowcodeToolRegistryService lowcodeToolRegistryService;
    private final DatasetToolRegistryService datasetToolRegistryService;
    private final KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService;
    private final McpServerService mcpServerService;
    private final SkillRecommendationService skillRecommendationService;
    private final JdbcTemplate jdbcTemplate;
    private final Object syncMonitor = new Object();
    private volatile boolean bindingSchemaReady;

    public SkillCatalogService(
            SkillCatalogMapper skillCatalogMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            ToolCatalogMapper toolCatalogMapper,
            SkillKit skillKit,
            SimpleSkillBox skillBox,
            GlobalToolRegistry globalToolRegistry,
            McpToolRegistryService mcpToolRegistryService,
            LowcodeToolRegistryService lowcodeToolRegistryService,
            DatasetToolRegistryService datasetToolRegistryService,
            KnowledgeBaseToolRegistryService knowledgeBaseToolRegistryService,
            McpServerService mcpServerService,
            SkillRecommendationService skillRecommendationService,
            JdbcTemplate jdbcTemplate) {
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.toolCatalogMapper = toolCatalogMapper;
        this.skillKit = skillKit;
        this.skillBox = skillBox;
        this.globalToolRegistry = globalToolRegistry;
        this.mcpToolRegistryService = mcpToolRegistryService;
        this.lowcodeToolRegistryService = lowcodeToolRegistryService;
        this.datasetToolRegistryService = datasetToolRegistryService;
        this.knowledgeBaseToolRegistryService = knowledgeBaseToolRegistryService;
        this.mcpServerService = mcpServerService;
        this.skillRecommendationService = skillRecommendationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initializeCatalogData() {
        syncRuntimeData();
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
        RuntimeSnapshot snapshot = syncRuntimeData();
        List<SkillCatalog> rows = visibleOnly ? skillCatalogMapper.selectVisibleOrdered() : skillCatalogMapper.selectAllOrdered();
        rows = rows.stream()
                .filter(row -> snapshot.runtimeMetadata().containsKey(row.getRuntimeSkillName()))
                .toList();
        Map<Long, List<String>> bindingMap = loadManualBindingMap(rows.stream().map(SkillCatalog::getId).toList());
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
                    return toCatalogView(
                            row,
                            bindingMap.getOrDefault(row.getId(), List.of()),
                            runtimeTools,
                            recommendationMap.get(row.getId()));
                })
                .toList();
    }

    public List<ToolLibraryItem> listToolLibrary() {
        RuntimeSnapshot snapshot = syncRuntimeData();
        return toolCatalogMapper.selectAllOrdered().stream()
                .filter(row ->
                        snapshot.runtimeToolNames().contains(row.getToolName())
                                || Objects.equals(row.getToolType(), "LOWCODE_API")
                                || Objects.equals(row.getToolType(), "DATASET_TOOL")
                                || Objects.equals(row.getToolType(), "KNOWLEDGE_BASE_TOOL"))
                .map(this::toToolLibraryItem)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public SkillCatalogView updateCatalog(Long skillId, SkillCatalogUpdateCommand command) throws TaskException {
        RuntimeSnapshot snapshot = syncRuntimeData();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, snapshot.runtimeMetadata());
        String displayName = normalizeRequired(command.displayName(), "展示名称不能为空");
        String description = normalizeRequired(command.description(), "技能描述不能为空");
        String category = normalizeRequired(command.category(), "业务能力分类不能为空");
        catalog.setDisplayName(displayName);
        catalog.setDescription(description);
        catalog.setCategory(category);
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
                runtimeTools,
                null);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> updateBindings(Long skillId, List<String> toolNames) throws TaskException {
        RuntimeSnapshot snapshot = syncRuntimeData();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, snapshot.runtimeMetadata());
        List<String> normalizedNames = normalizeToolNames(toolNames);
        for (String toolName : normalizedNames) {
            if (!isBindableToolName(toolName)) {
                throw new TaskException("仅支持绑定可追加工具：" + toolName, TaskException.Code.UNKNOWN);
            }
        }
        skillToolBindingMapper.deleteBySkillIdAndBindingType(skillId, BINDING_TYPE_MANUAL);
        for (String toolName : normalizedNames) {
            SkillToolBinding binding = new SkillToolBinding();
            binding.setSkillId(skillId);
            binding.setToolName(toolName);
            binding.setBindingType(BINDING_TYPE_MANUAL);
            skillToolBindingMapper.insert(binding);
        }
        return normalizedNames;
    }

    public SkillChatContext resolveSkillChatContext(Long skillId) throws TaskException {
        RuntimeSnapshot snapshot = syncRuntimeData();
        SkillCatalog catalog = requireCatalog(skillId);
        ensureRuntimeSkillExists(catalog, snapshot.runtimeMetadata());
        if (!isVisible(catalog)) {
            throw new TaskException("技能未上架或不可用", TaskException.Code.UNKNOWN);
        }
        Skill skill = skillKit.getSkill(catalog.getRuntimeSkillName());
        if (skill == null) {
            throw new TaskException("运行时技能不存在：" + catalog.getRuntimeSkillName(), TaskException.Code.UNKNOWN);
        }
        List<String> boundToolNames = loadManualBindingMap(List.of(skillId)).getOrDefault(skillId, List.of());
        List<ToolCallback> mergedToolCallbacks = mergeToolCallbacks(skill.getTools(), boundToolNames);
        List<ResolvedSkillTool> resolvedTools = resolveSkillTools(mergedToolCallbacks, boundToolNames);
        boolean readFileAvailable = mergedToolCallbacks.stream()
                .map(callback -> callback.getToolDefinition() == null ? null : callback.getToolDefinition().name())
                .filter(Objects::nonNull)
                .anyMatch("readFile"::equals);
        return new SkillChatContext(
                catalog.getId(),
                catalog.getRuntimeSkillName(),
                catalog.getDisplayName(),
                catalog.getDescription(),
                buildSkillSystemPrompt(catalog, skill, resolvedTools),
                mergedToolCallbacks,
                readFileAvailable);
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

    private RuntimeSnapshot syncRuntimeData() {
        synchronized (syncMonitor) {
            ensureSkillToolBindingSchema();
            Map<String, SkillMetadata> runtimeMetadata = loadRuntimeMetadata();
            syncRuntimeSkillCatalogs(runtimeMetadata);
            List<ToolSeed> runtimeTools = collectRuntimeTools(runtimeMetadata);
            syncRuntimeToolCatalogs(runtimeTools);
            syncNativeToolBindings(runtimeMetadata);
            Set<String> runtimeToolNames =
                    runtimeTools.stream().map(ToolSeed::toolName).collect(LinkedHashSet::new, Set::add, Set::addAll);
            runtimeToolNames.addAll(mcpServerService.listEnabledToolNames());
            return new RuntimeSnapshot(
                    runtimeMetadata,
                    runtimeToolNames);
        }
    }

    private void ensureSkillToolBindingSchema() {
        if (bindingSchemaReady) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'skill_tool_binding'
                  AND column_name = 'binding_type'
                """,
                Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute(
                    """
                    ALTER TABLE skill_tool_binding
                    ADD COLUMN binding_type varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '绑定类型：NATIVE/MANUAL'
                    AFTER tool_name
                    """);
        }
        jdbcTemplate.update(
                """
                UPDATE skill_tool_binding
                SET binding_type = 'MANUAL'
                WHERE binding_type IS NULL OR binding_type = ''
                """);
        bindingSchemaReady = true;
    }

    private void syncRuntimeSkillCatalogs(Map<String, SkillMetadata> runtimeMetadata) {
        int index = 0;
        for (SkillMetadata metadata : runtimeMetadata.values().stream()
                .sorted(Comparator.comparing(SkillMetadata::getName))
                .toList()) {
            SkillCatalogLocalization.SkillLabel label =
                    SkillCatalogLocalization.resolveSkill(metadata.getName(), metadata.getDescription());
            SkillCatalog existing = skillCatalogMapper.selectByRuntimeSkillName(metadata.getName());
            if (existing == null) {
                SkillCatalog created = new SkillCatalog();
                created.setRuntimeSkillName(metadata.getName());
                created.setDisplayName(label.displayName());
                created.setDescription(label.description());
                created.setCategory(deriveCategory(metadata));
                created.setSource(metadata.getSource());
                created.setVisible(1);
                created.setSortOrder(index * 10);
                skillCatalogMapper.insert(created);
            } else {
                boolean changed = false;
                if (shouldUseGeneratedDisplayName(existing.getDisplayName(), metadata.getName())
                        && !Objects.equals(existing.getDisplayName(), label.displayName())) {
                    existing.setDisplayName(label.displayName());
                    changed = true;
                }
                if (shouldUseGeneratedDescription(existing.getDescription(), metadata.getDescription())
                        && !Objects.equals(existing.getDescription(), label.description())) {
                    existing.setDescription(label.description());
                    changed = true;
                }
                String derivedCategory = deriveCategory(metadata);
                if (shouldUseGeneratedCategory(existing.getCategory(), metadata)
                        && !Objects.equals(existing.getCategory(), derivedCategory)) {
                    existing.setCategory(derivedCategory);
                    changed = true;
                }
                if (!Objects.equals(existing.getSource(), metadata.getSource())) {
                    existing.setSource(metadata.getSource());
                    changed = true;
                }
                if (existing.getVisible() == null) {
                    existing.setVisible(1);
                    changed = true;
                }
                if (existing.getSortOrder() == null) {
                    existing.setSortOrder(index * 10);
                    changed = true;
                }
                if (changed) {
                    skillCatalogMapper.updateById(existing);
                }
            }
            index++;
        }
    }

    private List<ToolSeed> collectRuntimeTools(Map<String, SkillMetadata> runtimeMetadata) {
        List<ToolSeed> runtimeTools = new ArrayList<>();
        Set<String> seenToolNames = new LinkedHashSet<>();
        int sortOrder = 0;

        for (GlobalToolRegistry.ToolDescriptor descriptor : globalToolRegistry.getDescriptors()) {
            if (!seenToolNames.add(descriptor.name())) {
                continue;
            }
            SkillCatalogLocalization.ToolLabel label =
                    SkillCatalogLocalization.resolveTool(descriptor.name(), descriptor.description());
            runtimeTools.add(new ToolSeed(
                    descriptor.name(),
                    label.displayName(),
                    label.description(),
                    descriptor.description(),
                    "GLOBAL",
                    1,
                    null,
                    "runtime",
                    sortOrder++));
        }

        for (SkillMetadata metadata : runtimeMetadata.values().stream()
                .sorted(Comparator.comparing(SkillMetadata::getName))
                .toList()) {
            Skill skill = skillKit.getSkill(metadata.getName());
            if (skill == null) {
                continue;
            }
            for (ToolCallback callback : safeToolCallbacks(skill.getTools())) {
                if (callback.getToolDefinition() == null) {
                    continue;
                }
                String toolName = callback.getToolDefinition().name();
                if (!StringUtils.hasText(toolName) || !seenToolNames.add(toolName)) {
                    continue;
                }
                SkillCatalogLocalization.ToolLabel label =
                        SkillCatalogLocalization.resolveTool(toolName, callback.getToolDefinition().description());
                runtimeTools.add(new ToolSeed(
                        toolName,
                        label.displayName(),
                        label.description(),
                        callback.getToolDefinition().description(),
                        "SKILL_NATIVE",
                        0,
                        metadata.getName(),
                        metadata.getSource(),
                        sortOrder++));
            }
        }
        return runtimeTools;
    }

    private void syncRuntimeToolCatalogs(List<ToolSeed> runtimeTools) {
        for (ToolSeed tool : runtimeTools) {
            ToolCatalog existing = toolCatalogMapper.selectByToolName(tool.toolName());
            if (existing == null) {
                ToolCatalog created = new ToolCatalog();
                created.setToolName(tool.toolName());
                created.setDisplayName(tool.displayName());
                created.setDescription(tool.description());
                created.setToolType(tool.toolType());
                created.setBindable(tool.bindable());
                created.setOwnerSkillName(tool.ownerSkillName());
                created.setSource(tool.source());
                created.setSortOrder(tool.sortOrder());
                toolCatalogMapper.insert(created);
                continue;
            }

            boolean changed = false;
            if (shouldUseGeneratedDisplayName(existing.getDisplayName(), tool.toolName())
                    && !Objects.equals(existing.getDisplayName(), tool.displayName())) {
                existing.setDisplayName(tool.displayName());
                changed = true;
            }
            if (shouldUseGeneratedDescription(existing.getDescription(), tool.runtimeDescription())
                    && !Objects.equals(existing.getDescription(), tool.description())) {
                existing.setDescription(tool.description());
                changed = true;
            }
            if (!Objects.equals(existing.getToolType(), tool.toolType())) {
                existing.setToolType(tool.toolType());
                changed = true;
            }
            if (!Objects.equals(existing.getBindable(), tool.bindable())) {
                existing.setBindable(tool.bindable());
                changed = true;
            }
            if (!Objects.equals(existing.getOwnerSkillName(), tool.ownerSkillName())) {
                existing.setOwnerSkillName(tool.ownerSkillName());
                changed = true;
            }
            if (!Objects.equals(existing.getSource(), tool.source())) {
                existing.setSource(tool.source());
                changed = true;
            }
            if (!Objects.equals(existing.getSortOrder(), tool.sortOrder())) {
                existing.setSortOrder(tool.sortOrder());
                changed = true;
            }
            if (changed) {
                toolCatalogMapper.updateById(existing);
            }
        }
    }

    private Map<String, SkillMetadata> loadRuntimeMetadata() {
        return new LinkedHashMap<>(skillBox.getAllMetadata());
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

    private void ensureRuntimeSkillExists(SkillCatalog catalog, Map<String, SkillMetadata> runtimeMetadata) throws TaskException {
        if (catalog == null) {
            return;
        }
        if (runtimeMetadata == null || !runtimeMetadata.containsKey(catalog.getRuntimeSkillName())) {
            throw new TaskException("运行时技能不存在：" + catalog.getRuntimeSkillName(), TaskException.Code.UNKNOWN);
        }
    }

    private void syncNativeToolBindings(Map<String, SkillMetadata> runtimeMetadata) {
        for (SkillMetadata metadata : runtimeMetadata.values().stream()
                .sorted(Comparator.comparing(SkillMetadata::getName))
                .toList()) {
            SkillCatalog catalog = skillCatalogMapper.selectByRuntimeSkillName(metadata.getName());
            if (catalog == null || catalog.getId() == null) {
                continue;
            }
            Skill skill = skillKit.getSkill(metadata.getName());
            if (skill == null) {
                continue;
            }

            Set<String> nativeToolNames = safeToolCallbacks(skill.getTools()).stream()
                    .map(callback -> callback.getToolDefinition() == null ? null : callback.getToolDefinition().name())
                    .filter(StringUtils::hasText)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);

            List<SkillToolBinding> existing = skillToolBindingMapper.selectBySkillId(catalog.getId());
            Map<String, SkillToolBinding> existingByToolName = new LinkedHashMap<>();
            for (SkillToolBinding binding : existing) {
                if (StringUtils.hasText(binding.getToolName())) {
                    existingByToolName.put(binding.getToolName(), binding);
                }
            }

            for (String toolName : nativeToolNames) {
                SkillToolBinding existingBinding = existingByToolName.get(toolName);
                if (existingBinding == null) {
                    SkillToolBinding binding = new SkillToolBinding();
                    binding.setSkillId(catalog.getId());
                    binding.setToolName(toolName);
                    binding.setBindingType(BINDING_TYPE_NATIVE);
                    skillToolBindingMapper.insert(binding);
                    continue;
                }
                if (!Objects.equals(existingBinding.getBindingType(), BINDING_TYPE_NATIVE)) {
                    existingBinding.setBindingType(BINDING_TYPE_NATIVE);
                    skillToolBindingMapper.updateById(existingBinding);
                }
            }

            for (SkillToolBinding binding : existing) {
                if (!Objects.equals(binding.getBindingType(), BINDING_TYPE_NATIVE)) {
                    continue;
                }
                if (!nativeToolNames.contains(binding.getToolName())) {
                    skillToolBindingMapper.deleteById(binding.getId());
                }
            }
        }
    }

    private Map<Long, List<String>> loadManualBindingMap(Collection<Long> skillIds) {
        Map<Long, List<String>> bindingMap = new LinkedHashMap<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return bindingMap;
        }
        for (SkillToolBinding binding : skillToolBindingMapper.selectBySkillIdsAndBindingType(skillIds, BINDING_TYPE_MANUAL)) {
            bindingMap.computeIfAbsent(binding.getSkillId(), ignored -> new ArrayList<>()).add(binding.getToolName());
        }
        return bindingMap;
    }

    private List<ResolvedSkillTool> resolveSkillTools(List<ToolCallback> mergedToolCallbacks, List<String> boundToolNames) {
        if (mergedToolCallbacks == null || mergedToolCallbacks.isEmpty()) {
            return List.of();
        }
        Set<String> manualBoundNames = boundToolNames == null
                ? Set.of()
                : boundToolNames.stream().filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> toolNames = mergedToolCallbacks.stream()
                .map(callback -> callback.getToolDefinition() == null ? null : callback.getToolDefinition().name())
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
            SkillCatalogLocalization.ToolLabel label =
                    SkillCatalogLocalization.resolveTool(toolName, callback.getToolDefinition().description());
            String displayName = toolCatalog != null && StringUtils.hasText(toolCatalog.getDisplayName())
                    ? toolCatalog.getDisplayName().trim()
                    : label.displayName();
            String description = toolCatalog != null && StringUtils.hasText(toolCatalog.getDescription())
                    ? toolCatalog.getDescription().trim()
                    : label.description();
            String toolType = toolCatalog == null ? "" : toolCatalog.getToolType();
            String source = toolCatalog == null ? "" : toolCatalog.getSource();
            resolvedTools.add(new ResolvedSkillTool(
                    toolName,
                    displayName,
                    description,
                    toolType,
                    source,
                    manualBoundNames.contains(toolName)));
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
            nativeToolMap.computeIfAbsent(binding.getSkillId(), ignored -> new ArrayList<>())
                    .add(toToolLibraryItem(toolCatalog));
        }
        return nativeToolMap;
    }

    private SkillCatalogView toCatalogView(
            SkillCatalog catalog,
            List<String> boundGlobalToolNames,
            List<ToolLibraryItem> runtimeTools,
            SkillRecommendationService.RecommendationProfile recommendationProfile) {
        boolean recommended = recommendationProfile != null && recommendationProfile.recommended();
        int recommendationScore = recommendationProfile == null ? 0 : recommendationProfile.recommendationScore();
        int usageCount = recommendationProfile == null ? 0 : recommendationProfile.usageCount();
        String recommendationReason = recommendationProfile == null
                ? ""
                : recommendationProfile.recommendationReason();
        return new SkillCatalogView(
                catalog.getId(),
                catalog.getRuntimeSkillName(),
                catalog.getDisplayName(),
                catalog.getDescription(),
                catalog.getCategory(),
                catalog.getSource(),
                isVisible(catalog),
                catalog.getSortOrder() == null ? 0 : catalog.getSortOrder(),
                runtimeTools,
                List.copyOf(boundGlobalToolNames),
                recommended,
                recommendationScore,
                usageCount,
                recommendationReason);
    }

    private ToolLibraryItem toToolLibraryItem(ToolCatalog catalog) {
        SkillCatalogLocalization.ToolLabel label =
                SkillCatalogLocalization.resolveTool(catalog.getToolName(), catalog.getDescription());
        String displayName = StringUtils.hasText(catalog.getDisplayName()) ? catalog.getDisplayName() : label.displayName();
        String description = StringUtils.hasText(catalog.getDescription()) ? catalog.getDescription() : label.description();
        String ownerSkillDisplayName = null;
        if (StringUtils.hasText(catalog.getOwnerSkillName())) {
            ownerSkillDisplayName = SkillCatalogLocalization.resolveSkill(catalog.getOwnerSkillName(), "")
                    .displayName();
        }
        return new ToolLibraryItem(
                catalog.getToolName(),
                displayName,
                description,
                catalog.getToolType(),
                isBindable(catalog),
                catalog.getOwnerSkillName(),
                ownerSkillDisplayName,
                catalog.getSource());
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
            if (item != null) {
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
        SkillCatalogLocalization.ToolLabel label =
                SkillCatalogLocalization.resolveTool(toolName, callback.getToolDefinition().description());
        String ownerSkillDisplayName = SkillCatalogLocalization.resolveSkill(runtimeSkillName, "").displayName();
        return new ToolLibraryItem(
                toolName,
                label.displayName(),
                label.description(),
                globalToolRegistry.contains(toolName) ? "GLOBAL" : "SKILL_NATIVE",
                globalToolRegistry.contains(toolName),
                globalToolRegistry.contains(toolName) ? null : runtimeSkillName,
                globalToolRegistry.contains(toolName) ? null : ownerSkillDisplayName,
                globalToolRegistry.contains(toolName) ? "runtime" : runtimeSkillName);
    }

    private List<ToolCallback> mergeToolCallbacks(List<ToolCallback> runtimeCallbacks, List<String> boundToolNames) {
        Map<String, ToolCallback> merged = new LinkedHashMap<>();
        for (ToolCallback callback : safeToolCallbacks(runtimeCallbacks)) {
            if (callback.getToolDefinition() == null || !StringUtils.hasText(callback.getToolDefinition().name())) {
                continue;
            }
            merged.putIfAbsent(callback.getToolDefinition().name(), callback);
        }
        for (String toolName : boundToolNames) {
            ToolCallback callback = resolveBindableToolCallback(toolName);
            if (callback != null) {
                merged.putIfAbsent(toolName, callback);
            }
        }
        return List.copyOf(merged.values());
    }

    private boolean isBindableToolName(String toolName) {
        if (globalToolRegistry.contains(toolName)) {
            return true;
        }
        ToolCatalog catalog = toolCatalogMapper.selectBindableByToolName(toolName);
        return catalog != null
                && (Objects.equals(catalog.getToolType(), "MCP_REMOTE")
                        || Objects.equals(catalog.getToolType(), "LOWCODE_API")
                        || Objects.equals(catalog.getToolType(), "DATASET_TOOL")
                        || Objects.equals(catalog.getToolType(), "KNOWLEDGE_BASE_TOOL"));
    }

    private ToolCallback resolveBindableToolCallback(String toolName) {
        ToolCallback callback = globalToolRegistry.findByName(toolName);
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
        callback = knowledgeBaseToolRegistryService.findByName(toolName);
        if (callback != null) {
            return callback;
        }
        return datasetToolRegistryService.findByName(toolName);
    }

    private List<ToolCallback> safeToolCallbacks(List<ToolCallback> callbacks) {
        return callbacks == null ? List.of() : callbacks;
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

    private boolean isBindable(ToolCatalog catalog) {
        return catalog != null && catalog.getBindable() != null && catalog.getBindable() == 1;
    }

    private boolean shouldUseGeneratedDisplayName(String currentDisplayName, String generatedKey) {
        if (!StringUtils.hasText(currentDisplayName)) {
            return true;
        }
        return currentDisplayName.trim().equals(generatedKey);
    }

    private boolean shouldUseGeneratedDescription(String currentDescription, String generatedDescription) {
        if (!StringUtils.hasText(currentDescription)) {
            return true;
        }
        if (!StringUtils.hasText(generatedDescription)) {
            return false;
        }
        return currentDescription.trim().equals(generatedDescription.trim());
    }

    private String deriveCategory(SkillMetadata metadata) {
        Object extension = metadata.getExtensions().get("category");
        String rawCategory = extension == null ? "" : String.valueOf(extension).trim();
        return SkillCatalogLocalization.resolveCategory(metadata.getName(), rawCategory);
    }

    private boolean shouldUseGeneratedCategory(String currentCategory, SkillMetadata metadata) {
        Object extension = metadata.getExtensions().get("category");
        String rawCategory = extension == null ? "" : String.valueOf(extension).trim();
        return SkillCatalogLocalization.isLegacyCategoryValue(currentCategory, rawCategory, metadata.getSource());
    }

    private String buildSkillSystemPrompt(SkillCatalog catalog, Skill skill, List<ResolvedSkillTool> resolvedTools) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are operating in skill mode.\n");
        builder.append("The current skill is already resolved and active.\n");
        builder.append("Current skill display name: ").append(catalog.getDisplayName()).append("\n");
        builder.append("Current skill runtime name: ").append(catalog.getRuntimeSkillName()).append("\n");
        if (StringUtils.hasText(catalog.getDescription())) {
            builder.append("Skill description: ").append(catalog.getDescription().trim()).append("\n");
        }
        builder.append("If you call any SkillBox or skill-reference loading tool, use the runtime skill name as the tool parameter. ")
                .append("This parameter rule does not mean the display name is invalid.\n");
        appendResolvedToolPrompt(builder, resolvedTools);
        builder.append("\nFollow the skill instructions below. Use available tools only when needed.\n\n");
        builder.append(skill.getContent());
        return builder.toString();
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
            builder.append("Use dataset tools in this order when appropriate: dataset summary first, then dataset schema, and finally dataset SQL.\n");
        }
    }

    private String resolveToolUsageHint(ResolvedSkillTool tool) {
        String toolName = tool.name();
        if (toolName.endsWith(".search_dataset_summary")) {
            return "Use this for understanding what the dataset can answer before writing SQL. Example args: {\"question\":\"近三个月报销趋势\"}.";
        }
        if (toolName.endsWith(".get_dataset_schema")) {
            return "Use this for table, field, and relationship lookup. Args must only use objectCodes/objectNames, or {} for full schema.";
        }
        if (toolName.endsWith(".execute_dataset_sql")) {
            return "Use this only after understanding the dataset. Only run read-only SELECT/WITH queries. Example args: {\"sql\":\"select ...\",\"limit\":50}.";
        }
        if (Objects.equals(tool.toolType(), "KNOWLEDGE_BASE_TOOL") || toolName.endsWith(".search")) {
            return "Use this for reimbursement policy, process, invoice, and rules lookup based on the knowledge base.";
        }
        if (StringUtils.hasText(tool.description())) {
            return tool.description();
        }
        return "Use according to the tool definition available in the current environment.";
    }

    public record RuntimeSkillSummary(
            String name,
            String displayName,
            String description,
            String source,
            boolean activated,
            Map<String, Object> extensions) {}

    public record ToolLibraryItem(
            String name,
            String displayName,
            String description,
            String type,
            boolean bindable,
            String ownerSkillName,
            String ownerSkillDisplayName,
            String source) {}

    public record SkillCatalogView(
            Long id,
            String runtimeSkillName,
            String displayName,
            String description,
            String category,
            String source,
            boolean visible,
            int sortOrder,
            List<ToolLibraryItem> runtimeTools,
            List<String> boundGlobalToolNames,
            boolean recommended,
            int recommendationScore,
            int usageCount,
            String recommendationReason) {}

    public record SkillCatalogUpdateCommand(
            String displayName, String description, String category, Integer sortOrder, Boolean visible) {}

    public record SkillChatContext(
            Long skillId,
            String runtimeSkillName,
            String displayName,
            String description,
            String systemPrompt,
            List<ToolCallback> toolCallbacks,
            boolean readFileAvailable) {}

    private record RuntimeSnapshot(Map<String, SkillMetadata> runtimeMetadata, Set<String> runtimeToolNames) {}

    private record ToolSeed(
            String toolName,
            String displayName,
            String description,
            String runtimeDescription,
            String toolType,
            Integer bindable,
            String ownerSkillName,
            String source,
            Integer sortOrder) {}

    private record ResolvedSkillTool(
            String name,
            String displayName,
            String description,
            String toolType,
            String source,
            boolean manualBound) {}
}
