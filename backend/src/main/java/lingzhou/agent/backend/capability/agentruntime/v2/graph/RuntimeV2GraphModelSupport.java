package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.v2.prompt.RuntimeV2PromptAssembler;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol.ReactDecision;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol.ReactDecisionValidation;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class RuntimeV2GraphModelSupport {

    private static final int MAX_DECISION_REPAIR_ATTEMPTS = 1;
    private static final String STAGE_GRAPH_REACT_DECISION = "graph-react-decision";
    private static final String STAGE_GRAPH_REACT_DECISION_REPAIR = "graph-react-decision-repair";
    private static final String STAGE_GRAPH_DIRECT_ANSWER = "graph-direct-answer";
    private static final String STAGE_GRAPH_REACT_FINAL_ANSWER = "graph-react-final-answer";
    private static final int MAX_RUNTIME_TOOL_MESSAGE_PAIRS = 4;
    private static final int MAX_TOOL_RESULT_MESSAGE_CHARS = 4000;
    private static final int MAX_LOG_TEXT_CHARS = 200;

    private final ContextEngineeringService contextEngineeringService;
    private final RuntimeV2PromptAssembler promptAssembler;
    private final RuntimeV2ReactDecisionProtocol reactDecisionProtocol;

    public RuntimeV2GraphModelSupport(
            ContextEngineeringService contextEngineeringService,
            RuntimeV2PromptAssembler promptAssembler,
            RuntimeV2ReactDecisionProtocol reactDecisionProtocol) {
        this.contextEngineeringService = contextEngineeringService;
        this.promptAssembler = promptAssembler;
        this.reactDecisionProtocol = reactDecisionProtocol;
    }

    public ReactDecision resolveReactDecision(
            RuntimeV2State runtimeState, ChatClient chatClient, Collection<String> availableToolNames) {
        return resolveReactDecision(runtimeState, chatClient, availableToolNames, null);
    }

    public ReactDecision resolveReactDecision(
            RuntimeV2State runtimeState,
            ChatClient chatClient,
            Collection<String> availableToolNames,
            Consumer<String> visibleDeltaConsumer) {
        if (runtimeState == null || chatClient == null) {
            throw new IllegalStateException("缺少 graph reasoning 所需的运行时上下文");
        }
        throwIfCancellationRequested(runtimeState);
        List<Message> historyMessages = runtimeState.conversation() == null
                ? List.of()
                : contextEngineeringService.buildHistoryMessages(runtimeState.conversation());
        String systemPrompt = promptAssembler.buildReactSystemPrompt(runtimeState);
        DecisionStreamResult streamResult = invokeDecisionModelStream(
                runtimeState,
                chatClient,
                historyMessages,
                STAGE_GRAPH_REACT_DECISION,
                systemPrompt,
                promptAssembler.buildReactUserPrompt(runtimeState),
                visibleDeltaConsumer);
        String rawOutput = streamResult.rawOutput();
        logModelOutput(runtimeState, STAGE_GRAPH_REACT_DECISION, rawOutput);
        ReactDecisionValidation validation = null;
        try {
            ReactDecision nativeToolDecision = resolveNativeToolDecision(streamResult, availableToolNames);
            if (nativeToolDecision != null) {
                return nativeToolDecision;
            }
        } catch (IllegalStateException ex) {
            validation = ReactDecisionValidation.invalid(ex.getMessage());
        }
        ReactDecision nativeFinalDecision = resolveNativeFinalDecision(streamResult, rawOutput);
        if (nativeFinalDecision != null) {
            return nativeFinalDecision;
        }
        if (validation == null) {
            validation = reactDecisionProtocol.validate(rawOutput, availableToolNames);
        }
        if (validation.valid() && "tool".equalsIgnoreCase(validation.decision().type())) {
            return validation.decision();
        }
        if (validation.valid()) {
            validation = ReactDecisionValidation.invalid("当前 graph final answer 不允许输出 JSON，请直接输出最终回答正文");
        }

        String lastRawOutput = rawOutput;
        ReactDecisionValidation lastValidation = validation;
        for (int attempt = 0; attempt < MAX_DECISION_REPAIR_ATTEMPTS; attempt += 1) {
            throwIfCancellationRequested(runtimeState);
            runtimeState.incrementDecisionRepairCount();
            String repairPrompt = promptAssembler.buildReactRepairUserPrompt(
                    runtimeState, lastRawOutput, lastValidation.errorMessage());
            lastRawOutput = invokeModel(
                    runtimeState,
                    chatClient,
                    historyMessages,
                    STAGE_GRAPH_REACT_DECISION_REPAIR,
                    systemPrompt,
                    repairPrompt);
            logModelOutput(runtimeState, STAGE_GRAPH_REACT_DECISION_REPAIR, lastRawOutput);
            DecisionStreamResult repairedFinalResult = new DecisionStreamResult(lastRawOutput, "", List.of());
            ReactDecision repairedNativeFinalDecision = resolveNativeFinalDecision(repairedFinalResult, lastRawOutput);
            if (repairedNativeFinalDecision != null) {
                return repairedNativeFinalDecision;
            }
            lastValidation = reactDecisionProtocol.validate(lastRawOutput, availableToolNames);
            if (lastValidation.valid()
                    && "tool".equalsIgnoreCase(lastValidation.decision().type())) {
                return lastValidation.decision();
            }
            if (lastValidation.valid()) {
                lastValidation = ReactDecisionValidation.invalid("当前 graph final answer 不允许输出 JSON，请直接输出最终回答正文");
            }
        }
        throw new IllegalStateException(lastValidation.errorMessage());
    }

    public String streamDirectAnswer(
            RuntimeV2State runtimeState, ChatClient chatClient, Consumer<String> visibleDeltaConsumer) {
        if (runtimeState == null || chatClient == null) {
            throw new IllegalStateException("缺少 graph direct 所需的运行时上下文");
        }
        throwIfCancellationRequested(runtimeState);
        List<Message> historyMessages = runtimeState.conversation() == null
                ? List.of()
                : contextEngineeringService.buildHistoryMessages(runtimeState.conversation());
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        AtomicReference<String> fullAnswer = new AtomicReference<>("");
        invokeModelStream(
                        runtimeState,
                        chatClient,
                        historyMessages,
                        STAGE_GRAPH_DIRECT_ANSWER,
                        promptAssembler.buildDirectSystemPrompt(runtimeState),
                        runtimeState.prepared() == null
                                ? ""
                                : runtimeState.prepared().userMessage())
                .map(response -> {
                    appendUsage(runtimeState, response);
                    return extractDelta(response, previousResponseText);
                })
                .filter(StringUtils::hasText)
                .doOnNext(delta -> {
                    throwIfCancellationRequested(runtimeState);
                    fullAnswer.updateAndGet(existing -> existing + delta);
                    if (visibleDeltaConsumer != null) {
                        visibleDeltaConsumer.accept(delta);
                    }
                })
                .blockLast();
        String output = fullAnswer.get().trim();
        logModelOutput(runtimeState, STAGE_GRAPH_DIRECT_ANSWER, output);
        return output;
    }

    public Flux<String> streamReactFinalAnswer(RuntimeV2State runtimeState, ChatClient chatClient, String draftAnswer) {
        if (runtimeState == null || chatClient == null) {
            return Flux.error(new IllegalStateException("缺少 graph final-answer 所需的运行时上下文"));
        }
        throwIfCancellationRequested(runtimeState);
        List<Message> historyMessages = runtimeState.conversation() == null
                ? List.of()
                : contextEngineeringService.buildHistoryMessages(runtimeState.conversation());
        AtomicReference<String> fullAnswer = new AtomicReference<>("");
        return invokeModelStream(
                        runtimeState,
                        chatClient,
                        historyMessages,
                        STAGE_GRAPH_REACT_FINAL_ANSWER,
                        promptAssembler.buildReactFinalAnswerSystemPrompt(runtimeState),
                        promptAssembler.buildReactFinalAnswerUserPrompt(runtimeState, draftAnswer))
                .transform(responseFlux -> toDeltaFlux(runtimeState, responseFlux))
                .doOnNext(delta -> throwIfCancellationRequested(runtimeState))
                .doOnNext(delta -> fullAnswer.updateAndGet(existing -> existing + delta))
                .doOnComplete(() -> logModelOutput(runtimeState, STAGE_GRAPH_REACT_FINAL_ANSWER, fullAnswer.get()));
    }

    private String invokeModel(
            RuntimeV2State runtimeState,
            ChatClient chatClient,
            List<Message> historyMessages,
            String stage,
            String systemPrompt,
            String userPrompt) {
        ChatClient.ChatClientRequestSpec spec =
                prepareRequestSpec(chatClient, historyMessages, stage, systemPrompt, userPrompt, runtimeState, false);
        ChatResponse response = spec.call().chatResponse();
        throwIfCancellationRequested(runtimeState);
        runtimeState.incrementLlmCallCount();
        appendUsage(runtimeState, response);
        return resolveResponseText(response);
    }

    private Flux<ChatResponse> invokeModelStream(
            RuntimeV2State runtimeState,
            ChatClient chatClient,
            List<Message> historyMessages,
            String stage,
            String systemPrompt,
            String userPrompt) {
        ChatClient.ChatClientRequestSpec spec =
                prepareRequestSpec(chatClient, historyMessages, stage, systemPrompt, userPrompt, runtimeState, true);
        runtimeState.incrementLlmCallCount();
        return spec.stream().chatResponse().doOnNext(ignored -> throwIfCancellationRequested(runtimeState));
    }

    private DecisionStreamResult invokeDecisionModelStream(
            RuntimeV2State runtimeState,
            ChatClient chatClient,
            List<Message> historyMessages,
            String stage,
            String systemPrompt,
            String userPrompt,
            Consumer<String> visibleDeltaConsumer) {
        DecisionStreamAccumulator accumulator =
                new DecisionStreamAccumulator(reactDecisionProtocol.decisionJsonMarker(), visibleDeltaConsumer);
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        invokeModelStream(runtimeState, chatClient, historyMessages, stage, systemPrompt, userPrompt)
                .doOnNext(response -> {
                    throwIfCancellationRequested(runtimeState);
                    appendUsage(runtimeState, response);
                    accumulator.appendResponse(response, extractDelta(response, previousResponseText));
                })
                .blockLast();
        accumulator.finish();
        return accumulator.buildResult();
    }

    private void throwIfCancellationRequested(RuntimeV2State runtimeState) {
        if (runtimeState != null && runtimeState.cancellationRequested()) {
            String reason = runtimeState.cancellationReason();
            throw new CancellationException(StringUtils.hasText(reason) ? reason : "已终止本次执行。");
        }
    }

    private Flux<String> toDeltaFlux(RuntimeV2State runtimeState, Flux<ChatResponse> responseFlux) {
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        return responseFlux
                .map(response -> {
                    appendUsage(runtimeState, response);
                    return extractDelta(response, previousResponseText);
                })
                .filter(StringUtils::hasText);
    }

    private void appendUsage(RuntimeV2State runtimeState, ChatResponse response) {
        if (runtimeState == null
                || response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        runtimeState.addUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private String resolveResponseText(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text.trim();
    }

    private String extractDelta(ChatResponse response, AtomicReference<String> previousResponseText) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return "";
        }
        AssistantMessage output = response.getResult().getOutput();
        String current = output == null || output.getText() == null ? "" : output.getText();
        String previous = previousResponseText.get();
        String delta = current.startsWith(previous) ? current.substring(previous.length()) : current;
        previousResponseText.set(current);
        return delta == null ? "" : delta;
    }

    private void logModelOutput(RuntimeV2State runtimeState, String stage, String output) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "Runtime V2 GRAPH 模型输出原文：sessionId={}, iteration={}, llmCallIndex={}, stage={}, output={}",
                safeSessionId(runtimeState),
                runtimeState == null ? 0 : runtimeState.iterationCount(),
                runtimeState == null ? 0 : runtimeState.llmCallCount(),
                StringUtils.hasText(stage) ? stage.trim() : "",
                output == null ? "" : output);
    }

    private String safeSessionId(RuntimeV2State runtimeState) {
        if (runtimeState == null || runtimeState.conversation() == null) {
            return "";
        }
        return StringUtils.trimWhitespace(runtimeState.conversation().sessionCode());
    }

    private int nextLlmCallIndex(RuntimeV2State runtimeState) {
        return runtimeState == null ? 1 : runtimeState.llmCallCount() + 1;
    }

    private String formatHistoryMessages(List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (int i = 0; i < historyMessages.size(); i++) {
            Message message = historyMessages.get(i);
            if (message == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("messageType", message.getMessageType());
            item.put("text", truncateText(message.getText(), MAX_LOG_TEXT_CHARS));
            appendToolMessageDetails(item, message);
            values.add(item);
        }
        return JSON.toJSONString(values);
    }

    private void appendToolMessageDetails(Map<String, Object> item, Message message) {
        if (item == null || item.isEmpty() || message == null) {
            return;
        }
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (toolCall == null) {
                    continue;
                }
                Map<String, Object> toolCallItem = new LinkedHashMap<>();
                toolCallItem.put("id", normalizeText(toolCall.id()));
                toolCallItem.put("name", normalizeText(toolCall.name()));
                toolCallItem.put("arguments", truncateText(normalizeText(toolCall.arguments()), MAX_LOG_TEXT_CHARS));
                toolCalls.add(toolCallItem);
            }
            if (!toolCalls.isEmpty()) {
                item.put("toolCalls", toolCalls);
            }
            return;
        }
        if (message instanceof ToolResponseMessage toolResponseMessage
                && !toolResponseMessage.getResponses().isEmpty()) {
            List<Map<String, Object>> responses = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (response == null) {
                    continue;
                }
                Map<String, Object> responseItem = new LinkedHashMap<>();
                responseItem.put("id", normalizeText(response.id()));
                responseItem.put("name", normalizeText(response.name()));
                responseItem.put(
                        "responseData", truncateText(normalizeText(response.responseData()), MAX_LOG_TEXT_CHARS));
                responses.add(responseItem);
            }
            if (!responses.isEmpty()) {
                item.put("toolResponses", responses);
            }
        }
    }

    private String formatToolCallbacks(RuntimeV2State runtimeState) {
        if (runtimeState == null
                || runtimeState.toolCallbacks() == null
                || runtimeState.toolCallbacks().isEmpty()) {
            return "[]";
        }
        List<String> names = new ArrayList<>();
        runtimeState.toolCallbacks().forEach(callback -> {
            if (callback != null
                    && callback.getToolDefinition() != null
                    && StringUtils.hasText(callback.getToolDefinition().name())) {
                names.add(callback.getToolDefinition().name().trim());
            }
        });
        return JSON.toJSONString(names);
    }

    private ReactDecision resolveNativeToolDecision(
            DecisionStreamResult streamResult, Collection<String> availableToolNames) {
        if (streamResult == null || streamResult.toolCalls().isEmpty()) {
            return null;
        }
        if (streamResult.toolCalls().size() > 1) {
            throw new IllegalStateException("当前 graph reasoning 流暂不支持并行多个工具调用");
        }
        NativeToolCall toolCall = streamResult.toolCalls().get(0);
        String toolName = normalizeText(toolCall.name());
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalStateException("流式 tool call 缺少 toolName，无法继续 graph 决策");
        }
        if (!normalizeToolNames(availableToolNames).contains(toolName)) {
            throw new IllegalStateException("toolName 不在当前可用工具列表中：" + toolName);
        }
        return ReactDecision.toolCall(
                toolName, parseToolArguments(toolCall.arguments()), normalizeText(streamResult.visiblePreamble()));
    }

    private ReactDecision resolveNativeFinalDecision(DecisionStreamResult streamResult, String rawOutput) {
        if (streamResult == null || streamResult.toolCalls().size() > 0) {
            return null;
        }
        String normalized = normalizeText(rawOutput);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String extractedDecisionJson = reactDecisionProtocol.extractDecisionJson(normalized);
        if (StringUtils.hasText(extractedDecisionJson)
                && (extractedDecisionJson.startsWith("{")
                        || extractedDecisionJson.startsWith(reactDecisionProtocol.decisionJsonMarker()))) {
            return null;
        }
        if (normalized.contains(reactDecisionProtocol.decisionJsonMarker()) || normalized.startsWith("{")) {
            return null;
        }
        return ReactDecision.finalAnswer(normalized);
    }

    private ChatClient.ChatClientRequestSpec prepareRequestSpec(
            ChatClient chatClient,
            List<Message> historyMessages,
            String stage,
            String systemPrompt,
            String userPrompt,
            RuntimeV2State runtimeState,
            boolean includeToolCallbacks) {
        List<Message> runtimeMessages = buildRuntimeMessages(runtimeState);
        List<Message> requestMessages = buildRequestMessages(historyMessages, runtimeMessages);
        logModelRequest(
                runtimeState, stage, historyMessages, runtimeMessages, systemPrompt, userPrompt, includeToolCallbacks);
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (!requestMessages.isEmpty()) {
            spec = spec.messages(requestMessages);
        }
        if (StringUtils.hasText(systemPrompt)) {
            spec = spec.system(systemPrompt);
        }
        if (StringUtils.hasText(userPrompt)) {
            spec = spec.user(userPrompt);
        }
        if (includeToolCallbacks
                && runtimeState != null
                && runtimeState.toolCallbacks() != null
                && !runtimeState.toolCallbacks().isEmpty()) {
            spec = spec.options(OpenAiChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .build());
            spec = spec.toolCallbacks(runtimeState.toolCallbacks());
        }
        return spec;
    }

    private List<Message> buildRequestMessages(List<Message> historyMessages, List<Message> runtimeMessages) {
        if (runtimeMessages != null && !runtimeMessages.isEmpty()) {
            return List.copyOf(runtimeMessages);
        }
        List<Message> requestMessages = new ArrayList<>();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            requestMessages.addAll(historyMessages);
        }
        if (runtimeMessages != null && !runtimeMessages.isEmpty()) {
            requestMessages.addAll(runtimeMessages);
        }
        return List.copyOf(requestMessages);
    }

    private List<Message> buildRuntimeMessages(RuntimeV2State runtimeState) {
        if (runtimeState != null && !runtimeState.messages().isEmpty()) {
            return List.copyOf(runtimeState.messages());
        }
        return buildRuntimeToolMessagesFromEvents(runtimeState);
    }

    private List<Message> buildRuntimeToolMessagesFromEvents(RuntimeV2State runtimeState) {
        if (runtimeState == null || runtimeState.promptToolEvents().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> recentEvents = selectRecentToolEvents(runtimeState.promptToolEvents());
        if (recentEvents.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> event : recentEvents) {
            String eventType = normalizeText(event.get("type"));
            Map<String, Object> content = asObject(event.get("content"));
            if ("tool".equalsIgnoreCase(eventType)) {
                String toolId = normalizeText(content.get("id"));
                String toolName = normalizeText(content.get("name"));
                String arguments = normalizeText(content.get("arguments"));
                if (!StringUtils.hasText(toolName)) {
                    continue;
                }
                messages.add(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(toolId, "function", toolName, arguments)))
                        .build());
                continue;
            }
            if ("result".equalsIgnoreCase(eventType)) {
                String toolId = normalizeText(content.get("id"));
                String toolName = normalizeText(content.get("name"));
                String response = truncateText(normalizeText(content.get("response")), MAX_TOOL_RESULT_MESSAGE_CHARS);
                if (!StringUtils.hasText(toolName) || !StringUtils.hasText(response)) {
                    continue;
                }
                messages.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(toolId, toolName, response)))
                        .build());
            }
        }
        return List.copyOf(messages);
    }

    private List<Map<String, Object>> selectRecentToolEvents(List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> selectedToolIds = new LinkedHashSet<>();
        for (int index = toolEvents.size() - 1; index >= 0; index -= 1) {
            Map<String, Object> content = asObject(toolEvents.get(index).get("content"));
            String toolId = normalizeText(content.get("id"));
            if (!StringUtils.hasText(toolId)) {
                continue;
            }
            selectedToolIds.add(toolId);
            if (selectedToolIds.size() >= MAX_RUNTIME_TOOL_MESSAGE_PAIRS) {
                break;
            }
        }
        if (selectedToolIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> selectedEvents = new ArrayList<>();
        for (Map<String, Object> event : toolEvents) {
            Map<String, Object> content = asObject(event == null ? null : event.get("content"));
            String toolId = normalizeText(content.get("id"));
            if (selectedToolIds.contains(toolId)) {
                selectedEvents.add(event);
            }
        }
        return List.copyOf(selectedEvents);
    }

    private void logModelRequest(
            RuntimeV2State runtimeState,
            String stage,
            List<Message> historyMessages,
            List<Message> runtimeMessages,
            String systemPrompt,
            String userPrompt,
            boolean includeToolCallbacks) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "Runtime V2 GRAPH 模型请求上下文：sessionId={}, iteration={}, llmCallIndex={}, stage={}, includeToolCallbacks={}, toolCallbacks={}\n{}",
                safeSessionId(runtimeState),
                runtimeState == null ? 0 : runtimeState.iterationCount(),
                nextLlmCallIndex(runtimeState),
                StringUtils.hasText(stage) ? stage.trim() : "",
                includeToolCallbacks,
                formatToolCallbacks(runtimeState),
                buildRequestPromptDebugSections(systemPrompt, historyMessages, runtimeMessages, userPrompt));
    }

    private String buildRequestPromptDebugSections(
            String systemPrompt, List<Message> historyMessages, List<Message> runtimeMessages, String userPrompt) {
        return new StringBuilder()
                .append("[systemPrompt]\n")
                .append(systemPrompt == null ? "" : systemPrompt)
                .append("\n[historyMessages]\n")
                .append(formatHistoryMessages(historyMessages))
                .append("\n[runtimeMessages]\n")
                .append(formatHistoryMessages(runtimeMessages))
                .append("\n[userPrompt]\n")
                .append(userPrompt == null ? "" : userPrompt)
                .toString();
    }

    @SuppressWarnings("unused")
    private String buildRequestPromptDebugSections(
            String systemPrompt, List<Message> historyMessages, String userPrompt) {
        return buildRequestPromptDebugSections(systemPrompt, historyMessages, List.of(), userPrompt);
    }

    private Map<String, Object> parseToolArguments(String rawArguments) {
        if (!StringUtils.hasText(rawArguments)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(rawArguments, new TypeReference<Map<String, Object>>() {});
            if (parsed == null || parsed.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(parsed);
        } catch (Exception ex) {
            throw new IllegalStateException("流式 tool call arguments 不是合法 JSON 对象");
        }
    }

    private List<String> normalizeToolNames(Collection<String> availableToolNames) {
        if (availableToolNames == null || availableToolNames.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String availableToolName : availableToolNames) {
            String normalized = normalizeText(availableToolName);
            if (StringUtils.hasText(normalized)) {
                names.add(normalized);
            }
        }
        return List.copyOf(names);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key), item);
                }
            });
            return normalized;
        }
        return Map.of();
    }

    private String truncateText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || maxLength <= 0 || text.length() <= maxLength) {
            return normalizeText(text);
        }
        return text.substring(0, maxLength) + "\n...[truncated]";
    }

    private record DecisionStreamResult(String rawOutput, String visiblePreamble, List<NativeToolCall> toolCalls) {}

    private record NativeToolCall(String id, String name, String arguments) {}

    private static final class DecisionStreamAccumulator {

        private static final int JSON_START_HOLDBACK = 2;
        private final String marker;
        private final Consumer<String> visibleDeltaConsumer;
        private final StringBuilder raw = new StringBuilder();
        private final Map<String, NativeToolCall> toolCalls = new LinkedHashMap<>();
        private int emittedVisibleChars = 0;
        private boolean decisionBoundaryDetected = false;
        private int visibleBoundary = 0;

        private DecisionStreamAccumulator(String marker, Consumer<String> visibleDeltaConsumer) {
            this.marker = StringUtils.hasText(marker) ? marker : "<DECISION_JSON>";
            this.visibleDeltaConsumer = visibleDeltaConsumer;
        }

        private void appendDelta(String delta) {
            if (!StringUtils.hasText(delta)) {
                return;
            }
            raw.append(delta);
            flushVisibleText();
        }

        private void appendResponse(ChatResponse response, String delta) {
            appendDelta(delta);
            if (response == null
                    || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                return;
            }
            AssistantMessage output = response.getResult().getOutput();
            if (!output.hasToolCalls() || output.getToolCalls() == null) {
                return;
            }
            int index = 0;
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                if (toolCall == null) {
                    index += 1;
                    continue;
                }
                String key = StringUtils.hasText(toolCall.id()) ? toolCall.id() : "tool-call-" + index;
                NativeToolCall previous = toolCalls.get(key);
                String name = StringUtils.hasText(toolCall.name())
                        ? toolCall.name()
                        : previous == null ? "" : previous.name();
                String arguments = StringUtils.hasText(toolCall.arguments())
                        ? toolCall.arguments()
                        : previous == null ? "" : previous.arguments();
                toolCalls.put(key, new NativeToolCall(key, name, arguments));
                index += 1;
            }
        }

        private void finish() {
            if (!decisionBoundaryDetected && !startsWithJsonPayload(raw)) {
                emitVisibleUntil(raw.length());
                return;
            }
            flushVisibleText();
        }

        private DecisionStreamResult buildResult() {
            String visiblePreamble = raw.length() == 0
                    ? ""
                    : raw.substring(0, Math.min(raw.length(), emittedVisibleChars))
                            .trim();
            return new DecisionStreamResult(raw.toString(), visiblePreamble, List.copyOf(toolCalls.values()));
        }

        private void flushVisibleText() {
            DecisionBoundary boundary = locateDecisionBoundary(raw);
            if (boundary.detected()) {
                decisionBoundaryDetected = true;
                visibleBoundary = boundary.visibleBoundary();
                emitVisibleUntil(visibleBoundary);
                return;
            }
            if (decisionBoundaryDetected || visibleDeltaConsumer == null) {
                return;
            }
            if (startsWithJsonPayload(raw)) {
                return;
            }
            int safeVisibleEnd = Math.max(0, raw.length() - Math.max(marker.length() - 1, JSON_START_HOLDBACK));
            emitVisibleUntil(safeVisibleEnd);
        }

        private void emitVisibleUntil(int endExclusive) {
            if (visibleDeltaConsumer == null) {
                emittedVisibleChars = Math.max(emittedVisibleChars, Math.max(0, endExclusive));
                return;
            }
            int boundedEnd = Math.max(0, Math.min(endExclusive, raw.length()));
            if (boundedEnd <= emittedVisibleChars) {
                return;
            }
            String delta = raw.substring(emittedVisibleChars, boundedEnd);
            emittedVisibleChars = boundedEnd;
            if (StringUtils.hasText(delta)) {
                visibleDeltaConsumer.accept(delta);
            }
        }

        private DecisionBoundary locateDecisionBoundary(CharSequence text) {
            String current = text == null ? "" : text.toString();
            int markerIndex = current.indexOf(marker);
            if (markerIndex >= 0) {
                return new DecisionBoundary(true, markerIndex);
            }
            int braceIndex = current.indexOf('{');
            if (braceIndex == 0
                    || (braceIndex > 0 && current.substring(0, braceIndex).isBlank())) {
                return new DecisionBoundary(true, 0);
            }
            int newlineJsonIndex = current.indexOf("\n{");
            if (newlineJsonIndex >= 0) {
                return new DecisionBoundary(true, newlineJsonIndex);
            }
            int windowsNewlineJsonIndex = current.indexOf("\r\n{");
            if (windowsNewlineJsonIndex >= 0) {
                return new DecisionBoundary(true, windowsNewlineJsonIndex);
            }
            return new DecisionBoundary(false, 0);
        }

        private boolean startsWithJsonPayload(CharSequence text) {
            if (text == null || text.length() == 0) {
                return false;
            }
            String current = text.toString().trim();
            return current.startsWith(marker) || current.startsWith("{");
        }
    }

    private record DecisionBoundary(boolean detected, int visibleBoundary) {}
}
