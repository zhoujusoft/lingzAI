package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("conversation_message")
public class ConversationMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String messageCode;

    private Long parentMessageId;

    private String role;

    private String messageKind;

    private String content;

    private String segmentsJson;

    private String contentFormat;

    private String status;

    private String errorCode;

    private String errorMessage;

    private String paramsJson;

    private String attachmentsJson;

    private String artifactSummaryJson;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Boolean usageAvailable;

    private Integer llmCallCount;

    private Integer toolCallCount;

    private Long modelId;

    private String modelProvider;

    private String modelName;

    private String adapterType;

    private String usageSummaryJson;

    private Integer sequenceNo;

    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedAt;
}
