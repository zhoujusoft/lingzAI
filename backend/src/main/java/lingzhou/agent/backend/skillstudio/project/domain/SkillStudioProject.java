package lingzhou.agent.backend.skillstudio.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

@Data
@TableName("skill_studio_project")
public class SkillStudioProject {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String projectCode;

    private String name;

    private String description;

    private String status;

    private String projectType;

    private String draftSkillName;

    private String runtimeSkillName;

    private String icon;

    private String iconColor;

    private String category;

    private String draftPath;

    private String coverSummary;

    private String initialPrompt;

    private Long lastSessionId;

    private String lastMessagePreview;

    private String projectHintsJson;

    private String projectConstraintsJson;

    private String toolBindingsJson;

    private String toolSettingsDigest;

    private String lastGeneratedToolDigest;

    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date archivedAt;
}
