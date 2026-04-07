/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lingzhou.agent.spring.ai.skill.spi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import reactor.util.context.ContextView;

/**
 * Skill-aware ToolCallingManager that automatically merges skill tools with base tools.
 *
 * <p><b>Why We Need This:</b>
 *
 * <p>We implement {@code ToolCallingManager} interface and delegate to the underlying
 * {@code ToolCallingManager} implementation while adding skill tool merging logic.
 *
 * <p><b>How It Works:</b>
 *
 * <p>This class works together with {@code SkillAwareToolCallbackResolver}:
 *
 * <pre>
 * 1. resolveToolDefinitions() - Provides tool definitions to LLM:
 *    - Delegate to underlying ToolCallingManager to resolve base tool definitions
 *    - Get default skill tools from skillKit.getSkillTools()
 *    - Get active skill tools from skillKit.getAllActiveTools()
 *    - Merge with deduplication by tool name (priority: base > default > active)
 *    - Return merged ToolDefinition list
 *
 * 2. executeToolCalls() - Executes tool calls from LLM:
 *    - Delegates to the underlying ToolCallingManager
 *    - The delegate uses SkillAwareToolCallbackResolver
 *    - SkillAwareToolCallbackResolver finds tools in skillKit.getAllActiveTools()
 * </pre>
 *
 * @see ToolCallingManager
 * @see SkillKit
 * @since 1.1.3
 */
public class SkillAwareToolCallingManager implements ToolCallingManager {

    private static final Logger logger = LoggerFactory.getLogger(SkillAwareToolCallingManager.class);

    private final SkillKit skillKit;
    private final ToolCallingManager delegate;

    /**
     * Create a SkillAwareToolCallingManager with custom delegate.
     *
     * @param skillKit The SkillKit containing skill management and tools
     * @param delegate The underlying ToolCallingManager to delegate to
     */
    public SkillAwareToolCallingManager(SkillKit skillKit, ToolCallingManager delegate) {
        this.skillKit = skillKit;
        this.delegate = delegate;
    }

    /**
     * Resolve tool definitions by merging base tools with skill tools.
     *
     * <p>This is the KEY method where we inject skill tools dynamically!
     *
     * @param chatOptions The chat options containing base tools
     * @return Merged list of tool definitions (base + skill tools)
     */
    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        // Delegate to the underlying ToolCallingManager to resolve base definitions
        List<ToolDefinition> baseDefinitions = delegate.resolveToolDefinitions(chatOptions);

        if (skillKit == null) {
            logger.warn("No SkillKit configured, returning base tools only");
            return baseDefinitions != null ? baseDefinitions : List.of();
        }

        logger.info("Active skills: {}", skillKit.getActivatedSkillNames());

        List<ToolCallback> defaultSkillToolCallbacks = skillKit.getSkillLoaderTools();
        List<ToolDefinition> defaultSkillToolDefinitions = new ArrayList<>();
        for (ToolCallback callback : defaultSkillToolCallbacks) {
            if (callback != null && callback.getToolDefinition() != null) {
                defaultSkillToolDefinitions.add(callback.getToolDefinition());
            }
        }

        List<ToolCallback> activeSkillCallbacks = skillKit.getAllActiveTools();

        List<ToolDefinition> skillDefinitions = new ArrayList<>();
        for (ToolCallback callback : activeSkillCallbacks) {
            if (callback != null && callback.getToolDefinition() != null) {
                skillDefinitions.add(callback.getToolDefinition());
            }
        }

        logger.info(
                "Default skill tools: {}",
                defaultSkillToolDefinitions.stream().map(ToolDefinition::name).toList());
        logger.info(
                "Active skill tools: {}",
                skillDefinitions.stream().map(ToolDefinition::name).toList());

        List<ToolDefinition> mergedDefinitions =
                mergeToolDefinitions(baseDefinitions, defaultSkillToolDefinitions, skillDefinitions);

