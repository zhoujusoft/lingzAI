package lingzhou.agent.backend.business.channel.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("channel_config")
public class ChannelConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String channelType;

    private String routeType;

    private Long routeTargetId;

    private Long ownerUserId;

    private String botPrefix;

    private String configJson;

    private Boolean enabled;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
