package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("conversation_run")
public class ConversationRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String runCode;
    private Long sessionId;
    private Long triggerMessageId;
    private Long finalMessageId;
    private String runType;
    private String status;
    private String phase;
    private String subStage;
    private String currentTask;
    private String currentRuntimeSkillName;
    private String contextJson;
    private String errorCode;
    private String errorMessage;
    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
