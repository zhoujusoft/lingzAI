package lingzhou.agent.backend.business.system.model;

public class UserListItemDto {

    private Long id;
    private String name;
    private String code;
    private String mobile;
    private String email;
    private Integer userType;
    private Integer state;
    private Long roleId;
    private String roleName;
    private String avatarUrl;
    private UserTokenQuotaSummaryDto tokenQuota;

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

    public Integer getUserType() {
        return userType;
    }

    public void setUserType(Integer userType) {
        this.userType = userType;
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public UserTokenQuotaSummaryDto getTokenQuota() {
        return tokenQuota;
    }

    public void setTokenQuota(UserTokenQuotaSummaryDto tokenQuota) {
        this.tokenQuota = tokenQuota;
    }
}
