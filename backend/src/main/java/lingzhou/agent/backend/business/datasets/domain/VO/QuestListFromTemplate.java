package lingzhou.agent.backend.business.datasets.domain.VO;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class QuestListFromTemplate {

    private String name;

    private String type;

    private String code;

    private List<Map<String, String>> options;
}
