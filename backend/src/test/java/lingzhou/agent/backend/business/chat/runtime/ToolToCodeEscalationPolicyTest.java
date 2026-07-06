package lingzhou.agent.backend.business.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolToCodeEscalationPolicyTest {

    private final ToolToCodeEscalationPolicy policy = new ToolToCodeEscalationPolicy();

    @Test
    void shouldAllowCodeFallbackForZipInspectionWithoutForcingIt() {
        ToolToCodeEscalationDecision decision = policy.evaluate("帮我看看这个压缩包里都有什么", List.of("file-1"), List.of(), null);

        assertThat(decision.allowCodeExecution()).isTrue();
        assertThat(decision.codeEscalationCandidate()).isEqualTo(decision.allowCodeExecution());
        assertThat(decision.recommendedPath()).isEqualTo("TOOL_THEN_CODE");
        assertThat(decision.reason()).contains("是否升级由模型自行判断");
    }

    @Test
    void shouldTreatLegacyCandidateAsAllowCodeExecutionAlias() {
        ToolToCodeEscalationDecision decision =
                policy.evaluate("帮我提取这个压缩包中的 PDF 发票文件，然后重新打包给我", List.of("file-1"), List.of(), null);

        assertThat(decision.codeEscalationCandidate()).isEqualTo(decision.allowCodeExecution());
        assertThat(decision.signals()).contains("命中显式 ZIP 提取/筛选/重打包意图");
    }
}
