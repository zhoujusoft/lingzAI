package lingzhou.agent.backend.business.datasets.service.knowledge;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import lingzhou.agent.backend.app.RagQaProperties;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class KnowledgeQaService {

    private static final int DEFAULT_TOP_K = 8;
    private static final int MAX_TOP_K = 20;
    private static final int DEFAULT_PREFERENCE_EXPIRE_ROUNDS = 10;
    private static final double DEFAULT_LOW_RERANK_THRESHOLD = 0.35D;
    private static final String DEFAULT_FALLBACK_DISCLAIMER = "以下回答基于通用模型能力，非知识库依据。";

    private static final Pattern ENABLE_KB_PREFERENCE_PATTERN =
            Pattern.compile("(优先|先).*(查|检索).*(知识库)|后续.*知识库|都.*知识库", Pattern.CASE_INSENSITIVE);

    private static final Pattern SMALL_TALK_HEURISTIC_PATTERN = Pattern.compile(
            "^(你好|您好|hello|hi|嗨|你是谁|你是什么|介绍一下你自己|在吗|谢谢|再见|早上好|晚上好)[!！。,.，?？ ]*$",
            Pattern.CASE_INSENSITIVE);

    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final ChatMemory chatMemory;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final ConversationHistoryService conversationHistoryService;
    private final RagQaProperties qaProperties;
    private final Map<String, Integer> preferKbRoundsBySession = new ConcurrentHashMap<>();

    public KnowledgeQaService(
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            ChatMemory chatMemory,
            KnowledgeChunkSearchService knowledgeChunkSearchService,
            ConversationHistoryService conversationHistoryService,
            RagQaProperties qaProperties) {
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.chatMemory = chatMemory;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.conversationHistoryService = conversationHistoryService;
        this.qaProperties = qaProperties;
    }

    public Flux<ServerSentEvent<String>> streamAnswer(Long kbId, KnowledgeBase kb, QaStreamRequest request, Long userId) {
        if (kbId == null || request == null || !StringUtils.hasText(request.message())) {
            return Flux.just(errorEvent("message is required")).concatWithValues(doneEvent());
        }
        if (userId == null || userId <= 0) {
            return Flux.just(errorEvent("user is required")).concatWithValues(doneEvent());
        }

        String query = request.message().trim();
        int topK = normalizeTopK(request.topK());
        String requestedSessionCode = normalizeSessionCode(request.sessionId());

        PreferenceDecision preferenceDecision = resolvePreferenceDecision(requestedSessionCode, query);
        IntentType intent = classifyIntent(query, kbId, requestedSessionCode);
        boolean retrievalRoute = preferenceDecision.forceKb() || intent == IntentType.KB_QA;
        String routeReason = resolveRouteReason(intent, preferenceDecision);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("kbId", kbId);
        params.put("topK", topK);
        params.put("intent", intent.name());
        params.put("routeReason", routeReason);
        params.put("preferKb", preferenceDecision.forceKb());

        ConversationHistoryService.ConversationContext context;
        try {
            context = conversationHistoryService.startMessage(
                    userId,
                    ConversationSessionType.KNOWLEDGE_QA,
                    requestedSessionCode,
                    kbId,
                    kb == null ? "" : kb.getKbName(),
                    query,
                    "normal",
                    query,
                    retrievalRoute ? "KB_QA" : "SMALL_TALK",
                    JSON.toJSONString(params),
                    "[]");
        } catch (Exception ex) {
            log.error(
                    "会话初始化失败：kbId={}, userId={}, sessionCode={}, error={}",
                    kbId,
                    userId,
                    requestedSessionCode,
                    ex.getMessage(),
                    ex);
            return Flux.just(errorEvent("会话初始化失败，请稍后重试")).concatWithValues(doneEvent());
        }

        PreferenceLifecycle lifecycle = applyPreferenceLifecycle(context.sessionCode(), preferenceDecision);
        if (lifecycle.preferenceExpired()) {
            routeReason = routeReason + "|preferenceExpired";
        }

        List<RecallChunkVo> recalls = List.of();
        String retrievalStrategy = retrievalRoute ? "HYBRID" : "NONE";
        if (retrievalRoute) {
            try {
                KnowledgeChunkSearchService.SearchResult searchResult =
                        knowledgeChunkSearchService.searchForQa(kbId, query, topK);
                recalls = searchResult.recalls();
                retrievalStrategy = searchResult.retrievalStrategy();
            } catch (Exception ex) {
                log.error(
                        "知识库检索失败：kbId={}, sessionCode={}, topK={}, strategy={}, error={}",
                        kbId,
                        context.sessionCode(),
                        topK,
                        retrievalStrategy,
                        ex.getMessage(),
                        ex);
                conversationHistoryService.failMessage(context, ex.getMessage(), "", null, 0L);
                return Flux.just(errorEvent(ex.getMessage())).concatWithValues(doneEvent());
            }
        }

        String answerMode = resolveAnswerMode(retrievalRoute, recalls);
        String fallbackReason = resolveFallbackReason(answerMode, recalls);
        String documentListJson = "KB_QA".equals(answerMode) ? buildDocumentListJson(recalls) : "[]";
        String userPrompt = buildPromptByMode(answerMode, kb, query, recalls, fallbackReason);
        String systemPrompt = buildSystemPromptByMode(answerMode);

        AtomicReference<String> answerRef = new AtomicReference<>("");
        AtomicBoolean finalized = new AtomicBoolean(false);
        AtomicBoolean modelFailed = new AtomicBoolean(false);
        long startedAt = System.currentTimeMillis();
        var chatRuntimeBundle = modelRuntimeClientFactory.createChatBundle();

        Flux<ServerSentEvent<String>> citationStream =
                "KB_QA".equals(answerMode) ? buildCitationStream(recalls) : Flux.empty();

        Flux<ServerSentEvent<String>> answerStream = chatRuntimeBundle.chatClient()
                .prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(context.sessionCode())
                        .build())
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .flatMap(chunk -> {
                    String delta = normalizeDelta(chunk);
                    if (!StringUtils.hasText(delta)) {
                        return Flux.empty();
                    }
                    answerRef.updateAndGet(existing -> existing + delta);
                    return Flux.just(messageEvent(delta));
                })
                .onErrorResume(error -> {
                    modelFailed.set(true);
                    if (finalized.compareAndSet(false, true)) {
                        log.error(
                                "知识问答流式生成失败：kbId={}, sessionCode={}, answerMode={}, error={}",
                                kbId,
                                context.sessionCode(),
                                answerMode,
                                error.getMessage(),
                                error);
                        conversationHistoryService.failMessage(
                                context,
                                error.getMessage(),
                                answerRef.get(),
                                null,
                                System.currentTimeMillis() - startedAt);
                    }
                    return Flux.just(errorEvent(error.getMessage()));
                })
                .concatWith(Flux.defer(() -> {
                    if (modelFailed.get()) {
                        return Flux.empty();
                    }
                    return buildFallbackNoticeStream(answerMode, answerRef);
                }))
                .doOnComplete(() -> {
                    if (finalized.compareAndSet(false, true)) {
                        conversationHistoryService.completeMessage(
                                context,
                                answerRef.get(),
                                documentListJson,
                                "[]",
                                null,
                                System.currentTimeMillis() - startedAt);
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == reactor.core.publisher.SignalType.CANCEL
                            && finalized.compareAndSet(false, true)) {
                        conversationHistoryService.interruptMessage(
                                context, answerRef.get(), null, System.currentTimeMillis() - startedAt);
                    }
                });

        Map<String, Object> metaPayload = new LinkedHashMap<>(conversationHistoryService.buildMetaPayload(context));
        metaPayload.put("intent", intent.name());
        metaPayload.put("routeReason", routeReason);
        metaPayload.put("answerMode", answerMode);
        metaPayload.put("fallbackReason", fallbackReason);
        metaPayload.put("retrievalStrategy", retrievalStrategy);
        metaPayload.put("preferenceActive", preferenceDecision.forceKb());
        metaPayload.put("preferenceRemainingRounds", lifecycle.remainingRounds() == null ? "" : lifecycle.remainingRounds());
        metaPayload.put("preferenceExpired", lifecycle.preferenceExpired());

        return Flux.just(metaEvent(metaPayload))
                .concatWith(citationStream)
                .concatWith(answerStream)
                .concatWithValues(doneEvent());
    }

    private int normalizeTopK(Integer value) {
        int topK = value == null || value <= 0 ? DEFAULT_TOP_K : value;
        return Math.min(topK, MAX_TOP_K);
    }

    private String normalizeSessionCode(String sessionCode) {
        if (!StringUtils.hasText(sessionCode)) {
            return "";
        }
        return sessionCode.trim();
    }

    private PreferenceDecision resolvePreferenceDecision(String requestedSessionCode, String query) {
        boolean enablePreference = shouldEnableKbPreference(query);
        boolean hasActivePreference =
                StringUtils.hasText(requestedSessionCode) && getPreferenceRemainingRounds(requestedSessionCode) > 0;
        return new PreferenceDecision(enablePreference || hasActivePreference, enablePreference, hasActivePreference);
    }

    private PreferenceLifecycle applyPreferenceLifecycle(String sessionCode, PreferenceDecision decision) {
        if (!StringUtils.hasText(sessionCode)) {
            return new PreferenceLifecycle(false, null);
        }

        if (decision.enablePreference()) {
            int expireRounds = getPreferenceExpireRounds();
            preferKbRoundsBySession.put(sessionCode, expireRounds);
            log.info("会话偏好已启用：sessionCode={}, remainRounds={}", sessionCode, expireRounds);
            return new PreferenceLifecycle(false, expireRounds);
        }

        if (!decision.consumePreference()) {
            return new PreferenceLifecycle(false, getPreferenceRemainingRounds(sessionCode));
        }

        AtomicBoolean expired = new AtomicBoolean(false);
        Integer remaining = preferKbRoundsBySession.computeIfPresent(sessionCode, (key, rounds) -> {
            int next = rounds - 1;
            if (next <= 0) {
                expired.set(true);
                return null;
            }
            return next;
        });

        if (expired.get()) {
            log.info("会话偏好已自动过期：sessionCode={}, event=preferenceExpired", sessionCode);
            return new PreferenceLifecycle(true, 0);
        }
        return new PreferenceLifecycle(false, remaining);
    }

    private int getPreferenceRemainingRounds(String sessionCode) {
        Integer value = preferKbRoundsBySession.get(sessionCode);
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean shouldEnableKbPreference(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        return ENABLE_KB_PREFERENCE_PATTERN.matcher(query.trim()).find();
    }

    private IntentType classifyIntent(String query, Long kbId, String sessionCode) {
        IntentType modelIntent = classifyIntentByModel(query, kbId, sessionCode);
        if (modelIntent != null) {
            return modelIntent;
        }
        return classifyIntentByHeuristic(query);
    }

    private IntentType classifyIntentByModel(String query, Long kbId, String sessionCode) {
        try {
            String label = modelRuntimeClientFactory
                    .createChatBundle()
                    .chatClient()
                    .prompt()
                    .system("""
                            你是意图分类器。仅输出一个标签：SMALL_TALK 或 KB_QA。
                            SMALL_TALK：问候、寒暄、自我介绍、泛聊天，不依赖知识库证据。
                            KB_QA：需要基于知识库事实回答的问题。
                            禁止输出任何解释或多余字符。
                            """)
                    .user("用户问题：%s".formatted(query))
                    .call()
                    .content();
            return parseIntentLabel(label);
        } catch (Exception ex) {
            log.warn(
                    "意图分类模型调用失败，使用启发式分类：kbId={}, sessionCode={}, error={}",
                    kbId,
                    sessionCode,
                    ex.getMessage(),
                    ex);
            return null;
        }
    }

    private IntentType parseIntentLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.contains("SMALL_TALK")) {
            return IntentType.SMALL_TALK;
        }
        if (normalized.contains("KB_QA")) {
            return IntentType.KB_QA;
        }
        return null;
    }

    private IntentType classifyIntentByHeuristic(String query) {
        if (!StringUtils.hasText(query)) {
            return IntentType.KB_QA;
        }
        String normalized = query.trim();
        if (SMALL_TALK_HEURISTIC_PATTERN.matcher(normalized).matches()) {
            return IntentType.SMALL_TALK;
        }
        return IntentType.KB_QA;
    }

    private String resolveRouteReason(IntentType intent, PreferenceDecision preferenceDecision) {
        if (preferenceDecision.enablePreference()) {
            return "PREFERENCE_SET";
        }
        if (preferenceDecision.consumePreference() && intent == IntentType.SMALL_TALK) {
            return "PREFERENCE_OVERRIDE";
        }
        return intent == IntentType.SMALL_TALK ? "MODEL_SMALL_TALK" : "MODEL_KB_QA";
    }

    private String resolveAnswerMode(boolean retrievalRoute, List<RecallChunkVo> recalls) {
        if (!retrievalRoute) {
            return "SMALL_TALK";
        }
        if (!StringUtils.hasText(resolveFallbackReason("KB_QA", recalls))) {
            return "KB_QA";
        }
        return "LLM_FALLBACK";
    }

    private String resolveFallbackReason(String answerMode, List<RecallChunkVo> recalls) {
        if (!"KB_QA".equals(answerMode) && !"LLM_FALLBACK".equals(answerMode)) {
            return "";
        }
        if (recalls == null || recalls.isEmpty()) {
            return "EMPTY_RECALL";
        }
        RecallChunkVo top1 = recalls.get(0);
        boolean rerankApplied = Boolean.TRUE.equals(top1.getRerankApplied());
        Double topScore = top1.getScore();
        if (rerankApplied && topScore != null && topScore < getLowRerankThreshold()) {
            return "LOW_RERANK_SCORE";
        }
        return "";
    }
    private String buildSystemPromptByMode(String answerMode) {
        if ("KB_QA".equals(answerMode)) {
            return """
                你是知识库问答助手。
                只能基于当前提供的知识库内容进行回答，不得编造不存在的信息。

                如果信息不足以回答问题，请明确说明：“当前知识库中没有找到相关内容”或“信息不足以支持回答”。

                回答要求：
                - 优先用简洁自然的语言直接回答问题，不要写成长篇说明
                - 在不影响阅读的情况下，可以在关键信息后标注来源编号，如[1][2]
                - 不要逐条罗列来源或做“分析报告式”输出
                - 不要输出工具调用信息
                """;
        }
        if ("LLM_FALLBACK".equals(answerMode)) {
            return """
                你是通用问答助手。
                当前问题未命中知识库，请基于通用知识进行回答。

                回答要求：
                - 保持简洁、清晰，优先直接回答问题
                - 对不确定的信息要明确说明（如“可能”、“一般情况下”）
                - 不要编造“来自知识库”的内容或引用
                - 不要输出工具调用信息
                """;
        }
        return """
            你是对话助手。
            当前是闲聊或简单问题，请用自然、友好的语气直接回答。

            回答要求：
            - 简洁自然，不要过度解释
            - 不要输出工具调用信息
            """;
    }

    private String buildPromptByMode(
            String answerMode, KnowledgeBase kb, String query, List<RecallChunkVo> recalls, String fallbackReason) {
        if ("KB_QA".equals(answerMode)) {
            return buildQaPrompt(kb, query, recalls);
        }
        if ("LLM_FALLBACK".equals(answerMode)) {
            return """
                    用户问题：
                    %s

                    未命中原因：%s
                    请直接给出尽可能有帮助的回答。
                    """.formatted(query, fallbackReason);
        }
        return query;
    }

    private Flux<ServerSentEvent<String>> buildFallbackNoticeStream(
            String answerMode, AtomicReference<String> answerRef) {
        if (!"LLM_FALLBACK".equals(answerMode)) {
            return Flux.empty();
        }
        String notice = getFallbackDisclaimer();
        answerRef.updateAndGet(existing -> appendFallbackNotice(existing, notice));
        return Flux.just(fallbackNoticeEvent(notice));
    }

    private String appendFallbackNotice(String answer, String notice) {
        String body = StringUtils.hasText(answer) ? answer : "";
        if (!StringUtils.hasText(notice)) {
            return body;
        }
        if (!StringUtils.hasText(body)) {
            return notice;
        }
        return body + "\n\n" + notice;
    }

    private int getPreferenceExpireRounds() {
        Integer configured = qaProperties == null ? null : qaProperties.getPreferenceExpireRounds();
        return configured == null || configured <= 0 ? DEFAULT_PREFERENCE_EXPIRE_ROUNDS : configured;
    }

    private double getLowRerankThreshold() {
        Double configured = qaProperties == null ? null : qaProperties.getLowRerankThreshold();
        if (configured == null || configured <= 0D) {
            return DEFAULT_LOW_RERANK_THRESHOLD;
        }
        return configured;
    }

    private String getFallbackDisclaimer() {
        String configured = qaProperties == null ? null : qaProperties.getFallbackDisclaimer();
        if (!StringUtils.hasText(configured)) {
            return DEFAULT_FALLBACK_DISCLAIMER;
        }
        return configured.trim();
    }

    private String normalizeDelta(String chunk) {
        if (chunk == null) {
            return "";
        }
        return chunk;
    }

    private Flux<ServerSentEvent<String>> buildCitationStream(List<RecallChunkVo> recalls) {
        if (recalls == null || recalls.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(recalls)
                .index()
                .map(tuple -> {
                    long idx = tuple.getT1();
                    RecallChunkVo item = tuple.getT2();
                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("ref", idx + 1);
                    content.put("docId", item.getDocId());
                    content.put("chunkId", item.getChunkId());
                    content.put("indexId", StringUtils.hasText(item.getId()) ? item.getId() : "");
                    content.put("fileName", StringUtils.hasText(item.getFileName()) ? item.getFileName() : "unknown");
                    content.put("lawTitle", StringUtils.hasText(item.getLawTitle()) ? item.getLawTitle() : "");
                    content.put("articleCn", StringUtils.hasText(item.getArticleCn()) ? item.getArticleCn() : "");
                    content.put("score", item.getScore() == null ? 0D : item.getScore());
                    content.put("snippet", normalizeChunk(item.getContent()));
                    return citationEvent(content);
                });
    }

    private String buildQaPrompt(KnowledgeBase kb, String query, List<RecallChunkVo> recalls) {
        String kbName = kb == null ? "" : StringUtils.trimWhitespace(kb.getKbName());
        if (recalls == null || recalls.isEmpty()) {
            return """
                    你将回答一个知识库问题，但当前没有可用证据。
                    问题：
                    %s

                    请明确说明未检索到足够证据，不要编造。
                    """.formatted(query);
        }

        String evidence = buildEvidenceSection(recalls);
        return """
                知识库：%s
                用户问题：
                %s

                可用证据（按相关性排序）：
                %s

                请仅基于上述证据作答，回答尽量结构化，并在关键结论后附上证据编号，如[1][2]。
                如果证据无法支持结论，请明确说明不确定。
                """.formatted(StringUtils.hasText(kbName) ? kbName : "未命名知识库", query, evidence);
    }

    private String buildEvidenceSection(List<RecallChunkVo> recalls) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recalls.size(); i++) {
            RecallChunkVo item = recalls.get(i);
            String content = safeTrim(item.getContent(), 600);
            String fileName = buildEvidenceSourceName(item);
            String indexId = StringUtils.hasText(item.getId()) ? item.getId() : "N/A";
            sb.append("[").append(i + 1).append("] ")
                    .append("file=").append(fileName)
                    .append(", indexId=").append(indexId)
                    .append(", score=").append(item.getScore() == null ? 0D : item.getScore())
                    .append("\n")
                    .append(content)
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String safeTrim(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeChunk(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim();
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

    private ServerSentEvent<String> metaEvent(Map<String, Object> content) {
        return typedEvent("meta", "meta", content);
    }

    private ServerSentEvent<String> citationEvent(Map<String, Object> content) {
        return typedEvent("citation", "citation", content);
    }

    private ServerSentEvent<String> fallbackNoticeEvent(String content) {
        return typedEvent("fallback_notice", "fallback_notice", content);
    }

    private ServerSentEvent<String> typedEvent(String eventName, String type, Object content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return ServerSentEvent.builder(JSON.toJSONString(payload)).event(eventName).build();
    }

    private String buildDocumentListJson(List<RecallChunkVo> recalls) {
        if (recalls == null || recalls.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> items = new ArrayList<>(recalls.size());
        for (int i = 0; i < recalls.size(); i++) {
            RecallChunkVo item = recalls.get(i);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("ref", i + 1);
            citation.put("docId", item.getDocId());
            citation.put("chunkId", item.getChunkId());
            citation.put("indexId", item.getId());
            citation.put("fileName", item.getFileName());
            citation.put("lawTitle", item.getLawTitle());
            citation.put("articleCn", item.getArticleCn());
            citation.put("score", item.getScore());
            citation.put("snippet", normalizeChunk(item.getContent()));
            items.add(citation);
        }
        return JSON.toJSONString(items);
    }

    private String buildEvidenceSourceName(RecallChunkVo item) {
        String lawTitle = StringUtils.hasText(item.getLawTitle()) ? item.getLawTitle() : item.getFileName();
        String articleCn = item.getArticleCn();
        if (StringUtils.hasText(lawTitle) && StringUtils.hasText(articleCn)) {
            return lawTitle + " " + articleCn;
        }
        if (StringUtils.hasText(lawTitle)) {
            return lawTitle;
        }
        return "unknown";
    }

    public record QaStreamRequest(String message, String sessionId, Integer topK) {}

    private record PreferenceDecision(boolean forceKb, boolean enablePreference, boolean consumePreference) {}

    private record PreferenceLifecycle(boolean preferenceExpired, Integer remainingRounds) {}

    private enum IntentType {
        SMALL_TALK,
        KB_QA
    }
}
