package lingzhou.agent.backend.capability.agentruntime.prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RuntimePromptPack(List<RuntimePromptBlock> systemPromptBlocks, String systemPrompt) {

    public RuntimePromptPack {
        systemPromptBlocks = systemPromptBlocks == null
                ? List.of()
                : systemPromptBlocks.stream()
                        .filter(Objects::nonNull)
                        .filter(RuntimePromptBlock::hasContent)
                        .sorted(Comparator.comparingInt(RuntimePromptBlock::order)
                                .thenComparing(block -> block.sourceType().name())
                                .thenComparing(RuntimePromptBlock::source))
                        .toList();
        systemPrompt = joinSystemPrompt(systemPromptBlocks);
    }

    public static RuntimePromptPack of(List<RuntimePromptBlock> systemPromptBlocks) {
        return new RuntimePromptPack(systemPromptBlocks, null);
    }

    public static RuntimePromptPack empty() {
        return new RuntimePromptPack(List.of(), null);
    }

    private static String joinSystemPrompt(List<RuntimePromptBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (RuntimePromptBlock block : blocks) {
            if (block != null && block.hasContent()) {
                values.add(block.content());
            }
        }
        return String.join("\n\n", values);
    }
}
