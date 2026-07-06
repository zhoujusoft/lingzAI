package lingzhou.agent.backend.business.system.model;

import java.util.Date;

/**
 * 角色资源权限实体
 */
public class RoleResourcePermission {

    private Long id;
    private Long roleId;
    private String resourceType;
    private Long resourceId;
    private Date createdAt;
    private Long createdBy;

    public RoleResourcePermission() {
    }

    public RoleResourcePermission(Long roleId, String resourceType, Long resourceId) {
        this.roleId = roleId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
