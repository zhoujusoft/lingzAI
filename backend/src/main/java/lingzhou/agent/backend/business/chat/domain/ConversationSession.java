package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("conversation_session")
public class ConversationSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionCode;

    private String sessionType;

    private String scopeType;

    private Long scopeId;

    private Long chatModelId;

    @TableField("title")
    private String name;

    private String status;

    private Long lastMessageId;

    @TableField("last_message_preview")
    private String lastMessage;

    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date archivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getName() {
        return name;
    }

    public Long getChatModelId() {
        return chatModelId;
    }

    public void setChatModelId(Long chatModelId) {
        this.chatModelId = chatModelId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(Long lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
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

    public Date getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Date archivedAt) {
        this.archivedAt = archivedAt;
    }
}