        return mergedDefinitions;
    }

    /**
     * Merge tool definitions with deduplication by tool name.
     *
     * <p><b>Priority order</b>: base > default > skills
     * <ul>
     *   <li>Base tools take highest precedence (from chatOptions)</li>
     *   <li>Default SkillTools added next (for progressive loading)</li>
     *   <li>Skill tools added last (from activated skills)</li>
     * </ul>
     *
     * @param baseDefinitions Base tool definitions from chatOptions
     * @param defaultSkillToolDefinitions Default SkillTools for progressive loading
     * @param skillDefinitions Skill tool definitions from activated skills
     * @return Merged list with no duplicates
     */
    private List<ToolDefinition> mergeToolDefinitions(
            List<ToolDefinition> baseDefinitions,
            List<ToolDefinition> defaultSkillToolDefinitions,
            List<ToolDefinition> skillDefinitions) {

        List<ToolDefinition> merged = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        for (ToolDefinition def : baseDefinitions) {
            if (def != null && def.name() != null) {
                merged.add(def);
                toolNames.add(def.name());
            }
        }

        for (ToolDefinition def : defaultSkillToolDefinitions) {
            if (def != null && def.name() != null) {
                if (!toolNames.contains(def.name())) {
                    merged.add(def);
                    toolNames.add(def.name());
                }
            }
        }

        for (ToolDefinition def : skillDefinitions) {
            if (def != null && def.name() != null) {
                if (!toolNames.contains(def.name())) {
                    merged.add(def);
                    toolNames.add(def.name());
                }
            }
        }

        return merged;
    }

    /**
     * Execute tool calls - delegate to the underlying ToolCallingManager.
     *
     * <p>Tool execution logic is delegated to the underlying manager. The delegate uses {@code
     * SkillAwareToolCallbackResolver} to find tool callbacks from both chatOptions and
     * skillBox.getAllActiveTools().
     *
     * @param prompt The prompt
     * @param chatResponse The chat response containing tool calls
     * @return Tool execution result
     */
    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ChatResponse sanitizedResponse = sanitizeToolCalls(chatResponse);
        if (sanitizedResponse != chatResponse) {
            chatResponse = sanitizedResponse;
        }
        if (chatResponse != null) {
            chatResponse.getResult();
            if (chatResponse.getResult().getOutput() != null) {
                var output = chatResponse.getResult().getOutput();
                if (output.hasToolCalls()) {
                    for (var toolCall : output.getToolCalls()) {
                        logger.info(
                                "Raw tool call (pre-resolve): id={}, type={}, name={}, arguments={}",
                                toolCall.id(),
                                toolCall.type(),
                                toolCall.name(),
                                toolCall.arguments());
                        if (!isBlank(toolCall.name())) {
                            publishToolEvent(
                                    "loadSkillContent".equals(toolCall.name()) ? "skill" : "tool",
                                    "{\"id\":\""
                                            + safeJson(toolCall.id())
                                            + "\",\"name\":\""
                                            + safeJson(toolCall.name())
                                            + "\",\"arguments\":\""
                                            + safeJson(toolCall.arguments())
                                            + "\"}");
                        }
                    }
                } else {
                    logger.debug("No tool calls present in chat response output");
                }
            }
        }
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        publishToolResults(result);
        return result;
    }

    private void publishToolResults(ToolExecutionResult result) {
        if (result == null || result.conversationHistory() == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Message message : result.conversationHistory()) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    if (!isBlank(response.name()) || !isBlank(response.responseData())) {
                        String key = safeJson(response.id())
                                + "|"
                                + safeJson(response.name())
                                + "|"
                                + safeJson(response.responseData());
                        if (!seen.add(key)) {
                            continue;
                        }
                        publishToolEvent(
                                "result",
                                "{\"id\":\""
                                        + safeJson(response.id())
                                        + "\",\"name\":\""
                                        + safeJson(response.name())
                                        + "\",\"response\":\""
                                        + safeJson(response.responseData())
                                        + "\"}");
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void publishToolEvent(String eventType, String payload) {
        ContextView context = ToolCallReactiveContextHolder.getContext();
        if (context == null || !context.hasKey("toolEventPublisher")) {
            return;
        }
        Object publisher = context.get("toolEventPublisher");
        if (publisher instanceof BiConsumer<?, ?>) {
            String wrapped = "{\"type\":\"" + safeJson(eventType) + "\",\"content\":" + payload + "}";
            ((BiConsumer<String, String>) publisher).accept(eventType, wrapped);
        }
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ChatResponse sanitizeToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null) {
            return chatResponse;
        }

        boolean changed = false;
        List<Generation> results = chatResponse.getResults();
        List<Generation> newResults = new ArrayList<>(results.size());

        for (Generation generation : results) {
            AssistantMessage output = generation.getOutput();
            if (output == null || !output.hasToolCalls()) {
                newResults.add(generation);
                continue;
            }

            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            List<AssistantMessage.ToolCall> sanitized = normalizeToolCalls(toolCalls);

            if (sanitized != toolCalls) {
                changed = true;
                AssistantMessage newOutput = AssistantMessage.builder()
                        .content(output.getText())
                        .properties(output.getMetadata())
                        .toolCalls(sanitized)
                        .media(output.getMedia())
                        .build();
                newResults.add(new Generation(newOutput, generation.getMetadata()));
            } else {
                newResults.add(generation);
            }
        }

        if (!changed) {
            return chatResponse;
        }

        return new ChatResponse(newResults, chatResponse.getMetadata());
    }

    private List<AssistantMessage.ToolCall> normalizeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return toolCalls;
        }

        Map<String, ToolCallAggregate> aggregates = new LinkedHashMap<>();
        int index = 0;
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }

            String id = toolCall.id();
            String key = (id == null || id.isBlank()) ? "idx-" + (index++) : id;
            ToolCallAggregate aggregate = aggregates.computeIfAbsent(key, k -> new ToolCallAggregate());
            aggregate.merge(toolCall);
        }

        List<AssistantMessage.ToolCall> merged = new ArrayList<>(aggregates.size());
        for (Map.Entry<String, ToolCallAggregate> entry : aggregates.entrySet()) {
            ToolCallAggregate agg = entry.getValue();
            if (agg.name == null || agg.name.isBlank()) {
                logger.warn(
                        "Dropping tool call with empty name after merge: id={}, type={}, arguments={}",
                        agg.id,
                        agg.type,
                        agg.arguments);
                continue;
            }
            merged.add(new AssistantMessage.ToolCall(agg.id, agg.type, agg.name, agg.arguments));
        }

        boolean sameSize = merged.size() == toolCalls.size();
        if (sameSize) {
            boolean sameContent = true;
            for (int i = 0; i < merged.size(); i++) {
                if (!merged.get(i).equals(toolCalls.get(i))) {
                    sameContent = false;
                    break;
                }
            }
            if (sameContent) {
                return toolCalls;
            }
        }

        return merged;
    }

    private static final class ToolCallAggregate {
        private String id;
        private String type;
        private String name;
        private String arguments;

        private void merge(AssistantMessage.ToolCall toolCall) {
            if (toolCall == null) {
                return;
            }
            if (isBlank(id) && !isBlank(toolCall.id())) {
                id = toolCall.id();
            }
            if (isBlank(type) && !isBlank(toolCall.type())) {
                type = toolCall.type();
            }
            if (isBlank(name) && !isBlank(toolCall.name())) {
                name = toolCall.name();
            }
            if (isBlank(arguments) && !isBlank(toolCall.arguments())) {
                arguments = toolCall.arguments();
            }
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * Builder for SkillAwareToolCallingManager.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SkillKit skillKit;
        private ToolCallingManager delegate;

        public Builder skillKit(SkillKit skillKit) {
            this.skillKit = skillKit;
            return this;
        }

        public Builder delegate(ToolCallingManager delegate) {
            this.delegate = delegate;
            return this;
        }

        public SkillAwareToolCallingManager build() {
            if (skillKit == null) {
                throw new IllegalArgumentException("skillKit cannot be null");
            }
            if (delegate == null) {
                // Create delegate with SkillAwareToolCallbackResolver
                delegate = DefaultToolCallingManager.builder()
                        .toolCallbackResolver(
                                new DelegatingToolCallbackResolver(List.of(SkillAwareToolCallbackResolver.builder()
                                        .skillKit(skillKit)
                                        .build())))
                        .build();
            }
            return new SkillAwareToolCallingManager(skillKit, delegate);
        }
    }
}
