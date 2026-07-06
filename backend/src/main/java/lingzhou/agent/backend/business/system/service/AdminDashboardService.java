package lingzhou.agent.backend.business.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.service.ChannelConfigService;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.integration.service.connector.IntegrationConnectorService;
import lingzhou.agent.backend.business.integration.service.datasource.IntegrationDataSourceService;
import lingzhou.agent.backend.business.license.model.LicenseStatusView;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.business.model.service.ModelLibraryService;
import lingzhou.agent.backend.business.skill.service.LowcodeApiBrowseService;
import lingzhou.agent.backend.business.skill.service.McpServerService;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.system.controller.AdminDashboardApiModels;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.AgentListItemDto;
import lingzhou.agent.backend.business.system.model.AgentPageInput;
import lingzhou.agent.backend.business.system.model.AgentPageResult;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminDashboardService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final int PAGE_SIZE = 200;
    private static final Set<String> ACTIVE_STATUS_KEYS =
            Set.of("published", "enabled", "active", "running", "connected", "visible", "registered", "initialized");
    private static final Set<String> DEFAULT_CHANNEL_TYPES = Set.of("weixin", "wecom", "dingtalk", "webchat");

    private final IKnowledgeBaseService knowledgeBaseService;
    private final SkillStudioProjectMapper skillStudioProjectMapper;
    private final SkillCatalogService skillCatalogService;
    private final McpServerService mcpServerService;
    private final LowcodeApiBrowseService lowcodeApiBrowseService;
    private final IntegrationConnectorService integrationConnectorService;
    private final ChannelConfigService channelConfigService;
    private final IntegrationDataSourceService integrationDataSourceService;
    private final IntegrationDatasetService integrationDatasetService;
    private final ModelLibraryService modelLibraryService;
    private final AgentTemplateService agentTemplateService;
    private final LicenseService licenseService;
    private final SysUserMapper sysUserMapper;

    public AdminDashboardService(
            IKnowledgeBaseService knowledgeBaseService,
            SkillStudioProjectMapper skillStudioProjectMapper,
            SkillCatalogService skillCatalogService,
            McpServerService mcpServerService,
            LowcodeApiBrowseService lowcodeApiBrowseService,
            IntegrationConnectorService integrationConnectorService,
            ChannelConfigService channelConfigService,
            IntegrationDataSourceService integrationDataSourceService,
            IntegrationDatasetService integrationDatasetService,
            ModelLibraryService modelLibraryService,
            AgentTemplateService agentTemplateService,
            LicenseService licenseService,
            SysUserMapper sysUserMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.skillStudioProjectMapper = skillStudioProjectMapper;
        this.skillCatalogService = skillCatalogService;
        this.mcpServerService = mcpServerService;
        this.lowcodeApiBrowseService = lowcodeApiBrowseService;
        this.integrationConnectorService = integrationConnectorService;
        this.channelConfigService = channelConfigService;
        this.integrationDataSourceService = integrationDataSourceService;
        this.integrationDatasetService = integrationDatasetService;
        this.modelLibraryService = modelLibraryService;
        this.agentTemplateService = agentTemplateService;
        this.licenseService = licenseService;
        this.sysUserMapper = sysUserMapper;
    }

    public AdminDashboardApiModels.DashboardResponse getDashboard(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        List<AdminDashboardApiModels.ModuleView> modules = List.of(
                buildKnowledgeModule(operatorUserId),
                buildSkillStudioModule(),
                buildSkillManagementModule(operatorUserId),
                buildToolLibraryModule(operatorUserId),
                buildMcpModule(operatorUserId),
                buildApiLibraryModule(),
                buildConnectorModule(operatorUserId),
                buildChannelModule(),
                buildDataSourceModule(operatorUserId),
                buildDatasetModule(operatorUserId),
                buildModelLibraryModule(operatorUserId),
                buildAgentTemplateModule());

        long resourceCount = modules.stream().mapToLong(AdminDashboardApiModels.ModuleView::total).sum();
        long activeCount = modules.stream()
                .flatMap(module -> module.statuses().stream())
                .filter(status -> ACTIVE_STATUS_KEYS.contains(status.key()))
                .mapToLong(AdminDashboardApiModels.StatusView::count)
                .sum();
        AdminDashboardApiModels.ModuleView largestModule = modules.stream()
                .max(Comparator.comparingLong(AdminDashboardApiModels.ModuleView::total))
                .orElse(null);

        AdminDashboardApiModels.SummaryView summary = new AdminDashboardApiModels.SummaryView(
                modules.size(),
                resourceCount,
                activeCount,
                largestModule == null ? "" : largestModule.moduleId(),
                largestModule == null ? "" : largestModule.label(),
                largestModule == null ? 0 : largestModule.total());
        return new AdminDashboardApiModels.DashboardResponse(summary, buildLicenseView(), modules);
    }

    private AdminDashboardApiModels.LicenseView buildLicenseView() {
        LicenseStatusView status = licenseService.getStatusView();
        Date expiresAt = status.getExpiresAt();
        Integer maxActiveUsers = status.getMaxActiveUsers();
        Long maxTotalTokens = status.getMaxTotalTokens();
        long consumedTokens = status.getConsumedTokens() == null ? 0L : status.getConsumedTokens();
        return new AdminDashboardApiModels.LicenseView(
                status.isEnabled(),
                trimText(status.getStatus()),
                trimText(status.getCustomerName()),
                trimText(status.getEdition()),
                expiresAt,
                expiresAt == null,
                calculateRemainingDays(expiresAt),
                countRegisteredUsers(),
                status.getActiveUsers() == null ? 0 : status.getActiveUsers(),
                maxActiveUsers,
                maxActiveUsers == null || maxActiveUsers < 0,
                consumedTokens,
                maxTotalTokens,
                status.getRemainingTokens(),
                maxTotalTokens == null || maxTotalTokens <= 0);
    }

    private long calculateRemainingDays(Date expiresAt) {
        if (expiresAt == null) {
            return 0L;
        }
        LocalDate expirationDate = expiresAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return Math.max(ChronoUnit.DAYS.between(LocalDate.now(), expirationDate), 0L);
    }

    private int countRegisteredUsers() {
        return (int) sysUserMapper.selectList(null).stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getLicenseExempt() == null || user.getLicenseExempt() != 1)
                .count();
    }

    private AdminDashboardApiModels.ModuleView buildKnowledgeModule(Long operatorUserId) {
        List<KnowledgeBase> items = loadVisibleKnowledgeBases(operatorUserId);
        long total = items.size();
        long published = items.stream()
                .filter(item -> STATUS_PUBLISHED.equalsIgnoreCase(trimUpper(item.getPublishStatus())))
                .count();
        return module(
                "knowledge",
                "Knowledge Base",
                total,
                status("published", "Published", published),
                status("draft", "Not Published", remaining(total, published)));
    }

    private AdminDashboardApiModels.ModuleView buildSkillStudioModule() {
        long total = countSkillStudioProjects(null);
        long published = countSkillStudioProjects(STATUS_PUBLISHED);
        return module(
                "skillstudio",
                "Skill Studio",
                total,
                status("published", "Published", published),
                status("unpublished", "Not Published", remaining(total, published)));
    }

    private AdminDashboardApiModels.ModuleView buildSkillManagementModule(Long operatorUserId) {
        List<SkillCatalogService.SkillCatalogView> items = skillCatalogService.listCatalogs(operatorUserId, false);
        long total = items.size();
        long visible = items.stream().filter(SkillCatalogService.SkillCatalogView::visible).count();
        return module(
                "skill-management",
                "Skill Management",
                total,
                status("visible", "Visible", visible),
                status("hidden", "Hidden", remaining(total, visible)));
    }

    private AdminDashboardApiModels.ModuleView buildToolLibraryModule(Long operatorUserId) {
        List<ToolLibraryDisplayItem> items = buildToolLibraryDisplayItems(operatorUserId);
        long total = items.size();
        long enabled = items.stream().filter(ToolLibraryDisplayItem::enabled).count();
        return module(
                "tool-library",
                "Tool Library",
                total,
                status("enabled", "Enabled", enabled),
                status("disabled", "Disabled", remaining(total, enabled)));
    }

    private AdminDashboardApiModels.ModuleView buildMcpModule(Long operatorUserId) {
        List<McpServerService.McpServerView> items = loadAllMcpServers(operatorUserId);
        long total = items.size();
        long enabled = items.stream().filter(McpServerService.McpServerView::enabled).count();
        return module(
                "mcp",
                "MCP Service",
                total,
                status("enabled", "Enabled", enabled),
                status("disabled", "Disabled", remaining(total, enabled)));
    }

    private AdminDashboardApiModels.ModuleView buildApiLibraryModule() {
        List<LowcodeApiBrowseService.PlatformOption> platforms = lowcodeApiBrowseService.listPlatforms();
        if (platforms.isEmpty()) {
            return emptyApiModule();
        }
        try {
            String platformKey = platforms.get(0).key();
            List<LowcodeApiBrowseService.AppView> apps = lowcodeApiBrowseService.listApps(platformKey);
            if (apps.isEmpty()) {
                return emptyApiModule();
            }
            List<LowcodeApiBrowseService.ApiView> apis =
                    lowcodeApiBrowseService.listApis(platformKey, apps.get(0).appId());
            long total = apis.size();
            long registered = apis.stream().filter(LowcodeApiBrowseService.ApiView::registered).count();
            return module(
                    "api-library",
                    "API Library",
                    total,
                    status("registered", "Registered", registered),
                    status("unregistered", "Unregistered", remaining(total, registered)));
        } catch (TaskException ex) {
            return emptyApiModule();
        }
    }

    private AdminDashboardApiModels.ModuleView buildConnectorModule(Long operatorUserId) {
        List<IntegrationConnectorService.ConnectorSummary> items = loadAllConnectors(operatorUserId);
        long total = items.size();
        List<String> statuses = items.stream().map(IntegrationConnectorService.ConnectorSummary::status).toList();
        long active = countStatus(statuses, STATUS_ACTIVE);
        long draft = countStatus(statuses, STATUS_DRAFT);
        return module(
                "connector",
                "Connector",
                total,
                status("active", "Active", active),
                status("draft", "Draft", draft),
                status("disabled", "Disabled", remaining(total, active + draft)));
    }

    private AdminDashboardApiModels.ModuleView buildChannelModule() {
        Map<String, ChannelConfig> configMap = new LinkedHashMap<>();
        for (ChannelConfig config : channelConfigService.listAll()) {
            String channelType = normalizeChannelType(config == null ? null : config.getChannelType());
            if (DEFAULT_CHANNEL_TYPES.contains(channelType)) {
                configMap.putIfAbsent(channelType, config);
            }
        }
        long total = DEFAULT_CHANNEL_TYPES.size();
        long initialized = configMap.size();
        return module(
                "channel",
                "Channel",
                total,
                status("initialized", "Initialized", initialized),
                status("uninitialized", "Uninitialized", remaining(total, initialized)));
    }

    private AdminDashboardApiModels.ModuleView buildDataSourceModule(Long operatorUserId) {
        List<IntegrationDataSourceService.DataSourceSummary> items =
                integrationDataSourceService.listDataSources("", "", "", operatorUserId);
        long total = items.size();
        long active = countStatus(
                items.stream().map(IntegrationDataSourceService.DataSourceSummary::status).toList(), STATUS_ACTIVE);
        return module(
                "data-source",
                "Data Source",
                total,
                status("active", "Active", active),
                status("disabled", "Disabled", remaining(total, active)));
    }

    private AdminDashboardApiModels.ModuleView buildDatasetModule(Long operatorUserId) {
        List<IntegrationDatasetService.DatasetSummary> items =
                integrationDatasetService.listDatasets("", "", null, null, operatorUserId);
        long total = items.size();
        long published = items.stream()
                .filter(item -> STATUS_PUBLISHED.equalsIgnoreCase(trimUpper(item.publishStatus())))
                .count();
        return module(
                "dataset",
                "Dataset",
                total,
                status("published", "Published", published),
                status("draft", "Not Published", remaining(total, published)));
    }

    private AdminDashboardApiModels.ModuleView buildModelLibraryModule(Long operatorUserId) throws TaskException {
        List<ModelLibraryService.ModelView> items =
                modelLibraryService.listModels(operatorUserId, null, null, null, null);
        long total = items.size();
        long active = countStatus(items.stream().map(ModelLibraryService.ModelView::status).toList(), STATUS_ACTIVE);
        return module(
                "model-library",
                "Model Library",
                total,
                status("active", "Active", active),
                status("draft", "Draft", remaining(total, active)));
    }

    private AdminDashboardApiModels.ModuleView buildAgentTemplateModule() {
        List<AgentListItemDto> items = loadAllAgentTemplates();
        long total = items.size();
        long enabled = items.stream()
                .filter(item -> item.getEnabled() != null && item.getEnabled() == 1)
                .count();
        return module(
                "agent-template",
                "Agent Template",
                total,
                status("enabled", "Enabled", enabled),
                status("disabled", "Disabled", remaining(total, enabled)));
    }

    private AdminDashboardApiModels.ModuleView emptyApiModule() {
        return module(
                "api-library",
                "API Library",
                0,
                status("registered", "Registered", 0),
                status("unregistered", "Unregistered", 0));
    }

    private long countSkillStudioProjects(String status) {
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.isNull("archived_at");
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim().toUpperCase(Locale.ROOT));
        }
        return skillStudioProjectMapper.selectCount(wrapper);
    }

    private List<KnowledgeBase> loadVisibleKnowledgeBases(Long operatorUserId) {
        Map<Long, KnowledgeBase> result = new LinkedHashMap<>();
        long pageNum = 1L;
        while (true) {
            IPage<KnowledgeBase> page = knowledgeBaseService.selectVisibleKnowledgeBasePage(
                    new KnowledgeBase(), pageNum, PAGE_SIZE, "", true, operatorUserId);
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }
            page.getRecords().stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.getKbId() != null)
                    .forEach(item -> result.put(item.getKbId(), item));
            if (result.size() >= page.getTotal()) {
                break;
            }
            pageNum++;
        }
        return List.copyOf(result.values());
    }

    private List<ToolLibraryDisplayItem> buildToolLibraryDisplayItems(Long operatorUserId) {
        List<SkillCatalogService.ToolLibraryItem> items = skillCatalogService.listToolLibrary(operatorUserId);
        Map<String, List<SkillCatalogService.ToolLibraryItem>> datasetGroups = new LinkedHashMap<>();
        List<ToolLibraryDisplayItem> displayItems = new ArrayList<>();
        for (SkillCatalogService.ToolLibraryItem item : items) {
            if (item == null || "SKILL_NATIVE".equals(item.type())) {
                continue;
            }
            if ("DATASET_TOOL".equals(item.type()) && item.source() != null && item.source().startsWith("dataset:")) {
                datasetGroups.computeIfAbsent(item.source(), key -> new ArrayList<>()).add(item);
                continue;
            }
            displayItems.add(new ToolLibraryDisplayItem(item.enabledGlobal()));
        }
        datasetGroups.values().forEach(group ->
                displayItems.add(new ToolLibraryDisplayItem(group.stream().allMatch(SkillCatalogService.ToolLibraryItem::enabledGlobal))));
        return List.copyOf(displayItems);
    }

    private List<McpServerService.McpServerView> loadAllMcpServers(Long operatorUserId) {
        List<McpServerService.McpServerView> result = new ArrayList<>();
        int page = 1;
        while (true) {
            McpServerService.McpServerPageResult pageResult =
                    mcpServerService.listServers(page, PAGE_SIZE, "", operatorUserId);
            if (pageResult == null || pageResult.list() == null || pageResult.list().isEmpty()) {
                break;
            }
            result.addAll(pageResult.list());
            if (result.size() >= pageResult.total()) {
                break;
            }
            page++;
        }
        return List.copyOf(result);
    }

    private List<IntegrationConnectorService.ConnectorSummary> loadAllConnectors(Long operatorUserId) {
        List<IntegrationConnectorService.ConnectorSummary> result = new ArrayList<>();
        int page = 1;
        while (true) {
            IntegrationConnectorService.ConnectorPageResult pageResult =
                    integrationConnectorService.listConnectors(page, PAGE_SIZE, "", "", operatorUserId);
            if (pageResult == null || pageResult.list() == null || pageResult.list().isEmpty()) {
                break;
            }
            result.addAll(pageResult.list());
            if (result.size() >= pageResult.total()) {
                break;
            }
            page++;
        }
        return List.copyOf(result);
    }

    private List<AgentListItemDto> loadAllAgentTemplates() {
        List<AgentListItemDto> result = new ArrayList<>();
        int page = 1;
        while (true) {
            AgentPageInput input = new AgentPageInput();
            input.setPage(page);
            input.setPageSize(PAGE_SIZE);
            AgentPageResult pageResult = agentTemplateService.listAgents(input);
            if (pageResult == null || pageResult.getItems() == null || pageResult.getItems().isEmpty()) {
                break;
            }
            result.addAll(pageResult.getItems());
            if (result.size() >= pageResult.getTotal()) {
                break;
            }
            page++;
        }
        return List.copyOf(result);
    }

    private long countStatus(List<String> statuses, String targetStatus) {
        return statuses.stream()
                .filter(status -> trimUpper(targetStatus).equals(trimUpper(status)))
                .count();
    }

    private long remaining(long total, long counted) {
        return Math.max(0L, total - counted);
    }

    private String normalizeChannelType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimText(String value) {
        return value == null ? "" : value.trim();
    }

    private AdminDashboardApiModels.ModuleView module(
            String moduleId, String label, long total, AdminDashboardApiModels.StatusView... statuses) {
        return new AdminDashboardApiModels.ModuleView(moduleId, label, total, List.of(statuses));
    }

    private AdminDashboardApiModels.StatusView status(String key, String label, long count) {
        return new AdminDashboardApiModels.StatusView(key, label, Math.max(0L, count));
    }

    private void requireAdmin(Long operatorUserId) throws TaskException {
        if (operatorUserId == null) {
            throw new TaskException("User context missing", TaskException.Code.UNKNOWN);
        }
        SysUserModel user = sysUserMapper.selectById(operatorUserId);
        if (user != null
                && user.getUserType() != null
                && user.getUserType() == UserType.admin.getValue()) {
            return;
        }
        if (user != null && "ADMIN".equals(trimUpper(user.getCode()))) {
            return;
        }
        throw new TaskException("Dashboard is admin only", TaskException.Code.UNKNOWN);
    }

    private record ToolLibraryDisplayItem(boolean enabled) {}
}
