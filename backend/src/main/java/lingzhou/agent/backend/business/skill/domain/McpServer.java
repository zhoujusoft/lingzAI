package lingzhou.agent.backend.business.skill.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("mcp_server")
public class McpServer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String serverKey;

    private String displayName;

    private String description;

    private String transportType;

    private String endpoint;

    private String serverScope;

    private String authType;

    private String authConfigJson;

    private Integer enabled;

    private String lastRefreshStatus;

    private String lastRefreshMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRefreshedAt;

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

    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
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

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getServerScope() {
        return serverScope;
    }

    public void setServerScope(String serverScope) {
        this.serverScope = serverScope;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthConfigJson() {
        return authConfigJson;
    }

    public void setAuthConfigJson(String authConfigJson) {
        this.authConfigJson = authConfigJson;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getLastRefreshStatus() {
        return lastRefreshStatus;
    }

    public void setLastRefreshStatus(String lastRefreshStatus) {
        this.lastRefreshStatus = lastRefreshStatus;
    }

    public String getLastRefreshMessage() {
        return lastRefreshMessage;
    }

    public void setLastRefreshMessage(String lastRefreshMessage) {
        this.lastRefreshMessage = lastRefreshMessage;
    }

    public Date getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Date lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
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
