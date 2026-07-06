package lingzhou.agent.backend.capability.agentruntime.v2;

import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;

public final class RuntimeV2UsageGuardSupport {

    public static final String TOKEN_BUDGET_EXCEEDED_STATUS = "graph-token-budget-exceeded";
    public static final String TOKEN_BUDGET_EXCEEDED_MESSAGE = "已达到本次运行最大 Token 消耗限制，请收敛问题后重试。";
    public static final String OBSERVATION_LOOP_DETECTED_STATUS = "graph-observation-loop-detected";
    public static final String OBSERVATION_LOOP_DETECTED_MESSAGE = "检测到重复推理结果，已停止本次运行，请收敛问题后重试。";

    private RuntimeV2UsageGuardSupport() {}

    public static UsageGuardResult resolveTokenBudgetExceeded(
            RuntimeV2State runtimeState, RuntimeExecutionProperties runtimeExecutionProperties) {
        if (runtimeState == null || runtimeExecutionProperties == null) {
            return null;
        }
        int maxTotalTokensPerRun = Math.max(0, runtimeExecutionProperties.getMaxTotalTokensPerRun());
        Integer totalTokens = runtimeState.totalTokens();
        if (maxTotalTokensPerRun <= 0 || totalTokens == null || totalTokens <= maxTotalTokensPerRun) {
            return null;
        }
        return new UsageGuardResult(
                TOKEN_BUDGET_EXCEEDED_STATUS,
                "本次运行累计消耗 Tokens 已达到 " + totalTokens + "，超过上限 " + maxTotalTokensPerRun + "，请收敛问题后重试。");
    }

    public record UsageGuardResult(String status, String message) {}
}
