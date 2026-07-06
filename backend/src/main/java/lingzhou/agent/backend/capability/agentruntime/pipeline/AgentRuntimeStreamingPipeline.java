package lingzhou.agent.backend.capability.agentruntime.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ToolPlanningRetryGuard;
import lingzhou.agent.backend.business.chat.service.ChatSseEventBuilder;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.ObservabilityCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TokenUsageCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.model.RuntimeModelRequest;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentExecutionSnapshotService;
import lingzhou.agent.backend.capability.tool.ToolCallbackSupport;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeTokenUsageAccumulator;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeErrorMessageResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Component
public class AgentRuntimeStreamingPipeline {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeStreamingPipeline.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SIMULATED_STREAM_CHUNK_SIZE = 16;
    private static final Duration SIMULATED_STREAM_DELAY = Duration.ofMillis(35);
    private static final Pattern SIMULATED_STREAM_BOUNDARY =
            Pattern.compile(".{1,16}([，。！？；：,.!?;:]|\\s+|$)", Pattern.DOTALL);

    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final ObservabilityCapabilityAdapter observabilityCapability;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;
    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService;
    private final ToolPlanningRetryGuard toolPlanningRetryGuard;

    public AgentRuntimeStreamingPipeline(
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            ObservabilityCapabilityAdapter observabilityCapability,
            TokenUsageCapabilityAdapter tokenUsageCapability,
            RuntimeExecutionProperties runtimeExecutionProperties,
            PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService,
            ToolPlanningRetryGuard toolPlanningRetryGuard) {
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.observabilityCapability = observabilityCapability;
        this.tokenUsageCapability = tokenUsageCapability;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.personalAgentExecutionSnapshotService = personalAgentExecutionSnapshotService;
        this.toolPlanningRetryGuard = toolPlanningRetryGuard;
    }

    public Flux<ServerSentEvent<String>> execute(
            AgentRuntimeExecutionContext executionContext, RuntimeModelRequest modelRequest) {
        if (executionContext.usesToolAwarePipeline()) {
            return buildSkillStreamingResponse(executionContext, modelRequest);
        }
        return buildGeneralStreamingResponse(executionContext, modelRequest);
    }

    public Flux<ServerSentEvent<String>> buildGeneralStreamingResponse(
            AgentRuntimeExecutionContext executionContext, RuntimeModelRequest modelRequest) {
        ConversationHistoryService.ConversationContext context = executionContext.conversation();
        AtomicReference<ChatRuntimePreparedRequest> preparedRef =
                new AtomicReference<>(personalAgentExecutionSnapshotService.startCurrentStep(
                        context,
                        personalAgentExecutionSnapshotService.markExecutionRunning(executionContext.prepared())));
        AtomicReference<String> last = new AtomicReference<>("");
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        AtomicBoolean finalized = new AtomicBoolean(false);
        long startedAt = System.currentTimeMillis();
        RuntimeTokenUsageAccumulator tokenAccumulator = tokenUsageCapability.createAccumulator(
                modelRequest.chatRuntimeBundle().config(), startedAt);
        tokenUsageCapability.ensureRunStarted(
                context, preparedRef.get(), modelRequest.chatRuntimeBundle().config(), startedAt);
        Flux<ServerSentEvent<String>> stream = buildGeneralChatResponseStream(
                        executionContext,
                        preparedRef,
                        modelRequest,
                        last,
                        previousResponseText,
                        tokenAccumulator,
                        startedAt,
                        finalized)
                .concatWithValues(doneEvent());
        return stream;
    }

