package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.service.ChatSseEventBuilder;
import lingzhou.agent.backend.business.chat.service.ConversationContextWindowService;
import lingzhou.agent.backend.business.chat.service.ConversationEventService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import lingzhou.agent.backend.business.chat.service.ConversationRunConstants;
import lingzhou.agent.backend.business.chat.service.ConversationRunService;
import lingzhou.agent.backend.business.chat.service.ConversationRunUsageService;
import lingzhou.agent.backend.business.chat.service.UserTokenQuotaService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentPreflightService;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RequestHints;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageRuntime;
import lingzhou.agent.backend.capability.agentruntime.v2.engine.RuntimeV2ExecutionSessionFactory.RuntimeV2ExecutionSession;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphBuilder;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphEvent;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphEventProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphStateProjector;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphSeed;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class RuntimeV2GraphEngine implements RuntimeV2ExecutionEngine {

    private static final int SYNTHETIC_DELTA_MAX_CHARS = 24;

    private final RuntimeV2GraphBuilder graphBuilder;
    private final RuntimeV2ExecutionSessionFactory executionSessionFactory;
    private final ConversationHistoryService conversationHistoryService;
    private final ConversationContextWindowService conversationContextWindowService;
    private final ConversationRunService conversationRunService;
    private final ConversationEventService conversationEventService;
    private final ConversationRunUsageService conversationRunUsageService;
    private final UserTokenQuotaService userTokenQuotaService;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeV2LedgerEngine ledgerEngine;
    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final PersonalAgentPreflightService personalAgentPreflightService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private volatile CompiledGraph compiledGraph;

    @Override
    public boolean requestCancellation(String runCode, Long userId, String reason) {
        if (!StringUtils.hasText(runCode)) {
            return false;
        }
        return runtimeRegistry.requestCancellation(runCode.trim(), reason);
    }

    public RuntimeV2GraphEngine(
            RuntimeV2GraphBuilder graphBuilder,
            RuntimeV2ExecutionSessionFactory executionSessionFactory,
            ConversationHistoryService conversationHistoryService,
            ConversationContextWindowService conversationContextWindowService,
            ConversationRunService conversationRunService,
            ConversationEventService conversationEventService,
            ConversationRunUsageService conversationRunUsageService,
            UserTokenQuotaService userTokenQuotaService,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeV2LedgerEngine ledgerEngine,
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            PersonalAgentPreflightService personalAgentPreflightService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this.graphBuilder = graphBuilder;
        this.executionSessionFactory = executionSessionFactory;
        this.conversationHistoryService = conversationHistoryService;
        this.conversationContextWindowService = conversationContextWindowService;
        this.conversationRunService = conversationRunService;
        this.conversationEventService = conversationEventService;
        this.conversationRunUsageService = conversationRunUsageService;
        this.userTokenQuotaService = userTokenQuotaService;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.runtimeRegistry = runtimeRegistry;
        this.ledgerEngine = ledgerEngine;
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.personalAgentPreflightService = personalAgentPreflightService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
    }

    @Override
    public RuntimeV2EngineType engineType() {
        return RuntimeV2EngineType.GRAPH;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatRuntimePreparedRequest prepared, Long userId) {
        return Flux.defer(() -> {
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
            Schedulers.boundedElastic().schedule(() -> executeGraphStream(prepared, userId, sink));
            return sink.asFlux();
        });
    }

    private void executeGraphStream(
            ChatRuntimePreparedRequest prepared, Long userId, Sinks.Many<ServerSentEvent<String>> sink) {
        RuntimeV2ExecutionSession executionSession = null;
        RuntimeV2State runtimeState = null;
        String runtimeContextKey = "";
        boolean metaEmitted = false;
        long runStartedAtMillis = 0L;
        try {
            ChatRuntimePreparedRequest resolvedPrepared =
                    executionSessionFactory.resolvePreparedRequest(prepared, userId);
            String quotaError = resolvedPrepared == null
                    ? null
                    : userTokenQuotaService.validateQuota(userId, resolvedPrepared.sessionType());
            if (StringUtils.hasText(quotaError)) {
                persistTerminalFailureResponse(resolvedPrepared, userId, quotaError, sink);
                return;
            }
            executionSession = executionSessionFactory.createResolved(resolvedPrepared, userId);
            RuntimeV2ExecutionSession session = executionSession;
            ChatRuntimePreparedRequest preparedWithTools = executionSession.preparedWithTools();
            PersonalAgentPreflightService.PreflightResult preflightResult = personalAgentPreflightService.prepare(
                    session.context(), executionSession.runContext(), preparedWithTools);
            if (preflightResult.terminal()) {
                emitRealtimeGraphEvent(
                        sink, RuntimeV2GraphEvent.meta(conversationHistoryService.buildMetaPayload(session.context())));
                metaEmitted = true;
                emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.message(preflightResult.responseMessage()));
                emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
                return;
            }
            ModelRuntimeClientFactory.ChatRuntimeBundle streamBundle =
                    modelRuntimeClientFactory.createChatBundleWithoutDefaultSystem(preparedWithTools.chatModelId());
            ModelRuntimeClientFactory.ChatRuntimeBundle decisionBundle =
                    modelRuntimeClientFactory.createChatBundleWithoutDefaultSystem(preparedWithTools.chatModelId());
            runtimeState = new RuntimeV2State(
                    preparedWithTools,
                    userId,
                    executionSession.context(),
                    preparedWithTools.toolCallbacks(),
                    executionSession.requestSkillKit(),
                    streamBundle.config());
            final RuntimeV2State activeRuntimeState = runtimeState;
            if (executionSession.runContext() != null) {
                runtimeState.setRunId(executionSession.runContext().runId());
                runtimeState.setRunCode(executionSession.runContext().runCode());
                runtimeState.setRunType(executionSession.runContext().runType());
            }
            ledgerEngine.refresh(runtimeState);
            List<Message> initialMessages = executionSession.context() == null
                    ? List.of()
                    : conversationEventService.buildSpringHistoryMessages(
                            executionSession.context().sessionCode(),
                            executionSession.context().userMessageId());
            runtimeState.replaceMessages(initialMessages);
            RuntimeV2Mode mode = RuntimeV2RequestHints.resolveMode(preparedWithTools);
            runtimeState.setMode(mode);
            runtimeContextKey = resolveRuntimeContextKey(executionSession.context(), preparedWithTools);
            runStartedAtMillis = System.currentTimeMillis();
            markRunStarted(runtimeState, runStartedAtMillis);

            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.meta(buildMetaPayload(session, runtimeState)));
            metaEmitted = true;

            runtimeRegistry.register(
                    runtimeContextKey,
                    runtimeState,
                    streamBundle.chatClient(),
                    decisionBundle.chatClient(),
                    executionSession.executionReadyToolIndex(),
                    new RuntimeV2CodeStageRuntime(runtimeState, streamBundle.chatClient()),
                    event -> emitRealtimeGraphEvent(sink, event));
            if (StringUtils.hasText(runtimeState.runCode())) {
                runtimeRegistry.register(
                        runtimeState.runCode(),
                        runtimeState,
                        streamBundle.chatClient(),
                        decisionBundle.chatClient(),
                        executionSession.executionReadyToolIndex(),
                        new RuntimeV2CodeStageRuntime(runtimeState, streamBundle.chatClient()),
                        event -> emitRealtimeGraphEvent(sink, event));
            }
            String effectiveSessionId = resolveEffectiveSessionId(executionSession.context(), preparedWithTools);
            RuntimeV2GraphSeed seed = new RuntimeV2GraphSeed(
                    effectiveSessionId,
                    userId,
                    preparedWithTools == null ? "" : preparedWithTools.userMessage(),
                    preparedWithTools == null ? "" : preparedWithTools.paramsJson(),
                    preparedWithTools == null ? "" : preparedWithTools.fileListJson(),
                    RuntimeV2RequestHints.readExecutionModeHint(
                            preparedWithTools == null ? null : preparedWithTools.paramsJson()),
                    mode,
                    0,
                    initialMessages,
                    extractToolNames(preparedWithTools),
                    runtimeContextKey);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(resolveGraphThreadId(executionSession.context(), preparedWithTools))
                    .streamMode(CompiledGraph.StreamMode.SNAPSHOTS)
                    .build();
            AtomicReference<OverAllState> latestStateRef = new AtomicReference<>();
            AtomicReference<String> announcedCodeStatus = new AtomicReference<>("");
            AtomicReference<String> announcedPhase = new AtomicReference<>("");

            compiledGraph()
                    .streamSnapshots(seed.toStateMap(), runnableConfig)
                    .doOnNext(nodeOutput -> {
                        throwIfCancellationRequested(activeRuntimeState);
                        latestStateRef.set(nodeOutput == null ? null : nodeOutput.state());
                        syncRuntimeState(activeRuntimeState, nodeOutput);
                    })
                    .filter(nodeOutput -> nodeOutput != null && !nodeOutput.isSTART() && !nodeOutput.isEND())
                    .doOnNext(nodeOutput -> buildNodeEvents(
                                    activeRuntimeState, nodeOutput, announcedCodeStatus, announcedPhase)
                            .forEach(event -> emitRealtimeGraphEvent(sink, event)))
                    .blockLast();

            throwIfCancellationRequested(activeRuntimeState);

            OverAllState latestState = latestStateRef.get();
            syncRuntimeState(activeRuntimeState, latestState);
            String answer = extractDraftAnswer(latestState);
            emitTerminalAnswerIfNeeded(runtimeState, answer, sink);
            runtimeState.setFinalAnswer(answer);
            if (runtimeState.finishReason() == null) {
                runtimeState.setFinishReason(RuntimeV2FinishReason.COMPLETED);
            }
            if (runtimeState.finishReason() == RuntimeV2FinishReason.WAITING_APPROVAL) {
                Map<String, Object> stateSnapshot = latestState == null ? Map.of() : buildStateSnapshot(latestState);
                Map<String, Object> waitingStateSnapshot =
                        mergeStateSnapshotWithRuntimeState(stateSnapshot, runtimeState, answer);
                persistWaitingApproval(
                        session, runtimeState, answer, preparedWithTools, waitingStateSnapshot, runStartedAtMillis);
                log.info(
                        "Runtime V2 GRAPH 等待人工审批：sessionId={}, runCode={}", effectiveSessionId, runtimeState.runCode());
                emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
                return;
            }
            emitGraphPhase(runtimeState, RuntimeV2Phase.COMPLETED, announcedPhase, sink);
            Map<String, Object> stateSnapshot = latestState == null ? Map.of() : buildStateSnapshot(latestState);
            Map<String, Object> completedStateSnapshot =
                    mergeStateSnapshotWithRuntimeState(stateSnapshot, runtimeState, answer);
            persistSuccess(
                    session, runtimeState, answer, preparedWithTools, completedStateSnapshot, runStartedAtMillis);
            log.info(
                    "Runtime V2 GRAPH 执行完成：sessionId={}, mode={}, nodes={}",
                    effectiveSessionId,
                    mode,
                    graphBuilder.nodeNames());
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
        } catch (CancellationException ex) {
            runtimeRegistry.unregister(runtimeContextKey);
            if (runtimeState != null && StringUtils.hasText(runtimeState.runCode())) {
                runtimeRegistry.unregister(runtimeState.runCode());
            }
            if (executionSession != null && executionSession.context() != null && !metaEmitted) {
                emitRealtimeGraphEvent(
                        sink, RuntimeV2GraphEvent.meta(buildMetaPayload(executionSession, runtimeState)));
            }
            if (runtimeState != null) {
                runtimeState.setFinishReason(RuntimeV2FinishReason.CANCELLED);
                emitGraphPhase(
                        runtimeState,
                        RuntimeV2Phase.CANCELLED,
                        new AtomicReference<>(
                                runtimeState.phase() == null
                                        ? ""
                                        : runtimeState.phase().name()),
                        sink);
            }
            String cancelMessage = resolveCancellationMessage(runtimeState);
            if (executionSession != null) {
                persistCancelled(executionSession, runtimeState, prepared, cancelMessage, runStartedAtMillis);
            }
            log.info(
                    "Runtime V2 GRAPH 执行已终止：sessionId={}, runCode={}",
                    prepared == null ? "" : prepared.sessionId(),
                    runtimeState == null ? "" : runtimeState.runCode());
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.message(cancelMessage));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
        } catch (Exception ex) {
            runtimeRegistry.unregister(runtimeContextKey);
            if (runtimeState != null && StringUtils.hasText(runtimeState.runCode())) {
                runtimeRegistry.unregister(runtimeState.runCode());
            }
            if (executionSession != null && executionSession.context() != null && !metaEmitted) {
                emitRealtimeGraphEvent(
                        sink, RuntimeV2GraphEvent.meta(buildMetaPayload(executionSession, runtimeState)));
            }
            String friendlyMessage =
                    StringUtils.hasText(ex.getMessage()) ? ex.getMessage().trim() : "运行失败，请稍后重试";
            if (executionSession != null) {
                if (runtimeState != null) {
                    runtimeState.setFinishReason(RuntimeV2FinishReason.TOOL_ERROR);
                    emitGraphPhase(
                            runtimeState,
                            RuntimeV2Phase.FAILED,
                            new AtomicReference<>(
                                    runtimeState.phase() == null
                                            ? ""
                                            : runtimeState.phase().name()),
                            sink);
                }
                persistFailure(executionSession, runtimeState, prepared, friendlyMessage, runStartedAtMillis);
            }
            log.warn(
                    "Runtime V2 GRAPH 执行失败：sessionId={}, error={}",
                    prepared == null ? "" : prepared.sessionId(),
                    friendlyMessage,
                    ex);
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.message(friendlyMessage));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.error(friendlyMessage));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
        } finally {
            runtimeRegistry.unregister(runtimeContextKey);
            if (runtimeState != null
                    && StringUtils.hasText(runtimeState.runCode())
                    && runtimeState.finishReason() != RuntimeV2FinishReason.WAITING_APPROVAL) {
                runtimeRegistry.unregister(runtimeState.runCode());
            }
            sink.tryEmitComplete();
        }
    }

    private void emitTerminalAnswerIfNeeded(
            RuntimeV2State runtimeState, String draftAnswer, Sinks.Many<ServerSentEvent<String>> sink) {
        String answer = fallbackAnswer(runtimeState, draftAnswer);
        if (!StringUtils.hasText(answer) || hasTerminalAnswerAlreadyStreamed(runtimeState, answer)) {
            return;
        }
        if (runtimeState != null) {
            eventPersistenceCapability.appendTextSegment(runtimeState.timelineSegments(), answer);
        }
        emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.message(answer));
    }

    private CompiledGraph compiledGraph() throws Exception {
        CompiledGraph current = compiledGraph;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (compiledGraph == null) {
                compiledGraph = graphBuilder.buildGraph();
            }
            return compiledGraph;
        }
    }

    private List<String> extractToolNames(ChatRuntimePreparedRequest prepared) {
        if (prepared == null
                || prepared.toolCallbacks() == null
                || prepared.toolCallbacks().isEmpty()) {
            return List.of();
        }
        return prepared.toolCallbacks().stream()
                .map(ToolCallback::getToolDefinition)
                .filter(java.util.Objects::nonNull)
                .map(definition ->
                        definition.name() == null ? "" : definition.name().trim())
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildStateSnapshot(OverAllState state) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("mode", state.value(RuntimeV2GraphStateKeys.MODE, ""));
        snapshot.put("phase", state.value(RuntimeV2GraphStateKeys.PHASE, ""));
        snapshot.put("route", state.value(RuntimeV2GraphStateKeys.ROUTE, ""));
        snapshot.put("iterationCount", state.value(RuntimeV2GraphStateKeys.ITERATION_COUNT, 0));
        snapshot.put("llmCallCount", state.value(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0));
        snapshot.put("toolCallCount", state.value(RuntimeV2GraphStateKeys.TOOL_CALL_COUNT, 0));
        snapshot.put("finishReason", state.value(RuntimeV2GraphStateKeys.FINISH_REASON, ""));
        snapshot.put(
                "terminalAnswerStreamed", state.value(RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED, Boolean.FALSE));
        snapshot.put(
                "lastDecision",
                state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.LAST_DECISION)
                        .orElse(Map.of()));
        snapshot.put(
                "lastToolResult",
                state.value(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT).orElse(Map.of()));
        snapshot.put(
                "toolState",
                state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.TOOL_STATE)
                        .orElse(Map.of()));
        snapshot.put(
                "documentState",
                state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.DOCUMENT_STATE)
                        .orElse(Map.of()));
        snapshot.put(
                "codeState",
                state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                        .orElse(Map.of()));
        snapshot.put(
                "completionState",
                state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.COMPLETION_STATE)
                        .orElse(Map.of()));
        snapshot.put(
                "observationTraceSize",
                state.<List<Map<String, Object>>>value(RuntimeV2GraphStateKeys.OBSERVATION_TRACE)
                        .orElse(List.of())
                        .size());
        return snapshot;
    }

    private List<RuntimeV2GraphEvent> buildNodeEvents(
            RuntimeV2State runtimeState,
            NodeOutput nodeOutput,
            AtomicReference<String> announcedCodeStatus,
            AtomicReference<String> announcedPhase) {
        List<RuntimeV2GraphEvent> events = new java.util.ArrayList<>();
        appendSnapshotPhaseEvent(events, runtimeState, nodeOutput, announcedPhase);
        appendCodeStageMessage(events, runtimeState, nodeOutput, announcedCodeStatus);
        return events;
    }

    private void appendSnapshotPhaseEvent(
            List<RuntimeV2GraphEvent> events,
            RuntimeV2State runtimeState,
            NodeOutput nodeOutput,
            AtomicReference<String> announcedPhase) {
        if (events == null || nodeOutput == null || nodeOutput.state() == null || announcedPhase == null) {
            return;
        }
        RuntimeV2Phase nextPhase = resolvePhase(nodeOutput.state().value(RuntimeV2GraphStateKeys.PHASE, ""));
        if (nextPhase == null || nextPhase == RuntimeV2Phase.COMPLETED) {
            return;
        }
        String phaseName = nextPhase.name();
        if (phaseName.equals(announcedPhase.get())) {
            return;
        }
        announcedPhase.set(phaseName);
        recordRuntimePhase(runtimeState, nextPhase);
        events.add(RuntimeV2GraphEvent.phase(buildPhasePayload(runtimeState, nextPhase)));
    }

    private void appendCodeStageMessage(
            List<RuntimeV2GraphEvent> events,
            RuntimeV2State runtimeState,
            NodeOutput nodeOutput,
            AtomicReference<String> announcedCodeStatus) {
        if (events == null || nodeOutput == null || nodeOutput.state() == null || announcedCodeStatus == null) {
            return;
        }
        Map<String, Object> codeState = nodeOutput
                .state()
                .<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE)
                .orElse(Map.of());
        String status = normalizeText(String.valueOf(codeState.getOrDefault("status", "")));
        if (!StringUtils.hasText(status) || status.equals(announcedCodeStatus.get())) {
            return;
        }
        String message = resolveCodeStageMessage(status);
        if (!StringUtils.hasText(message)) {
            return;
        }
        announcedCodeStatus.set(status);
        if (runtimeState != null) {
            eventPersistenceCapability.appendTextSegment(runtimeState.timelineSegments(), message);
        }
        events.addAll(toGraphContentDeltaEvents(message));
    }

    private List<RuntimeV2GraphEvent> toGraphContentDeltaEvents(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(RuntimeV2GraphEvent.contentDelta(text));
    }

    private String resolveCodeStageMessage(String status) {
        return switch (status) {
            case "CODE_PLAN_PREPARED" -> "已确定处理步骤，开始生成处理文件。";
            case "CODE_SCRIPT_READY" -> "处理文件已生成，开始执行。";
            case "CODE_OUTPUT_READY" -> "处理已完成，开始整理结果。";
            case "CODE_ARTIFACT_READY" -> "结果文件已生成。";
            case "CODE_SCRIPT_WRITE_FAILED" -> "处理文件写入受阻，正在自动调整。";
            case "CODE_RUN_FAILED" -> "执行结果不符合预期，正在自动修正。";
            case "CODE_ARTIFACT_WRITE_FAILED" -> "结果发布受阻，正在重试。";
            default -> "";
        };
    }

    private String extractFinalAnswer(OverAllState state) {
        if (state == null) {
            return "Runtime V2 graph 未返回最终结果。";
        }
        return state.<String>value(RuntimeV2GraphStateKeys.FINAL_ANSWER)
                .filter(StringUtils::hasText)
                .orElse("Runtime V2 graph 未返回最终结果。");
    }

    private String extractDraftAnswer(OverAllState state) {
        if (state == null) {
            return "";
        }
        String finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        if (StringUtils.hasText(finalAnswer)) {
            return finalAnswer.trim();
        }
        String draftAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        return draftAnswer == null ? "" : draftAnswer.trim();
    }

    private String fallbackAnswer(RuntimeV2State runtimeState, String draftAnswer) {
        if (StringUtils.hasText(draftAnswer)) {
            return draftAnswer.trim();
        }
        if (runtimeState != null && StringUtils.hasText(runtimeState.finalAnswer())) {
            return runtimeState.finalAnswer().trim();
        }
        return "未生成有效回答。";
    }

    private Map<String, Object> mergeStateSnapshotWithRuntimeState(
            Map<String, Object> stateSnapshot, RuntimeV2State runtimeState, String answer) {
        Map<String, Object> merged = new LinkedHashMap<>(stateSnapshot == null ? Map.of() : stateSnapshot);
        if (runtimeState == null) {
            return merged;
        }
        merged.put(
                "mode", runtimeState.mode() == null ? "" : runtimeState.mode().name());
        merged.put(
                "phase",
                runtimeState.phase() == null ? "" : runtimeState.phase().name());
        merged.put(
                "finishReason",
                runtimeState.finishReason() == null
                        ? ""
                        : runtimeState.finishReason().name());
        merged.put("iterationCount", runtimeState.iterationCount());
        merged.put("llmCallCount", runtimeState.llmCallCount());
        merged.put("toolCallCount", runtimeState.toolCallCount());
        merged.put("observationTraceSize", runtimeState.observationTrace().size());
        merged.put(
                "lastDecision",
                merged.getOrDefault("lastDecision", Map.of("type", "final", "source", "graph-final-stream")));
        merged.put("terminalAnswerStreamed", runtimeState.terminalAnswerStreamed());
        if (StringUtils.hasText(answer)) {
            merged.put("finalAnswer", answer.trim());
        }
        return merged;
    }

    private String resolveGraphThreadId(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        String sessionId = resolveEffectiveSessionId(context, prepared);
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return "runtime-v2-graph-" + System.currentTimeMillis();
    }

    private String resolveRuntimeContextKey(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        return resolveGraphThreadId(context, prepared);
    }

    private String resolveEffectiveSessionId(
            ConversationHistoryService.ConversationContext context, ChatRuntimePreparedRequest prepared) {
        if (context != null && StringUtils.hasText(context.sessionCode())) {
            return context.sessionCode().trim();
        }
        if (prepared != null && StringUtils.hasText(prepared.sessionId())) {
            return prepared.sessionId().trim();
        }
        return "";
    }

    private void persistSuccess(
            RuntimeV2ExecutionSession executionSession,
            RuntimeV2State runtimeState,
            String answer,
            ChatRuntimePreparedRequest preparedWithTools,
            Map<String, Object> stateSnapshot,
            long runStartedAtMillis) {
        if (executionSession == null || executionSession.context() == null) {
            return;
        }
        String paramsJson = buildGraphParamsJson(runtimeState, preparedWithTools, stateSnapshot, null, false);
        String segmentsJson = buildSegmentsJson(runtimeState, answer);
        conversationHistoryService.completeMessage(
                executionSession.context(),
                answer,
                segmentsJson,
                null,
                preparedWithTools == null ? null : preparedWithTools.fileListJson(),
                paramsJson,
                0L,
                buildUsagePayload(runtimeState));
        conversationEventService.upsertAssistantMessage(
                executionSession.context(),
                answer,
                segmentsJson,
                preparedWithTools == null ? null : preparedWithTools.messageType(),
                paramsJson,
                null);
        if (executionSession.runContext() != null
                && executionSession.runContext().runId() != null) {
            conversationRunService.succeedRun(
                    executionSession.runContext().runId(),
                    executionSession.context().assistantMessageId(),
                    paramsJson);
        }
        finalizeRunUsage(
                runtimeState, preparedWithTools, ConversationRunConstants.STATUS_SUCCEEDED, runStartedAtMillis);
        conversationContextWindowService.compactIfNeeded(executionSession.context());
    }

    private void persistFailure(
            RuntimeV2ExecutionSession executionSession,
            RuntimeV2State runtimeState,
            ChatRuntimePreparedRequest prepared,
            String errorMessage,
            long runStartedAtMillis) {
        if (executionSession == null || executionSession.context() == null) {
            return;
        }
        String answer = runtimeState != null && StringUtils.hasText(runtimeState.finalAnswer())
                ? runtimeState.finalAnswer()
                : errorMessage;
        String paramsJson = buildGraphParamsJson(runtimeState, prepared, Map.of(), normalizeText(errorMessage), true);
        String segmentsJson = buildSegmentsJson(runtimeState, answer);
        conversationHistoryService.failMessage(
                executionSession.context(),
                errorMessage,
                answer,
                segmentsJson,
                paramsJson,
                0L,
                buildUsagePayload(runtimeState));
        conversationEventService.upsertAssistantMessage(
                executionSession.context(),
                answer,
                segmentsJson,
                prepared == null ? null : prepared.messageType(),
                paramsJson,
                null);
        if (executionSession.runContext() != null
                && executionSession.runContext().runId() != null) {
            conversationRunService.failRun(
                    executionSession.runContext().runId(),
                    executionSession.context().assistantMessageId(),
                    "GRAPH_FAILED",
                    errorMessage,
                    paramsJson);
        }
        finalizeRunUsage(runtimeState, prepared, ConversationRunConstants.STATUS_FAILED, runStartedAtMillis);
        conversationContextWindowService.compactIfNeeded(executionSession.context());
    }

    private void persistCancelled(
            RuntimeV2ExecutionSession executionSession,
            RuntimeV2State runtimeState,
            ChatRuntimePreparedRequest prepared,
            String cancelMessage,
            long runStartedAtMillis) {
        if (executionSession == null || executionSession.context() == null) {
            return;
        }
        String answer = runtimeState != null && StringUtils.hasText(runtimeState.finalAnswer())
                ? runtimeState.finalAnswer()
                : cancelMessage;
        String paramsJson = buildGraphParamsJson(
                runtimeState,
                prepared,
                Map.of("cancelled", Boolean.TRUE, "reason", cancelMessage),
                cancelMessage,
                false);
        String segmentsJson = buildSegmentsJson(runtimeState, answer);
        conversationHistoryService.interruptMessage(
                executionSession.context(), answer, segmentsJson, paramsJson, 0L, buildUsagePayload(runtimeState));
        conversationEventService.upsertAssistantMessage(
                executionSession.context(),
                answer,
                segmentsJson,
                prepared == null ? null : prepared.messageType(),
                paramsJson,
                null);
        if (executionSession.runContext() != null
                && executionSession.runContext().runId() != null) {
            conversationRunService.cancelRun(
                    executionSession.runContext().runId(),
                    executionSession.context().assistantMessageId(),
                    paramsJson);
        }
        finalizeRunUsage(runtimeState, prepared, ConversationRunConstants.STATUS_CANCELLED, runStartedAtMillis);
        conversationContextWindowService.compactIfNeeded(executionSession.context());
    }

    private void persistWaitingApproval(
            RuntimeV2ExecutionSession executionSession,
            RuntimeV2State runtimeState,
            String answer,
            ChatRuntimePreparedRequest preparedWithTools,
            Map<String, Object> stateSnapshot,
            long runStartedAtMillis) {
        if (executionSession == null || executionSession.context() == null) {
            return;
        }
        String paramsJson = buildGraphParamsJson(runtimeState, preparedWithTools, stateSnapshot, null, false);
        String segmentsJson = buildSegmentsJson(runtimeState, answer);
        conversationHistoryService.waitingApprovalMessage(
                executionSession.context(), answer, segmentsJson, paramsJson, 0L, buildUsagePayload(runtimeState));
        conversationEventService.upsertAssistantMessage(
                executionSession.context(),
                answer,
                segmentsJson,
                preparedWithTools == null ? null : preparedWithTools.messageType(),
                paramsJson,
                null);
        if (executionSession.runContext() != null
                && executionSession.runContext().runId() != null) {
            conversationRunService.updateRunningState(
                    executionSession.runContext().runId(),
                    ConversationRunConstants.STATUS_WAITING_APPROVAL,
                    runtimeState == null || runtimeState.phase() == null
                            ? null
                            : runtimeState.phase().name(),
                    "APPROVAL_REQUIRED",
                    answer,
                    resolveCurrentRuntimeSkillName(runtimeState),
                    paramsJson);
        }
        conversationContextWindowService.compactIfNeeded(executionSession.context());
    }

    private void persistTerminalFailureResponse(
            ChatRuntimePreparedRequest prepared,
            Long userId,
            String errorMessage,
            Sinks.Many<ServerSentEvent<String>> sink) {
        if (prepared == null || userId == null || userId <= 0) {
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.error(errorMessage));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
            return;
        }
        try {
            ConversationHistoryService.ConversationContext context = conversationHistoryService.startMessage(
                    userId,
                    prepared.sessionType(),
                    prepared.sessionId(),
                    prepared.scopeId(),
                    prepared.scopeDisplayName(),
                    prepared.message(),
                    prepared.messageType(),
                    prepared.message(),
                    prepared.questionType(),
                    prepared.paramsJson(),
                    prepared.fileListJson(),
                    prepared.chatModelId());
            conversationHistoryService.failMessage(
                    context, errorMessage, errorMessage, prepared.paramsJson(), 0L, null);
            emitRealtimeGraphEvent(
                    sink, RuntimeV2GraphEvent.meta(conversationHistoryService.buildMetaPayload(context)));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.message(errorMessage));
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.done());
        } catch (Exception ex) {
            log.error("Runtime V2 GRAPH 终态失败回复持久化失败：error={}", ex.getMessage(), ex);
            emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.error(errorMessage));
        }
    }

    String buildGraphParamsJson(
            RuntimeV2State runtimeState,
            ChatRuntimePreparedRequest prepared,
            Map<String, Object> stateSnapshot,
            String errorMessage,
            boolean failed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        ChatRuntimePreparedRequest effectivePrepared =
                runtimeState != null && runtimeState.prepared() != null ? runtimeState.prepared() : prepared;
        String paramsJson = effectivePrepared == null ? null : effectivePrepared.paramsJson();
        if (StringUtils.hasText(paramsJson)) {
            try {
                Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
                if (parsed != null) {
                    payload.putAll(parsed);
                }
            } catch (Exception ignored) {
                payload.put("rawParamsJson", paramsJson);
            }
        }
        payload.put("runtimeVersion", "v2");
        payload.put("runtimeV2Engine", engineType().configValue());
        payload.put("graphRuntime", Boolean.TRUE);
        payload.put("graphState", stateSnapshot == null ? Map.of() : stateSnapshot);
        Object approvalPayload = extractApprovalPayload(stateSnapshot);
        if (approvalPayload != null) {
            payload.put("approval", approvalPayload);
        }
        if (runtimeState != null) {
            payload.put(
                    "mode",
                    runtimeState.mode() == null ? "" : runtimeState.mode().name());
            payload.put(
                    "phase",
                    runtimeState.phase() == null ? "" : runtimeState.phase().name());
            payload.put(
                    "finishReason",
                    runtimeState.finishReason() == null
                            ? ""
                            : runtimeState.finishReason().name());
            payload.put("iterationCount", runtimeState.iterationCount());
            payload.put("llmCallCount", runtimeState.llmCallCount());
            payload.put("toolCallCount", runtimeState.toolCallCount());
            payload.put("decisionRepairCount", runtimeState.decisionRepairCount());
            payload.put("toolEvents", List.copyOf(runtimeState.toolEvents()));
            payload.put("phaseTrace", List.copyOf(runtimeState.phaseTrace()));
            payload.put("observationTrace", List.copyOf(runtimeState.observationTrace()));
        }
        if (failed) {
            payload.put("status", "FAILED");
            payload.put("errorMessage", normalizeText(errorMessage));
        }
        if (runtimeState != null && runtimeState.cancellationRequested()) {
            payload.put("status", ConversationRunConstants.STATUS_CANCELLED);
            payload.put("cancelReason", resolveCancellationMessage(runtimeState));
        }
        return mergeGraphSkillStateParams(payload, runtimeState, effectivePrepared);
    }

    @SuppressWarnings("unchecked")
    private Object extractApprovalPayload(Map<String, Object> stateSnapshot) {
        if (stateSnapshot == null || stateSnapshot.isEmpty()) {
            return null;
        }
        Object lastToolResult = stateSnapshot.get("lastToolResult");
        if (lastToolResult instanceof Map<?, ?> payload) {
            Object approvalCode = payload.get("approvalCode");
            if (approvalCode != null && StringUtils.hasText(String.valueOf(approvalCode))) {
                return Map.copyOf((Map<String, Object>) payload);
            }
        }
        Object lastDecision = stateSnapshot.get("lastDecision");
        if (lastDecision instanceof Map<?, ?> payload) {
            Object type = payload.get("type");
            Object approvalCode = payload.get("approvalCode");
            if ("approval".equalsIgnoreCase(String.valueOf(type))
                    && approvalCode != null
                    && StringUtils.hasText(String.valueOf(approvalCode))) {
                return Map.copyOf((Map<String, Object>) payload);
            }
        }
        return null;
    }

    private String mergeGraphSkillStateParams(
            Map<String, Object> payload, RuntimeV2State runtimeState, ChatRuntimePreparedRequest prepared) {
        String merged = JSON.toJSONString(payload == null ? Map.of() : payload);
        if (prepared == null) {
            return merged;
        }
        List<RuntimeLoadedSkill> loadedSkills = requestScopedSkillRuntimeService.extractLoadedSkills(
                runtimeState == null ? null : runtimeState.requestSkillKit(), prepared.availableSkills());
        if ((loadedSkills == null || loadedSkills.isEmpty())
                && prepared.loadedSkills() != null
                && !prepared.loadedSkills().isEmpty()) {
            loadedSkills = prepared.loadedSkills();
        }
        String currentRuntimeSkillName = requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(
                runtimeState == null ? null : runtimeState.requestSkillKit(), prepared);
        return requestScopedSkillRuntimeService.mergeSkillStateParams(
                merged, prepared.availableSkills(), loadedSkills, currentRuntimeSkillName);
    }

    private String buildSegmentsJson(RuntimeV2State runtimeState, String answer) {
        if (runtimeState == null) {
            return null;
        }
        List<Map<String, Object>> segments = new java.util.ArrayList<>(runtimeState.timelineSegments());
        if (!hasTerminalAnswerAlreadyStreamed(runtimeState, answer)) {
            eventPersistenceCapability.appendTextSegment(segments, answer);
        }
        return eventPersistenceCapability.toSegmentsJson(segments);
    }

    private boolean hasTerminalAnswerAlreadyStreamed(RuntimeV2State runtimeState, String answer) {
        if (runtimeState == null || !StringUtils.hasText(answer)) {
            return false;
        }
        String normalizedAnswer = answer.trim();
        if (normalizedAnswer.isEmpty()) {
            return false;
        }
        String streamedTerminalAnswer = runtimeState.terminalAnswerStreamText().trim();
        if (runtimeState.terminalAnswerStreamed() && StringUtils.hasText(streamedTerminalAnswer)) {
            return streamedTerminalAnswer.endsWith(normalizedAnswer);
        }
        StringBuilder streamedText = new StringBuilder();
        for (Map<String, Object> segment : runtimeState.timelineSegments()) {
            if (segment == null) {
                continue;
            }
            Object type = segment.get("type");
            if (!"text".equals(type)) {
                continue;
            }
            Object text = segment.get("text");
            if (text != null) {
                streamedText.append(text);
            }
        }
        String aggregated = streamedText.toString().trim();
        return StringUtils.hasText(aggregated) && aggregated.endsWith(normalizedAnswer);
    }

    private ConversationMessageUsagePayload buildUsagePayload(RuntimeV2State runtimeState) {
        if (runtimeState == null) {
            return null;
        }
        ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig = runtimeState.modelConfig();
        String usageSummaryJson = JSON.toJSONString(Map.of(
                "runtimeVersion", "v2",
                "runtimeV2Engine", engineType().configValue(),
                "llmCallCount", runtimeState.llmCallCount(),
                "toolCallCount", runtimeState.toolCallCount()));
        boolean usageAvailable = runtimeState.promptTokens() != null
                || runtimeState.completionTokens() != null
                || runtimeState.totalTokens() != null;
        return new ConversationMessageUsagePayload(
                runtimeState.promptTokens(),
                runtimeState.completionTokens(),
                runtimeState.totalTokens(),
                usageAvailable,
                runtimeState.llmCallCount(),
                runtimeState.toolCallCount(),
                modelConfig == null ? null : modelConfig.modelId(),
                modelConfig == null ? null : modelConfig.provider(),
                modelConfig == null ? null : modelConfig.displayName(),
                modelConfig == null ? null : modelConfig.adapterType(),
                usageSummaryJson);
    }

    private void syncRuntimeState(RuntimeV2State runtimeState, NodeOutput nodeOutput) {
        if (runtimeState == null || nodeOutput == null || nodeOutput.state() == null) {
            return;
        }
        RuntimeV2GraphStateProjector.syncRuntimeState(runtimeState, nodeOutput.state());
    }

    private void syncRuntimeState(RuntimeV2State runtimeState, OverAllState state) {
        RuntimeV2GraphStateProjector.syncRuntimeState(runtimeState, state);
    }

    private void emitGraphPhase(
            RuntimeV2State runtimeState,
            RuntimeV2Phase phase,
            AtomicReference<String> announcedPhase,
            Sinks.Many<ServerSentEvent<String>> sink) {
        if (runtimeState == null || phase == null || sink == null || announcedPhase == null) {
            return;
        }
        String phaseName = phase.name();
        if (phaseName.equals(announcedPhase.get())) {
            return;
        }
        announcedPhase.set(phaseName);
        recordRuntimePhase(runtimeState, phase);
        emitRealtimeGraphEvent(sink, RuntimeV2GraphEvent.phase(buildPhasePayload(runtimeState, phase)));
    }

    private void recordRuntimePhase(RuntimeV2State runtimeState, RuntimeV2Phase phase) {
        if (runtimeState == null || phase == null) {
            return;
        }
        runtimeState.setPhase(phase);
        updateRunState(runtimeState, resolveRunStatusForPhase(phase));
        String lastRecordedPhase = runtimeState.phaseTrace().isEmpty()
                ? ""
                : normalizeText(String.valueOf(runtimeState
                        .phaseTrace()
                        .get(runtimeState.phaseTrace().size() - 1)
                        .get("phase")));
        if (phase.name().equals(lastRecordedPhase)) {
            return;
        }
        Map<String, Object> phaseEvent = new LinkedHashMap<>();
        phaseEvent.put("phase", phase.name());
        phaseEvent.put("at", Instant.now().toString());
        runtimeState.phaseTrace().add(phaseEvent);
    }

    private Map<String, Object> buildPhasePayload(RuntimeV2State runtimeState, RuntimeV2Phase phase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "sessionId",
                runtimeState == null || runtimeState.conversation() == null
                        ? ""
                        : normalizeText(runtimeState.conversation().sessionCode()));
        payload.put("runId", runtimeState == null ? null : runtimeState.runId());
        payload.put("runCode", runtimeState == null ? "" : normalizeText(runtimeState.runCode()));
        payload.put("runType", runtimeState == null ? "" : normalizeText(runtimeState.runType()));
        payload.put("phase", phase == null ? "" : phase.name());
        payload.put(
                "mode",
                runtimeState == null || runtimeState.mode() == null
                        ? ""
                        : runtimeState.mode().name());
        payload.put("iterationCount", runtimeState == null ? 0 : runtimeState.iterationCount());
        payload.put("llmCallCount", runtimeState == null ? 0 : runtimeState.llmCallCount());
        payload.put("toolCallCount", runtimeState == null ? 0 : runtimeState.toolCallCount());
        payload.put("decisionRepairCount", runtimeState == null ? 0 : runtimeState.decisionRepairCount());
        payload.put(
                "finishReason",
                runtimeState == null || runtimeState.finishReason() == null
                        ? ""
                        : runtimeState.finishReason().name());
        payload.put("at", Instant.now().toString());
        return payload;
    }

    private Map<String, Object> buildMetaPayload(
            RuntimeV2ExecutionSession executionSession, RuntimeV2State runtimeState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (executionSession != null && executionSession.context() != null) {
            payload.putAll(conversationHistoryService.buildMetaPayload(executionSession.context()));
        }
        if (runtimeState != null) {
            payload.put("runId", runtimeState.runId());
            payload.put("runCode", normalizeText(runtimeState.runCode()));
            payload.put("runType", normalizeText(runtimeState.runType()));
            payload.put(
                    "mode",
                    runtimeState.mode() == null ? "" : runtimeState.mode().name());
            payload.put(
                    "phase",
                    runtimeState.phase() == null ? "" : runtimeState.phase().name());
            payload.put(
                    "finishReason",
                    runtimeState.finishReason() == null
                            ? ""
                            : runtimeState.finishReason().name());
        }
        return payload;
    }

    private void markRunStarted(RuntimeV2State runtimeState, long runStartedAtMillis) {
        if (runtimeState == null) {
            return;
        }
        updateRunState(runtimeState, ConversationRunConstants.STATUS_RUNNING);
        if (runtimeState.conversation() == null
                || runtimeState.prepared() == null
                || runtimeState.modelConfig() == null) {
            return;
        }
        conversationRunUsageService.ensureRunningRecord(
                runtimeState.conversation(),
                runtimeState.prepared(),
                runtimeState.modelConfig().modelId(),
                runtimeState.modelConfig().provider(),
                runtimeState.modelConfig().model(),
                runtimeState.modelConfig().adapterType(),
                runStartedAtMillis);
    }

    private void updateRunState(RuntimeV2State runtimeState, String status) {
        if (runtimeState == null || runtimeState.runId() == null || runtimeState.runId() <= 0) {
            return;
        }
        conversationRunService.updateRunningState(
                runtimeState.runId(),
                status,
                runtimeState.phase() == null ? null : runtimeState.phase().name(),
                runtimeState.phaseSubStage(),
                resolveCurrentTask(runtimeState),
                resolveCurrentRuntimeSkillName(runtimeState),
                buildRunContextJson(runtimeState));
    }

    private String resolveCurrentTask(RuntimeV2State runtimeState) {
        if (runtimeState == null) {
            return null;
        }
        if (StringUtils.hasText(runtimeState.phaseProgressMessage())) {
            return runtimeState.phaseProgressMessage();
        }
        return runtimeState.phase() == null ? null : runtimeState.phase().name();
    }

    private String resolveCurrentRuntimeSkillName(RuntimeV2State runtimeState) {
        if (runtimeState == null || runtimeState.prepared() == null) {
            return null;
        }
        return normalizeText(runtimeState.prepared().runtimeSkillName());
    }

    private String resolveRunStatusForPhase(RuntimeV2Phase phase) {
        if (phase == null) {
            return ConversationRunConstants.STATUS_RUNNING;
        }
        return switch (phase) {
            case COMPLETED -> ConversationRunConstants.STATUS_SUCCEEDED;
            case CANCELLED -> ConversationRunConstants.STATUS_CANCELLED;
            case FAILED -> ConversationRunConstants.STATUS_FAILED;
            default -> ConversationRunConstants.STATUS_RUNNING;
        };
    }

    private String buildRunContextJson(RuntimeV2State runtimeState) {
        if (runtimeState == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runtimeState.runId());
        payload.put("runCode", runtimeState.runCode());
        payload.put("runType", runtimeState.runType());
        payload.put("runtimeVersion", "v2");
        payload.put("runtimeV2Engine", engineType().configValue());
        payload.put("graphRuntime", Boolean.TRUE);
        payload.put(
                "mode", runtimeState.mode() == null ? "" : runtimeState.mode().name());
        payload.put(
                "phase",
                runtimeState.phase() == null ? "" : runtimeState.phase().name());
        payload.put("subStage", runtimeState.phaseSubStage());
        payload.put("subStageLabel", runtimeState.phaseSubStageLabel());
        payload.put("progressMessage", runtimeState.phaseProgressMessage());
        payload.put(
                "finishReason",
                runtimeState.finishReason() == null
                        ? ""
                        : runtimeState.finishReason().name());
        payload.put("iterationCount", runtimeState.iterationCount());
        payload.put("llmCallCount", runtimeState.llmCallCount());
        payload.put("toolCallCount", runtimeState.toolCallCount());
        payload.put("decisionRepairCount", runtimeState.decisionRepairCount());
        payload.put("currentRuntimeSkillName", resolveCurrentRuntimeSkillName(runtimeState));
        return JSON.toJSONString(payload);
    }

    private void finalizeRunUsage(
            RuntimeV2State runtimeState,
            ChatRuntimePreparedRequest prepared,
            String runStatus,
            long runStartedAtMillis) {
        if (runtimeState == null
                || runtimeState.conversation() == null
                || prepared == null
                || runStartedAtMillis <= 0) {
            return;
        }
        conversationRunUsageService.finalizeRun(
                runtimeState.conversation(),
                prepared,
                buildRunUsageSnapshot(runtimeState, runStatus, runStartedAtMillis, System.currentTimeMillis()));
    }

    static RuntimeRunUsageSnapshot buildRunUsageSnapshot(
            RuntimeV2State runtimeState, String runStatus, long startedAtMillis, long completedAtMillis) {
        if (runtimeState == null) {
            return null;
        }
        ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig = runtimeState.modelConfig();
        boolean usageAvailable = runtimeState.promptTokens() != null
                || runtimeState.completionTokens() != null
                || runtimeState.totalTokens() != null;
        long safeStartedAt = startedAtMillis <= 0 ? completedAtMillis : startedAtMillis;
        long safeCompletedAt = Math.max(completedAtMillis, safeStartedAt);
        return new RuntimeRunUsageSnapshot(
                StringUtils.hasText(runStatus) ? runStatus.trim() : "",
                usageAvailable,
                runtimeState.promptTokens(),
                runtimeState.completionTokens(),
                runtimeState.totalTokens(),
                runtimeState.llmCallCount(),
                runtimeState.toolCallCount(),
                Math.max(0L, safeCompletedAt - safeStartedAt),
                safeStartedAt,
                safeCompletedAt,
                modelConfig == null ? null : modelConfig.modelId(),
                modelConfig == null ? null : modelConfig.provider(),
                modelConfig == null ? null : modelConfig.model(),
                modelConfig == null ? null : modelConfig.adapterType(),
                List.of());
    }

    private RuntimeV2Mode resolveMode(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return null;
        }
        try {
            return RuntimeV2Mode.valueOf(rawMode.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private RuntimeV2Phase resolvePhase(String rawPhase) {
        if (!StringUtils.hasText(rawPhase)) {
            return null;
        }
        try {
            return RuntimeV2Phase.valueOf(rawPhase.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private RuntimeV2FinishReason resolveFinishReason(String rawFinishReason) {
        if (!StringUtils.hasText(rawFinishReason)) {
            return null;
        }
        try {
            return RuntimeV2FinishReason.valueOf(rawFinishReason.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void throwIfCancellationRequested(RuntimeV2State runtimeState) {
        if (runtimeState != null && runtimeState.cancellationRequested()) {
            throw new CancellationException(resolveCancellationMessage(runtimeState));
        }
    }

    private String resolveCancellationMessage(RuntimeV2State runtimeState) {
        if (runtimeState != null && StringUtils.hasText(runtimeState.cancellationReason())) {
            return runtimeState.cancellationReason().trim();
        }
        return "已终止本次执行。";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String ensureSentence(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.endsWith("。")
                || text.endsWith("！")
                || text.endsWith("？")
                || text.endsWith(".")
                || text.endsWith("!")
                || text.endsWith("?")) {
            return text;
        }
        return text + "。";
    }

    private void emitRealtimeGraphEvent(Sinks.Many<ServerSentEvent<String>> sink, ServerSentEvent<String> event) {
        if (sink == null || event == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            sink.emitNext(event, (signalType, emitResult) -> emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED);
        }
    }

    private void emitRealtimeGraphEvent(Sinks.Many<ServerSentEvent<String>> sink, RuntimeV2GraphEvent event) {
        emitRealtimeGraphEvent(sink, RuntimeV2GraphEventProjector.toServerSentEvent(event));
    }
}
