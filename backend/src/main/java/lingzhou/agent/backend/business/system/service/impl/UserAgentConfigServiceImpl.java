package lingzhou.agent.backend.business.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.system.dao.AgentTemplateMapper;
import lingzhou.agent.backend.business.system.dao.AgentTemplateSkillBindingMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.dao.UserAgentMapper;
import lingzhou.agent.backend.business.system.dao.UserAgentFileMapper;
import lingzhou.agent.backend.business.system.dao.UserAgentSkillBindingMapper;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.AgentTemplate;
import lingzhou.agent.backend.business.system.model.AgentTemplateSkillBinding;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserAgent;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.business.system.model.UserAgentSkillBinding;
import lingzhou.agent.backend.business.system.model.UserSkillPreferenceDto;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.common.lzException.ExceptionCode;
import lingzhou.agent.backend.common.lzException.LZException;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.utils.ImageSignatureUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserAgentConfigServiceImpl implements UserAgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserAgentConfigServiceImpl.class);
    private static final String DEFAULT_AGENT_CODE = "general-assistant";
    private static final String FILENAME_PROFILE = "PROFILE.md";
    private static final String FILENAME_SOUL = "SOUL.md";
    private static final String SECTION_IDENTITY = "身份";
    private static final String SECTION_RESPONSIBILITY = "岗位职责";
    private static final String SECTION_SUPPLEMENT = "补充信息";
    private static final String USER_AGENT_TABLE = "user_agent";
    private static final long MAX_AVATAR_FILE_SIZE = 2 * 1024 * 1024;
    private static final Set<String> SUPPORTED_AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");

    private final UserAgentFileMapper userAgentFileMapper;
    private final UserAgentSkillBindingMapper userAgentSkillBindingMapper;
    private final AgentTemplateSkillBindingMapper agentTemplateSkillBindingMapper;
    private final AgentTemplateMapper agentTemplateMapper;
    private final SkillCatalogMapper skillCatalogMapper;
    private final SysUserMapper sysUserMapper;
    private final UserAgentMapper userAgentMapper;
    private final MinioService minioService;
    private final JdbcTemplate jdbcTemplate;
    private final RoleResourcePermissionService roleResourcePermissionService;
    private volatile Boolean userAgentTableAvailable;

    public UserAgentConfigServiceImpl(
            UserAgentFileMapper userAgentFileMapper,
            UserAgentSkillBindingMapper userAgentSkillBindingMapper,
            AgentTemplateSkillBindingMapper agentTemplateSkillBindingMapper,
            AgentTemplateMapper agentTemplateMapper,
            SkillCatalogMapper skillCatalogMapper,
            SysUserMapper sysUserMapper,
            UserAgentMapper userAgentMapper,
            MinioService minioService,
            JdbcTemplate jdbcTemplate,
            RoleResourcePermissionService roleResourcePermissionService) {
        this.userAgentFileMapper = userAgentFileMapper;
        this.userAgentSkillBindingMapper = userAgentSkillBindingMapper;
        this.agentTemplateSkillBindingMapper = agentTemplateSkillBindingMapper;
        this.agentTemplateMapper = agentTemplateMapper;
        this.skillCatalogMapper = skillCatalogMapper;
        this.sysUserMapper = sysUserMapper;
        this.userAgentMapper = userAgentMapper;
        this.minioService = minioService;
        this.jdbcTemplate = jdbcTemplate;
        this.roleResourcePermissionService = roleResourcePermissionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureUserConfig(Long userId, Long roleId) {
        Long existsCount = userAgentFileMapper.selectCount(
                new LambdaQueryWrapper<UserAgentFile>().eq(UserAgentFile::getUserId, userId));

        if (existsCount != null && existsCount > 0) {
            return;
        }
        syncUserConfig(userId, roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUserConfig(Long userId, Long roleId) {
        AgentTemplate template = resolveEffectiveTemplate(userId, roleId);
        SysUserModel user = sysUserMapper.selectById(userId);
        String agentCode = template != null && hasText(template.getAgentCode())
                ? template.getAgentCode().trim()
                : DEFAULT_AGENT_CODE;
        String soulContent = template != null && template.getSoulTemplate() != null ? template.getSoulTemplate() : "";
        String profileHint =
                template != null && template.getProfileTemplate() != null ? template.getProfileTemplate() : "";

        upsertUserAgentFile(userId, agentCode, FILENAME_SOUL, soulContent, true);

        UserAgentFile profileFile = getUserAgentFile(userId, FILENAME_PROFILE);
        String syncedProfileContent =
                buildSyncedProfileContent(user, profileHint, profileFile != null ? profileFile.getContent() : null);
        if (profileFile == null) {
            upsertUserAgentFile(userId, agentCode, FILENAME_PROFILE, syncedProfileContent, true);
            return;
        }
        profileFile.setAgentCode(agentCode);
        profileFile.setContent(syncedProfileContent);
        profileFile.setEnabled(1);
        userAgentFileMapper.updateById(profileFile);
    }

    @Override
    public List<UserAgentFile> getUserAgentFiles(Long userId) {
        return userAgentFileMapper.selectList(new LambdaQueryWrapper<UserAgentFile>()
                .eq(UserAgentFile::getUserId, userId)
                .eq(UserAgentFile::getEnabled, 1)
                .orderByAsc(UserAgentFile::getFilename));
    }

    @Override
    public AgentDetailDto getUserAgentTemplate(Long userId) {
        AgentTemplate template = resolveUserAgentTemplate(userId);
        UserAgent userAgent = getUserAgent(userId);
        if (template == null) {
            template = resolveTemplateByCode(resolveCurrentAgentCode(userId));
        }
        if (template == null) {
            template = resolveTemplate(resolveEffectiveRoleId(userId, null));
        }
        if (template == null) {
            return null;
        }
        AgentDetailDto dto = new AgentDetailDto();
        dto.setId(template.getId());
        dto.setAgentCode(template.getAgentCode());
        dto.setAgentName(template.getAgentName());
        dto.setDescription(template.getDescription());
        dto.setOpeningMessage(template.getOpeningMessage());
        dto.setIcon(template.getIcon());
        dto.setDisplayName(resolveUserAgentDisplayName(userAgent, template));
        dto.setAvatarObjectName(userAgent != null ? userAgent.getAvatarObjectName() : null);
        dto.setAvatarUrl(buildAvatarPreviewUrl(userAgent != null ? userAgent.getAvatarObjectName() : null));
        dto.setSoulTemplate(template.getSoulTemplate());
        dto.setProfileTemplate(template.getProfileTemplate());
        dto.setEnabled(template.getEnabled());
        List<SkillSimpleDto> skills = loadTemplateSkills(template);
        dto.setSkillCount(skills.size());
        dto.setSkills(skills);
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }

    @Override
    public List<AgentSimpleDto> listAvailableAgents() {
        return agentTemplateMapper
                .selectList(new LambdaQueryWrapper<AgentTemplate>()
                        .eq(AgentTemplate::getEnabled, 1)
                        .orderByAsc(AgentTemplate::getAgentCode))
                .stream()
                .map(this::toAgentSimpleDto)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAgentTemplate(Long userId, Long agentId) {
        if (userId == null) {
            throw new LZException(ExceptionCode.Default, "用户ID不能为空");
        }
        if (agentId == null) {
            throw new LZException(ExceptionCode.Default, "Agent模板不能为空");
        }
        ensureUserAgentTableReady();

        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            throw new LZException(ExceptionCode.Default, "Agent模板不存在: " + agentId);
        }
        if (template.getEnabled() != null && template.getEnabled() != 1) {
            throw new LZException(ExceptionCode.Default, "Agent模板未启用: " + agentId);
        }

        UserAgent existing = getUserAgent(userId);
        if (existing == null) {
            UserAgent userAgent = new UserAgent();
            userAgent.setUserId(userId);
            userAgent.setAgentId(agentId);
            userAgentMapper.insert(userAgent);
        } else if (!agentId.equals(existing.getAgentId())) {
            existing.setAgentId(agentId);
            userAgentMapper.updateById(existing);
        }

        syncUserConfig(userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAgentProfile(Long userId, String agentName) {
        if (userId == null) {
            throw new LZException(ExceptionCode.Default, "用户ID不能为空");
        }
        UserAgent userAgent = ensureUserAgentRecord(userId);
        String normalizedAgentName = hasText(agentName) ? agentName.trim() : null;
        userAgent.setAgentName(normalizedAgentName);
        userAgentMapper.updateById(userAgent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAvatarUploadResult uploadUserAgentAvatar(Long userId, MultipartFile file) throws TaskException {
        if (userId == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        if (file == null || file.isEmpty()) {
            throw new TaskException("请先选择头像文件", TaskException.Code.UNKNOWN);
        }
        if (file.getSize() > MAX_AVATAR_FILE_SIZE) {
            throw new TaskException("头像文件不能超过 2MB", TaskException.Code.UNKNOWN);
        }

        UserAgent userAgent = ensureUserAgentRecord(userId);
        String originalName = StringUtils.trimToEmpty(file.getOriginalFilename());
        String requestedExtension = normalizeAvatarExtension(originalName);
        if (!SUPPORTED_AVATAR_EXTENSIONS.contains(requestedExtension)) {
            throw new TaskException("头像仅支持 JPG 或 PNG 格式", TaskException.Code.UNKNOWN);
        }

        byte[] content = readFileBytes(file);
        ImageSignatureUtils.SupportedImageType imageType = ImageSignatureUtils.detectSupportedImageType(content);
        if (imageType == null || !matchesAvatarExtension(requestedExtension, imageType)) {
            throw new TaskException("头像文件内容校验失败，请上传 JPG 或 PNG 图片", TaskException.Code.UNKNOWN);
        }

        long version = System.currentTimeMillis();
        String objectName = minioService.buildMediaObjectName(
                "user-agents", userId, "avatar", "assistant-avatar", version, imageType.extension());
        String previousObjectName = StringUtils.trimToNull(userAgent.getAvatarObjectName());
        try {
            minioService.uploadObject(objectName, content, imageType.contentType());
        } catch (Exception ex) {
            log.warn("个人助手头像上传失败：userId={}, fileName={}, error={}", userId, originalName, ex.getMessage(), ex);
            throw new TaskException("头像上传失败，请稍后重试", TaskException.Code.UNKNOWN, asException(ex));
        }

        userAgent.setAvatarObjectName(objectName);
        try {
            int affectedRows = userAgentMapper.updateById(userAgent);
            if (affectedRows <= 0) {
                throw new TaskException("头像保存失败，请稍后重试", TaskException.Code.UNKNOWN);
            }
        } catch (Exception ex) {
            cleanupUploadedAvatar(objectName, userId, "个人助手头像保存失败后清理新文件");
            if (ex instanceof TaskException taskException) {
                throw taskException;
            }
            throw new TaskException("头像保存失败，请稍后重试", TaskException.Code.UNKNOWN, asException(ex));
        }

        cleanupPreviousAvatar(previousObjectName, objectName, userId);
        UserAvatarUploadResult result = new UserAvatarUploadResult();
        result.setAvatarObjectName(objectName);
        result.setAvatarUrl(minioService.buildObjectPreviewUrl(objectName));
        return result;
    }

    @Override
    public UserAgentFile getUserAgentFile(Long userId, String filename) {
        return userAgentFileMapper.selectOne(new LambdaQueryWrapper<UserAgentFile>()
                .eq(UserAgentFile::getUserId, userId)
                .eq(UserAgentFile::getFilename, filename));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAgentFile(Long userId, String filename, String content) {
        UserAgentFile existing = getUserAgentFile(userId, filename);
        if (existing != null) {
            existing.setContent(content);
            userAgentFileMapper.updateById(existing);
        } else {
            UserAgentFile newFile = new UserAgentFile();
            newFile.setUserId(userId);
            newFile.setAgentCode(resolveCurrentAgentCode(userId));
            newFile.setFilename(filename);
            newFile.setContent(content);
            newFile.setEnabled(1);
            userAgentFileMapper.insert(newFile);
        }
    }

    @Override
    public List<Long> getUserSkillIds(Long userId) {
        List<Long> permittedSkillIds = getUserPermittedSkillIds(userId);
        if (permittedSkillIds.isEmpty()) {
            return List.of();
        }
        UserAgent userAgent = getUserAgent(userId);
        if (!isSkillPreferenceConfigured(userAgent)) {
            return permittedSkillIds;
        }
        Set<Long> permittedSet = new LinkedHashSet<>(permittedSkillIds);
        return listUserEnabledSkillIds(userId).stream()
                .filter(permittedSet::contains)
                .toList();
    }

    @Override
    public List<SkillSimpleDto> getUserSkills(Long userId) {
        List<Long> skillIds = getUserSkillIds(userId);
        return loadSkillsByIds(skillIds);
    }

    @Override
    public UserSkillPreferenceDto getUserSkillPreference(Long userId) {
        List<Long> permittedSkillIds = getUserPermittedSkillIds(userId);
        UserAgent userAgent = getUserAgent(userId);
        boolean configured = isSkillPreferenceConfigured(userAgent);
        List<Long> enabledSkillIds = configured ? getUserSkillIds(userId) : permittedSkillIds;

        UserSkillPreferenceDto dto = new UserSkillPreferenceDto();
        dto.setPermittedSkills(loadSkillsByIds(permittedSkillIds));
        dto.setEnabledSkillIds(enabledSkillIds);
        dto.setConfigured(configured);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserSkillPreference(Long userId, List<Long> enabledSkillIds) {
        if (userId == null) {
            throw new LZException(ExceptionCode.Default, "用户ID不能为空");
        }
        List<Long> permittedSkillIds = getUserPermittedSkillIds(userId);
        Set<Long> permittedSet = new LinkedHashSet<>(permittedSkillIds);
        List<Long> normalizedEnabledSkillIds = normalizeSkillIds(enabledSkillIds).stream()
                .filter(permittedSet::contains)
                .toList();

        userAgentSkillBindingMapper.delete(
                new LambdaQueryWrapper<UserAgentSkillBinding>().eq(UserAgentSkillBinding::getUserId, userId));
        for (int i = 0; i < normalizedEnabledSkillIds.size(); i++) {
            UserAgentSkillBinding binding = new UserAgentSkillBinding();
            binding.setUserId(userId);
            binding.setSkillId(normalizedEnabledSkillIds.get(i));
            binding.setSortOrder(i);
            userAgentSkillBindingMapper.insert(binding);
        }

        UserAgent userAgent = ensureUserAgentRecord(userId);
        userAgent.setSkillPreferenceConfigured(1);
        userAgentMapper.updateById(userAgent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUserSkillBinding(Long userId, Long skillId) {
        if (!getUserPermittedSkillIds(userId).contains(skillId)) {
            throw new LZException(ExceptionCode.Default, "技能不在当前角色资源权限范围内");
        }
        Long count = userAgentSkillBindingMapper.selectCount(new LambdaQueryWrapper<UserAgentSkillBinding>()
                .eq(UserAgentSkillBinding::getUserId, userId)
                .eq(UserAgentSkillBinding::getSkillId, skillId));
        if (count != null && count > 0) {
            markSkillPreferenceConfigured(userId);
            return;
        }

        List<UserAgentSkillBinding> existing =
                userAgentSkillBindingMapper.selectList(new LambdaQueryWrapper<UserAgentSkillBinding>()
                        .eq(UserAgentSkillBinding::getUserId, userId)
                        .orderByDesc(UserAgentSkillBinding::getSortOrder)
                        .last("LIMIT 1"));
        int newOrder = existing.isEmpty()
                ? 0
                : (existing.get(0).getSortOrder() == null ? 0 : existing.get(0).getSortOrder()) + 1;

        UserAgentSkillBinding binding = new UserAgentSkillBinding();
        binding.setUserId(userId);
        binding.setSkillId(skillId);
        binding.setSortOrder(newOrder);
        userAgentSkillBindingMapper.insert(binding);
        markSkillPreferenceConfigured(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserSkillBinding(Long userId, Long skillId) {
        userAgentSkillBindingMapper.delete(new LambdaQueryWrapper<UserAgentSkillBinding>()
                .eq(UserAgentSkillBinding::getUserId, userId)
                .eq(UserAgentSkillBinding::getSkillId, skillId));
        markSkillPreferenceConfigured(userId);
    }

    @Override
    public String getUserAgentCode(Long userId) {
        return resolveCurrentAgentCode(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserProfile(Long userId, String profileContent) {
        updateUserAgentFile(userId, FILENAME_PROFILE, profileContent);
    }

    private AgentTemplate resolveTemplate(Long roleId) {
        return resolveTemplateByCode(DEFAULT_AGENT_CODE);
    }

    private List<Long> getUserPermittedSkillIds(Long userId) {
        Long roleId = resolveEffectiveRoleId(userId, null);
        return roleId != null ? roleResourcePermissionService.getRoleSkillIds(roleId) : List.of();
    }

    private boolean isSkillPreferenceConfigured(UserAgent userAgent) {
        return userAgent != null
                && userAgent.getSkillPreferenceConfigured() != null
                && userAgent.getSkillPreferenceConfigured() == 1;
    }

    private List<Long> listUserEnabledSkillIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<UserAgentSkillBinding> bindings =
                userAgentSkillBindingMapper.selectList(new LambdaQueryWrapper<UserAgentSkillBinding>()
                        .eq(UserAgentSkillBinding::getUserId, userId)
                        .orderByAsc(UserAgentSkillBinding::getSortOrder)
                        .orderByAsc(UserAgentSkillBinding::getId));
        return bindings.stream()
                .map(UserAgentSkillBinding::getSkillId)
                .filter(id -> id != null)
                .toList();
    }

    private List<SkillSimpleDto> loadSkillsByIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        var skillMap = skillCatalogMapper.selectByIds(skillIds).stream()
                .collect(Collectors.toMap(SkillCatalog::getId, item -> item));
        return skillIds.stream()
                .map(skillMap::get)
                .filter(skill -> skill != null)
                .map(this::toSkillSimpleDto)
                .toList();
    }

    private List<Long> normalizeSkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long skillId : skillIds) {
            if (skillId != null) {
                normalized.add(skillId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void markSkillPreferenceConfigured(Long userId) {
        UserAgent userAgent = ensureUserAgentRecord(userId);
        if (userAgent.getSkillPreferenceConfigured() != null && userAgent.getSkillPreferenceConfigured() == 1) {
            return;
        }
        userAgent.setSkillPreferenceConfigured(1);
        userAgentMapper.updateById(userAgent);
    }

    private Long resolveEffectiveRoleId(Long userId, Long roleId) {
        if (roleId != null) {
            return roleId;
        }
        if (userId == null) {
            return null;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        return user != null ? user.getRoleId() : null;
    }

    private AgentTemplate resolveEffectiveTemplate(Long userId, Long roleId) {
        AgentTemplate userTemplate = resolveUserAgentTemplate(userId);
        if (userTemplate != null) {
            return userTemplate;
        }
        return resolveTemplate(resolveEffectiveRoleId(userId, roleId));
    }

    private UserAgent getUserAgent(Long userId) {
        if (userId == null) {
            return null;
        }
        if (!isUserAgentTableAvailable()) {
            return null;
        }
        return userAgentMapper.selectOne(new LambdaQueryWrapper<UserAgent>()
                .eq(UserAgent::getUserId, userId)
                .last("LIMIT 1"));
    }

    private UserAgent ensureUserAgentRecord(Long userId) {
        ensureUserAgentTableReady();
        UserAgent existing = getUserAgent(userId);
        if (existing != null) {
            return existing;
        }
        AgentTemplate template = resolveEffectiveTemplate(userId, null);
        if (template == null || template.getId() == null) {
            throw new LZException(ExceptionCode.Default, "未找到可用的 Agent 模板");
        }
        UserAgent userAgent = new UserAgent();
        userAgent.setUserId(userId);
        userAgent.setAgentId(template.getId());
        userAgentMapper.insert(userAgent);
        return userAgent;
    }

    private void ensureUserAgentTableReady() {
        if (isUserAgentTableAvailable()) {
            return;
        }
        throw new LZException(
                ExceptionCode.Default,
                "当前环境缺少 user_agent 表，请先执行数据库升级脚本并重启服务");
    }

    private boolean isUserAgentTableAvailable() {
        Boolean cached = userAgentTableAvailable;
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        try {
            String schemaName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (!hasText(schemaName)) {
                return false;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    Integer.class,
                    schemaName,
                    USER_AGENT_TABLE);
            boolean available = count != null && count > 0;
            userAgentTableAvailable = available;
            return available;
        } catch (Exception ex) {
            log.warn("检查 user_agent 表是否存在失败，将回退旧逻辑: {}", ex.getMessage());
            return false;
        }
    }

    private AgentTemplate resolveUserAgentTemplate(Long userId) {
        UserAgent userAgent = getUserAgent(userId);
        if (userAgent == null || userAgent.getAgentId() == null) {
            return null;
        }
        return agentTemplateMapper.selectById(userAgent.getAgentId());
    }

    private String resolveUserAgentDisplayName(UserAgent userAgent, AgentTemplate template) {
        if (userAgent != null && hasText(userAgent.getAgentName())) {
            return userAgent.getAgentName().trim();
        }
        if (template != null && hasText(template.getAgentName())) {
            return template.getAgentName().trim();
        }
        return "AI 助手";
    }

    private String resolveCurrentAgentCode(Long userId) {
        AgentTemplate userAgentTemplate = resolveUserAgentTemplate(userId);
        if (userAgentTemplate != null && hasText(userAgentTemplate.getAgentCode())) {
            return userAgentTemplate.getAgentCode().trim();
        }
        UserAgentFile file = findAnyUserAgentFile(userId);
        if (file != null && hasText(file.getAgentCode())) {
            return file.getAgentCode().trim();
        }
        AgentTemplate template = resolveTemplate(resolveEffectiveRoleId(userId, null));
        if (template != null && hasText(template.getAgentCode())) {
            return template.getAgentCode().trim();
        }
        return DEFAULT_AGENT_CODE;
    }

    private AgentTemplate resolveTemplateByCode(String agentCode) {
        String normalizedCode = hasText(agentCode) ? agentCode.trim() : DEFAULT_AGENT_CODE;
        return agentTemplateMapper.selectOne(new LambdaQueryWrapper<AgentTemplate>()
                .eq(AgentTemplate::getAgentCode, normalizedCode)
                .last("LIMIT 1"));
    }

    private AgentSimpleDto toAgentSimpleDto(AgentTemplate template) {
        AgentSimpleDto dto = new AgentSimpleDto();
        dto.setId(template.getId());
        dto.setAgentCode(template.getAgentCode());
        dto.setAgentName(template.getAgentName());
        dto.setDescription(template.getDescription());
        dto.setOpeningMessage(template.getOpeningMessage());
        dto.setIcon(template.getIcon());
        return dto;
    }

    private UserAgentFile findAnyUserAgentFile(Long userId) {
        return userAgentFileMapper.selectOne(new LambdaQueryWrapper<UserAgentFile>()
                .eq(UserAgentFile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private List<SkillSimpleDto> loadTemplateSkills(AgentTemplate template) {
        if (template == null || template.getId() == null) {
            return List.of();
        }
        List<AgentTemplateSkillBinding> bindings =
                agentTemplateSkillBindingMapper.selectList(new LambdaQueryWrapper<AgentTemplateSkillBinding>()
                        .eq(AgentTemplateSkillBinding::getTemplateId, template.getId())
                        .orderByAsc(AgentTemplateSkillBinding::getSortOrder)
                        .orderByAsc(AgentTemplateSkillBinding::getId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream()
                .map(AgentTemplateSkillBinding::getSkillId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (skillIds.isEmpty()) {
            return List.of();
        }
        var skillMap = skillCatalogMapper.selectByIds(skillIds).stream()
                .collect(java.util.stream.Collectors.toMap(SkillCatalog::getId, item -> item));
        return bindings.stream()
                .map(binding -> skillMap.get(binding.getSkillId()))
                .filter(skill -> skill != null)
                .map(this::toSkillSimpleDto)
                .toList();
    }

    private void upsertUserAgentFile(
            Long userId, String agentCode, String filename, String content, boolean forceEnable) {
        UserAgentFile existing = getUserAgentFile(userId, filename);
        if (existing == null) {
            UserAgentFile file = new UserAgentFile();
            file.setUserId(userId);
            file.setAgentCode(agentCode);
            file.setFilename(filename);
            file.setContent(content);
            file.setEnabled(forceEnable ? 1 : 0);
            userAgentFileMapper.insert(file);
            return;
        }
        existing.setAgentCode(agentCode);
        existing.setContent(content);
        if (forceEnable) {
            existing.setEnabled(1);
        }
        userAgentFileMapper.updateById(existing);
    }

    static String buildSyncedProfileContent(SysUserModel user, String templateProfile, String existingProfileContent) {
        Map<String, String> sections = parseMarkdownSections(existingProfileContent);
        String prefaceContent = sections.remove("");
        String identityContent = buildIdentitySection(user, sections.remove(SECTION_IDENTITY));
        String responsibilityContent = buildResponsibilitySection(templateProfile);

        StringBuilder sb = new StringBuilder();
        appendSection(sb, SECTION_IDENTITY, identityContent);
        appendSection(sb, SECTION_RESPONSIBILITY, responsibilityContent);

        if (hasText(prefaceContent) && !sections.containsKey(SECTION_SUPPLEMENT)) {
            sections.put(SECTION_SUPPLEMENT, prefaceContent.trim());
        }

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (!hasText(entry.getKey())) {
                continue;
            }
            appendSection(sb, entry.getKey(), entry.getValue());
        }
        return sb.toString().trim();
    }

    private static String buildIdentitySection(SysUserModel user, String existingIdentityContent) {
        String displayName = resolveUserDisplayName(user);
        String remainingIdentityContent = removeUsernameLine(existingIdentityContent);
        StringBuilder sb = new StringBuilder();
        sb.append("- 用户名：").append(displayName);
        if (hasText(remainingIdentityContent)) {
            sb.append("\n").append(remainingIdentityContent.trim());
        } else {
            sb.append("\n- 部门：\n- 其他：");
        }
        return sb.toString().trim();
    }

    private static String buildResponsibilitySection(String templateProfile) {
        if (hasText(templateProfile)) {
            return templateProfile.trim();
        }
        return "- 请补充岗位职责";
    }

    private static String resolveUserDisplayName(SysUserModel user) {
        if (user == null) {
            return "";
        }
        if (hasText(user.getName())) {
            return user.getName().trim();
        }
        if (hasText(user.getCode())) {
            return user.getCode().trim();
        }
        return "";
    }

    private static String removeUsernameLine(String content) {
        if (!hasText(content)) {
            return "";
        }
        return content.replaceAll("(?m)^-\\s*用户名\\s*[：:].*$\\n?", "").trim();
    }

    private static Map<String, String> parseMarkdownSections(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (!hasText(content)) {
            return sections;
        }
        String normalizedContent = content.replace("\r\n", "\n");
        String currentSection = "";
        StringBuilder currentBody = new StringBuilder();
        for (String line : normalizedContent.split("\n", -1)) {
            if (line.startsWith("## ")) {
                sections.put(currentSection, currentBody.toString().trim());
                currentSection = line.substring(3).trim();
                currentBody.setLength(0);
                continue;
            }
            if (currentBody.length() > 0) {
                currentBody.append("\n");
            }
            currentBody.append(line);
        }
        sections.put(currentSection, currentBody.toString().trim());
        return sections;
    }

    private static void appendSection(StringBuilder sb, String title, String content) {
        if (!hasText(title) || !hasText(content)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("## ").append(title).append("\n\n").append(content.trim());
    }

    private SkillSimpleDto toSkillSimpleDto(SkillCatalog skillCatalog) {
        SkillSimpleDto dto = new SkillSimpleDto();
        dto.setId(skillCatalog.getId());
        dto.setRuntimeSkillName(skillCatalog.getRuntimeSkillName());
        dto.setDisplayName(skillCatalog.getDisplayName());
        dto.setDescription(skillCatalog.getDescription());
        dto.setCategory(skillCatalog.getCategory());
        dto.setIcon(skillCatalog.getIcon());
        dto.setIconColor(skillCatalog.getIconColor());
        return dto;
    }

    private String buildAvatarPreviewUrl(String avatarObjectName) {
        String normalizedObjectName = StringUtils.trimToNull(avatarObjectName);
        if (normalizedObjectName == null) {
            return null;
        }
        return minioService.buildObjectPreviewUrl(normalizedObjectName);
    }

    private byte[] readFileBytes(MultipartFile file) throws TaskException {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new TaskException("读取头像文件失败，请重试", TaskException.Code.UNKNOWN, ex);
        }
    }

    private void cleanupPreviousAvatar(String previousObjectName, String currentObjectName, Long userId) {
        if (StringUtils.isBlank(previousObjectName) || StringUtils.equals(previousObjectName, currentObjectName)) {
            return;
        }
        cleanupUploadedAvatar(previousObjectName, userId, "替换个人助手头像后清理旧文件");
    }

    private void cleanupUploadedAvatar(String objectName, Long userId, String action) {
        if (StringUtils.isBlank(objectName)) {
            return;
        }
        try {
            minioService.deleteFile(objectName);
        } catch (Exception ex) {
            log.warn("{}失败：userId={}, objectName={}, error={}", action, userId, objectName, ex.getMessage(), ex);
        }
    }

    private static String normalizeAvatarExtension(String fileName) {
        String normalized = StringUtils.trimToEmpty(fileName).toLowerCase();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot);
    }

    private static boolean matchesAvatarExtension(
            String requestedExtension, ImageSignatureUtils.SupportedImageType imageType) {
        if (imageType == null) {
            return false;
        }
        if (imageType == ImageSignatureUtils.SupportedImageType.JPEG) {
            return ".jpg".equals(requestedExtension) || ".jpeg".equals(requestedExtension);
        }
        return imageType.extension().equals(requestedExtension);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Exception asException(Throwable ex) {
        return ex instanceof Exception exception ? exception : new Exception(ex);
    }
}