    public Flux<ServerSentEvent<String>> buildSkillStreamingResponse(
            AgentRuntimeExecutionContext executionContext, RuntimeModelRequest modelRequest) {
        ConversationHistoryService.ConversationContext context = executionContext.conversation();
        AtomicReference<ChatRuntimePreparedRequest> preparedRef =
                new AtomicReference<>(personalAgentExecutionSnapshotService.startCurrentStep(
                        context,
                        personalAgentExecutionSnapshotService.markExecutionRunning(executionContext.prepared())));
        var requestSkillKit = executionContext.requestSkillKit();
        return Flux.defer(() -> {
            AtomicReference<String> last = new AtomicReference<>("");
            AtomicReference<String> previousResponseText = new AtomicReference<>("");
            AtomicBoolean finalized = new AtomicBoolean(false);
            AtomicBoolean retryIssued = new AtomicBoolean(false);
            AtomicBoolean holdAssistantText =
                    new AtomicBoolean(toolPlanningRetryGuard.shouldHoldPlanningText(preparedRef.get()));
            AtomicInteger modelRound = new AtomicInteger(1);
            AtomicInteger currentRoundOutputLength = new AtomicInteger(0);
            AtomicLong currentRoundStartedAt = new AtomicLong(System.currentTimeMillis());
            List<Map<String, Object>> segments = Collections.synchronizedList(new ArrayList<>());
            List<Map<String, Object>> toolEvents = Collections.synchronizedList(new ArrayList<>());
            Map<String, Map<String, Object>> pendingToolCalls = Collections.synchronizedMap(new LinkedHashMap<>());
            Map<String, Long> toolStartedAtByTrace = Collections.synchronizedMap(new LinkedHashMap<>());
            long startedAt = System.currentTimeMillis();
            RuntimeTokenUsageAccumulator tokenAccumulator = tokenUsageCapability.createAccumulator(
                    modelRequest.chatRuntimeBundle().config(), startedAt);
            tokenUsageCapability.ensureRunStarted(
                    context, preparedRef.get(), modelRequest.chatRuntimeBundle().config(), startedAt);
            Sinks.Many<ServerSentEvent<String>> toolSink =
                    Sinks.many().unicast().onBackpressureBuffer();
            AtomicBoolean executeStepActivated = new AtomicBoolean(false);
            BiConsumer<String, String> publisher = (eventType, payload) -> {
                if (("tool".equals(eventType) || "result".equals(eventType))
                        && executeStepActivated.compareAndSet(false, true)) {
                    preparedRef.updateAndGet(current -> personalAgentExecutionSnapshotService.moveToStep(
                            context, current, "step-execute", "进入工具执行阶段"));
                }
                if ("tool".equals(eventType) || "result".equals(eventType)) {
                    holdAssistantText.set(false);
                }
                String normalizedPayload =
                        eventPersistenceCapability.enrichToolEventPayload(eventType, payload, toolStartedAtByTrace);
                eventPersistenceCapability.recordToolEvent(eventType, normalizedPayload, toolEvents);
                eventPersistenceCapability.recordTimelineToolEvent(segments, eventType, normalizedPayload);
                tokenUsageCapability.recordToolEvent(tokenAccumulator, eventType, normalizedPayload);
                enforceToolCallGuard(tokenAccumulator);
                eventPersistenceCapability.persistCompletedToolTrace(
                        context, eventType, normalizedPayload, pendingToolCalls);
                toolSink.tryEmitNext(ServerSentEvent.builder(normalizedPayload)
                        .event(eventType)
                        .build());
            };

            Flux<ServerSentEvent<String>> stream;
            try {
                boolean simulatedStreaming = isVllmToolAware(modelRequest);
                stream = buildSkillResponseFlux(
                                modelRequest,
                                executionContext.prepared() == null ? List.of() : executionContext.prepared().toolCallbacks(),
                                publisher,
                                tokenAccumulator,
                                currentRoundStartedAt,
                                previousResponseText)
                        .flatMap(chatResponse -> {
                            if (chatResponse == null
                                    || chatResponse.getResult() == null
                                    || chatResponse.getResult().getOutput() == null) {
                                return Flux.empty();
                            }
                            AssistantMessage output = chatResponse.getResult().getOutput();
                            String current = output.getText();
                            if (current == null) {
                                current = "";
                            }
                            String previous = previousResponseText.get();
                            String delta =
                                    current.startsWith(previous) ? current.substring(previous.length()) : current;
                            previousResponseText.set(current);
                            boolean usageOnlyChunk = isUsageOnlyChunk(chatResponse, delta);
                            if (!usageOnlyChunk || tokenAccumulator.hasCurrentCall()) {
                                tokenAccumulator.ensureCurrentCall(currentRoundStartedAt.get());
                            }
                            if (StringUtils.hasText(delta)) {
                                last.updateAndGet(existing -> existing + delta);
                                currentRoundOutputLength.addAndGet(safeLength(delta));
                                eventPersistenceCapability.appendTextSegment(segments, delta);
                            }
                            tokenUsageCapability.recordResponse(tokenAccumulator, chatResponse);
                            if (usageOnlyChunk
                                    && holdAssistantText.get()
                                    && toolPlanningRetryGuard.shouldRetry(preparedRef.get(), toolEvents, last.get())) {
                                if (!retryIssued.compareAndSet(false, true)) {
                                    last.set(toolPlanningRetryGuard.retryFailureMessage());
                                    segments.clear();
                                    eventPersistenceCapability.appendTextSegment(segments, last.get());
                                    currentRoundOutputLength.set(safeLength(last.get()));
                                    return Flux.just(messageEvent(last.get()));
                                }
                                last.set("");
                                previousResponseText.set("");
                                segments.clear();
                                currentRoundOutputLength.set(0);
                                currentRoundStartedAt.set(System.currentTimeMillis());
                                tokenAccumulator.ensureCurrentCall(currentRoundStartedAt.get());
                                return buildSkillResponseFlux(
                                                modelRequest.withAdditionalSystemPrompt(
                                                        toolPlanningRetryGuard.buildCorrectionPrompt(preparedRef.get())),
                                                executionContext.prepared() == null
                                                        ? List.of()
                                                        : executionContext.prepared().toolCallbacks(),
                                                publisher,
                                                tokenAccumulator,
                                                currentRoundStartedAt,
                                                previousResponseText)
                                        .flatMap(retryResponse -> {
                                            if (retryResponse == null
                                                    || retryResponse.getResult() == null
                                                    || retryResponse.getResult().getOutput() == null) {
                                                return Flux.empty();
                                            }
                                            AssistantMessage retryOutput = retryResponse.getResult().getOutput();
                                            String retryCurrent = retryOutput.getText();
                                            if (retryCurrent == null) {
                                                retryCurrent = "";
                                            }
                                            String retryPrevious = previousResponseText.get();
                                            String retryDelta = retryCurrent.startsWith(retryPrevious)
                                                    ? retryCurrent.substring(retryPrevious.length())
                                                    : retryCurrent;
                                            previousResponseText.set(retryCurrent);
                                            boolean retryUsageOnlyChunk = isUsageOnlyChunk(retryResponse, retryDelta);
                                            if (!retryUsageOnlyChunk || tokenAccumulator.hasCurrentCall()) {
                                                tokenAccumulator.ensureCurrentCall(currentRoundStartedAt.get());
                                            }
                                            if (StringUtils.hasText(retryDelta)) {
                                                last.updateAndGet(existing -> existing + retryDelta);
                                                currentRoundOutputLength.addAndGet(safeLength(retryDelta));
                                                eventPersistenceCapability.appendTextSegment(segments, retryDelta);
                                            }
                                            tokenUsageCapability.recordResponse(tokenAccumulator, retryResponse);
                                            if (retryUsageOnlyChunk
                                                    && holdAssistantText.get()
                                                    && toolPlanningRetryGuard.shouldRetry(
                                                            preparedRef.get(), toolEvents, last.get())) {
                                                holdAssistantText.set(false);
                                                last.set(toolPlanningRetryGuard.retryFailureMessage());
                                                segments.clear();
                                                eventPersistenceCapability.appendTextSegment(segments, last.get());
                                                currentRoundOutputLength.set(safeLength(last.get()));
                                                return Flux.just(messageEvent(last.get()));
                                            }
                                            if (retryUsageOnlyChunk) {
                                                holdAssistantText.set(false);
                                                long retryCompletedAt = System.currentTimeMillis();
                                                finishSkillModelCall(
                                                        context,
                                                        preparedRef.get(),
                                                        tokenAccumulator,
                                                        modelRound,
                                                        currentRoundOutputLength,
                                                        currentRoundStartedAt,
                                                        "COMPLETED",
                                                        retryCompletedAt);
                                                enforceUsageGuards(
                                                        context, preparedRef.get(), tokenAccumulator, retryCompletedAt);
                                            }
                                            if (!retryUsageOnlyChunk
                                                    && holdAssistantText.get()
                                                    && !toolPlanningRetryGuard.hasPlanningText(last.get())) {
                                                holdAssistantText.set(false);
                                            }
                                            return toSkillMessageEvents(
                                                    executionContext,
                                                    retryDelta,
                                                    simulatedStreaming,
                                                    tokenAccumulator,
                                                    segments,
                                                    last,
                                                    holdAssistantText);
                                        });
                            }
                            if (usageOnlyChunk) {
                                holdAssistantText.set(false);
                                long completedAt = System.currentTimeMillis();
                                finishSkillModelCall(
                                        context,
                                        preparedRef.get(),
                                        tokenAccumulator,
                                        modelRound,
                                        currentRoundOutputLength,
                                        currentRoundStartedAt,
                                        "COMPLETED",
                                        completedAt);
                                enforceUsageGuards(context, preparedRef.get(), tokenAccumulator, completedAt);
                            }
                            if (!usageOnlyChunk
                                    && holdAssistantText.get()
                                    && !toolPlanningRetryGuard.hasPlanningText(last.get())) {
                                holdAssistantText.set(false);
                            }
                            return toSkillMessageEvents(
                                    executionContext,
                                    delta,
                                    simulatedStreaming,
                                    tokenAccumulator,
                                    segments,
                                    last,
                                    holdAssistantText);
                        })
                        .onErrorResume(error -> {
                            String friendlyMessage = ModelRuntimeErrorMessageResolver.resolve(error);
                            observabilityCapability.logStreamingError(
                                    "skill",
                                    preparedRef.get(),
                                    modelRequest.chatRuntimeBundle().config(),
                                    error);
                            if (finalized.compareAndSet(false, true)) {
                                finishSkillModelCall(
                                        context,
                                        preparedRef.get(),
                                        tokenAccumulator,
                                        modelRound,
                                        currentRoundOutputLength,
                                        currentRoundStartedAt,
                                        "FAILED",
                                        System.currentTimeMillis());
                                RuntimeRunUsageSnapshot usageSnapshot = tokenUsageCapability.snapshot(
                                        tokenAccumulator, "FAILED", System.currentTimeMillis());
                                ConversationMessageUsagePayload usagePayload =
                                        tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                                preparedRef.updateAndGet(
                                        current -> personalAgentExecutionSnapshotService.prepareForFailureCompletion(
                                                context, current, friendlyMessage, Map.of()));
                                eventPersistenceCapability.persistFailedSkillResponse(
                                        context,
                                        preparedRef.get(),
                                        requestSkillKit,
                                        friendlyMessage,
                                        last.get(),
                                        segments,
                                        toolEvents,
                                        usagePayload,
                                        usageSnapshot,
                                        startedAt);
                            }
                            return Flux.just(errorEvent(friendlyMessage));
                        })
                        .doOnComplete(() -> {
                            if (finalized.compareAndSet(false, true)) {
                                finishSkillModelCall(
                                        context,
                                        preparedRef.get(),
                                        tokenAccumulator,
                                        modelRound,
                                        currentRoundOutputLength,
                                        currentRoundStartedAt,
                                        "COMPLETED",
                                        System.currentTimeMillis());
                                RuntimeRunUsageSnapshot usageSnapshot = tokenUsageCapability.snapshot(
                                        tokenAccumulator, "COMPLETED", System.currentTimeMillis());
                                ConversationMessageUsagePayload usagePayload =
                                        tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                                Map<String, Object> finalArtifactPreview =
                                        eventPersistenceCapability.extractFinalArtifact(toolEvents, last.get());
                                preparedRef.updateAndGet(
                                        current -> personalAgentExecutionSnapshotService.prepareForSuccessfulCompletion(
                                                context, current, finalArtifactPreview));
                                eventPersistenceCapability.persistCompletedSkillResponse(
                                        context,
                                        preparedRef.get(),
                                        requestSkillKit,
                                        last.get(),
                                        segments,
                                        toolEvents,
                                        usagePayload,
                                        usageSnapshot,
                                        startedAt);
                                observabilityCapability.logSkillConversationStats(
                                        context,
                                        preparedRef.get(),
                                        last.get(),
                                        startedAt,
                                        usageSnapshot.llmCallCount());
                            }
                        })
                        .doFinally(signalType -> {
                            if (signalType == SignalType.CANCEL && finalized.compareAndSet(false, true)) {
                                finishSkillModelCall(
                                        context,
                                        preparedRef.get(),
                                        tokenAccumulator,
                                        modelRound,
                                        currentRoundOutputLength,
                                        currentRoundStartedAt,
                                        "CANCELLED",
                                        System.currentTimeMillis());
                                RuntimeRunUsageSnapshot usageSnapshot = tokenUsageCapability.snapshot(
                                        tokenAccumulator, "CANCELLED", System.currentTimeMillis());
                                ConversationMessageUsagePayload usagePayload =
                                        tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                                Map<String, Object> finalArtifactPreview =
                                        eventPersistenceCapability.extractFinalArtifact(toolEvents, last.get());
                                preparedRef.updateAndGet(
                                        current -> personalAgentExecutionSnapshotService.prepareForCancelledCompletion(
                                                context, current, finalArtifactPreview));
                                eventPersistenceCapability.persistInterruptedSkillResponse(
                                        context,
                                        preparedRef.get(),
                                        requestSkillKit,
                                        last.get(),
                                        segments,
                                        toolEvents,
                                        usagePayload,
                                        usageSnapshot,
                                        startedAt);
                            }
                            toolSink.tryEmitComplete();
                        })
                        .concatWithValues(doneEvent())
                        .contextWrite(Context.of("toolEventPublisher", publisher));
            } catch (Exception error) {
                String friendlyMessage = ModelRuntimeErrorMessageResolver.resolve(error);
                observabilityCapability.logStreamingError(
                        "skill",
                        preparedRef.get(),
                        modelRequest.chatRuntimeBundle().config(),
                        error);
                if (finalized.compareAndSet(false, true)) {
                    finishSkillModelCall(
                            context,
                            preparedRef.get(),
                            tokenAccumulator,
                            modelRound,
                            currentRoundOutputLength,
                            currentRoundStartedAt,
                            "FAILED",
                            System.currentTimeMillis());
                    RuntimeRunUsageSnapshot usageSnapshot =
                            tokenUsageCapability.snapshot(tokenAccumulator, "FAILED", System.currentTimeMillis());
                    ConversationMessageUsagePayload usagePayload =
                            tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                    preparedRef.updateAndGet(
                            current -> personalAgentExecutionSnapshotService.prepareForFailureCompletion(
                                    context, current, friendlyMessage, Map.of()));
                    eventPersistenceCapability.persistFailedSkillResponse(
                            context,
                            preparedRef.get(),
                            requestSkillKit,
                            friendlyMessage,
                            last.get(),
                            segments,
                            toolEvents,
                            usagePayload,
                            usageSnapshot,
                            startedAt);
                }
                toolSink.tryEmitComplete();
                return Flux.just(errorEvent(friendlyMessage), doneEvent());
            }

            return Flux.merge(stream, toolSink.asFlux());
        });
    }

