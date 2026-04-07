package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.attachment.AttachmentParseResult;
import lingzhou.agent.backend.business.chat.attachment.AttachmentParseService;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.capability.dataset.runtime.IntegrationDatasetAgentToolRegistry;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallbackResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

@Service
public class ChatConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ChatConversationService.class);

    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final ChatMemory chatMemory;
    private final ConversationHistoryService conversationHistoryService;
    private final ChatFileService chatFileService;
    private final AttachmentParseService attachmentParseService;
    private final SkillCatalogService skillCatalogService;
    private final IntegrationDatasetService integrationDatasetService;
    private final IntegrationDatasetAgentToolRegistry integrationDatasetAgentToolRegistry;
    private final SkillKit skillKit;

    public ChatConversationService(
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            ChatMemory chatMemory,
            ConversationHistoryService conversationHistoryService,
            ChatFileService chatFileService,
            AttachmentParseService attachmentParseService,
            SkillCatalogService skillCatalogService,
            IntegrationDatasetService integrationDatasetService,
            IntegrationDatasetAgentToolRegistry integrationDatasetAgentToolRegistry,
            SkillKit skillKit) {
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.chatMemory = chatMemory;
        this.conversationHistoryService = conversationHistoryService;
        this.chatFileService = chatFileService;
        this.attachmentParseService = attachmentParseService;
        this.skillCatalogService = skillCatalogService;
        this.integrationDatasetService = integrationDatasetService;
        this.integrationDatasetAgentToolRegistry = integrationDatasetAgentToolRegistry;
        this.skillKit = skillKit;
    }

    public Flux<ServerSentEvent<String>> streamGeneral(GeneralChatRequest request, Long userId) {
        if (!hasRequestContent(request == null ? null : request.message(), request == null ? null : request.fileIds())) {
            return Flux.just(errorEvent("message or file is required"));
        }
        String query = resolveQuery(
                request == null ? null : request.message(),
                request == null ? null : request.fileIds(),
                null);
        String fileListJson = chatFileService.buildFileListJson(request.fileIds());
        PreparedChat prepared = new PreparedChat(
                ConversationSessionType.GENERAL_CHAT,
                request.sessionId(),
                null,
                null,
                query,
                chatFileService.buildUserMessage(query.equals(normalizeMessage(request.message())) ? query : "", request.fileIds(), false),
                ConversationSessionType.GENERAL_CHAT.name(),
                JSON.toJSONString(Map.of("mode", "general")),
                fileListJson,
                List.of(),
                null,
                null);
        return streamPrepared(prepared, userId);
    }

    public Flux<ServerSentEvent<String>> streamSkill(SkillChatRequest request, Long userId) {
        if (!hasRequestContent(request == null ? null : request.message(), request == null ? null : request.fileIds())) {
            return Flux.just(errorEvent("message or file is required"));
        }
        try {
            SkillCatalogService.SkillChatContext context = skillCatalogService.resolveSkillChatContext(request.skillId());
            String query = resolveQuery(request.message(), request.fileIds(), context.runtimeSkillName());
            String fileListJson = chatFileService.buildFileListJson(request.fileIds());
            List<AttachmentParseResult> parsedAttachments = attachmentParseService.parseUploads(request.fileIds());
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("skillId", context.skillId());
            params.put("runtimeSkillName", context.runtimeSkillName());
            params.put("fileIds", request.fileIds() == null ? List.of() : request.fileIds());
            params.put("parsedAttachments", attachmentParseService.toSerializablePayload(parsedAttachments));
            String rawMessage = normalizeMessage(request.message());
            String userMessage = chatFileService.buildUserMessage(
                            rawMessage, request.fileIds(), context.readFileAvailable())
                    + attachmentParseService.buildPromptContext(parsedAttachments);

            PreparedChat prepared = new PreparedChat(
                    ConversationSessionType.SKILL_CHAT,
                    request.sessionId(),
                    context.skillId(),
                    context.displayName(),
                    query,
                    userMessage,
                    context.runtimeSkillName(),
                    JSON.toJSONString(params),
                    fileListJson,
                    context.toolCallbacks(),
                    context.systemPrompt(),
                    context.runtimeSkillName());
            return streamPrepared(prepared, userId);
        } catch (TaskException ex) {
            return Flux.just(errorEvent(ex.getMessage()));
        }
    }

    public Flux<ServerSentEvent<String>> streamDataset(Long datasetId, DatasetChatRequest request, Long userId) {
        if (!hasRequestContent(request == null ? null : request.message(), null)) {
            return Flux.just(errorEvent("message or file is required"));
        }
        try {
            IntegrationDatasetService.DatasetDetail dataset = integrationDatasetService.getDataset(datasetId);
            String rawMessage = normalizeMessage(request.message());
            String query = StringUtils.hasText(rawMessage) ? rawMessage : "请分析当前数据集并回答问题";
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("datasetId", dataset.id());
            params.put("datasetCode", dataset.datasetCode());
            params.put("datasetName", dataset.name());
            params.put("sourceKind", dataset.sourceKind());
            PreparedChat prepared = new PreparedChat(
                    ConversationSessionType.DATASET_CHAT,
                    request == null ? null : request.sessionId(),
                    dataset.id(),
                    dataset.name(),
                    query,
                    rawMessage,
                    ConversationSessionType.DATASET_CHAT.name(),
                    JSON.toJSONString(params),
                    null,
                    integrationDatasetAgentToolRegistry.buildCallbacks(dataset.id()),
                    buildDatasetSystemPrompt(dataset),
                    null);
            return streamPrepared(prepared, userId);
        } catch (TaskException ex) {
            return Flux.just(errorEvent(ex.getMessage()));
        }
    }


    public Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Flux<ServerSentEvent<String>> streamPrepared(PreparedChat prepared, Long userId) {
        ConversationHistoryService.ConversationContext context;
        try {
            context = conversationHistoryService.startMessage(
                    userId,
                    prepared.sessionType(),
                    prepared.sessionId(),
                    prepared.scopeId(),
                    prepared.scopeDisplayName(),
                    prepared.query(),
                    prepared.query(),
                    prepared.questionType(),
                    prepared.paramsJson(),
                    prepared.fileListJson());
        } catch (Exception ex) {
            logger.error("初始化会话消息失败：error={}", ex.getMessage(), ex);
            return Flux.just(errorEvent("会话初始化失败，请稍后重试"));
        }

        logSkillContextStats(prepared);
        Flux<ServerSentEvent<String>> meta = Flux.just(metaEvent(conversationHistoryService.buildMetaPayload(context)));
        if (prepared.sessionType() == ConversationSessionType.SKILL_CHAT
                || prepared.sessionType() == ConversationSessionType.DATASET_CHAT) {
            return meta.concatWith(buildSkillStreamingResponse(context, prepared));
        }

        ModelRuntimeClientFactory.ChatRuntimeBundle chatRuntimeBundle = modelRuntimeClientFactory.createChatBundle();
        AtomicReference<String> last = new AtomicReference<>("");
        AtomicBoolean finalized = new AtomicBoolean(false);
        long startedAt = System.currentTimeMillis();
        Flux<ServerSentEvent<String>> stream =
                buildGeneralStreamingResponse(chatRuntimeBundle.chatClient(), context, prepared, last);
        stream = finalizeResponse(
                        stream,
                        context,
                        prepared,
                        chatRuntimeBundle.config(),
                        last,
                        List.of(),
                        finalized,
                        startedAt)
                .concatWithValues(doneEvent());
        return meta.concatWith(stream);
    }

    private Flux<ServerSentEvent<String>> buildGeneralStreamingResponse(
            ChatClient chatClient,
            ConversationHistoryService.ConversationContext context,
            PreparedChat prepared,
            AtomicReference<String> last) {
        ChatClient.ChatClientRequestSpec spec = buildRequestSpec(chatClient, context, prepared);
        return spec.stream().content().flatMap(chunk -> {
            String delta = normalizeDelta(chunk);
//            if (!StringUtils.hasText(delta)) {
//                return Flux.empty();
//            }
            last.updateAndGet(existing -> existing + delta);
            logger.info(
                    "SSE chat chunk length={}, sessionType={}, scopeId={}, contentType={}",
                    delta.length(),
                    prepared.sessionType().name(),
                    prepared.scopeId(),
                    MediaType.TEXT_EVENT_STREAM_VALUE);
            return Flux.just(messageEvent(delta));
        });
    }

    private Flux<ServerSentEvent<String>> buildSkillStreamingResponse(
            ConversationHistoryService.ConversationContext context, PreparedChat prepared) {
        AtomicReference<String> last = new AtomicReference<>("");
        AtomicReference<String> previousResponseText = new AtomicReference<>("");
        AtomicBoolean finalized = new AtomicBoolean(false);
        AtomicInteger modelRound = new AtomicInteger(1);
        AtomicInteger currentRoundOutputLength = new AtomicInteger(0);
        AtomicLong currentRoundStartedAt = new AtomicLong(System.currentTimeMillis());
        List<Map<String, Object>> toolEvents = Collections.synchronizedList(new ArrayList<>());
        long startedAt = System.currentTimeMillis();
        ModelRuntimeClientFactory.ChatRuntimeBundle skillChatBundle = createSkillChatBundle(prepared);
        ChatClient.ChatClientRequestSpec spec = buildRequestSpec(skillChatBundle.chatClient(), context, prepared);
        Sinks.Many<ServerSentEvent<String>> toolSink = Sinks.many().unicast().onBackpressureBuffer();
        BiConsumer<String, String> publisher = (eventType, payload) -> {
            String normalizedPayload = enrichToolEventPayload(payload);
            recordToolEvent(eventType, normalizedPayload, toolEvents);
            toolSink.tryEmitNext(ServerSentEvent.builder(normalizedPayload).event(eventType).build());
        };

        Flux<ServerSentEvent<String>> stream = spec.stream()
                .chatResponse()
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
                    boolean newModelRound = StringUtils.hasText(previous) && !current.startsWith(previous);
                    if (newModelRound) {
                        logModelRoundThroughput(
                                context,
                                prepared,
                                modelRound.getAndIncrement(),
                                currentRoundOutputLength.get(),
                                currentRoundStartedAt.getAndSet(System.currentTimeMillis()));
                        currentRoundOutputLength.set(0);
                    }
                    String delta = current.startsWith(previous) ? current.substring(previous.length()) : current;
                    previousResponseText.set(current);
//                    if (!StringUtils.hasText(delta)) {
//                        return Flux.empty();
//                    }
                    last.updateAndGet(existing -> existing + delta);
                    currentRoundOutputLength.addAndGet(safeLength(delta));
                    logger.info(
                            "SSE skill chat chunk length={}, sessionType={}, scopeId={}, hasToolCalls={}",
                            current.length(),
                            prepared.sessionType().name(),
                            prepared.scopeId(),
                            output.hasToolCalls());
                    return Flux.just(messageEvent(delta));
                })
                .onErrorResume(error -> {
                    logStreamingError("skill", prepared, skillChatBundle.config(), error);
                    if (finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.failMessage(
                                context,
                                error.getMessage(),
                                last.get(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                    }
                    return Flux.just(errorEvent(error.getMessage()));
                })
                .doOnComplete(() -> {
                    if (finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.completeMessage(
                                context,
                                last.get(),
                                null,
                                prepared.fileListJson(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                        logModelRoundThroughput(
                                context,
                                prepared,
                                modelRound.get(),
                                currentRoundOutputLength.get(),
                                currentRoundStartedAt.get());
                        logSkillConversationStats(context, prepared, last.get(), startedAt, modelRound.get());
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL && finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.interruptMessage(
                                context,
                                last.get(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                    }
                })
                .concatWithValues(doneEvent())
                .contextWrite(Context.of("toolEventPublisher", publisher));

        return Flux.merge(stream, toolSink.asFlux());
    }

    private ChatClient.ChatClientRequestSpec buildRequestSpec(
            ChatClient chatClient, ConversationHistoryService.ConversationContext context, PreparedChat prepared) {
        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(context.sessionCode())
                        .build())
                .user(prepared.userMessage());

        if (StringUtils.hasText(prepared.systemPrompt())) {
            spec.system(prepared.systemPrompt());
        }
        if (!prepared.toolCallbacks().isEmpty()) {
            spec.toolCallbacks(prepared.toolCallbacks());
        }
        return spec;
    }

    private ModelRuntimeClientFactory.ChatRuntimeBundle createSkillChatBundle(PreparedChat prepared) {
        ToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new DelegatingToolCallbackResolver(List.of(
                        new StaticToolCallbackResolver(prepared.toolCallbacks()),
                        SkillAwareToolCallbackResolver.builder().skillKit(skillKit).build())))
                .build();
        ToolCallingManager toolCallingManager = SkillAwareToolCallingManager.builder()
                .skillKit(skillKit)
                .delegate(delegate)
                .build();
        return modelRuntimeClientFactory.createChatBundle(toolCallingManager);
    }

    private Flux<ServerSentEvent<String>> finalizeResponse(
            Flux<ServerSentEvent<String>> stream,
            ConversationHistoryService.ConversationContext context,
            PreparedChat prepared,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig chatConfig,
            AtomicReference<String> last,
            List<Map<String, Object>> toolEvents,
            AtomicBoolean finalized,
            long startedAt) {
        return stream.onErrorResume(error -> {
                    logStreamingError("general", prepared, chatConfig, error);
                    if (finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.failMessage(
                                context,
                                error.getMessage(),
                                last.get(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                    }
                    return Flux.just(errorEvent(error.getMessage()));
                })
                .doOnComplete(() -> {
                    if (finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.completeMessage(
                                context,
                                last.get(),
                                null,
                                prepared.fileListJson(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL && finalized.compareAndSet(false, true)) {
                        String paramsJson = mergeParamsJson(prepared.paramsJson(), toolEvents);
                        conversationHistoryService.interruptMessage(
                                context,
                                last.get(),
                                paramsJson,
                                System.currentTimeMillis() - startedAt);
                    }
                });
    }

    private void logStreamingError(
            String scene,
            PreparedChat prepared,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig chatConfig,
            Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            logger.error(
                    "聊天流式请求失败：scene={}, provider={}, model={}, sessionType={}, scopeId={}, status={}, responseBody={}",
                    scene,
                    chatConfig.provider(),
                    chatConfig.model(),
                    prepared.sessionType().name(),
                    prepared.scopeId(),
                    responseException.getStatusCode().value(),
                    responseException.getResponseBodyAsString(),
                    error);
            return;
        }
        logger.error(
                "聊天流式请求失败：scene={}, provider={}, model={}, sessionType={}, scopeId={}, error={}",
                scene,
                chatConfig.provider(),
                chatConfig.model(),
                prepared.sessionType().name(),
                prepared.scopeId(),
                error.getMessage(),
                error);
    }

    private void logSkillContextStats(PreparedChat prepared) {
        if (prepared == null || prepared.sessionType() != ConversationSessionType.SKILL_CHAT) {
            return;
        }
        int systemPromptLength = safeLength(prepared.systemPrompt());
        int userMessageLength = safeLength(prepared.userMessage());
        int queryLength = safeLength(prepared.query());
        int totalContextLength = systemPromptLength + userMessageLength;
//        logger.info(
//                "技能对话上下文统计：runtimeSkillName={}, scopeId={}, sessionId={}, queryLength={}, userMessageLength={}, systemPromptLength={}, totalContextLength={}, toolCount={}",
//                prepared.runtimeSkillName(),
//                prepared.scopeId(),
//                prepared.sessionId(),
//                queryLength,
//                userMessageLength,
//                systemPromptLength,
//                totalContextLength,
//                prepared.toolCallbacks() == null ? 0 : prepared.toolCallbacks().size());
    }

    private void logSkillConversationStats(
            ConversationHistoryService.ConversationContext context,
            PreparedChat prepared,
            String content,
            long startedAt,
            int modelRoundCount) {
        if (prepared == null || prepared.sessionType() != ConversationSessionType.SKILL_CHAT) {
            return;
        }
        long durationMs = Math.max(1L, System.currentTimeMillis() - startedAt);
        int outputLength = safeLength(content);
        double outputCharsPerSecond = outputLength * 1000.0 / durationMs;
//        logger.info(
//                "技能对话整体统计：runtimeSkillName={}, scopeId={}, sessionCode={}, modelRoundCount={}, outputLength={}, durationMs={}, outputCharsPerSecond={}",
//                prepared.runtimeSkillName(),
//                prepared.scopeId(),
//                context == null ? "" : context.sessionCode(),
//                modelRoundCount,
//                outputLength,
//                durationMs,
//                String.format(java.util.Locale.ROOT, "%.2f", outputCharsPerSecond));
    }

    private void logModelRoundThroughput(
            ConversationHistoryService.ConversationContext context,
            PreparedChat prepared,
            int roundIndex,
            int outputLength,
            long roundStartedAt) {
        if (prepared == null || prepared.sessionType() != ConversationSessionType.SKILL_CHAT || outputLength <= 0) {
            return;
        }
        long durationMs = Math.max(1L, System.currentTimeMillis() - roundStartedAt);
        double outputCharsPerSecond = outputLength * 1000.0 / durationMs;
//        logger.info(
//                "技能对话模型轮次统计：runtimeSkillName={}, scopeId={}, sessionCode={}, roundIndex={}, roundOutputLength={}, roundDurationMs={}, roundOutputCharsPerSecond={}",
//                prepared.runtimeSkillName(),
//                prepared.scopeId(),
//                context == null ? "" : context.sessionCode(),
//                roundIndex,
//                outputLength,
//                durationMs,
//                String.format(java.util.Locale.ROOT, "%.2f", outputCharsPerSecond));
    }

    private void recordToolEvent(String eventType, String payload, List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || (!"tool".equals(eventType) && !"result".equals(eventType))) {
            return;
        }
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Object content = wrapper == null ? null : wrapper.get("content");
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", eventType);
            if (content instanceof Map<?, ?> contentMap) {
                event.put("content", new LinkedHashMap<>(contentMap));
            } else if (content != null) {
                event.put("content", content);
            }
            toolEvents.add(event);
        } catch (Exception ex) {
            logger.warn("记录工具事件失败：eventType={}, error={}", eventType, ex.getMessage());
        }
    }

    private String enrichToolEventPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return payload;
        }
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Object content = wrapper == null ? null : wrapper.get("content");
            if (!(content instanceof Map<?, ?> contentMap)) {
                return payload;
            }
            Object rawName = contentMap.get("name");
            String toolName = rawName == null ? "" : String.valueOf(rawName).trim();
            if (!StringUtils.hasText(toolName)) {
                return payload;
            }
            Map<String, Object> normalizedContent = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                normalizedContent.put(String.valueOf(key), entry.getValue());
            }
            normalizedContent.put("displayName", resolveToolDisplayName(toolName));
            wrapper.put("content", normalizedContent);
            return JSON.toJSONString(wrapper);
        } catch (Exception ex) {
            logger.warn("补充工具展示名称失败：error={}", ex.getMessage());
            return payload;
        }
    }

    private String resolveToolDisplayName(String toolName) {
        String normalizedToolName = StringUtils.hasText(toolName) ? toolName.trim() : "";
        if (!StringUtils.hasText(normalizedToolName)) {
            return "";
        }
        return switch (normalizedToolName) {
            case "search_dataset_summary" -> "查看数据集摘要";
            case "get_dataset_schema" -> "查看数据集结构";
            case "execute_dataset_sql" -> "执行数据集 SQL";
            default -> skillCatalogService.resolveToolDisplayName(normalizedToolName);
        };
    }

    private String mergeParamsJson(String paramsJson, List<Map<String, Object>> toolEvents) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(paramsJson)) {
            try {
                Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
                if (parsed != null) {
                    payload.putAll(parsed);
                }
            } catch (Exception ex) {
                logger.warn("解析参数 JSON 失败，将重建参数：error={}", ex.getMessage());
            }
        }
        payload.put("toolEvents", toolEvents == null ? List.of() : List.copyOf(toolEvents));
        return JSON.toJSONString(payload);
    }

    private boolean hasRequestContent(String message, List<String> fileIds) {
        return StringUtils.hasText(message) || (fileIds != null && !fileIds.isEmpty());
    }

    private String resolveQuery(String message, List<String> fileIds, String runtimeSkillName) {
        String normalized = normalizeMessage(message);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        List<ChatFileService.UploadedFile> files = chatFileService.resolveFiles(fileIds);
        if (files.isEmpty()) {
            return "请基于我上传的附件继续分析";
        }
        String joinedNames = files.stream()
                .map(ChatFileService.UploadedFile::name)
                .filter(StringUtils::hasText)
                .limit(3)
                .reduce((left, right) -> left + "、" + right)
                .orElse("附件");
        if ("form-app-assistant".equals(StringUtils.trimWhitespace(runtimeSkillName))) {
            return "请基于我上传的表单参考附件分析语义，并推荐字段与布局：" + joinedNames;
        }
        return "请基于我上传的附件继续分析：" + joinedNames;
    }

    private String normalizeMessage(String message) {
        return StringUtils.hasText(message) ? message.trim() : "";
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String normalizeDelta(String chunk) {
        if (chunk == null) {
            return "";
        }
        return chunk;
    }

    private String buildDatasetSystemPrompt(IntegrationDatasetService.DatasetDetail dataset) {
        return "你是数据集智能问数助手。你的任务是基于当前选中的数据集完成统计分析、指标计算和结果解释。"
                + "你可以按需多次调用工具，先理解数据集，再查询数据，最后给出结论。\n\n"
                + "工作规则：\n"
                + "1. 遇到业务口径不明确、对象不明确、字段不明确时，先调用 search_dataset_summary 或 get_dataset_schema，不要直接猜测。\n"
                + "2. 需要统计结果时，先确认对象、字段和关联关系，再调用 execute_dataset_sql。必要时可以多次修正 SQL。\n"
                + "3. 只能基于工具返回的数据和当前数据集信息作答，禁止编造不存在的字段、表、结果或业务规则。\n"
                + "4. 如果 SQL 执行失败，要根据错误信息调整查询，而不是直接放弃。\n"
                + "5. 如果用户请求超出当前数据集能力或数据不足，必须明确说明原因。\n"
                + "6. 最终回答尽量包含：核心结论、统计口径、过滤条件、时间范围、分组方式，以及必要的限制说明。\n"
                + "7. 除非问题非常简单且上下文已足够，否则优先先看摘要或结构，再做 SQL 查询。\n\n"
                + buildDatasetPromptContext(dataset);
    }

    private String buildDatasetPromptContext(IntegrationDatasetService.DatasetDetail dataset) {
        if (dataset == null) {
            return "当前未提供数据集上下文。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前数据集信息：\n");
        builder.append("- 名称：").append(defaultText(dataset.name())).append("\n");
        builder.append("- 编码：").append(defaultText(dataset.datasetCode())).append("\n");
        builder.append("- 来源类型：").append(defaultText(dataset.sourceKind())).append("\n");
        builder.append("- 描述：").append(defaultText(dataset.description())).append("\n");
        builder.append("- 业务说明：").append(defaultText(dataset.businessLogic())).append("\n");
        builder.append("- 对象数量：").append(dataset.objectCount()).append("\n");
        builder.append("- 字段数量：").append(dataset.fieldCount()).append("\n");
        List<IntegrationDatasetService.ObjectBindingView> objects = dataset.objectBindings() == null
                ? List.of()
                : dataset.objectBindings();
        if (!objects.isEmpty()) {
            builder.append("对象列表：\n");
            objects.stream().limit(10).forEach(item -> builder.append("- ")
                    .append(defaultText(item.objectName()))
                    .append(" (")
                    .append(defaultText(item.objectCode()))
                    .append(")")
                    .append(StringUtils.hasText(item.objectSource()) ? " 来源=" + item.objectSource() : "")
                    .append("\n"));
        }
        List<IntegrationDatasetService.FieldBindingView> fields = dataset.fieldBindings() == null
                ? List.of()
                : dataset.fieldBindings();
        if (!fields.isEmpty()) {
            builder.append("字段列表：\n");
            fields.stream().limit(30).forEach(item -> builder.append("- ")
                    .append(defaultText(item.objectName()))
                    .append(".")
                    .append(defaultText(item.fieldName()))
                    .append(StringUtils.hasText(item.fieldAlias()) ? "（别名=" + item.fieldAlias() + "）" : "")
                    .append(StringUtils.hasText(item.fieldType()) ? " 类型=" + item.fieldType() : "")
                    .append(StringUtils.hasText(item.fieldScope()) ? " 范围=" + item.fieldScope() : "")
                    .append("\n"));
        }
        List<IntegrationDatasetService.RelationBindingView> relations = dataset.relationBindings() == null
                ? List.of()
                : dataset.relationBindings();
        if (!relations.isEmpty()) {
            builder.append("关系列表：\n");
            relations.stream().limit(20).forEach(item -> builder.append("- ")
                    .append(defaultText(item.leftObjectCode()))
                    .append(".")
                    .append(defaultText(item.leftFieldName()))
                    .append(" -> ")
                    .append(defaultText(item.rightObjectCode()))
                    .append(".")
                    .append(defaultText(item.rightFieldName()))
                    .append("\n"));
        }
        return builder.toString().trim();
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "无";
    }

    private ServerSentEvent<String> metaEvent(Map<String, Object> content) {
        return typedEvent("meta", "meta", content);
    }

    private ServerSentEvent<String> messageEvent(String content) {
        return typedEvent("message", "message", content);
    }

    private ServerSentEvent<String> errorEvent(String error) {
        return typedEvent("error", "error", error);
    }

    private ServerSentEvent<String> doneEvent() {
        return typedEvent("done", "done", "[DONE]");
    }

    private ServerSentEvent<String> typedEvent(String eventName, String type, Object content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return ServerSentEvent.builder(JSON.toJSONString(payload)).event(eventName).build();
    }

    private record PreparedChat(
            ConversationSessionType sessionType,
            String sessionId,
            Long scopeId,
            String scopeDisplayName,
            String query,
            String userMessage,
            String questionType,
            String paramsJson,
            String fileListJson,
            List<ToolCallback> toolCallbacks,
            String systemPrompt,
            String runtimeSkillName) {}

    public record GeneralChatRequest(String message, List<String> fileIds, String sessionId) {}

    public record SkillChatRequest(Long skillId, String message, List<String> fileIds, String sessionId) {}

    public record DatasetChatRequest(String message, String sessionId) {}
}
