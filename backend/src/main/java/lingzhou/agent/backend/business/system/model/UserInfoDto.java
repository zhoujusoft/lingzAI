package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class UserInfoDto {

    private Long id;
    private String name;
    private String code;
    private String mobile;
    private String email;
    private Integer userType;
    private Integer state;
    private Long roleId;
    private String roleName;
    private String roleCode;
    private String avatarUrl;
    private String appVersion;
    private AgentSimpleDto roleAgent;
    private UserTokenQuotaSummaryDto tokenQuota;
    private List<String> menuPermissions;
    private boolean resourcePermissionUnrestricted;
    private List<Long> permittedSkillIds;
    private List<Long> permittedToolIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    public Integer getUserType() {
        return userType;
    }

    public void setUserType(Integer userType) {
        this.userType = userType;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public AgentSimpleDto getRoleAgent() {
        return roleAgent;
    }

    public void setRoleAgent(AgentSimpleDto roleAgent) {
        this.roleAgent = roleAgent;
    }

    public UserTokenQuotaSummaryDto getTokenQuota() {
        return tokenQuota;
    }

    public void setTokenQuota(UserTokenQuotaSummaryDto tokenQuota) {
        this.tokenQuota = tokenQuota;
    }

    public List<String> getMenuPermissions() {
        return menuPermissions;
    }

    public void setMenuPermissions(List<String> menuPermissions) {
        this.menuPermissions = menuPermissions;
    }

    public boolean isResourcePermissionUnrestricted() {
        return resourcePermissionUnrestricted;
    }

    public void setResourcePermissionUnrestricted(boolean resourcePermissionUnrestricted) {
        this.resourcePermissionUnrestricted = resourcePermissionUnrestricted;
    }

    public List<Long> getPermittedSkillIds() {
        return permittedSkillIds;
    }

    public void setPermittedSkillIds(List<Long> permittedSkillIds) {
        this.permittedSkillIds = permittedSkillIds;
    }

    public List<Long> getPermittedToolIds() {
        return permittedToolIds;
    }

    public void setPermittedToolIds(List<Long> permittedToolIds) {
        this.permittedToolIds = permittedToolIds;
    }
}
