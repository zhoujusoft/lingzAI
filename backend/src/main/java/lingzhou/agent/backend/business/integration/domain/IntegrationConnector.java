package lingzhou.agent.backend.business.integration.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("integration_connector")
public class IntegrationConnector {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String alias;

    private String baseUrl;

    private String authType;

    private String authConfigJson;

    private String connectParamsJson;

    private Long ownerUserId;

    private Integer permissionScope;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
