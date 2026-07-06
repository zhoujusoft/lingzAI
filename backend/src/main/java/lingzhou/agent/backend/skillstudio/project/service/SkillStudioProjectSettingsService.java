package lingzhou.agent.backend.skillstudio.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioProjectSettingsService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ProjectToolBindingPayload>> BINDING_LIST_TYPE = new TypeReference<>() {};

    private final SkillStudioProjectMapper projectMapper;
    private final SkillCatalogService skillCatalogService;
    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;

    public SkillStudioProjectSettingsService(
            SkillStudioProjectMapper projectMapper,
            SkillCatalogService skillCatalogService,
            ObjectMapper objectMapper,
            SysUserMapper sysUserMapper) {
        this.projectMapper = projectMapper;
        this.skillCatalogService = skillCatalogService;
        this.objectMapper = objectMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public ProjectSettingsView getSettings(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        return toView(loadState(project));
    }

    public ProjectSettingsState loadState(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireReadableProject(userId, projectId);
        return loadState(project);
    }

    public ProjectSettingsState loadState(SkillStudioProject project) {
        if (project == null) {
            return emptyState();
        }
        List<String> projectHints = normalizeStringList(readStringList(project.getProjectHintsJson()));
        List<String> projectConstraints = normalizeStringList(readStringList(project.getProjectConstraintsJson()));
        List<ProjectToolBindingState> bindings = normalizeBindings(readBindings(project.getToolBindingsJson()));
        String digest = ensureDigest(project, projectHints, projectConstraints, bindings);
        String lastGeneratedDigest = normalizeText(project.getLastGeneratedToolDigest());
        boolean needsRegenerate = StringUtils.hasText(digest) && !Objects.equals(digest, lastGeneratedDigest);
        return new ProjectSettingsState(
                project.getId(),
                projectHints,
                projectConstraints,
                bindings,
                digest,
                lastGeneratedDigest,
                needsRegenerate);
    }

    public List<String> listEnabledToolNames(Long userId, Long projectId) throws TaskException {
        return loadState(userId, projectId).bindings().stream()
                .filter(ProjectToolBindingState::enabled)
                .map(ProjectToolBindingState::toolName)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectSettingsView updateSettings(Long userId, Long projectId, UpdateProjectSettingsRequest request)
            throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        List<String> projectHints = normalizeStringList(request == null ? null : request.projectHints());
        List<String> projectConstraints = normalizeStringList(request == null ? null : request.projectConstraints());
        List<ProjectToolBindingState> bindings =
                normalizeBindings(toPayloadListFromInput(request == null ? null : request.bindings()));
        validateBindings(userId, bindings);
        String digest = computeDigest(projectHints, projectConstraints, bindings);

        SkillStudioProject update = new SkillStudioProject();
        update.setId(project.getId());
        update.setProjectHintsJson(writeJson(projectHints));
        update.setProjectConstraintsJson(writeJson(projectConstraints));
        update.setToolBindingsJson(writeJson(toPayloadListFromState(bindings)));
        update.setToolSettingsDigest(digest);
        projectMapper.updateById(update);

        SkillStudioProject refreshed = requireOwnedProject(userId, projectId);
        refreshed.setProjectHintsJson(update.getProjectHintsJson());
        refreshed.setProjectConstraintsJson(update.getProjectConstraintsJson());
        refreshed.setToolBindingsJson(update.getToolBindingsJson());
        refreshed.setToolSettingsDigest(digest);
        return toView(loadState(refreshed));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markGenerated(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        markGenerated(project);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markGenerated(SkillStudioProject project) throws TaskException {
        if (project == null || project.getId() == null) {
            return;
        }
        SkillStudioProject latestProject = resolveLatestProject(project);
        String digest = normalizeText(latestProject.getToolSettingsDigest());
        if (!StringUtils.hasText(digest)) {
            digest = loadState(latestProject).toolSettingsDigest();
        }
        SkillStudioProject update = new SkillStudioProject();
        update.setId(latestProject.getId());
        update.setLastGeneratedToolDigest(digest);
        projectMapper.updateById(update);
    }

    private SkillStudioProject resolveLatestProject(SkillStudioProject project) {
        if (project == null || project.getId() == null) {
            return project;
        }
        SkillStudioProject latest = projectMapper.selectById(project.getId());
        return latest == null ? project : latest;
    }

    private ProjectSettingsView toView(ProjectSettingsState state) {
        return new ProjectSettingsView(
                state.projectId(),
                state.projectHints(),
                state.projectConstraints(),
                state.toolSettingsDigest(),
                state.lastGeneratedToolDigest(),
                state.needsRegenerate(),
                state.bindings().stream()
                        .map(binding -> new ProjectToolBindingView(
                                binding.toolName(),
                                binding.enabled(),
                                binding.businessPurpose(),
                                binding.triggerHints(),
                                binding.priority()))
                        .toList());
    }

    private void validateBindings(Long userId, List<ProjectToolBindingState> bindings) throws TaskException {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Map<String, SkillCatalogService.ToolLibraryItem> toolMap = new LinkedHashMap<>();
        for (SkillCatalogService.ToolLibraryItem item : skillCatalogService.listToolLibrary(userId)) {
            if (item != null && item.bindable() && StringUtils.hasText(item.name())) {
                toolMap.put(item.name(), item);
            }
        }
        for (ProjectToolBindingState binding : bindings) {
            if (binding == null || !StringUtils.hasText(binding.toolName())) {
                continue;
            }
            SkillCatalogService.ToolLibraryItem tool = toolMap.get(binding.toolName());
            if (tool == null || !tool.bindable()) {
                throw new TaskException("仅支持绑定可追加工具：" + binding.toolName(), TaskException.Code.UNKNOWN);
            }
        }
    }

    private SkillStudioProject requireOwnedProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = projectMapper.selectOwnedProject(userId, projectId);
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private SkillStudioProject requireReadableProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = projectMapper.selectOwnedProject(userId, projectId);
        if (project == null && isAdminUser(userId)) {
            project = projectMapper.selectActiveProject(projectId);
        }
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private boolean isAdminUser(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        return user != null && user.getUserType() != null && user.getUserType() == UserType.admin.getValue();
    }

    private ProjectSettingsState emptyState() {
        return new ProjectSettingsState(null, List.of(), List.of(), List.of(), "", "", false);
    }

    private String ensureDigest(
            SkillStudioProject project,
            List<String> projectHints,
            List<String> projectConstraints,
            List<ProjectToolBindingState> bindings) {
        String current = normalizeText(project.getToolSettingsDigest());
        if (StringUtils.hasText(current)) {
            return current;
        }
        if (projectHints.isEmpty() && projectConstraints.isEmpty() && bindings.isEmpty()) {
            return "";
        }
        return computeDigest(projectHints, projectConstraints, bindings);
    }

    private String computeDigest(
            List<String> projectHints, List<String> projectConstraints, List<ProjectToolBindingState> bindings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectHints", projectHints == null ? List.of() : projectHints);
        payload.put("projectConstraints", projectConstraints == null ? List.of() : projectConstraints);
        payload.put(
                "bindings",
                (bindings == null ? List.<ProjectToolBindingState>of() : bindings)
                        .stream()
                                .sorted(Comparator.comparing(ProjectToolBindingState::toolName))
                                .map(binding -> Map.of(
                                        "toolName", binding.toolName(),
                                        "enabled", binding.enabled(),
                                        "businessPurpose", binding.businessPurpose(),
                                        "triggerHints", binding.triggerHints(),
                                        "priority", binding.priority()))
                                .toList());
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("计算技能工坊工具设置摘要失败", ex);
        }
    }

    private List<String> readStringList(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ProjectToolBindingPayload> readBindings(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, BINDING_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化技能工坊项目设置失败", ex);
        }
    }

    private List<String> normalizeStringList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String normalized = normalizeText(rawValue);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private List<ProjectToolBindingPayload> toPayloadListFromInput(List<ProjectToolBindingInput> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> new ProjectToolBindingPayload(
                        binding.toolName(),
                        binding.enabled(),
                        binding.businessPurpose(),
                        binding.triggerHints(),
                        binding.priority()))
                .toList();
    }

    private List<ProjectToolBindingPayload> toPayloadListFromState(List<ProjectToolBindingState> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> new ProjectToolBindingPayload(
                        binding.toolName(),
                        binding.enabled(),
                        binding.businessPurpose(),
                        binding.triggerHints(),
                        binding.priority()))
                .toList();
    }

    private List<ProjectToolBindingState> normalizeBindings(List<ProjectToolBindingPayload> rawBindings) {
        if (rawBindings == null || rawBindings.isEmpty()) {
            return List.of();
        }
        Map<String, ProjectToolBindingState> dedup = new LinkedHashMap<>();
        for (ProjectToolBindingPayload rawBinding : rawBindings) {
            if (rawBinding == null) {
                continue;
            }
            String toolName = normalizeText(rawBinding.toolName());
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            List<String> triggerHints = normalizeStringList(rawBinding.triggerHints());
            int priority = rawBinding.priority() == null ? 100 : Math.max(1, rawBinding.priority());
            dedup.put(
                    toolName,
                    new ProjectToolBindingState(
                            toolName,
                            rawBinding.enabled() == null || Boolean.TRUE.equals(rawBinding.enabled()),
                            normalizeText(rawBinding.businessPurpose()),
                            triggerHints,
                            priority));
        }
        List<ProjectToolBindingState> normalized = new ArrayList<>(dedup.values());
        normalized.sort(Comparator.comparingInt(ProjectToolBindingState::priority)
                .thenComparing(ProjectToolBindingState::toolName));
        return List.copyOf(normalized);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record ProjectSettingsView(
            Long projectId,
            List<String> projectHints,
            List<String> projectConstraints,
            String toolSettingsDigest,
            String lastGeneratedToolDigest,
            boolean needsRegenerate,
            List<ProjectToolBindingView> bindings) {}

    public record ProjectToolBindingView(
            String toolName, boolean enabled, String businessPurpose, List<String> triggerHints, Integer priority) {}

    public record UpdateProjectSettingsRequest(
            List<String> projectHints, List<String> projectConstraints, List<ProjectToolBindingInput> bindings) {}

    public record ProjectToolBindingInput(
            String toolName, Boolean enabled, String businessPurpose, List<String> triggerHints, Integer priority) {}

    public record ProjectSettingsState(
            Long projectId,
            List<String> projectHints,
            List<String> projectConstraints,
            List<ProjectToolBindingState> bindings,
            String toolSettingsDigest,
            String lastGeneratedToolDigest,
            boolean needsRegenerate) {}

    public record ProjectToolBindingState(
            String toolName, boolean enabled, String businessPurpose, List<String> triggerHints, int priority) {}

    private record ProjectToolBindingPayload(
            String toolName, Boolean enabled, String businessPurpose, List<String> triggerHints, Integer priority) {}
}
