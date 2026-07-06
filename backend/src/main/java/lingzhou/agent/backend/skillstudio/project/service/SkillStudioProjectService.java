package lingzhou.agent.backend.skillstudio.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lingzhou.agent.backend.app.SkillFilesystemSupport;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.mapper.ConversationSessionMapper;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.skill.service.SkillPackageService;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.draft.SkillStudioDraftFileService;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioProjectService {

    public static final ConversationSessionType SESSION_TYPE = ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT;
    private static final String PROJECT_STATUS_DRAFT = "DRAFT";
    private static final String PROJECT_STATUS_PUBLISHED = "PUBLISHED";
    private static final String SKILL_CATALOG_SOURCE_SKILLSTUDIO = "skillstudio";
    private static final String INITIAL_PUBLISHED_VERSION = "1.0";
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_SUMMARY_LENGTH = 120;
    private static final int MAX_RUNTIME_SKILL_NAME_LENGTH = 48;
    private static final Pattern SKILL_FRONT_MATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n?", Pattern.DOTALL);

    private final SkillStudioProjectMapper projectMapper;
    private final ConversationSessionMapper conversationSessionMapper;
    private final ConversationHistoryService conversationHistoryService;
    private final SkillStudioProjectMetadataGenerator metadataGenerator;
    private final SkillStudioProjectPendingUsageService pendingUsageService;
    private final SkillStudioDraftFileService draftFileService;
    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillCatalogService skillCatalogService;
    private final SkillStudioProjectSettingsService projectSettingsService;
    private final SkillPackageService skillPackageService;
    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final SysUserMapper sysUserMapper;

    public SkillStudioProjectService(
            SkillStudioProjectMapper projectMapper,
            ConversationSessionMapper conversationSessionMapper,
            ConversationHistoryService conversationHistoryService,
            SkillStudioProjectMetadataGenerator metadataGenerator,
            SkillStudioProjectPendingUsageService pendingUsageService,
            SkillStudioDraftFileService draftFileService,
            SkillCatalogMapper skillCatalogMapper,
            SkillCatalogService skillCatalogService,
            SkillStudioProjectSettingsService projectSettingsService,
            SkillPackageService skillPackageService,
            RuntimeExecutionProperties runtimeExecutionProperties,
            SysUserMapper sysUserMapper) {
        this.projectMapper = projectMapper;
        this.conversationSessionMapper = conversationSessionMapper;
        this.conversationHistoryService = conversationHistoryService;
        this.metadataGenerator = metadataGenerator;
        this.pendingUsageService = pendingUsageService;
        this.draftFileService = draftFileService;
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillCatalogService = skillCatalogService;
        this.projectSettingsService = projectSettingsService;
        this.skillPackageService = skillPackageService;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.sysUserMapper = sysUserMapper;
    }

    public ProjectPageResult listProjects(
            Long userId, Integer page, Integer pageSize, String keyword, String projectType, String status) {
        boolean adminUser = isAdminUser(userId);
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize == null ? 10 : pageSize, 100));
        IPage<SkillStudioProject> pageData =
                projectMapper.searchPage(userId, adminUser, safePage, safePageSize, keyword, projectType, status);
        List<SkillStudioProject> projects = pageData.getRecords();
        Map<Long, SysUserModel> creatorMap = loadProjectCreators(projects);
        List<ProjectSummary> summaries = projects.stream()
                .map(project -> toSummary(
                        resolveSessionViewerUserId(userId, adminUser, project),
                        project,
                        creatorMap.get(project.getCreateUserId())))
                .toList();
        return new ProjectPageResult(summaries, pageData.getTotal(), safePage, safePageSize);
    }

    public ProjectDetail getProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        return toDetail(userId, project);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetail createProject(Long userId, CreateProjectRequest request) throws TaskException {
        String description = normalizeRequiredDescription(request == null ? null : request.description());
        var generatedResult = metadataGenerator.generateWithUsage(description);
        var generated = ensureUniqueGeneratedMetadata(generatedResult.metadata());
        SkillStudioProject entity = new SkillStudioProject();
        entity.setProjectCode(UlidGenerator.next());
        entity.setName(buildProjectName(request == null ? null : request.name(), generated.name()));
        entity.setDescription(description);
        entity.setStatus(PROJECT_STATUS_DRAFT);
        entity.setProjectType(generated.projectType());
        entity.setDraftSkillName(generated.draftSkillName());
        entity.setRuntimeSkillName(generated.runtimeSkillName());
        entity.setIcon(generated.icon());
        entity.setIconColor(generated.iconColor());
        entity.setCategory(generated.category());
        entity.setDraftPath(SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + entity.getDraftSkillName());
        entity.setCoverSummary(shrink(generated.summary(), MAX_SUMMARY_LENGTH));
        entity.setInitialPrompt(description);
        entity.setCreateUserId(userId);
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        projectMapper.insert(entity);
        draftFileService.initializeDraftStructure(entity.getDraftSkillName());
        pendingUsageService.savePendingMetadataUsage(entity.getDraftSkillName(), generatedResult.usageSnapshot());
        return toDetail(userId, entity);
    }

    private SkillStudioProjectMetadataGenerator.GeneratedMetadata ensureUniqueGeneratedMetadata(
            SkillStudioProjectMetadataGenerator.GeneratedMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        String originalRuntimeSkillName = metadata.runtimeSkillName();
        String originalDraftSkillName = metadata.draftSkillName();
        String runtimeSkillName = SkillStudioProjectIdentityResolver.resolveUniqueName(
                originalRuntimeSkillName, MAX_RUNTIME_SKILL_NAME_LENGTH, this::runtimeSkillNameExists);
        boolean keepDraftAligned = StringUtils.hasText(originalRuntimeSkillName)
                && originalRuntimeSkillName.equals(originalDraftSkillName);
        String draftBase = keepDraftAligned ? runtimeSkillName : originalDraftSkillName;
        String draftSkillName = SkillStudioProjectIdentityResolver.resolveUniqueName(
                draftBase, MAX_RUNTIME_SKILL_NAME_LENGTH, this::draftSkillNameExists);
        return new SkillStudioProjectMetadataGenerator.GeneratedMetadata(
                metadata.name(),
                runtimeSkillName,
                draftSkillName,
                metadata.icon(),
                metadata.iconColor(),
                metadata.projectType(),
                metadata.category(),
                metadata.summary());
    }

    private boolean runtimeSkillNameExists(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return false;
        }
        String normalized = runtimeSkillName.trim();
        if (projectMapper.selectActiveByRuntimeSkillName(normalized) != null) {
            return true;
        }
        if (skillCatalogMapper.selectByRuntimeSkillName(normalized) != null) {
            return true;
        }
        Path runtimeRoot =
                SkillFilesystemSupport.resolveSkillRoot().toAbsolutePath().normalize();
        Path runtimeSkillDir = runtimeRoot.resolve(normalized).normalize();
        return runtimeSkillDir.startsWith(runtimeRoot) && Files.isDirectory(runtimeSkillDir);
    }

    private boolean draftSkillNameExists(String draftSkillName) {
        if (!StringUtils.hasText(draftSkillName)) {
            return false;
        }
        String normalized = draftSkillName.trim();
        if (projectMapper.selectActiveByDraftSkillName(normalized) != null) {
            return true;
        }
        Path draftRoot =
                Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT).toAbsolutePath().normalize();
        Path draftSkillDir = draftRoot.resolve(normalized).normalize();
        return draftSkillDir.startsWith(draftRoot) && Files.isDirectory(draftSkillDir);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetail updateProject(Long userId, Long projectId, UpdateProjectRequest request) throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        String nextDescription =
                normalizeOptionalDescription(request == null ? null : request.description(), project.getDescription());
        String nextName = buildProjectName(request == null ? null : request.name(), nextDescription);

        SkillStudioProject update = new SkillStudioProject();
        update.setId(project.getId());
        update.setName(nextName);
        update.setDescription(nextDescription);
        update.setCoverSummary(shrink(nextDescription, MAX_SUMMARY_LENGTH));
        update.setUpdatedAt(new Date());
        projectMapper.updateById(update);

        SkillStudioProject refreshed = requireOwnedProject(userId, projectId);
        return toDetail(userId, refreshed);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetail publishProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        publishDraftToRuntimeSkill(project);
        SkillCatalog catalog = upsertSkillCatalog(project);
        refreshRuntimeSkills(project.getRuntimeSkillName());
        syncPublishedSkillToolBindings(userId, project, catalog);

        SkillStudioProject update = new SkillStudioProject();
        update.setId(project.getId());
        update.setStatus(PROJECT_STATUS_PUBLISHED);
        update.setUpdatedAt(new Date());
        projectMapper.updateById(update);

        SkillStudioProject refreshed = requireOwnedProject(userId, projectId);
        return toDetail(userId, refreshed);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteProjectResult deleteProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        boolean draftDeleted = deleteProjectDraftDirectory(project.getDraftSkillName());
        deleteProjectPythonRuntimeEnv(project.getDraftSkillName());
        int affectedRows = projectMapper.deleteById(project.getId());
        if (affectedRows <= 0) {
            throw new TaskException("技能工坊项目删除失败", TaskException.Code.UNKNOWN);
        }
        return new DeleteProjectResult(true, projectId, draftDeleted);
    }

    public List<ChatSessionVo> listProjectSessions(Long userId, Long projectId, int limit) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return conversationHistoryService.listSessions(
                resolveProjectViewerUserId(userId, project), SESSION_TYPE, projectId, safeLimit);
    }

    public List<ChatMessageVo> listProjectMessages(
            Long userId, Long projectId, String sessionId, int pageNo, int pageSize) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        return conversationHistoryService
                .listMessages(
                        resolveProjectViewerUserId(userId, project),
                        SESSION_TYPE,
                        sessionId,
                        projectId,
                        pageNo,
                        pageSize)
                .items();
    }

    public List<ProjectFileNode> listProjectFiles(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        return buildProjectFileTree(project.getDraftSkillName());
    }

    public ProjectFileContent getProjectFileContent(Long userId, Long projectId, String path) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        String normalized = normalizeRequiredPath(path);
        String content = draftFileService
                .readFile(project.getDraftSkillName(), normalized)
                .orElse("");
        return new ProjectFileContent(normalized, content, detectFileType(normalized));
    }

    private SkillStudioProject requireReadableProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireOwnedProjectOrNull(userId, projectId);
        if (project == null && isAdminUser(userId)) {
            project = projectMapper.selectActiveProject(projectId);
        }
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private SkillStudioProject requireOwnedProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireOwnedProjectOrNull(userId, projectId);
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private SkillStudioProject requireOwnedProjectOrNull(Long userId, Long projectId) {
        return projectMapper.selectOwnedProject(userId, projectId);
    }

    private ProjectSummary toSummary(Long userId, SkillStudioProject project, SysUserModel creator) {
        var sessions = conversationSessionMapper.selectRecentSessions(userId, SESSION_TYPE.name(), project.getId(), 1);
        ChatSessionVo latestSession = sessions.isEmpty()
                ? null
                : conversationHistoryService.listSessions(userId, SESSION_TYPE, project.getId(), 1).stream()
                        .findFirst()
                        .orElse(null);
        return new ProjectSummary(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getProjectType(),
                project.getDraftSkillName(),
                project.getRuntimeSkillName(),
                project.getIcon(),
                project.getIconColor(),
                project.getCategory(),
                project.getDraftPath(),
                project.getCoverSummary(),
                resolvePublishedVersion(project.getRuntimeSkillName()),
                latestSession == null ? null : latestSession.getId(),
                latestSession == null ? null : latestSession.getTitle(),
                latestSession == null ? null : latestSession.getLastMessage(),
                project.getCreateUserId(),
                creator == null ? null : normalizeNullableText(creator.getName()),
                creator == null ? null : normalizeNullableText(creator.getCode()),
                project.getUpdatedAt(),
                project.getCreatedAt());
    }

    private Long resolveSessionViewerUserId(Long userId, boolean adminUser, SkillStudioProject project) {
        if (!adminUser || project == null || project.getCreateUserId() == null) {
            return userId;
        }
        return project.getCreateUserId();
    }

    private Long resolveProjectViewerUserId(Long userId, SkillStudioProject project) {
        return resolveSessionViewerUserId(userId, isAdminUser(userId), project);
    }

    private boolean isAdminUser(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        return user != null && user.getUserType() != null && user.getUserType() == UserType.admin.getValue();
    }

    private ProjectDetail toDetail(Long userId, SkillStudioProject project) {
        Long viewerUserId = resolveProjectViewerUserId(userId, project);
        List<ChatSessionVo> sessions =
                conversationHistoryService.listSessions(viewerUserId, SESSION_TYPE, project.getId(), 20);
        return new ProjectDetail(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getProjectType(),
                project.getDraftSkillName(),
                project.getRuntimeSkillName(),
                project.getIcon(),
                project.getIconColor(),
                project.getCategory(),
                project.getDraftPath(),
                project.getCoverSummary(),
                resolvePublishedVersion(project.getRuntimeSkillName()),
                project.getInitialPrompt(),
                project.getLastSessionId(),
                project.getLastMessagePreview(),
                sessions,
                project.getUpdatedAt(),
                project.getCreatedAt());
    }

    private String resolvePublishedVersion(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return "";
        }
        SkillCatalog catalog = skillCatalogMapper.selectByRuntimeSkillName(runtimeSkillName.trim());
        if (catalog == null || !StringUtils.hasText(catalog.getVersion())) {
            return "";
        }
        return catalog.getVersion().trim();
    }

    private String normalizeRequiredDescription(String value) throws TaskException {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("项目描述不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeOptionalDescription(String next, String fallback) {
        if (!StringUtils.hasText(next)) {
            return fallback;
        }
        return next.trim();
    }

    private String buildProjectName(String name, String fallbackName) {
        String normalized = StringUtils.hasText(name) ? name.trim() : "";
        if (StringUtils.hasText(normalized)) {
            return shrink(normalized, MAX_NAME_LENGTH);
        }
        String source = StringUtils.hasText(fallbackName) ? fallbackName.trim() : "新技能项目";
        return shrink(source, 24);
    }

    private String shrink(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }

    private String detectFileType(String path) {
        String normalized = String.valueOf(path).toLowerCase();
        if (normalized.endsWith(".md")) {
            return "markdown";
        }
        if (normalized.endsWith(".json")) {
            return "json";
        }
        if (normalized.endsWith(".py")) {
            return "python";
        }
        if ("requirements.txt".equals(normalized)) {
            return "requirements";
        }
        if (normalized.endsWith(".txt")) {
            return "text";
        }
        return "text";
    }

    private Map<Long, SysUserModel> loadProjectCreators(List<SkillStudioProject> projects) {
        Map<Long, SysUserModel> result = new HashMap<>();
        if (projects == null || projects.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> creatorIds = new LinkedHashSet<>();
        for (SkillStudioProject project : projects) {
            if (project == null || project.getCreateUserId() == null) {
                continue;
            }
            creatorIds.add(project.getCreateUserId());
        }
        if (creatorIds.isEmpty()) {
            return result;
        }
        List<SysUserModel> users = sysUserMapper.selectBatchIds(creatorIds);
        if (users == null || users.isEmpty()) {
            return result;
        }
        for (SysUserModel user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            result.put(user.getId(), user);
        }
        return result;
    }

    private String normalizeNullableText(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String normalizeRequiredPath(String path) throws TaskException {
        String normalized = StringUtils.hasText(path) ? path.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("文件路径不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private boolean deleteProjectDraftDirectory(String draftSkillName) throws TaskException {
        String normalizedDraftSkillName = normalizeRequired(draftSkillName, "草稿技能名不能为空");
        Path draftRoot =
                Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT).toAbsolutePath().normalize();
        Path targetDraftDir = draftRoot.resolve(normalizedDraftSkillName).normalize();
        if (!targetDraftDir.startsWith(draftRoot)) {
            throw new TaskException("草稿目录路径非法，无法删除", TaskException.Code.UNKNOWN);
        }
        if (!Files.exists(targetDraftDir)) {
            return false;
        }
        if (!Files.isDirectory(targetDraftDir)) {
            throw new TaskException("草稿目录结构异常，无法删除", TaskException.Code.UNKNOWN);
        }
        try {
            deleteDirectory(targetDraftDir);
            return true;
        } catch (IOException ex) {
            throw new TaskException("删除技能工坊草稿目录失败: " + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private boolean deleteProjectPythonRuntimeEnv(String draftSkillName) throws TaskException {
        String normalizedDraftSkillName = normalizeRequired(draftSkillName, "草稿技能名不能为空");
        Path workspaceBaseDir = Path.of(runtimeExecutionProperties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize();
        Path pythonSkillsRoot = workspaceBaseDir
                .resolve("public")
                .resolve("runtime-envs")
                .resolve("python")
                .resolve("skills")
                .normalize();
        Path targetEnvDir = pythonSkillsRoot.resolve(normalizedDraftSkillName).normalize();
        if (!targetEnvDir.startsWith(pythonSkillsRoot)) {
            throw new TaskException("Python 运行时环境路径非法，无法删除", TaskException.Code.UNKNOWN);
        }
        if (!Files.exists(targetEnvDir)) {
            return false;
        }
        if (!Files.isDirectory(targetEnvDir)) {
            throw new TaskException("Python 运行时环境目录结构异常，无法删除", TaskException.Code.UNKNOWN);
        }
        try {
            deleteDirectory(targetEnvDir);
            return true;
        } catch (IOException ex) {
            throw new TaskException("删除 Python 运行时环境目录失败: " + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private void publishDraftToRuntimeSkill(SkillStudioProject project) throws TaskException {
        String draftSkillName = normalizeRequired(project == null ? null : project.getDraftSkillName(), "草稿技能名不能为空");
        String runtimeSkillName =
                normalizeRequired(project == null ? null : project.getRuntimeSkillName(), "运行时技能名不能为空");

        Path draftRoot =
                Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT).toAbsolutePath().normalize();
        Path draftDir = draftRoot.resolve(draftSkillName).normalize();
        if (!draftDir.startsWith(draftRoot) || !Files.isDirectory(draftDir)) {
            throw new TaskException("技能工坊草稿不存在，无法发布", TaskException.Code.UNKNOWN);
        }
        Path draftSkillFile = draftDir.resolve("SKILL.md").normalize();
        if (!Files.isRegularFile(draftSkillFile)) {
            throw new TaskException("技能工坊草稿缺少 SKILL.md，无法发布", TaskException.Code.UNKNOWN);
        }

        Path runtimeRoot =
                SkillFilesystemSupport.resolveSkillRoot().toAbsolutePath().normalize();
        Path runtimeSkillDir = runtimeRoot.resolve(runtimeSkillName).normalize();
        if (!runtimeSkillDir.startsWith(runtimeRoot)) {
            throw new TaskException("运行时技能路径非法，无法发布", TaskException.Code.UNKNOWN);
        }

        try {
            deleteDirectory(runtimeSkillDir);
            copyDirectory(draftDir, runtimeSkillDir);
            normalizePublishedSkillMarkdown(runtimeSkillDir.resolve("SKILL.md"), runtimeSkillName);
        } catch (IOException ex) {
            throw new TaskException("发布技能文件失败: " + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private SkillCatalog upsertSkillCatalog(SkillStudioProject project) throws TaskException {
        String runtimeSkillName =
                normalizeRequired(project == null ? null : project.getRuntimeSkillName(), "运行时技能名不能为空");
        SkillCatalog existing = skillCatalogMapper.selectByRuntimeSkillName(runtimeSkillName);
        if (existing == null) {
            SkillCatalog created = new SkillCatalog();
            created.setRuntimeSkillName(runtimeSkillName);
            created.setDisplayName(buildCatalogDisplayName(project));
            created.setDescription(buildCatalogDescription(project));
            created.setCategory(buildCatalogCategory(project));
            created.setSource(SKILL_CATALOG_SOURCE_SKILLSTUDIO);
            created.setOwnerUserId(project.getCreateUserId());
            created.setVersion(INITIAL_PUBLISHED_VERSION);
            created.setIcon(
                    StringUtils.hasText(project.getIcon()) ? project.getIcon().trim() : null);
            created.setIconColor(
                    StringUtils.hasText(project.getIconColor())
                            ? project.getIconColor().trim()
                            : null);
            skillCatalogMapper.insert(created);
            return created;
        }

        SkillCatalog update = new SkillCatalog();
        update.setId(existing.getId());
        update.setDisplayName(buildCatalogDisplayName(project));
        update.setDescription(buildCatalogDescription(project));
        update.setCategory(buildCatalogCategory(project));
        update.setSource(SKILL_CATALOG_SOURCE_SKILLSTUDIO);
        if (existing.getOwnerUserId() == null && project.getCreateUserId() != null) {
            update.setOwnerUserId(project.getCreateUserId());
        }
        update.setVersion(bumpMajorVersion(existing.getVersion()));
        update.setIcon(
                StringUtils.hasText(project.getIcon()) ? project.getIcon().trim() : null);
        update.setIconColor(
                StringUtils.hasText(project.getIconColor())
                        ? project.getIconColor().trim()
                        : null);
        skillCatalogMapper.updateById(update);
        existing.setDisplayName(update.getDisplayName());
        existing.setDescription(update.getDescription());
        existing.setCategory(update.getCategory());
        existing.setSource(update.getSource());
        if (update.getOwnerUserId() != null) {
            existing.setOwnerUserId(update.getOwnerUserId());
        }
        existing.setVersion(update.getVersion());
        existing.setIcon(update.getIcon());
        existing.setIconColor(update.getIconColor());
        return existing;
    }

    private void syncPublishedSkillToolBindings(Long userId, SkillStudioProject project, SkillCatalog catalog)
            throws TaskException {
        if (project == null || catalog == null || catalog.getId() == null) {
            return;
        }
        Long projectId = project.getId();
        if (projectId == null) {
            return;
        }
        SkillStudioProjectSettingsService.ProjectSettingsState settingsState =
                projectSettingsService.loadState(userId, projectId);
        List<String> boundToolNames = new ArrayList<>();
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (SkillStudioProjectSettingsService.ProjectToolBindingState binding : settingsState.bindings()) {
            if (binding == null || !binding.enabled() || !StringUtils.hasText(binding.toolName())) {
                continue;
            }
            String toolName = binding.toolName().trim();
            if (dedup.add(toolName)) {
                boundToolNames.add(toolName);
            }
        }
        skillCatalogService.updateBindings(catalog.getId(), boundToolNames, userId);
    }

    private void refreshRuntimeSkills(String runtimeSkillName) throws TaskException {
        SkillPackageService.RefreshResult refreshResult = skillPackageService.refreshSkillRuntime();
        List<String> names = refreshResult == null ? List.of() : refreshResult.runtimeSkillNames();
        if (names == null || !names.contains(runtimeSkillName)) {
            throw new TaskException("技能运行时刷新后未发现已发布技能: " + runtimeSkillName, TaskException.Code.UNKNOWN);
        }
    }

    private void normalizePublishedSkillMarkdown(Path skillFile, String runtimeSkillName) throws IOException {
        if (skillFile == null || !Files.isRegularFile(skillFile)) {
            return;
        }
        String content = Files.readString(skillFile);
        String patched = rewriteFrontMatterName(content, runtimeSkillName);
        Files.writeString(skillFile, patched);
    }

    private String rewriteFrontMatterName(String content, String runtimeSkillName) {
        String safeContent = content == null ? "" : content;
        String safeRuntimeName =
                StringUtils.hasText(runtimeSkillName) ? runtimeSkillName.trim() : "skill-studio-project";
        Matcher matcher = SKILL_FRONT_MATTER_PATTERN.matcher(safeContent);
        if (!matcher.find()) {
            return "---\nname: " + safeRuntimeName + "\n---\n\n" + safeContent;
        }
        String header = matcher.group(1);
        String rewrittenHeader;
        if (header.matches("(?ms).*^\\s*name\\s*:\\s*.*$.*")) {
            rewrittenHeader = header.replaceFirst("(?m)^\\s*name\\s*:\\s*.*$", "name: " + safeRuntimeName);
        } else {
            rewrittenHeader = "name: " + safeRuntimeName + "\n" + header;
        }
        return "---\n" + rewrittenHeader + "\n---\n" + safeContent.substring(matcher.end());
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            for (Path source : stream.toList()) {
                Path relative = sourceDir.relativize(source);
                Path target = targetDir.resolve(relative).normalize();
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.sorted(java.util.Comparator.comparingInt(Path::getNameCount)
                            .reversed())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String buildCatalogDisplayName(SkillStudioProject project) {
        return shrink(
                StringUtils.hasText(project == null ? null : project.getName())
                        ? project.getName().trim()
                        : "技能工坊技能",
                255);
    }

    private String buildCatalogDescription(SkillStudioProject project) {
        String description = StringUtils.hasText(project == null ? null : project.getDescription())
                ? project.getDescription().trim()
                : "";
        return shrink(description, 2000);
    }

    private String buildCatalogCategory(SkillStudioProject project) {
        if (StringUtils.hasText(project == null ? null : project.getCategory())) {
            return shrink(project.getCategory().trim(), 120);
        }
        return "技能工坊";
    }

    private String bumpMajorVersion(String version) {
        int[] parsed = parseVersion(version);
        int nextMajor = parsed[0] + 1;
        return nextMajor + ".0";
    }

    private int[] parseVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return new int[] {1, 0};
        }
        String[] parts = version.trim().split("\\.");
        int major = parseVersionPart(parts, 0, 1);
        int minor = parseVersionPart(parts, 1, 0);
        return new int[] {major, minor};
    }

    private int parseVersionPart(String[] parts, int index, int fallback) {
        if (parts == null || index < 0 || index >= parts.length) {
            return fallback;
        }
        String value = StringUtils.hasText(parts[index]) ? parts[index].trim() : "";
        if (!value.matches("\\d+")) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String normalizeRequired(String value, String errorMessage) throws TaskException {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException(errorMessage, TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private List<ProjectFileNode> buildProjectFileTree(String skillName) {
        Map<String, MutableProjectFileNode> folderMap = new LinkedHashMap<>();
        List<MutableProjectFileNode> rootChildren = new java.util.ArrayList<>();

        java.util.function.Function<String, MutableProjectFileNode> ensureFolder = new java.util.function.Function<>() {
            @Override
            public MutableProjectFileNode apply(String path) {
                if (!StringUtils.hasText(path)) {
                    return null;
                }
                if (folderMap.containsKey(path)) {
                    return folderMap.get(path);
                }
                String normalized = path.trim().replace("\\", "/");
                String[] parts = normalized.split("/");
                String name = parts[parts.length - 1];
                String parentPath =
                        normalized.contains("/") ? normalized.substring(0, normalized.lastIndexOf('/')) : "";
                MutableProjectFileNode node = new MutableProjectFileNode(
                        "dir:" + normalized, normalized, name, "folder", null, new java.util.ArrayList<>());
                folderMap.put(normalized, node);
                MutableProjectFileNode parent = this.apply(parentPath);
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    rootChildren.add(node);
                }
                return node;
            }
        };

        for (String entry : draftFileService.listAllEntries(skillName)) {
            String normalized = StringUtils.hasText(entry) ? entry.trim().replace("\\", "/") : "";
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            boolean isFile = normalized.contains(".")
                    && draftFileService.readFile(skillName, normalized).isPresent();
            if (!isFile) {
                ensureFolder.apply(normalized);
                continue;
            }
            String fileName =
                    normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
            String parentPath = normalized.contains("/") ? normalized.substring(0, normalized.lastIndexOf('/')) : "";
            MutableProjectFileNode parent = ensureFolder.apply(parentPath);
            MutableProjectFileNode fileNode = new MutableProjectFileNode(
                    "file:" + normalized, normalized, fileName, "file", detectFileType(normalized), null);
            if (parent != null) {
                parent.children().add(fileNode);
            } else {
                rootChildren.add(fileNode);
            }
        }

        sortNodes(rootChildren);
        return rootChildren.stream().map(this::toProjectFileNode).toList();
    }

    private void sortNodes(List<MutableProjectFileNode> nodes) {
        nodes.sort((left, right) -> {
            if (!String.valueOf(left.type()).equals(String.valueOf(right.type()))) {
                return "folder".equals(left.type()) ? -1 : 1;
            }
            return String.valueOf(left.name()).compareToIgnoreCase(String.valueOf(right.name()));
        });
        nodes.forEach(node -> {
            if (node.children() != null && !node.children().isEmpty()) {
                sortNodes(node.children());
            }
        });
    }

    private ProjectFileNode toProjectFileNode(MutableProjectFileNode node) {
        List<ProjectFileNode> children = node.children() == null
                ? List.of()
                : node.children().stream().map(this::toProjectFileNode).toList();
        return new ProjectFileNode(node.key(), node.path(), node.name(), node.type(), node.fileType(), children);
    }

    public record CreateProjectRequest(String name, String description) {}

    public record UpdateProjectRequest(String name, String description) {}

    public record DeleteProjectResult(boolean success, Long projectId, boolean draftDeleted) {}

    public record ProjectPageResult(List<ProjectSummary> list, long total, int page, int pageSize) {}

    public record ProjectSummary(
            Long id,
            String projectCode,
            String name,
            String description,
            String status,
            String projectType,
            String draftSkillName,
            String runtimeSkillName,
            String icon,
            String iconColor,
            String category,
            String draftPath,
            String coverSummary,
            String publishedVersion,
            String latestSessionCode,
            String latestSessionTitle,
            String latestMessage,
            Long createUserId,
            String creatorName,
            String creatorCode,
            Date updatedAt,
            Date createdAt) {}

    public record ProjectDetail(
            Long id,
            String projectCode,
            String name,
            String description,
            String status,
            String projectType,
            String draftSkillName,
            String runtimeSkillName,
            String icon,
            String iconColor,
            String category,
            String draftPath,
            String coverSummary,
            String publishedVersion,
            String initialPrompt,
            Long lastSessionId,
            String lastMessagePreview,
            List<ChatSessionVo> sessions,
            Date updatedAt,
            Date createdAt) {}

    public record ProjectFileNode(
            String key, String path, String name, String type, String fileType, List<ProjectFileNode> children) {}

    public record ProjectFileContent(String path, String content, String fileType) {}

    private record MutableProjectFileNode(
            String key,
            String path,
            String name,
            String type,
            String fileType,
            List<MutableProjectFileNode> children) {}
}
