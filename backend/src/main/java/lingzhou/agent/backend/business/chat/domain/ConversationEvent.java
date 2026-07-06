package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("conversation_event")
public class ConversationEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long messageId;

    private Long runId;

    private String eventCode;

    private Long parentEventId;

    private String eventType;

    private String eventSubtype;

    private String phase;

    private String subStage;

    private String toolName;

    private String eventStatus;

    private Integer sequenceNo;

    private String summaryText;

    private String payloadJson;

    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
}
