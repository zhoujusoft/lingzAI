package lingzhou.agent.backend.capability.agentruntime.approval;

import java.util.List;
import java.util.Map;

public record RuntimeToolApprovalAnalysis(
        String summary,
        String riskLevel,
        List<RiskItem> riskItems,
        Map<String, Object> preview,
        String recommendedAction) {

    public record RiskItem(String code, String level, String message) {}
}
