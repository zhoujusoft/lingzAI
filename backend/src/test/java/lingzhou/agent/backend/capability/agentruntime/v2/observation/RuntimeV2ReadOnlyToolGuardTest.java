package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;

class RuntimeV2ReadOnlyToolGuardTest {

    private final RuntimeV2ReadOnlyToolGuard guard = new RuntimeV2ReadOnlyToolGuard();

    @Test
    void shouldBlockDuplicateParseFileCallWithSameArguments() {
        RuntimeV2State state = newState();
        state.observationTrace()
                .add(Map.of(
                        "toolName", "parse_file",
                        "arguments", "{\"arg0\":\"/uploads/a.xlsx\",\"arg1\":\"structured\"}",
                        "observation", "status: SUCCESS"));

        String observation = guard.buildDuplicateObservation(
                state, "parse_file", Map.of("arg0", "/uploads/a.xlsx", "arg1", "structured"), 4000);

        assertThat(observation).contains("DUPLICATE_READONLY_TOOL_SKIPPED");
        assertThat(observation).contains("parse_file::/uploads/a.xlsx::structured");
        assertThat(observation).contains("duplicateReadOnly: true");
        assertThat(observation).doesNotContain("nextActionHint");
    }

    @Test
    void shouldAllowReadOnlyCallWhenArgumentsDiffer() {
        RuntimeV2State state = newState();
        state.observationTrace()
                .add(Map.of(
                        "toolName", "file_read",
                        "arguments", "{\"arg0\":\"/outputs/a.html\"}",
                        "observation", "fileKind: html"));

        String observation =
                guard.buildDuplicateObservation(state, "file_read", Map.of("arg0", "/outputs/b.html"), 4000);

        assertThat(observation).isEmpty();
    }

    private RuntimeV2State newState() {
        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                LingzRuntimeScopeType.GENERAL,
                "session-code",
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
                null,
                false,
                "");
        return new RuntimeV2State(prepared, 1L, null, List.of(), null, null);
    }
}
