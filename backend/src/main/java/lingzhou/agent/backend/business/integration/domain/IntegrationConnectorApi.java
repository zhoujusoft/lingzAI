package lingzhou.agent.backend.business.integration.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("integration_connector_api")
public class IntegrationConnectorApi {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long connectorId;

    /**
     * 绑定的鉴权配置 ID，对外语义与参考系统的 connectId 一致。
     */
    private String connectId;

    /**
     * 绑定的鉴权配置名称，对外语义与参考系统的 connectName 一致。
     */
    private String connectName;

    private String apiCode;

    private String apiName;

    private String description;

    private String method;

    private String pathTemplate;

    private String headersJson;

    private String queryParamsJson;

    private String bodyTemplateJson;

    private String contentType;

    private String inputSchemaJson;

    private String outputMappingJson;

    private String identityBindingPolicyJson;

    private String toolName;

    private Integer enabled;

    private String publishStatus;

    private Integer publishedVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
