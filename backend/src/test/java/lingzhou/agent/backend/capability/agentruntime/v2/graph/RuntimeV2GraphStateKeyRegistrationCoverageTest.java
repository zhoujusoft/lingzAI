package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import org.junit.jupiter.api.Test;

class RuntimeV2GraphStateKeyRegistrationCoverageTest {

    @Test
    void shouldRegisterAllGraphStateKeysExceptNodeNames() {
        Set<String> expectedStateKeys = declaredStateKeys();
        Set<String> registeredStateKeys = new LinkedHashSet<>(RuntimeV2GraphBuilder.registeredStateKeys());

        assertThat(registeredStateKeys)
                .as("RuntimeV2GraphBuilder registered state keys")
                .containsExactlyInAnyOrderElementsOf(expectedStateKeys);
    }

    private Set<String> declaredStateKeys() {
        Set<String> excludedFields = Set.of(
                "TRIAGE_NODE",
                "REASONING_NODE",
                "ACTION_NODE",
                "OBSERVATION_NODE",
                "CODE_ESCALATION_NODE",
                "FINAL_ANSWER_NODE",
                "LIMIT_EXCEEDED_NODE");

        return java.util.Arrays.stream(RuntimeV2GraphStateKeys.class.getDeclaredFields())
                .filter(this::isPublicStaticFinalString)
                .filter(field -> !excludedFields.contains(field.getName()))
                .map(this::readValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isPublicStaticFinalString(Field field) {
        int modifiers = field.getModifiers();
        return field.getType() == String.class
                && Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers);
    }

    private String readValue(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read RuntimeV2GraphStateKeys field: " + field.getName(), ex);
        }
    }
}
