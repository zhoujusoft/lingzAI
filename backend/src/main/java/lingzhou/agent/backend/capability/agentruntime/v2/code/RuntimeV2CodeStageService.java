package lingzhou.agent.backend.capability.agentruntime.v2.code;

import com.alibaba.fastjson.JSON;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.v2.prompt.RuntimeV2PromptAssembler;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RuntimeV2CodeStageService {

    private static final long STREAM_PROGRESS_MIN_INTERVAL_MS = 1200L;
    private static final long STREAM_HEARTBEAT_INTERVAL_MS = 3000L;

    private final ContextEngineeringService contextEngineeringService;
    private final RuntimeV2PromptAssembler promptAssembler;
    private final RuntimeV2CodePlanProtocol codePlanProtocol;
    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2ReactDecisionProtocol reactDecisionProtocol;

    public RuntimeV2CodeStageService(
            ContextEngineeringService contextEngineeringService,
            RuntimeV2PromptAssembler promptAssembler,
            RuntimeV2CodePlanProtocol codePlanProtocol,
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2ReactDecisionProtocol reactDecisionProtocol) {
        this.contextEngineeringService = contextEngineeringService;
        this.promptAssembler = promptAssembler;
        this.codePlanProtocol = codePlanProtocol;
        this.codeExecutionSupport = codeExecutionSupport;
        this.reactDecisionProtocol = reactDecisionProtocol;
    }

    public CodeStagePreparation prepare(RuntimeV2State state, ChatClient chatClient) {
        return prepare(state, chatClient, null);
    }

    public CodeStagePreparation prepare(
            RuntimeV2State state, ChatClient chatClient, CodeStageProgressListener progressListener) {
        if (state == null || chatClient == null) {
            return CodeStagePreparation.invalid("CODE 运行时上下文不完整");
        }
        CodeStageProgressListener listener =
                progressListener == null ? CodeStageProgressListener.noop() : progressListener;
        listener.onProgress(CodeStageProgress.planStarted("已完成附件结构分析，正在规划脚本处理步骤。"));
        RuntimeV2CodePlanProtocol.CodeExecutionPlan plan = resolveCodeExecutionPlan(state, chatClient, listener);
        if (plan == null) {
            listener.onProgress(CodeStageProgress.planFailed("脚本处理方案生成失败，准备回退到工具推理。"));
            return CodeStagePreparation.invalid("CODE 计划无效");
        }
        listener.onProgress(CodeStageProgress.planCompleted("脚本处理方案已确定。"));
        listener.onProgress(CodeStageProgress.scriptStarted("处理方案已确定，正在生成最小 Python 脚本。"));
        String scriptContent = resolveCodeScript(state, chatClient, plan, listener);
        if (!StringUtils.hasText(scriptContent)) {
            listener.onProgress(CodeStageProgress.scriptFailed("Python 脚本生成失败，准备回退到工具推理。"));
            return CodeStagePreparation.invalid("CODE 脚本为空");
        }
        listener.onProgress(CodeStageProgress.scriptCompleted("最小 Python 脚本已生成。"));
        return CodeStagePreparation.valid(plan, scriptContent);
    }

    private RuntimeV2CodePlanProtocol.CodeExecutionPlan resolveCodeExecutionPlan(
            RuntimeV2State state, ChatClient chatClient, CodeStageProgressListener listener) {
        List<Message> historyMessages = buildHistoryMessages(state);
        String latestObservation = resolveLatestObservation(state);
        String attachmentSummary = codeExecutionSupport.buildAttachmentSummary(
                state == null || state.prepared() == null
                        ? null
                        : state.prepared().fileListJson());
        String rawOutput = invokeCodeStageCall(
                state,
                chatClient,
                historyMessages,
                promptAssembler.buildCodePlanSystemPrompt(state),
                promptAssembler.buildCodePlanUserPrompt(state, attachmentSummary, latestObservation),
                listener,
                StreamingStage.plan());
        RuntimeV2CodePlanProtocol.CodePlanValidation validation = codePlanProtocol.validate(
                rawOutput,
                codeExecutionSupport.resolveSuggestedInputPaths(state),
                state.prepared().userMessage());
        return validation.valid() ? validation.plan() : null;
    }

    private String resolveCodeScript(
            RuntimeV2State state,
            ChatClient chatClient,
            RuntimeV2CodePlanProtocol.CodeExecutionPlan plan,
            CodeStageProgressListener listener) {
        List<Message> historyMessages = buildHistoryMessages(state);
        String latestObservation = resolveLatestObservation(state);
        String planJson = JSON.toJSONString(plan.toPromptPayload());
        String output = invokeCodeStageCall(
                state,
                chatClient,
                historyMessages,
                promptAssembler.buildCodeScriptSystemPrompt(state),
                promptAssembler.buildCodeScriptUserPrompt(state, planJson, latestObservation),
                listener,
                StreamingStage.script());
        String normalized = reactDecisionProtocol.stripCodeFence(output);
        return StringUtils.hasText(normalized) ? normalized : "";
    }

    private String invokeCodeStageCall(
            RuntimeV2State state,
            ChatClient chatClient,
            List<Message> historyMessages,
            String systemPrompt,
            String userPrompt,
            CodeStageProgressListener listener,
            StreamingStage streamingStage) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            spec = spec.messages(historyMessages);
        }
        if (StringUtils.hasText(systemPrompt)) {
            spec = spec.system(systemPrompt);
        }
        if (StringUtils.hasText(userPrompt)) {
            spec = spec.user(userPrompt);
        }
        state.incrementLlmCallCount();
        log.info(
                "Runtime V2 CODE 阶段开始流式调用：sessionId={}, iteration={}, llmCallIndex={}, stage={}",
                state == null || state.conversation() == null
                        ? ""
                        : StringUtils.trimWhitespace(state.conversation().sessionCode()),
                state == null ? 0 : state.iterationCount(),
                state == null ? 0 : state.llmCallCount(),
                streamingStage.name());
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        StringBuilder output = new StringBuilder();
        StreamingProgressTracker tracker = new StreamingProgressTracker(streamingStage, listener);
        ScheduledExecutorService heartbeatExecutor = startHeartbeat(tracker);
        try {
            for (ChatResponse response : spec.stream().chatResponse().toIterable()) {
                appendUsage(state, response);
                String delta = extractDelta(response, previousResponseText);
                if (!StringUtils.hasText(delta)) {
                    continue;
                }
                output.append(delta);
                tracker.onDelta(delta);
            }
        } finally {
            tracker.complete();
            heartbeatExecutor.shutdownNow();
        }
        log.info(
                "Runtime V2 CODE 阶段流式调用完成：sessionId={}, iteration={}, stage={}, outputLength={}",
                state == null || state.conversation() == null
                        ? ""
                        : StringUtils.trimWhitespace(state.conversation().sessionCode()),
                state == null ? 0 : state.iterationCount(),
                streamingStage.name(),
                output.length());
        logModelOutput(state, streamingStage.name(), output.toString());
        // CODE SCRIPT / PLAN 的空白字符是语义的一部分，不能在流式拼接阶段裁剪。
        return output.toString();
    }

    private List<Message> buildHistoryMessages(RuntimeV2State state) {
        ConversationHistoryService.ConversationContext context = state == null ? null : state.conversation();
        return context == null ? List.of() : contextEngineeringService.buildHistoryMessages(context);
    }

    private String resolveLatestObservation(RuntimeV2State state) {
        if (state == null || state.observationTrace().isEmpty()) {
            return "";
        }
        Map<String, Object> lastObservation =
                state.observationTrace().get(state.observationTrace().size() - 1);
        Object value = lastObservation.get("observation");
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveResponseText(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String extractDelta(ChatResponse response, AtomicReference<String> previousResponseText) {
        String current = resolveResponseText(response);
        String previous = previousResponseText.get();
        String delta = current.startsWith(previous) ? current.substring(previous.length()) : current;
        previousResponseText.set(current);
        return delta;
    }

    private ScheduledExecutorService startHeartbeat(StreamingProgressTracker tracker) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "runtime-v2-code-stage-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(
                tracker::emitHeartbeat,
                STREAM_HEARTBEAT_INTERVAL_MS,
                STREAM_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        return executor;
    }

    private void appendUsage(RuntimeV2State state, ChatResponse response) {
        if (state == null
                || response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        state.addUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private void logModelOutput(RuntimeV2State state, String stage, String output) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "Runtime V2 CODE 模型输出原文：sessionId={}, iteration={}, llmCallIndex={}, stage={}, output={}",
                state == null || state.conversation() == null
                        ? ""
                        : StringUtils.trimWhitespace(state.conversation().sessionCode()),
                state == null ? 0 : state.iterationCount(),
                state == null ? 0 : state.llmCallCount(),
                StringUtils.hasText(stage) ? stage.trim() : "",
                output == null ? "" : output);
    }

    public record CodeStagePreparation(
            boolean valid,
            RuntimeV2CodePlanProtocol.CodeExecutionPlan plan,
            String scriptContent,
            String errorMessage) {

        public static CodeStagePreparation valid(
                RuntimeV2CodePlanProtocol.CodeExecutionPlan plan, String scriptContent) {
            return new CodeStagePreparation(true, plan, scriptContent == null ? "" : scriptContent, "");
        }

        public static CodeStagePreparation invalid(String errorMessage) {
            return new CodeStagePreparation(false, null, "", errorMessage == null ? "CODE 阶段失败" : errorMessage);
        }
    }

    @FunctionalInterface
    public interface CodeStageProgressListener {

        void onProgress(CodeStageProgress progress);

        static CodeStageProgressListener noop() {
            return progress -> {};
        }
    }

    public record CodeStageProgress(
            String subStage, String subStageLabel, String progressMessage, boolean visibleToUser) {

        public static CodeStageProgress planStarted(String progressMessage) {
            return new CodeStageProgress("CODE_PLAN_START", "规划脚本", progressMessage, false);
        }

        public static CodeStageProgress planCompleted(String progressMessage) {
            return new CodeStageProgress("CODE_PLAN_DONE", "脚本规划完成", progressMessage, false);
        }

        public static CodeStageProgress planFailed(String progressMessage) {
            return new CodeStageProgress("CODE_PLAN_FAILED", "脚本规划失败", progressMessage, true);
        }

        public static CodeStageProgress planStreaming(int receivedChars) {
            return new CodeStageProgress(
                    "CODE_PLAN_STREAMING", "规划脚本", "正在规划脚本处理步骤，已接收 " + Math.max(receivedChars, 0) + " 字符。", false);
        }

        public static CodeStageProgress planHeartbeat(int receivedChars) {
            return new CodeStageProgress(
                    "CODE_PLAN_HEARTBEAT",
                    "规划脚本",
                    "正在规划脚本处理步骤，模型仍在处理中" + buildReceivedSuffix(receivedChars) + "。",
                    false);
        }

        public static CodeStageProgress scriptStarted(String progressMessage) {
            return new CodeStageProgress("CODE_SCRIPT_START", "生成脚本", progressMessage, false);
        }

        public static CodeStageProgress scriptCompleted(String progressMessage) {
            return new CodeStageProgress("CODE_SCRIPT_DONE", "脚本生成完成", progressMessage, false);
        }

        public static CodeStageProgress scriptFailed(String progressMessage) {
            return new CodeStageProgress("CODE_SCRIPT_FAILED", "脚本生成失败", progressMessage, true);
        }

        public static CodeStageProgress scriptStreaming(int receivedChars) {
            return new CodeStageProgress(
                    "CODE_SCRIPT_STREAMING", "生成脚本", "正在生成文件中，已接收 " + Math.max(receivedChars, 0) + " 字符。", true);
        }

        public static CodeStageProgress scriptHeartbeat(int receivedChars) {
            return new CodeStageProgress(
                    "CODE_SCRIPT_HEARTBEAT",
                    "生成脚本",
                    "正在生成文件中，模型仍在处理中" + buildReceivedSuffix(receivedChars) + "。",
                    false);
        }

        private static String buildReceivedSuffix(int receivedChars) {
            return receivedChars > 0 ? "（已接收 " + receivedChars + " 字符）" : "";
        }
    }

    private record StreamingStage(
            String name,
            java.util.function.IntFunction<CodeStageProgress> streamingFactory,
            java.util.function.IntFunction<CodeStageProgress> heartbeatFactory) {

        private static StreamingStage plan() {
            return new StreamingStage("code-plan", CodeStageProgress::planStreaming, CodeStageProgress::planHeartbeat);
        }

        private static StreamingStage script() {
            return new StreamingStage(
                    "code-script", CodeStageProgress::scriptStreaming, CodeStageProgress::scriptHeartbeat);
        }
    }

    private final class StreamingProgressTracker {

        private final StreamingStage stage;
        private final CodeStageProgressListener listener;
        private final AtomicInteger receivedChars = new AtomicInteger(0);
        private final AtomicLong lastEmitAt = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastDeltaAt = new AtomicLong(System.currentTimeMillis());
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private StreamingProgressTracker(StreamingStage stage, CodeStageProgressListener listener) {
            this.stage = stage;
            this.listener = listener;
        }

        private void onDelta(String delta) {
            if (!StringUtils.hasText(delta) || completed.get()) {
                return;
            }
            int totalChars = receivedChars.addAndGet(delta.length());
            long now = System.currentTimeMillis();
            lastDeltaAt.set(now);
            long previousEmitAt = lastEmitAt.get();
            if (totalChars == delta.length()
                    || previousEmitAt == 0L
                    || now - previousEmitAt >= STREAM_PROGRESS_MIN_INTERVAL_MS) {
                emit(stage.streamingFactory().apply(totalChars), now);
            }
        }

        private void emitHeartbeat() {
            if (completed.get()) {
                return;
            }
            long now = System.currentTimeMillis();
            long lastActivity = lastDeltaAt.get();
            long lastEmit = lastEmitAt.get();
            if (now - lastActivity < STREAM_HEARTBEAT_INTERVAL_MS || now - lastEmit < STREAM_HEARTBEAT_INTERVAL_MS) {
                return;
            }
            log.debug("Runtime V2 CODE 阶段心跳：stage={}, receivedChars={}", stage.name(), receivedChars.get());
            emit(stage.heartbeatFactory().apply(receivedChars.get()), now);
        }

        private void complete() {
            completed.set(true);
        }

        private void emit(CodeStageProgress progress, long timestamp) {
            listener.onProgress(progress);
            lastEmitAt.set(timestamp);
        }
    }
}
