package lingzhou.agent.backend.capability.agentruntime.v2.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeV2ReactDecisionProtocolTest {

    private final RuntimeV2ReactDecisionProtocol protocol = new RuntimeV2ReactDecisionProtocol();

    @Test
    void shouldRejectJsonFinalDecision() {
        var validation = protocol.validate("{\"type\":\"final\",\"answer\":\"完成\"}", Set.of("file_read"));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errorMessage()).contains("仅允许 type=tool");
    }

    @Test
    void shouldParseToolDecisionInsideCodeFence() {
        String raw =
                """
                ```json
                {"type":"tool","toolName":"file_read","arguments":{"arg0":"/outputs/a.html"},"userPreambleMessage":"我先看一下结果文件"}
                ```
                """;

        var validation = protocol.validate(raw, Set.of("file_read", "file_write"));

        assertThat(validation.valid()).isTrue();
        assertThat(validation.decision()).isNotNull();
        assertThat(validation.decision().type()).isEqualTo("tool");
        assertThat(validation.decision().toolName()).isEqualTo("file_read");
        assertThat(validation.decision().arguments()).containsEntry("arg0", "/outputs/a.html");
        assertThat(validation.decision().userPreambleMessage()).isEqualTo("我先看一下结果文件");
    }

    @Test
    void shouldAcceptLegacyUserLeadMessageForBackwardCompatibility() {
        var validation = protocol.validate(
                "{\"type\":\"tool\",\"toolName\":\"file_read\",\"arguments\":{},\"userLeadMessage\":\"我先看一下结果文件\"}",
                Set.of("file_read"));

        assertThat(validation.valid()).isTrue();
        assertThat(validation.decision()).isNotNull();
        assertThat(validation.decision().userPreambleMessage()).isEqualTo("我先看一下结果文件");
    }

    @Test
    void shouldTreatMissingTypeAsToolWhenToolNameIsPresent() {
        var validation = protocol.validate(
                "{\"toolName\":\"parse_file\",\"arguments\":{\"arg0\":\"名单.xlsx\",\"arg1\":\"structured\"}}",
                Set.of("parse_file"));

        assertThat(validation.valid()).isTrue();
        assertThat(validation.decision()).isNotNull();
        assertThat(validation.decision().type()).isEqualTo("tool");
        assertThat(validation.decision().toolName()).isEqualTo("parse_file");
        assertThat(validation.decision().arguments())
                .containsEntry("arg0", "名单.xlsx")
                .containsEntry("arg1", "structured");
    }

    @Test
    void shouldRejectUnknownToolName() {
        var validation = protocol.validate(
                "{\"type\":\"tool\",\"toolName\":\"run_shell\",\"arguments\":{}}", Set.of("file_read"));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errorMessage()).contains("toolName 不在当前可用工具列表中");
    }

    @Test
    void shouldRejectTooLongUserLeadMessage() {
        String longLeadMessage = "前置说明".repeat(20);

        var validation = protocol.validate(
                "{\"type\":\"tool\",\"toolName\":\"file_read\",\"arguments\":{},\"userPreambleMessage\":\""
                        + longLeadMessage
                        + "\"}",
                Set.of("file_read"));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errorMessage()).contains("userPreambleMessage 长度不能超过");
    }
}
