package lingzhou.agent.backend.business.system.service;

import java.util.List;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.UserSkillPreferenceDto;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.multipart.MultipartFile;

public interface UserAgentConfigService {

    /**
     * 确保用户有 Agent 配置（初始化）
     * @param userId 用户ID
     * @param roleId 角色ID（可选，用于从角色绑定初始化）
     */
    void ensureUserConfig(Long userId, Long roleId);

    /**
     * 按角色绑定同步用户 Agent 模板文件。
     * 已有 PROFILE.md 会保留用户内容，仅切换模板编码；SOUL.md 会按模板全量覆盖。
     */
    void syncUserConfig(Long userId, Long roleId);

    /**
     * 获取用户的档案文件
     * @param userId 用户ID
     * @return 用户档案文件列表
     */
    List<UserAgentFile> getUserAgentFiles(Long userId);

    /**
     * 获取用户当前生效的 Agent 模板详情
     */
    AgentDetailDto getUserAgentTemplate(Long userId);

    /**
     * 获取可供用户选择的 Agent 模板列表
     */
    List<AgentSimpleDto> listAvailableAgents();

    /**
     * 更新用户当前使用的 Agent 模板，并同步相关配置
     * @param userId 用户ID
     * @param agentId Agent模板ID
     */
    void updateUserAgentTemplate(Long userId, Long agentId);

    /**
     * 更新用户个人助手资料
     * @param userId 用户ID
     * @param agentName 助手名称；为空时回退到模板名称
     */
    void updateUserAgentProfile(Long userId, String agentName);

    /**
     * 上传用户个人助手头像
     * @param userId 用户ID
     * @param file 头像文件
     */
    UserAvatarUploadResult uploadUserAgentAvatar(Long userId, MultipartFile file) throws TaskException;

    /**
     * 获取用户档案文件（按文件名）
     * @param userId 用户ID
     * @param filename 文件名
     */
    UserAgentFile getUserAgentFile(Long userId, String filename);

    /**
     * 更新用户档案文件内容
     * @param userId 用户ID
     * @param filename 文件名
     * @param content 文件内容
     */
    void updateUserAgentFile(Long userId, String filename, String content);

    /**
     * 获取用户当前启用的技能ID列表；角色资源权限是上限，个人偏好是子集。
     * 未配置个人偏好时默认启用全部角色授权技能。
     * @param userId 用户ID
     */
    List<Long> getUserSkillIds(Long userId);

    /**
     * 获取用户当前启用的技能目录信息
     * @param userId 用户ID
     */
    List<SkillSimpleDto> getUserSkills(Long userId);

    /**
     * 获取角色授权范围与用户个人启用技能偏好
     * @param userId 用户ID
     */
    UserSkillPreferenceDto getUserSkillPreference(Long userId);

    /**
     * 保存用户个人启用技能偏好；只能保存角色资源权限范围内的技能
     * @param userId 用户ID
     * @param enabledSkillIds 启用技能ID列表
     */
    void updateUserSkillPreference(Long userId, List<Long> enabledSkillIds);

    /**
     * 添加用户启用技能；角色资源权限是允许范围
     * @param userId 用户ID
     * @param skillId 技能ID
     */
    void addUserSkillBinding(Long userId, Long skillId);

    /**
     * 移除用户启用技能
     * @param userId 用户ID
     * @param skillId 技能ID
     */
    void removeUserSkillBinding(Long userId, Long skillId);

    /**
     * 获取用户当前使用的模板编码
     * @param userId 用户ID
     */
    String getUserAgentCode(Long userId);

    /**
     * 更新用户身份描述（会触发 PROFILE.md 重新组装）
     * @param userId 用户ID
     * @param profileContent 用户身份描述内容
     */
    void updateUserProfile(Long userId, String profileContent);
}
