package lingzhou.agent.backend.capability.agentruntime.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport.UsageGuardResult;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import org.junit.jupiter.api.Test;

class RuntimeV2UsageGuardSupportTest {

    @Test
    void shouldReturnExceededWhenTotalTokensCrossBudget() {
        RuntimeExecutionProperties properties = new RuntimeExecutionProperties();
        properties.setMaxTotalTokensPerRun(100);
        RuntimeV2State state = buildState();
        state.addUsage(40, 30, 120);

        UsageGuardResult result = RuntimeV2UsageGuardSupport.resolveTokenBudgetExceeded(state, properties);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RuntimeV2UsageGuardSupport.TOKEN_BUDGET_EXCEEDED_STATUS);
        assertThat(result.message()).contains("120").contains("100");
    }

    @Test
    void shouldIgnoreWhenTotalTokensStayWithinBudget() {
        RuntimeExecutionProperties properties = new RuntimeExecutionProperties();
        properties.setMaxTotalTokensPerRun(100);
        RuntimeV2State state = buildState();
        state.addUsage(20, 30, 80);

        UsageGuardResult result = RuntimeV2UsageGuardSupport.resolveTokenBudgetExceeded(state, properties);

        assertThat(result).isNull();
    }

    private RuntimeV2State buildState() {
        return new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        LingzRuntimeScopeType.GENERAL,
                        "session-usage-guard",
                        null,
                        null,
                        "message",
                        "message",
                        "normal",
                        "GENERAL_CHAT_V2",
                        "{}",
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        12L,
                        false,
                        ""),
                7L,
                null,
                List.of(),
                null,
                new ModelRuntimeConfigResolver.ResolvedChatModelConfig(
                        "db",
                        "OPENAI",
                        "openai",
                        "GPT-4.1",
                        12L,
                        "https://api.openai.com",
                        "sk-ignored",
                        "/v1/chat/completions",
                        "gpt-4.1",
                        0.2,
                        8192,
                        null,
                        false));
    }
}
