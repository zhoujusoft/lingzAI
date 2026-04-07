package lingzhou.agent.backend.business.datasets.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("knowledge_base_publish_binding")
public class KnowledgeBasePublishBinding {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String publishStatus;

    private String publishedToolCodes;

    private Integer publishedVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastCompiledAt;

    private String lastPublishMessage;

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

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public String getPublishedToolCodes() {
        return publishedToolCodes;
    }

    public void setPublishedToolCodes(String publishedToolCodes) {
        this.publishedToolCodes = publishedToolCodes;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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

    public String getLastPublishMessage() {
        return lastPublishMessage;
    }

    public void setLastPublishMessage(String lastPublishMessage) {
        this.lastPublishMessage = lastPublishMessage;
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
