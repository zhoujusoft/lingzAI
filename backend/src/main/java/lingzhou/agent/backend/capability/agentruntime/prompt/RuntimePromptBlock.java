package lingzhou.agent.backend.capability.agentruntime.prompt;

import org.springframework.util.StringUtils;

public record RuntimePromptBlock(RuntimePromptSourceType sourceType, String source, int order, String content) {

    public RuntimePromptBlock {
        sourceType = sourceType == null ? RuntimePromptSourceType.CAPABILITY : sourceType;
        source = StringUtils.hasText(source) ? source.trim() : "unknown";
        content = StringUtils.hasText(content) ? content.trim() : "";
    }

    public static RuntimePromptBlock of(RuntimePromptSourceType sourceType, String source, String content) {
        RuntimePromptSourceType resolvedSourceType =
                sourceType == null ? RuntimePromptSourceType.CAPABILITY : sourceType;
        return new RuntimePromptBlock(resolvedSourceType, source, resolvedSourceType.defaultOrder(), content);
    }

    public boolean hasContent() {
        return StringUtils.hasText(content);
    }
}
