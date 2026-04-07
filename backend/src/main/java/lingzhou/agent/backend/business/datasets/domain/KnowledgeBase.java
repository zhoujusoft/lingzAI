package lingzhou.agent.backend.business.datasets.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lingzhou.agent.backend.framework.web.BaseEntity;

/**
 * 存储知识库基本信息对象 knowledge_base
 */
@TableName("knowledge_base")
public class KnowledgeBase  {

    private static final long serialVersionUID = 1L;

    @TableId(value = "kb_id", type = IdType.AUTO)
    private Long kbId;

    private String kbName;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date updatedAt;

    @TableField(exist = false)
    private Long docCount;

    @TableField(exist = false)
    private Long charCount;

    @TableField(exist = false)
    private Long appCount;

    @TableField(exist = false)
    private String publishStatus;

    @TableField(exist = false)
    private Integer publishedVersion;

    @TableField(exist = false)
    private String lastPublishMessage;

    @TableField(exist = false)
    private Date publishedAt;

    @TableField(exist = false)
    private Date lastCompiledAt;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Long getDocCount() {
        return docCount;
    }

    public void setDocCount(Long docCount) {
        this.docCount = docCount;
    }

    public Long getCharCount() {
        return charCount;
    }

    public void setCharCount(Long charCount) {
        this.charCount = charCount;
    }

    public Long getAppCount() {
        return appCount;
    }

    public void setAppCount(Long appCount) {
        this.appCount = appCount;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public String getLastPublishMessage() {
        return lastPublishMessage;
    }

    public void setLastPublishMessage(String lastPublishMessage) {
        this.lastPublishMessage = lastPublishMessage;
    }

    public Date getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Date getLastCompiledAt() {
        return lastCompiledAt;
    }

    public void setLastCompiledAt(Date lastCompiledAt) {
        this.lastCompiledAt = lastCompiledAt;
    }
}