    private Flux<ServerSentEvent<String>> toSkillMessageEvents(
            AgentRuntimeExecutionContext executionContext,
            String delta,
            boolean simulatedStreaming,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            List<Map<String, Object>> segments,
            AtomicReference<String> last,
            AtomicBoolean holdAssistantText) {
        if (!StringUtils.hasText(delta)) {
            return Flux.empty();
        }
        if (holdAssistantText != null && holdAssistantText.get()) {
            return Flux.empty();
        }
        if (!simulatedStreaming) {
            return Flux.just(messageEvent(delta));
        }
        List<String> chunks = splitForSimulatedStreaming(delta);
        if (chunks.isEmpty()) {
            return Flux.just(messageEvent(delta));
        }
        return Flux.fromIterable(chunks).delayElements(SIMULATED_STREAM_DELAY).map(this::messageEvent);
    }

    private Flux<ChatResponse> buildSkillResponseFlux(
            RuntimeModelRequest modelRequest,
            List<ToolCallback> toolCallbacks,
            BiConsumer<String, String> publisher,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            AtomicLong currentRoundStartedAt,
            AtomicReference<String> previousResponseText) {
        if (!isVllmToolAware(modelRequest)) {
            return modelRequest.requestSpec().stream()
                    .chatResponse()
                    .contextWrite(Context.of("toolEventPublisher", publisher));
        }
        List<ToolCallback> safeToolCallbacks = toolCallbacks == null ? List.of() : toolCallbacks;
        if (safeToolCallbacks.isEmpty()) {
            return modelRequest.requestSpec().stream()
                    .chatResponse()
                    .contextWrite(Context.of("toolEventPublisher", publisher));
        }
        configureVllmToolExecution(modelRequest, false);
        VllmToolStreamingState state = new VllmToolStreamingState(safeToolCallbacks);
        Flux<ChatResponse> streamAttempt = modelRequest.requestSpec().stream()
                .chatResponse()
                .doOnNext(state::observe)
                .doOnError(state::markStreamFailed)
                .contextWrite(Context.of("toolEventPublisher", state.wrapPublisher(publisher)));
        return streamAttempt
                .onErrorResume(error -> fallbackToNonStreamingToolCall(
                        modelRequest, publisher, tokenAccumulator, currentRoundStartedAt, state, error))
                .concatWith(Mono.defer(() -> {
                    if (!state.shouldFallbackAfterCompletion()) {
                        return Mono.empty();
                    }
                    return fallbackToNonStreamingToolCall(
                                    modelRequest, publisher, tokenAccumulator, currentRoundStartedAt, state, null)
                            .next();
                }));
    }

