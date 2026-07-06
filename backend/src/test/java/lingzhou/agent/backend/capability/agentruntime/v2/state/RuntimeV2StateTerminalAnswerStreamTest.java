package lingzhou.agent.backend.capability.agentruntime.v2.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeV2StateTerminalAnswerStreamTest {

    @Test
    void shouldAccumulateTerminalAnswerStreamSeparately() {
        RuntimeV2State state = new RuntimeV2State(null, 1L, null, List.of(), null, null);

        state.appendTerminalAnswerDelta("上海");
        state.appendTerminalAnswerDelta("出差");
        state.appendTerminalAnswerDelta("标准");

        assertThat(state.terminalAnswerStreamed()).isTrue();
        assertThat(state.terminalAnswerStreamText()).isEqualTo("上海出差标准");
    }
}
