package lingzhou.agent.backend.capability.agentruntime.approval;

import java.util.Map;

public interface RuntimeToolApprovalAnalyzer {

    RuntimeToolApprovalAnalysis analyze(String toolName, Map<String, Object> arguments);
}
