package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("runtime_file_asset")
public class RuntimeFileAsset {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String fileCode;

    private Long userId;

    private Long sessionId;

    private String sessionCode;

    private Long runId;

    private Long originMessageId;

    private Long originEventId;

    private String parentFileCode;

    private String fileRole;

    private String producerType;

    private String status;

    private String displayName;

    private String storageName;

    private String extension;

    private String contentType;

    private Long sizeBytes;

    private String sha256;

    private String logicalRoot;

    private String logicalPath;

    private String virtualPath;

    private String localPath;

    private String localStatus;

    private String bucket;

    private String objectName;

    private String minioStatus;

    private String metadataJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiredAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deletedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
