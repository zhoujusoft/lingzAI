package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

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
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

class RuntimeV2GraphStreamingToolCallCapabilityTest {

    @Test
    void streamChatResponseShouldExposeToolCallsAlongsideVisibleText() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                capturedPrompt,
                List.of(
                        response(message("我先看一下")),
                        response(message(
                                "我先看一下文件", new ToolCall("call_file_read_1", "function", "file_read", "{\"path\":\""))),
                        response(message(
                                "我先看一下文件",
                                new ToolCall(
                                        "call_file_read_1",
                                        "function",
                                        "file_read",
                                        "{\"path\":\"/tmp/result.txt\"}"))))));

        List<ChatResponse> responses =
                chatClient.prompt().user("读取结果文件").toolCallbacks(List.of(callback("file_read"))).stream()
                        .chatResponse()
                        .collectList()
                        .block();

        assertThat(capturedPrompt.get())
                .as("stream() 应实际触发底层 ChatModel.stream(prompt)")
                .isNotNull();
        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).hasToolCalls()).isFalse();
        assertThat(responses.get(1).hasToolCalls()).isTrue();
        assertThat(responses.get(1).getResult().getOutput().getToolCalls())
                .extracting(ToolCall::name, ToolCall::arguments)
                .containsExactly(tuple("file_read", "{\"path\":\""));
        assertThat(responses.get(2).getResult().getOutput().getToolCalls())
                .extracting(ToolCall::id, ToolCall::name, ToolCall::arguments)
                .containsExactly(tuple("call_file_read_1", "file_read", "{\"path\":\"/tmp/result.txt\"}"));
        assertThat(responses)
                .extracting(response -> response.getResult().getOutput().getText())
                .containsExactly("我先看一下", "我先看一下文件", "我先看一下文件");
    }

    @Test
    void streamedToolCallArgumentsShouldBeRecoverableAcrossChunks() {
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(
                        response(message("我先读取压缩包内容")),
                        response(message(
                                "我先读取压缩包内容",
                                new ToolCall(
                                        "call_extract_1", "function", "extract_document_text", "{\"filePath\":\""))),
                        response(message(
                                "我先读取压缩包内容",
                                new ToolCall(
                                        "call_extract_1",
                                        "function",
                                        "extract_document_text",
                                        "{\"filePath\":\"/tmp/archive.pdf\",\"mode\":\"ocr\"}"))))));

        List<ChatResponse> responses =
                chatClient.prompt().user("读取 PDF").toolCallbacks(List.of(callback("extract_document_text"))).stream()
                        .chatResponse()
                        .collectList()
                        .block();

        StreamAggregation aggregation = aggregate(responses);

        assertThat(aggregation.visibleText()).isEqualTo("我先读取压缩包内容");
        assertThat(aggregation.toolCalls()).containsOnlyKeys("call_extract_1");
        assertThat(aggregation.toolCalls().get("call_extract_1"))
                .containsEntry("toolName", "extract_document_text")
                .containsEntry("arguments", "{\"filePath\":\"/tmp/archive.pdf\",\"mode\":\"ocr\"}");
    }

    @Test
    void graphModelSupportFinalStreamStillOnlyEmitsTextDeltas() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(
                        response(message("我先")),
                        response(message(
                                "我先看一下",
                                new ToolCall(
                                        "call_file_read_1", "function", "file_read", "{\"path\":\"/tmp/a.txt\"}"))),
                        response(message(
                                "我先看一下文件",
                                new ToolCall(
                                        "call_file_read_1", "function", "file_read", "{\"path\":\"/tmp/a.txt\"}"))))));

        List<String> deltas = graphModelSupport
                .streamReactFinalAnswer(runtimeState("读取文件"), chatClient, "草稿答案")
                .collectList()
                .block();

        assertThat(deltas).containsExactly("我先", "看一下", "文件");
    }

    @Test
    void resolveReactDecisionShouldPreferNativeToolCallsWhenStreamContainsThem() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(
                        response(message("我先看一下文件")),
                        response(message(
                                "我先看一下文件",
                                new ToolCall(
                                        "call_file_read_1", "function", "file_read", "{\"path\":\"/tmp/a.txt\"}"))))));

        var decision = graphModelSupport.resolveReactDecision(
                runtimeState("读取文件"), chatClient, List.of("file_read"), delta -> {});

        assertThat(decision.type()).isEqualTo("tool");
        assertThat(decision.toolName()).isEqualTo("file_read");
        assertThat(decision.arguments()).containsEntry("path", "/tmp/a.txt");
        assertThat(decision.userPreambleMessage()).isEqualTo("我先看一下文件");
    }

    @Test
    void resolveReactDecisionShouldRepairUnknownNativeToolCallName() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(
                        response(message("我先看一下文件")),
                        response(message(
                                "我先看一下文件",
                                new ToolCall(
                                        "call_file_read_1",
                                        "function",
                                        "knowledge_base.KB00000053.search",
                                        "{\"query\":\"胰腺癌\"}")))),
                response(message("我改用当前可用能力回答。"))));

        var decision = graphModelSupport.resolveReactDecision(
                runtimeState("读取文件"), chatClient, List.of("file_read"), delta -> {});

        assertThat(decision.type()).isEqualTo("final");
        assertThat(decision.answer()).isEqualTo("我改用当前可用能力回答。");
    }

    @Test
    void resolveReactDecisionShouldTreatPlainVisibleTextAsFinalAnswer() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(response(message("这是")), response(message("这是最终")), response(message("这是最终回答")))));

        var decision = graphModelSupport.resolveReactDecision(
                runtimeState("直接回答"), chatClient, List.of("file_read"), delta -> {});

        assertThat(decision.type()).isEqualTo("final");
        assertThat(decision.answer()).isEqualTo("这是最终回答");
    }

    @Test
    void resolveReactDecisionShouldRepairJsonFinalIntoPlainTextFinal() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(response(message("{\"type\":\"final\",\"answer\":\"旧格式回答\"}"))),
                response(message("修正后的最终回答"))));

        var decision = graphModelSupport.resolveReactDecision(
                runtimeState("直接回答"), chatClient, List.of("file_read"), delta -> {});

        assertThat(decision.type()).isEqualTo("final");
        assertThat(decision.answer()).isEqualTo("修正后的最终回答");
    }

    @Test
    void resolveReactDecisionShouldLeaveSemanticToolChoiceToModel() {
        RuntimeV2GraphModelSupport graphModelSupport = new RuntimeV2GraphModelSupport(
                new ContextEngineeringService(null, null),
                new RuntimeV2PromptAssembler(),
                new RuntimeV2ReactDecisionProtocol());
        ChatClient chatClient = ChatClient.create(new StubStreamingChatModel(
                new AtomicReference<>(),
                List.of(
                        response(message("我先处理文档翻译")),
                        response(message(
                                "我先处理文档翻译",
                                new ToolCall(
                                        "call_parse_file_1",
                                        "function",
                                        "parse_file",
                                        "{\"arg0\":\"fanyi.docx\",\"arg1\":\"text\"}"))))));

        var decision = graphModelSupport.resolveReactDecision(
                runtimeState("先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多"),
                chatClient,
                List.of("parse_file", "dataset.DS20260420103211J78Q.execute_dataset_sql"),
                delta -> {});

        assertThat(decision.type()).isEqualTo("tool");
        assertThat(decision.toolName()).isEqualTo("parse_file");
        assertThat(decision.arguments()).containsEntry("arg0", "fanyi.docx");
    }

    private static StreamAggregation aggregate(List<ChatResponse> responses) {
        String visibleText = "";
        Map<String, Map<String, String>> toolCalls = new LinkedHashMap<>();
        for (ChatResponse response : responses) {
            if (response == null
                    || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                continue;
            }
            AssistantMessage output = response.getResult().getOutput();
            if (output.getText() != null && output.getText().length() >= visibleText.length()) {
                visibleText = output.getText();
            }
            if (!output.hasToolCalls()) {
                continue;
            }
            for (ToolCall toolCall : output.getToolCalls()) {
                toolCalls.compute(toolCall.id(), (key, existing) -> {
                    Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
                    next.put("toolName", toolCall.name());
                    next.put("arguments", toolCall.arguments());
                    return next;
                });
            }
        }
        return new StreamAggregation(visibleText, toolCalls);
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static AssistantMessage message(String text) {
        return AssistantMessage.builder().content(text).build();
    }

    private static AssistantMessage message(String text, ToolCall toolCall) {
        return AssistantMessage.builder()
                .content(text)
                .toolCalls(List.of(toolCall))
                .build();
    }

    private static ToolCallback callback(String name) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(name + " description")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return "";
            }
        };
    }

    private static RuntimeV2State runtimeState(String userMessage) {
        return new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        LingzRuntimeScopeType.GENERAL,
                        "session-id",
                        null,
                        null,
                        userMessage,
                        userMessage,
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
    }

    private record StreamAggregation(String visibleText, Map<String, Map<String, String>> toolCalls) {}

    private static final class StubStreamingChatModel implements ChatModel {

        private final AtomicReference<Prompt> capturedPrompt;
        private final List<ChatResponse> streamResponses;
        private final ChatResponse callResponse;

        private StubStreamingChatModel(AtomicReference<Prompt> capturedPrompt, List<ChatResponse> streamResponses) {
            this(capturedPrompt, streamResponses, null);
        }

        private StubStreamingChatModel(
                AtomicReference<Prompt> capturedPrompt, List<ChatResponse> streamResponses, ChatResponse callResponse) {
            this.capturedPrompt = capturedPrompt;
            this.streamResponses = streamResponses;
            this.callResponse = callResponse;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.capturedPrompt.set(prompt);
            if (callResponse != null) {
                return callResponse;
            }
            return streamResponses.isEmpty() ? response(message("")) : streamResponses.get(streamResponses.size() - 1);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.capturedPrompt.set(prompt);
            return Flux.fromIterable(streamResponses);
        }
    }
}
