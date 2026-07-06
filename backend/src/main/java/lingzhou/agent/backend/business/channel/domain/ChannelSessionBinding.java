package lingzhou.agent.backend.business.channel.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("channel_session_binding")
public class ChannelSessionBinding {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long channelId;

    private String channelType;

    private String externalSessionKey;

    private String externalSenderId;

    private String externalSenderName;

    private String replyTarget;

    private Long ownerUserId;

    private String chatSessionCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastActiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
