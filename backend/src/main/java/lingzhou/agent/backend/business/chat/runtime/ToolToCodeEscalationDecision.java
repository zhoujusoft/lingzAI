package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import org.springframework.util.StringUtils;

/**
 * TOOL/CODE 运行许可判定。
 *
 * <p>`recommendedPath` 与 `codeEscalationCandidate` 仅保留给旧链路做兼容映射，
 * 新逻辑应只消费 `allowCodeExecution`。它们不再代表独立业务判断。
 */
public record ToolToCodeEscalationDecision(
        String recommendedPath,
        boolean codeEscalationCandidate,
        boolean allowCodeExecution,
        String reason,
        List<String> signals,
        List<String> blockers) {

    private static final String PATH_TOOL_ONLY = "TOOL_ONLY";
    private static final String PATH_TOOL_THEN_CODE = "TOOL_THEN_CODE";

    public ToolToCodeEscalationDecision {
        if (!StringUtils.hasText(recommendedPath)) {
            recommendedPath = allowCodeExecution ? PATH_TOOL_THEN_CODE : PATH_TOOL_ONLY;
        }
        codeEscalationCandidate = allowCodeExecution;
    }
}
