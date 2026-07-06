package lingzhou.agent.backend.skillstudio.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillStudioProjectIdentityResolverTest {

    @Test
    void shouldAppendNumericSuffixWhenNameAlreadyExists() {
        String resolved = SkillStudioProjectIdentityResolver.resolveUniqueName(
                "reimbursement-assistant",
                48,
                Set.of("reimbursement-assistant", "reimbursement-assistant-2")::contains);

        assertThat(resolved).isEqualTo("reimbursement-assistant-3");
    }

    @Test
    void shouldTrimBaseWhenSuffixWouldExceedLimit() {
        String candidate = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv";

        String resolved =
                SkillStudioProjectIdentityResolver.resolveUniqueName(candidate, 20, Set.of(candidate)::contains);

        assertThat(resolved).isEqualTo("abcdefghijklmnopqr-2");
        assertThat(resolved.length()).isEqualTo(20);
    }
}
