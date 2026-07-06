package lingzhou.agent.backend.business.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("conversation_run_usage")
public class ConversationRunUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long assistantMessageId;

    private Long userMessageId;

    private Long sessionId;

    private String sessionCode;

    private String sessionType;

    private String scopeType;

    private Long scopeId;

    private Long userId;

    private String agentType;

    private Long agentId;

    private String agentName;

    private String runtimeSkillName;

    private Long modelId;

    private String modelProvider;

    private String modelName;

    private String adapterType;

    private String runStatus;

    private Boolean usageAvailable;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer llmCallCount;

    private Integer toolCallCount;

    private Long durationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}
