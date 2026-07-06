package lingzhou.agent.backend.business.skill.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("skill_catalog")
public class SkillCatalog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String runtimeSkillName;

    private String displayName;

    private String description;

    private String category;

    private String source;

    private Long ownerUserId;

    private String version;

    private String author;

    private String icon;

    private String iconColor;

    private String toolBindingStatus;

    private String toolBindingMessage;

    private String toolBindingDetails;

    private Integer visible;

    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuntimeSkillName() {
        return runtimeSkillName;
    }

    public void setRuntimeSkillName(String runtimeSkillName) {
        this.runtimeSkillName = runtimeSkillName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getIconColor() {
        return iconColor;
    }

    public void setIconColor(String iconColor) {
        this.iconColor = iconColor;
    }

    public String getToolBindingStatus() {
        return toolBindingStatus;
    }

    public void setToolBindingStatus(String toolBindingStatus) {
        this.toolBindingStatus = toolBindingStatus;
    }

    public String getToolBindingMessage() {
        return toolBindingMessage;
    }

    public void setToolBindingMessage(String toolBindingMessage) {
        this.toolBindingMessage = toolBindingMessage;
    }

    public String getToolBindingDetails() {
        return toolBindingDetails;
    }

    public void setToolBindingDetails(String toolBindingDetails) {
        this.toolBindingDetails = toolBindingDetails;
    }

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
