package lingzhou.agent.backend.capability.agentruntime.approval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("runtime_approval")
public class RuntimeApproval {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String approvalCode;
    private Long runId;
    private String runCode;
    private Long sessionId;
    private Long assistantMessageId;
    private String toolCallId;
    private String toolName;
    private String toolDisplayName;
    private String toolArgumentsJson;
    private String toolResult;
    private String approvalStatus;
    private String executionStatus;
    private String riskLevel;
    private String triggerReason;
    private String analysisJson;
    private Long requestedBy;
    private Long decidedBy;
    private String decisionComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date decidedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
