package lingzhou.agent.backend.business.datasets.domain.VO;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateQuest {

    private Long templateId;

    private String templateName;

    private String templateType;

    private String templateContent;

    private List<QuestListFromTemplate> questList;
}
