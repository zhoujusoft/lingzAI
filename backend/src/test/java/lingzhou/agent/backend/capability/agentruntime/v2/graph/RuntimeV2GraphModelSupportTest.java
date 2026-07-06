package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.v2.prompt.RuntimeV2PromptAssembler;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class RuntimeV2GraphModelSupportTest {

    @Test
    void shouldStreamDirectAnswerAsVisibleDeltas() {
        RuntimeV2GraphModelSupport support = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        RuntimeV2State state = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        LingzRuntimeScopeType.GENERAL,
                        "session-1",
                        null,
                        null,
                        "你好",
                        "你好",
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
                        ""),
                1L,
                null,
                List.of(),
                null,
                null);
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(), List.of(response("你好"), response("你好，我是"), response("你好，我是管理员。"))));
        List<String> deltas = new ArrayList<>();

        String answer = support.streamDirectAnswer(state, chatClient, deltas::add);

        assertThat(answer).isEqualTo("你好，我是管理员。");
        assertThat(deltas).containsExactly("你好", "，我是", "管理员。");
        assertThat(state.llmCallCount()).isEqualTo(1);
    }

    @Test
    void shouldAppendRuntimeToolEventsAsHistoryMessages() {
        RuntimeV2GraphModelSupport support = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        RuntimeV2State state = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        LingzRuntimeScopeType.GENERAL,
                        "session-2",
                        null,
                        null,
                        "查询武汉报销标准",
                        "查询武汉报销标准",
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
                        ""),
                1L,
                null,
                List.of(),
                null,
                null);
        state.promptToolEvents()
                .add(event("tool", "call_kb_1", "knowledge_base.KB00000053.search", "{\"query\":\"武汉 出差 报销标准\"}"));
        state.promptToolEvents()
                .add(event(
                        "result",
                        "call_kb_1",
                        "knowledge_base.KB00000053.search",
                        "{\"kbName\":\"报销制度知识库\",\"hits\":[{\"documentName\":\"travel-reimbursement-policy.md\"}]}"));
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatClient chatClient =
                ChatClient.create(new StubStreamingChatModel(capturedPrompt, List.of(response("已拿到依据，直接回答。"))));

        var decision = support.resolveReactDecision(
                state, chatClient, List.of("knowledge_base.KB00000053.search"), delta -> {});

        assertThat(decision.type()).isEqualTo("final");
        assertThat(capturedPrompt.get()).isNotNull();
        List<Message> instructions = capturedPrompt.get().getInstructions();
        assertThat(instructions.stream()
                        .filter(AssistantMessage.class::isInstance)
                        .map(AssistantMessage.class::cast)
                        .filter(AssistantMessage::hasToolCalls)
                        .flatMap(message -> message.getToolCalls().stream()))
                .extracting(
                        AssistantMessage.ToolCall::id,
                        AssistantMessage.ToolCall::name,
                        AssistantMessage.ToolCall::arguments)
                .contains(tuple("call_kb_1", "knowledge_base.KB00000053.search", "{\"query\":\"武汉 出差 报销标准\"}"));
        assertThat(instructions.stream()
                        .filter(ToolResponseMessage.class::isInstance)
                        .map(ToolResponseMessage.class::cast)
                        .flatMap(message -> message.getResponses().stream()))
                .extracting(ToolResponseMessage.ToolResponse::id, ToolResponseMessage.ToolResponse::name)
                .contains(tuple("call_kb_1", "knowledge_base.KB00000053.search"));
    }

    @Test
    void shouldExposeToolDetailsInFormattedHistoryMessages() throws Exception {
        RuntimeV2GraphModelSupport support = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        List<Message> messages = List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_kb_1",
                                "function",
                                "knowledge_base.KB00000053.search",
                                "{\"query\":\"武汉 出差 报销标准\"}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(
                                List.of(
                                        new ToolResponseMessage.ToolResponse(
                                                "call_kb_1",
                                                "knowledge_base.KB00000053.search",
                                                "{\"kbName\":\"报销制度知识库\",\"hits\":[{\"documentName\":\"travel-reimbursement-policy.md\"}]}")))
                        .build());

        String formatted = invokeFormatHistoryMessages(support, messages);

        assertThat(formatted).contains("\"toolCalls\"");
        assertThat(formatted).contains("\"knowledge_base.KB00000053.search\"");
        assertThat(formatted).contains("\"arguments\":\"{\\\"query\\\":\\\"武汉 出差 报销标准\\\"}\"");
        assertThat(formatted).contains("\"toolResponses\"");
        assertThat(formatted).contains("\"responseData\":\"{\\\"kbName\\\":\\\"报销制度知识库\\\"");
    }

    @Test
    void shouldSeparateSystemHistoryAndUserSectionsInDebugPayload() throws Exception {
        RuntimeV2GraphModelSupport support = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        List<Message> messages = List.of(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_kb_1", "function", "knowledge_base.KB00000053.search", "{\"query\":\"武汉 出差 报销标准\"}")))
                .build());

        String payload = invokeBuildRequestPromptDebugSections(support, "SYSTEM", messages, "USER");

        assertThat(payload).contains("[systemPrompt]\nSYSTEM");
        assertThat(payload).contains("\n[historyMessages]\n[");
        assertThat(payload).contains("\"toolCalls\"");
        assertThat(payload).contains("\n[userPrompt]\nUSER");
    }

    private static Map<String, Object> event(String type, String id, String name, String payload) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("id", id);
        content.put("name", name);
        if ("tool".equals(type)) {
            content.put("arguments", payload);
        } else {
            content.put("response", payload);
        }
        return Map.of("type", type, "content", Map.copyOf(content));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(
                List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }

    private static String invokeFormatHistoryMessages(RuntimeV2GraphModelSupport support, List<Message> messages)
            throws Exception {
        Method method = RuntimeV2GraphModelSupport.class.getDeclaredMethod("formatHistoryMessages", List.class);
        method.setAccessible(true);
        return (String) method.invoke(support, messages);
    }

    private static String invokeBuildRequestPromptDebugSections(
            RuntimeV2GraphModelSupport support, String systemPrompt, List<Message> messages, String userPrompt)
            throws Exception {
        Method method = RuntimeV2GraphModelSupport.class.getDeclaredMethod(
                "buildRequestPromptDebugSections", String.class, List.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(support, systemPrompt, messages, userPrompt);
    }

    private static final class StubStreamingChatModel implements ChatModel {

        private final AtomicReference<Prompt> capturedPrompt;
        private final List<ChatResponse> responses;

        private StubStreamingChatModel(AtomicReference<Prompt> capturedPrompt, List<ChatResponse> responses) {
            this.capturedPrompt = capturedPrompt;
            this.responses = responses;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            capturedPrompt.set(prompt);
            return responses.isEmpty() ? response("") : responses.get(responses.size() - 1);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            capturedPrompt.set(prompt);
            return Flux.fromIterable(responses);
        }
    }
}
