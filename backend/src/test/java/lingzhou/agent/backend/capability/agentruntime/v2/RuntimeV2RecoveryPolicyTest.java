package lingzhou.agent.backend.capability.agentruntime.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeV2RecoveryPolicyTest {

    private final RuntimeV2RecoveryPolicy policy = new RuntimeV2RecoveryPolicy();

    @Test
    void shouldRetryBlockedPythonFileWriteOnce() {
        String toolResult =
                """
                {"success":false,"action":"FILE_WRITE","errorCode":"FILE_WRITE_PYTHON_BLOCKED"}
                """;

        assertThat(policy.shouldRetryCodeScriptWrite(toolResult, 0)).isTrue();
        assertThat(policy.shouldRetryCodeScriptWrite(toolResult, 1)).isFalse();
    }

    @Test
    void shouldCountRecoverableRunFailuresFromStructuredObservationField() {
        int count = policy.countRecoverableRunPythonFailures(List.of(
                Map.of("toolName", "run_python", "observation", "failureKind: no-matching-pdf-found"),
                Map.of("toolName", "run_python", "observation", "failureKind: no-matching-pdf-found"),
                Map.of("toolName", "file_write", "observation", "failureKind: python-blocked")));

        assertThat(count).isEqualTo(2);
    }
}
