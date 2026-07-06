package lingzhou.agent.backend.business.system.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("user_token_account")
public class UserTokenAccount {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("granted_tokens")
    private Long grantedTokens;

    @TableField("consumed_tokens")
    private Long consumedTokens;

    @TableField("remaining_tokens")
    private Long remainingTokens;

    @TableField("is_unlimited")
    private Integer unlimited;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private Date updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGrantedTokens() {
        return grantedTokens;
    }

    public void setGrantedTokens(Long grantedTokens) {
        this.grantedTokens = grantedTokens;
    }

    public Long getConsumedTokens() {
        return consumedTokens;
    }

    public void setConsumedTokens(Long consumedTokens) {
        this.consumedTokens = consumedTokens;
    }

    public Long getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(Long remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public Integer getUnlimited() {
        return unlimited;
    }

    public void setUnlimited(Integer unlimited) {
        this.unlimited = unlimited;
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
