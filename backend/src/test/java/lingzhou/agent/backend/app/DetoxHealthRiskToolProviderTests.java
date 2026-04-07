package lingzhou.agent.backend.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DetoxHealthRiskToolProviderTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void generateUserDataShouldBeDeterministicForSameIdCard() {
        Map<String, Object> first = DetoxHealthRiskToolProvider.generateUserData("110101199001011234");
        Map<String, Object> second = DetoxHealthRiskToolProvider.generateUserData("110101199001011234");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void assessToolShouldReturnScoreAndRiskLevel() throws Exception {
        DetoxHealthRiskToolProvider provider = new DetoxHealthRiskToolProvider();
        String userDataJson = provider.generateDetoxHealthRiskTestData("110101199001011234", 1);

        String assessmentJson = provider.assessDetoxHealthRisk(userDataJson);
        Map<String, Object> assessment =
                JSON.readValue(assessmentJson, new TypeReference<Map<String, Object>>() {});

        assertThat(assessment).containsKeys("risk_level", "total_score", "category_scores", "report");
        assertThat(assessment.get("risk_level")).isIn("高风险", "中风险", "低风险");
        assertThat(((Number) assessment.get("total_score")).intValue()).isBetween(0, 155);
    }
}
