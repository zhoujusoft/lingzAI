package lingzhou.agent.backend.business.system.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_user")
public class SysUserModel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("code")
    private String code;

    @TableField("password")
    private String password;

    @TableField("user_type")
    private Integer userType;

    @TableField("mobile")
    private String mobile;

    @TableField("email")
    private String email;

    @TableField("state")
    private Integer state;

    @TableField("parent_id")
    private String parentId;

    @TableField(value = "role_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long roleId;

    @TableField("avatar_object_name")
    private String avatarObjectName;

    @TableField("license_exempt")
    private Integer licenseExempt;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getUserType() {
        return userType;
    }

    public void setUserType(Integer userType) {
        this.userType = userType;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getAvatarObjectName() {
        return avatarObjectName;
    }

    public void setAvatarObjectName(String avatarObjectName) {
        this.avatarObjectName = avatarObjectName;
    }

    public Integer getLicenseExempt() {
        return licenseExempt;
    }

    public void setLicenseExempt(Integer licenseExempt) {
        this.licenseExempt = licenseExempt;
    }
}
