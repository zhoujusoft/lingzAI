package lingzhou.agent.backend.business.channel.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("channel_user_binding")
public class ChannelUserBinding {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long channelId;

    private String channelType;

    private Long ownerUserId;

    private String routeType;

    private Long routeTargetId;

    private String runtimeContextJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date runtimeContextUpdatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