    private Flux<ChatResponse> fallbackToNonStreamingToolCall(
            RuntimeModelRequest modelRequest,
            BiConsumer<String, String> publisher,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            AtomicLong currentRoundStartedAt,
            VllmToolStreamingState state,
            Throwable error) {
        if (!state.markFallbackStarted()) {
            return error == null ? Flux.empty() : Flux.error(error);
        }
        String reason = state.fallbackReason(error);
        if (error != null) {
            log.warn("vLLM 流式 tool calling 失败，回退非流式重跑：reason={}, error={}", reason, error.getMessage());
        } else {
            log.warn("vLLM 流式 tool calling 未形成可执行 tool call，回退非流式重跑：reason={}", reason);
        }
        return Mono.fromCallable(() -> {
                    configureVllmToolExecution(modelRequest, true);
                    currentRoundStartedAt.set(System.currentTimeMillis());
                    tokenAccumulator.ensureCurrentCall(currentRoundStartedAt.get());
                    SkillAwareToolCallingManager.setToolEventPublisher(publisher);
                    try {
                        return modelRequest.requestSpec().call().chatResponse();
                    } finally {
                        SkillAwareToolCallingManager.clearToolEventPublisher();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    private void configureVllmToolExecution(RuntimeModelRequest modelRequest, boolean internalToolExecutionEnabled) {
        if (modelRequest == null || modelRequest.chatRuntimeBundle() == null) {
            return;
        }
        if (!(modelRequest.chatRuntimeBundle().chatModel().getDefaultOptions() instanceof OpenAiChatOptions defaultOptions)) {
            return;
        }
        OpenAiChatOptions options = OpenAiChatOptions.fromOptions(defaultOptions);
        options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
        if (!internalToolExecutionEnabled) {
            options.setParallelToolCalls(Boolean.FALSE);
        }
        modelRequest.requestSpec().options(options);
    }

    private boolean isVllmToolAware(RuntimeModelRequest modelRequest) {
        if (modelRequest == null || modelRequest.chatRuntimeBundle() == null || modelRequest.chatRuntimeBundle().config() == null) {
            return false;
        }
        String adapterType = modelRequest.chatRuntimeBundle().config().adapterType();
        String provider = modelRequest.chatRuntimeBundle().config().provider();
        return "VLLM".equalsIgnoreCase(adapterType) || "vllm".equalsIgnoreCase(provider);
    }

    private List<String> splitForSimulatedStreaming(String text) {
        if (!StringUtils.hasText(text) || text.length() <= SIMULATED_STREAM_CHUNK_SIZE) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        Matcher matcher = SIMULATED_STREAM_BOUNDARY.matcher(text);
        int consumed = 0;
        while (matcher.find()) {
            String chunk = matcher.group();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            consumed = matcher.end();
        }
        if (consumed < text.length()) {
            String tail = text.substring(consumed);
            if (StringUtils.hasText(tail)) {
                chunks.add(tail);
            }
        }
        return chunks.isEmpty() ? List.of(text) : chunks;
    }

    private Flux<ServerSentEvent<String>> buildGeneralChatResponseStream(
            AgentRuntimeExecutionContext executionContext,
            AtomicReference<ChatRuntimePreparedRequest> preparedRef,
            RuntimeModelRequest modelRequest,
            AtomicReference<String> last,
            AtomicReference<String> previousResponseText,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            long startedAt,
            AtomicBoolean finalized) {
        ConversationHistoryService.ConversationContext context = executionContext.conversation();
        var requestSkillKit = executionContext.requestSkillKit();
        List<Map<String, Object>> segments = new ArrayList<>();
        List<Map<String, Object>> toolEvents = List.of();
        return modelRequest.requestSpec().stream()
                .chatResponse()
                .flatMap(chatResponse -> toGeneralMessageEvents(
                        executionContext, chatResponse, last, previousResponseText, tokenAccumulator, segments))
                .onErrorResume(error -> {
                    String friendlyMessage = ModelRuntimeErrorMessageResolver.resolve(error);
                    observabilityCapability.logStreamingError(
                            "general",
                            preparedRef.get(),
                            modelRequest.chatRuntimeBundle().config(),
                            error);
                    if (finalized.compareAndSet(false, true)) {
                        long completedAt = System.currentTimeMillis();
                        tokenUsageCapability.completeCurrentCall(
                                tokenAccumulator, "FAILED", completedAt, safeLength(last.get()));
                        RuntimeRunUsageSnapshot usageSnapshot =
                                tokenUsageCapability.snapshot(tokenAccumulator, "FAILED", completedAt);
                        ConversationMessageUsagePayload usagePayload =
                                tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                        preparedRef.updateAndGet(
                                current -> personalAgentExecutionSnapshotService.prepareForFailureCompletion(
                                        context, current, friendlyMessage, Map.of()));
                        eventPersistenceCapability.persistFailedGeneralResponse(
                                context,
                                preparedRef.get(),
                                requestSkillKit,
                                friendlyMessage,
                                last.get(),
                                segments,
                                usagePayload,
                                usageSnapshot,
                                startedAt,
                                toolEvents);
                    }
                    return Flux.just(errorEvent(friendlyMessage));
                })
                .doOnComplete(() -> {
                    if (finalized.compareAndSet(false, true)) {
                        long completedAt = System.currentTimeMillis();
                        tokenUsageCapability.completeCurrentCall(
                                tokenAccumulator, "COMPLETED", completedAt, safeLength(last.get()));
                        RuntimeRunUsageSnapshot usageSnapshot =
                                tokenUsageCapability.snapshot(tokenAccumulator, "COMPLETED", completedAt);
                        ConversationMessageUsagePayload usagePayload =
                                tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                        preparedRef.updateAndGet(
                                current -> personalAgentExecutionSnapshotService.prepareForSuccessfulCompletion(
                                        context, current, Map.of()));
                        eventPersistenceCapability.persistCompletedGeneralResponse(
                                context,
                                preparedRef.get(),
                                requestSkillKit,
                                last.get(),
                                segments,
                                usagePayload,
                                usageSnapshot,
                                startedAt,
                                toolEvents);
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL && finalized.compareAndSet(false, true)) {
                        long completedAt = System.currentTimeMillis();
                        tokenUsageCapability.completeCurrentCall(
                                tokenAccumulator, "CANCELLED", completedAt, safeLength(last.get()));
                        RuntimeRunUsageSnapshot usageSnapshot =
                                tokenUsageCapability.snapshot(tokenAccumulator, "CANCELLED", completedAt);
                        ConversationMessageUsagePayload usagePayload =
                                tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                        preparedRef.updateAndGet(
                                current -> personalAgentExecutionSnapshotService.prepareForCancelledCompletion(
                                        context, current, Map.of()));
                        eventPersistenceCapability.persistInterruptedGeneralResponse(
                                context,
                                preparedRef.get(),
                                requestSkillKit,
                                last.get(),
                                segments,
                                usagePayload,
                                usageSnapshot,
                                startedAt,
                                toolEvents);
                    }
                });
    }

    private Flux<ServerSentEvent<String>> toGeneralMessageEvents(
            AgentRuntimeExecutionContext executionContext,
            ChatResponse chatResponse,
            AtomicReference<String> last,
            AtomicReference<String> previousResponseText,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            List<Map<String, Object>> segments) {
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return Flux.empty();
        }
        AssistantMessage output = chatResponse.getResult().getOutput();
        String current = output.getText();
        if (current == null) {
            current = "";
        }
        String previous = previousResponseText.get();
        String delta = current.startsWith(previous) ? current.substring(previous.length()) : current;
        previousResponseText.set(current);
        boolean usageOnlyChunk = isUsageOnlyChunk(chatResponse, delta);
        if (!usageOnlyChunk || tokenAccumulator.hasCurrentCall()) {
            tokenAccumulator.ensureCurrentCall(System.currentTimeMillis());
        }
        if (StringUtils.hasText(delta)) {
            last.updateAndGet(existing -> existing + delta);
            eventPersistenceCapability.appendTextSegment(segments, delta);
        }
        tokenUsageCapability.recordResponse(tokenAccumulator, chatResponse);
        if (usageOnlyChunk) {
            tokenUsageCapability.completeCurrentCall(
                    tokenAccumulator, "COMPLETED", System.currentTimeMillis(), safeLength(last.get()));
        }
        observabilityCapability.logGeneralStreamingChunk(
                executionContext.prepared(), delta.length(), MediaType.TEXT_EVENT_STREAM_VALUE);
        return StringUtils.hasText(delta) ? Flux.just(messageEvent(delta)) : Flux.empty();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static final class VllmToolStreamingState {
        private final Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
        private final Map<String, ToolCallDraft> drafts = new LinkedHashMap<>();
        private boolean sawToolCallChunk;
        private boolean sawToolExecutionEvent;
        private boolean fallbackStarted;
        private String fallbackReason;

        private VllmToolStreamingState(List<ToolCallback> toolCallbacks) {
            for (ToolCallback callback : toolCallbacks) {
                String toolName = ToolCallbackSupport.resolveToolName(callback);
                if (StringUtils.hasText(toolName)) {
                    callbacksByName.putIfAbsent(toolName, callback);
                }
            }
        }

        private BiConsumer<String, String> wrapPublisher(BiConsumer<String, String> delegate) {
            return (eventType, payload) -> {
                if ("tool".equals(eventType) || "result".equals(eventType)) {
                    sawToolExecutionEvent = true;
                }
                delegate.accept(eventType, payload);
            };
        }

        private void observe(ChatResponse chatResponse) {
            if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                return;
            }
            AssistantMessage output = chatResponse.getResult().getOutput();
            if (!output.hasToolCalls() || output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
                return;
            }
            sawToolCallChunk = true;
            int index = 0;
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                if (toolCall == null) {
                    index++;
                    continue;
                }
                String draftKey = resolveDraftKey(toolCall, index++);
                drafts.computeIfAbsent(draftKey, ignored -> new ToolCallDraft()).merge(toolCall);
            }
        }

        private void markStreamFailed(Throwable error) {
            if (error != null && !StringUtils.hasText(fallbackReason)) {
                fallbackReason = "stream_error";
            }
        }

        private boolean shouldFallbackAfterCompletion() {
            if (fallbackStarted || !sawToolCallChunk || sawToolExecutionEvent) {
                return false;
            }
            if (drafts.size() > 1) {
                fallbackReason = "multiple_tool_calls_not_supported";
                return true;
            }
            if (drafts.isEmpty()) {
                fallbackReason = "empty_tool_call_draft";
                return true;
            }
            ToolCallDraft draft = drafts.values().iterator().next();
            if (!StringUtils.hasText(draft.name)) {
                fallbackReason = "missing_tool_name";
                return true;
            }
            ToolCallback callback = callbacksByName.get(draft.name);
            if (callback == null) {
                fallbackReason = "unknown_tool_name";
                return true;
            }
            if (ToolCallbackSupport.acceptsEmptyArguments(callback) && !StringUtils.hasText(draft.arguments())) {
                fallbackReason = "empty_arguments_allowed";
                return true;
            }
            if (!StringUtils.hasText(draft.arguments())) {
                fallbackReason = "missing_tool_arguments";
                return true;
            }
            if (!isJsonObject(draft.arguments())) {
                fallbackReason = "invalid_tool_arguments";
                return true;
            }
            fallbackReason = "tool_call_not_executed_by_stream";
            return true;
        }

        private boolean markFallbackStarted() {
            if (fallbackStarted) {
                return false;
            }
            fallbackStarted = true;
            return true;
        }

        private String fallbackReason(Throwable error) {
            if (StringUtils.hasText(fallbackReason)) {
                return fallbackReason;
            }
            return error == null ? "incomplete_tool_call_stream" : "stream_error";
        }

        private static String resolveDraftKey(AssistantMessage.ToolCall toolCall, int index) {
            if (StringUtils.hasText(toolCall.id())) {
                return toolCall.id().trim();
            }
            if (StringUtils.hasText(toolCall.name())) {
                return toolCall.name().trim() + "#" + index;
            }
            return "tool#" + index;
        }

        private static boolean isJsonObject(String value) {
            try {
                return OBJECT_MAPPER.readTree(value).isObject();
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private static final class ToolCallDraft {
        private String id;
        private String type;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void merge(AssistantMessage.ToolCall toolCall) {
            if (!StringUtils.hasText(id) && StringUtils.hasText(toolCall.id())) {
                id = toolCall.id().trim();
            }
            if (!StringUtils.hasText(type) && StringUtils.hasText(toolCall.type())) {
                type = toolCall.type().trim();
            }
            if (!StringUtils.hasText(name) && StringUtils.hasText(toolCall.name())) {
                name = toolCall.name().trim();
            }
            if (!StringUtils.hasText(toolCall.arguments())) {
                return;
            }
            String next = toolCall.arguments();
            String current = arguments.toString();
            if (current.equals(next)) {
                return;
            }
            if (StringUtils.hasText(current) && next.startsWith(current)) {
                arguments.setLength(0);
                arguments.append(next);
                return;
            }
            arguments.append(next);
        }

        private String arguments() {
            return arguments.toString();
        }
    }

    private boolean isUsageOnlyChunk(ChatResponse chatResponse, String delta) {
        return hasUsage(chatResponse) && !StringUtils.hasText(delta);
    }

    private boolean hasUsage(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getMetadata() == null
                || chatResponse.getMetadata().getUsage() == null) {
            return false;
        }
        return chatResponse.getMetadata().getUsage().getPromptTokens() != null
                || chatResponse.getMetadata().getUsage().getCompletionTokens() != null
                || chatResponse.getMetadata().getUsage().getTotalTokens() != null;
    }

    private void finishSkillModelCall(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            AtomicInteger modelRound,
            AtomicInteger currentRoundOutputLength,
            AtomicLong currentRoundStartedAt,
            String status,
            long completedAtMillis) {
        int outputChars = currentRoundOutputLength.get();
        boolean completed =
                tokenUsageCapability.completeCurrentCall(tokenAccumulator, status, completedAtMillis, outputChars);
        if (!completed) {
            return;
        }
        observabilityCapability.logModelRoundThroughput(
                context, prepared, modelRound.getAndIncrement(), outputChars, currentRoundStartedAt.get());
        currentRoundOutputLength.set(0);
        currentRoundStartedAt.set(completedAtMillis);
    }

    private void enforceToolCallGuard(RuntimeTokenUsageAccumulator tokenAccumulator) {
        if (tokenAccumulator == null) {
            return;
        }
        int maxToolCalls = runtimeExecutionProperties.getMaxToolCallsPerRun();
        int currentToolCalls = tokenAccumulator.currentToolCallCount();
        if (maxToolCalls > 0 && currentToolCalls > maxToolCalls) {
            logGuardrailViolation("工具调用次数", currentToolCalls, maxToolCalls, "仅记录告警，不中断本次执行。");
        }
    }

    private void enforceUsageGuards(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeTokenUsageAccumulator tokenAccumulator,
            long completedAtMillis) {
        if (tokenAccumulator == null) {
            return;
        }
        RuntimeRunUsageSnapshot snapshot =
                tokenUsageCapability.snapshot(tokenAccumulator, "RUNNING", completedAtMillis);
        if (snapshot == null) {
            return;
        }
        enforceLimit("模型调用轮次", snapshot.llmCallCount(), runtimeExecutionProperties.getMaxModelRoundsPerRun(), snapshot);
        enforceLimit(
                "Prompt Tokens",
                safeInt(snapshot.promptTokens()),
                runtimeExecutionProperties.getMaxPromptTokensPerRun(),
                snapshot);
        enforceLimit(
                "Completion Tokens",
                safeInt(snapshot.completionTokens()),
                runtimeExecutionProperties.getMaxCompletionTokensPerRun(),
                snapshot);
        enforceLimit(
                "Total Tokens",
                safeInt(snapshot.totalTokens()),
                runtimeExecutionProperties.getMaxTotalTokensPerRun(),
                snapshot);
        log.debug(
                "运行保护检查通过：sessionId={}, assistantMessageId={}, llmCalls={}, toolCalls={}, promptTokens={}, completionTokens={}, totalTokens={}",
                context == null ? null : context.sessionId(),
                context == null ? null : context.assistantMessageId(),
                snapshot.llmCallCount(),
                snapshot.toolCallCount(),
                snapshot.promptTokens(),
                snapshot.completionTokens(),
                snapshot.totalTokens());
    }

    private void enforceLimit(String metricName, int currentValue, int limitValue, RuntimeRunUsageSnapshot snapshot) {
        if (limitValue <= 0 || currentValue <= limitValue) {
            return;
        }
        logGuardrailViolation(
                metricName,
                currentValue,
                limitValue,
                "仅记录告警，不中断本次执行。当前统计：模型 " + snapshot.llmCallCount() + " 轮，工具 " + snapshot.toolCallCount() + " 次。");
    }

    private void logGuardrailViolation(String metricName, int currentValue, int limitValue, String detail) {
        String message = "本次执行已触发运行保护：" + metricName + " 已达到 " + currentValue + "，超过上限 " + limitValue + "，" + detail;
        log.warn(message);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private ServerSentEvent<String> messageEvent(String content) {
        return ChatSseEventBuilder.message(content);
    }

    private ServerSentEvent<String> errorEvent(String error) {
        return ChatSseEventBuilder.error(error);
    }

    private ServerSentEvent<String> doneEvent() {
        return ChatSseEventBuilder.done();
    }
}
