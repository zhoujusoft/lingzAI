package lingzhou.agent.backend.capability.rag.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lingzhou.agent.backend.app.RagElasticsearchProperties;
import lingzhou.agent.backend.app.RagRetrievalProperties;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.tool.LawDocumentStructureAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import lingzhou.agent.backend.capability.rag.embedding.DocumentEmbeddingService;
import lingzhou.agent.backend.capability.rag.ranking.AlibabaRerankService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ElasticsearchChunkRetriever {

    private static final List<String> RETRIEVAL_SOURCE_FIELDS = List.of(
            "docId",
            "chunkId",
            "indexId",
            "content",
            "headings",
            "keywords",
            "documentName",
            "blockType",
            "charCount",
            "chunkMetadataValues",
            "documentDomain",
            "lawTitle",
            "lawAlias",
            "chapterNo",
            "chapterTitle",
            "articleNo",
            "articleCn",
            "articleKey",
            "articleTextType");

    private final ElasticsearchClient elasticsearchClient;
    private final RagElasticsearchProperties properties;
    private final RagRetrievalProperties retrievalProperties;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final AlibabaRerankService rerankService;
    private final LawDocumentStructureAnalyzer lawDocumentStructureAnalyzer = new LawDocumentStructureAnalyzer();

    public ElasticsearchChunkRetriever(
            ElasticsearchClient elasticsearchClient,
            RagElasticsearchProperties properties,
            RagRetrievalProperties retrievalProperties,
            DocumentEmbeddingService documentEmbeddingService,
            AlibabaRerankService rerankService) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
        this.retrievalProperties = retrievalProperties;
        this.documentEmbeddingService = documentEmbeddingService;
        this.rerankService = rerankService;
    }

    public void indexDocumentChunks(
            KnowledgeDocument document, List<DocumentEmbeddingService.VectorizedChunk> vectorizedChunks) {
        if (vectorizedChunks == null || vectorizedChunks.isEmpty()) {
            return;
        }
        ensureIndexExists();

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (DocumentEmbeddingService.VectorizedChunk vectorizedChunk : vectorizedChunks) {
            DocumentChunk chunk = vectorizedChunk.chunk();
            bulk.operations(op -> op.index(idx -> idx
                    .index(properties.getIndexName())
                    .id(chunk.getIndexId())
                    .document(buildSource(document, chunk, vectorizedChunk.vector()))));
        }

        try {
            BulkResponse response = elasticsearchClient.bulk(bulk.build());
            assertBulkSuccess(response, "index");
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch bulk index failed", ex);
        }
    }

    public void deleteByDocId(Long docId) {
        if (docId == null || !indexExists()) {
            return;
        }

        try {
            DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                    .index(properties.getIndexName())
                    .query(q -> q.term(t -> t.field("docId").value(docId))));
            if (response.failures() != null && !response.failures().isEmpty()) {
                throw new IllegalStateException("Elasticsearch delete by docId failed: " + response.failures());
            }
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch delete by docId failed", ex);
        }
    }

    public void deleteByIndexIds(Collection<String> indexIds) {
        if (indexIds == null || indexIds.isEmpty() || !indexExists()) {
            return;
        }

        List<String> ids = indexIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (String indexId : ids) {
            bulk.operations(op -> op.delete(del -> del.index(properties.getIndexName()).id(indexId)));
        }

        try {
            BulkResponse response = elasticsearchClient.bulk(bulk.build());
            assertBulkSuccess(response, "delete");
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch bulk delete failed", ex);
        }
    }

    public List<RetrievalCandidate> hybridSearch(Long kbId, String queryText, Integer topK) {
        if (kbId == null || !StringUtils.hasText(queryText) || !indexExists()) {
            return List.of();
        }

        String normalizedQuery = queryText.trim();
        int requestedTopK = normalizeTopK(topK, 5, resolveMaxResultWindow());
        int bm25TopK = Math.max(requestedTopK, safePositive(retrievalProperties.getBm25TopK(), 40));
        int vectorTopK = Math.max(requestedTopK, safePositive(retrievalProperties.getVectorTopK(), 40));
        int rrfK = safePositive(retrievalProperties.getRrfK(), 60);
        int rrfTopK = Math.max(requestedTopK, safePositive(retrievalProperties.getRrfTopK(), 30));
        int rerankInputTopK = Math.max(requestedTopK, safePositive(retrievalProperties.getRerankInputTopK(), 30));

        float[] queryVector = documentEmbeddingService.embedText(normalizedQuery);

        try {
            List<RetrievalCandidate> bm25Candidates = searchByBm25(kbId, normalizedQuery, bm25TopK);
            List<RetrievalCandidate> vectorCandidates = searchByVector(kbId, queryVector, vectorTopK);
            List<RetrievalCandidate> fusedCandidates = rrfFuse(bm25Candidates, vectorCandidates, rrfK, rrfTopK);
            if (fusedCandidates.isEmpty()) {
                return List.of();
            }

            List<RetrievalCandidate> rerankInput = new ArrayList<>(
                    fusedCandidates.subList(0, Math.min(rerankInputTopK, fusedCandidates.size())));
            RerankSelection rerankSelection = applyRerank(kbId, normalizedQuery, rerankInput, requestedTopK);
            List<RetrievalCandidate> finalCandidates = new ArrayList<>(rerankSelection.candidates());

            if (finalCandidates.size() < requestedTopK) {
                for (RetrievalCandidate candidate : fusedCandidates) {
                    if (finalCandidates.size() >= requestedTopK) {
                        break;
                    }
                    if (!rerankSelection.keys().contains(candidate.key())) {
                        finalCandidates.add(candidate);
                    }
                }
            }

            boolean rerankApplied = rerankSelection.applied();
            return finalCandidates.stream()
                    .limit(requestedTopK)
                    .map(candidate -> candidate.withRerankApplied(rerankApplied))
                    .toList();
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch hybrid search failed", ex);
        }
    }

    public List<RetrievalCandidate> searchLawArticle(Long kbId, String lawTitleCandidate, Integer articleNo, Integer topK) {
        if (kbId == null || articleNo == null || articleNo <= 0 || !indexExists()) {
            return List.of();
        }

        int requestedTopK = normalizeTopK(topK, 3, 10);
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(properties.getIndexName())
                            .size(requestedTopK)
                            .source(src -> src.filter(f -> f.includes(RETRIEVAL_SOURCE_FIELDS)))
                            .query(q -> q.bool(b -> {
                                b.filter(f -> f.term(t -> t.field("kbId").value(kbId)));
                                b.filter(f -> f.term(t -> t.field("documentDomain").value(ChunkRequest.DOCUMENT_DOMAIN_LAW)));
                                b.filter(f -> f.term(t -> t.field("articleNo").value(articleNo)));
                                if (StringUtils.hasText(lawTitleCandidate)) {
                                    b.should(sh -> sh.matchPhrase(m -> m.field("lawTitle").query(lawTitleCandidate).boost(2.2f)));
                                    b.should(sh -> sh.matchPhrase(m -> m.field("lawAlias").query(lawTitleCandidate).boost(2.0f)));
                                    b.should(sh -> sh.matchPhrase(m -> m.field("documentName").query(lawTitleCandidate).boost(1.5f)));
                                    b.should(sh -> sh.matchPhrase(m -> m.field("headings").query(lawTitleCandidate).boost(1.2f)));
                                    b.minimumShouldMatch("1");
                                }
                                return b;
                            })),
                    Map.class);
            return parseSearchCandidates(response, "law_exact").stream()
                    .limit(requestedTopK)
                    .map(candidate -> candidate.withRerankApplied(false))
                    .toList();
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch law article search failed", ex);
        }
    }

    public void assertAvailable() throws TaskException {
        try {
            if (!elasticsearchClient.ping().value()) {
                throw new TaskException("Elasticsearch 服务不可用，请检查向量库连接", TaskException.Code.CONFIG_ERROR);
            }
        } catch (ElasticsearchException ex) {
            throw asTaskException(ex);
        } catch (IOException ex) {
            throw new TaskException("Elasticsearch 服务不可用，请检查向量库连接", TaskException.Code.CONFIG_ERROR, ex);
        }
    }

    private List<RetrievalCandidate> searchByBm25(Long kbId, String queryText, int size)
            throws IOException, ElasticsearchException {
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
                        .index(properties.getIndexName())
                        .size(size)
                        .source(src -> src.filter(f -> f.includes(RETRIEVAL_SOURCE_FIELDS)))
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.term(t -> t.field("kbId").value(kbId)))
                                .should(sh -> sh.match(m -> m.field("content").query(queryText).boost(1.0f)))
                                .should(sh -> sh.match(m -> m.field("headings").query(queryText).boost(0.8f)))
                                .should(sh -> sh.match(m -> m.field("documentName").query(queryText).boost(0.6f)))
                                .should(sh -> sh.match(m -> m.field("keywords").query(queryText).boost(0.6f)))
                                .minimumShouldMatch("1"))),
                Map.class);
        return parseSearchCandidates(response, "bm25");
    }

    private List<RetrievalCandidate> searchByVector(Long kbId, float[] queryVector, int size)
            throws IOException, ElasticsearchException {
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
                        .index(properties.getIndexName())
                        .size(size)
                        .source(src -> src.filter(f -> f.includes(RETRIEVAL_SOURCE_FIELDS)))
                        .query(q -> q.scriptScore(ss -> ss
                                .query(inner -> inner.bool(b -> b
                                        .filter(f -> f.term(t -> t.field("kbId").value(kbId)))))
                                .script(script -> script
                                        .source("cosineSimilarity(params.queryVector, 'vector') + 1.0")
                                        .params("queryVector", JsonData.of(toVectorList(queryVector)))))),
                Map.class);
        return parseSearchCandidates(response, "vector");
    }

    private List<RetrievalCandidate> parseSearchCandidates(SearchResponse<Map> response, String channel) {
        if (response == null || response.hits() == null || response.hits().hits() == null) {
            return List.of();
        }

        List<RetrievalCandidate> candidates = new ArrayList<>();
        int fallbackRank = 0;
        for (Hit<Map> hit : response.hits().hits()) {
            Map source = hit.source();
            if (source == null) {
                continue;
            }
            fallbackRank++;
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceMap = new LinkedHashMap<>(source);
            String key = buildCandidateKey(sourceMap, fallbackRank);
            RetrievalCandidate candidate = new RetrievalCandidate(key, sourceMap);
            double score = hit.score() == null ? 0D : hit.score();
            if ("bm25".equals(channel)) {
                candidate.setBm25Score(score);
            } else {
                candidate.setVectorScore(score);
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    private List<RetrievalCandidate> rrfFuse(
            List<RetrievalCandidate> bm25Candidates,
            List<RetrievalCandidate> vectorCandidates,
            int rrfK,
            int topK) {
        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        applyRrfContribution(merged, bm25Candidates, rrfK, true);
        applyRrfContribution(merged, vectorCandidates, rrfK, false);

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalCandidate::rrfScore).reversed())
                .limit(topK)
                .toList();
    }

    private void applyRrfContribution(
            Map<String, RetrievalCandidate> merged,
            List<RetrievalCandidate> candidates,
            int rrfK,
            boolean bm25Channel) {
        int rank = 1;
        for (RetrievalCandidate item : candidates) {
            RetrievalCandidate current = merged.computeIfAbsent(item.key(), k -> item.copy());
            if (bm25Channel) {
                if (current.getBm25Rank() == null) {
                    current.setBm25Rank(rank);
                }
                if (current.getBm25Score() == null) {
                    current.setBm25Score(item.getBm25Score());
                }
            } else {
                if (current.getVectorRank() == null) {
                    current.setVectorRank(rank);
                }
                if (current.getVectorScore() == null) {
                    current.setVectorScore(item.getVectorScore());
                }
            }
            current.setRrfScore(current.rrfScore() + (1.0D / (rrfK + rank)));
            rank++;
        }
    }

    private RerankSelection applyRerank(Long kbId, String queryText, List<RetrievalCandidate> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return new RerankSelection(List.of(), false, Set.of());
        }
        if (!rerankService.isEnabled()) {
            List<RetrievalCandidate> sliced = new ArrayList<>(candidates.subList(0, Math.min(topN, candidates.size())));
            Set<String> keys = sliced.stream().map(RetrievalCandidate::key).collect(Collectors.toCollection(LinkedHashSet::new));
            return new RerankSelection(sliced, false, keys);
        }

        List<String> documents = candidates.stream().map(this::extractContentForRerank).toList();

        try {
            List<AlibabaRerankService.RerankResult> rerankResults =
                    rerankService.rerank(queryText, documents, Math.min(topN, candidates.size()));
            if (rerankResults.isEmpty()) {
                throw new IllegalStateException("Rerank 结果为空");
            }

            List<RetrievalCandidate> ranked = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            for (AlibabaRerankService.RerankResult result : rerankResults) {
                int index = result.index();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                RetrievalCandidate candidate = candidates.get(index);
                if (keys.contains(candidate.key())) {
                    continue;
                }
                candidate.setRerankScore(result.score());
                ranked.add(candidate);
                keys.add(candidate.key());
            }
            if (ranked.isEmpty()) {
                throw new IllegalStateException("Rerank 未返回有效排序结果");
            }
            return new RerankSelection(ranked, true, keys);
        } catch (Exception ex) {
            if (!rerankService.allowFallbackToRrf()) {
                throw new IllegalStateException("Rerank 失败且禁用了 RRF 降级", ex);
            }
            log.warn("Rerank 执行失败，降级使用 RRF 结果：kbId={}, error={}", kbId, ex.getMessage());
            List<RetrievalCandidate> fallback =
                    new ArrayList<>(candidates.subList(0, Math.min(topN, candidates.size())));
            Set<String> keys = fallback.stream().map(RetrievalCandidate::key).collect(Collectors.toCollection(LinkedHashSet::new));
            return new RerankSelection(fallback, false, keys);
        }
    }

    private String extractContentForRerank(RetrievalCandidate candidate) {
        String content = asString(candidate.source().get("content"));
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        int maxLength = 2000;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private int resolveMaxResultWindow() {
        return safePositive(retrievalProperties.getMaxResultWindow(), 20);
    }

    private int normalizeTopK(Integer topK, int defaultValue, int maxValue) {
        int value = topK == null || topK <= 0 ? defaultValue : topK;
        return Math.min(value, maxValue);
    }

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String buildCandidateKey(Map<String, Object> source, int fallbackRank) {
        String indexId = asString(source.get("indexId"));
        if (StringUtils.hasText(indexId)) {
            return indexId;
        }

        Long docId = asLong(source.get("docId"));
        Long chunkId = asLong(source.get("chunkId"));
        if (docId != null && chunkId != null) {
            return docId + "-" + chunkId;
        }
        return "hit-" + fallbackRank;
    }

    private void ensureIndexExists() {
        if (indexExists()) {
            return;
        }

        Integer dimensions = properties.getDimensions();
        if (dimensions == null || dimensions <= 0) {
            throw new IllegalStateException("Elasticsearch vector dimensions must be configured.");
        }

        Map<String, Property> fields = new LinkedHashMap<>();
        fields.put("kbId", Property.of(p -> p.long_(v -> v)));
        fields.put("docId", Property.of(p -> p.long_(v -> v)));
        fields.put("chunkId", Property.of(p -> p.long_(v -> v)));
        fields.put("indexId", Property.of(p -> p.keyword(v -> v)));
        fields.put("blockId", Property.of(p -> p.keyword(v -> v)));
        fields.put("content", Property.of(p -> p.text(v -> v)));
        fields.put("headings", Property.of(p -> p.text(v -> v)));
        fields.put("keywords", Property.of(p -> p.keyword(v -> v)));
        fields.put("blockType", Property.of(p -> p.keyword(v -> v)));
        fields.put("chunkOrder", Property.of(p -> p.integer(v -> v)));
        fields.put("charCount", Property.of(p -> p.long_(v -> v)));
        fields.put("documentName", Property.of(p -> p.text(v -> v)));
        fields.put("fileType", Property.of(p -> p.keyword(v -> v)));
        fields.put("path", Property.of(p -> p.keyword(v -> v)));
        fields.put("metadataValues", Property.of(p -> p.text(v -> v)));
        fields.put("chunkMetadataValues", Property.of(p -> p.text(v -> v)));
        fields.put("documentDomain", Property.of(p -> p.keyword(v -> v)));
        fields.put("lawTitle", Property.of(p -> p.text(v -> v)));
        fields.put("lawAlias", Property.of(p -> p.text(v -> v)));
        fields.put("lawVersion", Property.of(p -> p.keyword(v -> v)));
        fields.put("chapterNo", Property.of(p -> p.integer(v -> v)));
        fields.put("chapterTitle", Property.of(p -> p.text(v -> v)));
        fields.put("articleNo", Property.of(p -> p.integer(v -> v)));
        fields.put("articleCn", Property.of(p -> p.keyword(v -> v)));
        fields.put("articleKey", Property.of(p -> p.keyword(v -> v)));
        fields.put("articleTextType", Property.of(p -> p.keyword(v -> v)));
        fields.put("vector", Property.of(p -> p.denseVector(v -> v
                .dims(dimensions)
                .index(true)
                .similarity(properties.getSimilarity()))));

        try {
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(properties.getIndexName())
                    .settings(s -> s
                            .numberOfShards(String.valueOf(properties.getShards()))
                            .numberOfReplicas(String.valueOf(properties.getReplicas())))
                    .mappings(TypeMapping.of(m -> m.properties(fields)))));
        } catch (IOException | ElasticsearchException ex) {
            throw new IllegalStateException("Elasticsearch create index failed", ex);
        }
    }

    private boolean indexExists() {
        try {
            return elasticsearchClient.indices()
                    .exists(ExistsRequest.of(r -> r.index(properties.getIndexName())))
                    .value();
        } catch (ElasticsearchException ex) {
            throw new IllegalStateException(resolveElasticsearchMessage(ex), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Elasticsearch 服务不可用，请检查向量库连接", ex);
        }
    }

    private Map<String, Object> buildSource(KnowledgeDocument document, DocumentChunk chunk, float[] vector) {
        Map<String, Object> source = new LinkedHashMap<>();
        LawDocumentInfo lawDocumentInfo = resolveLawDocumentInfo(document);
        ChunkLawMetadata chunkLawMetadata = parseChunkLawMetadata(chunk.getMetadataValues());
        source.put("kbId", document.getKbId());
        source.put("docId", document.getDocId());
        source.put("chunkId", chunk.getChunkId());
        source.put("indexId", chunk.getIndexId());
        source.put("blockId", chunk.getIndexId());
        source.put("content", TableChunkContentSupport.toPlainText(chunk.getChunkType(), chunk.getChunkContent()));
        source.put("headings", parseStringArray(chunk.getHeadings()));
        source.put("keywords", parseStringArray(chunk.getKeywords()));
        source.put("blockType", chunk.getChunkType());
        source.put("chunkOrder", chunk.getChunkOrder());
        source.put("charCount", chunk.getCharCount());
        source.put("documentName", document.getName());
        source.put("fileType", document.getFileType());
        source.put("path", document.getPath());
        source.put("metadataValues", document.getMetadataValues());
        source.put("chunkMetadataValues", chunk.getMetadataValues());
        if (StringUtils.hasText(chunkLawMetadata.documentDomain())
                || chunkLawMetadata.articleNo() != null
                || chunkLawMetadata.chapterNo() != null
                || StringUtils.hasText(lawDocumentInfo.lawTitle())) {
            source.put("documentDomain", StringUtils.hasText(chunkLawMetadata.documentDomain())
                    ? chunkLawMetadata.documentDomain()
                    : ChunkRequest.DOCUMENT_DOMAIN_LAW);
            source.put("lawTitle", lawDocumentInfo.lawTitle());
            source.put("lawAlias", lawDocumentInfo.lawAlias());
            source.put("lawVersion", lawDocumentInfo.lawVersion());
            source.put("chapterNo", chunkLawMetadata.chapterNo());
            source.put("chapterTitle", chunkLawMetadata.chapterTitle());
            source.put("articleNo", chunkLawMetadata.articleNo());
            source.put("articleCn", chunkLawMetadata.articleCn());
            source.put("articleKey", chunkLawMetadata.articleKey());
            source.put("articleTextType", chunkLawMetadata.articleTextType());
        }
        source.put("vector", toVectorList(vector));
        return source;
    }

    private List<String> parseStringArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return JSON.parseArray(raw, String.class);
        } catch (Exception ex) {
            return List.of(raw);
        }
    }

    private List<Float> toVectorList(float[] vector) {
        if (vector == null) {
            return List.of();
        }
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private void assertBulkSuccess(BulkResponse response, String operation) {
        if (!Boolean.TRUE.equals(response.errors())) {
            return;
        }

        String failedItems = response.items().stream()
                .filter(item -> item.error() != null)
                .filter(item -> isBulkFailure(operation, item.status()))
                .map(item -> item.error().reason())
                .collect(Collectors.joining(","));
        if (!failedItems.isEmpty()) {
            throw new IllegalStateException("Elasticsearch bulk " + operation + " failed: " + failedItems);
        }
    }

    private boolean isBulkFailure(String operation, int status) {
        return !("delete".equals(operation) && status == 404) && status >= 300;
    }

    private TaskException asTaskException(ElasticsearchException ex) {
        String message = resolveElasticsearchMessage(ex);
        return new TaskException(message, TaskException.Code.CONFIG_ERROR, ex);
    }

    private String resolveElasticsearchMessage(ElasticsearchException ex) {
        int status = ex.response() == null ? 0 : ex.response().status();
        if (status == 401 || status == 403) {
            return "Elasticsearch 鉴权失败，请检查用户名、密码或 API Key";
        }
        return "Elasticsearch 服务响应异常" + (status > 0 ? "：" + status : "");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String resolveLawVersion(String documentName) {
        if (!StringUtils.hasText(documentName)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})").matcher(documentName);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
    }

    private LawDocumentInfo resolveLawDocumentInfo(KnowledgeDocument document) {
        if (document == null) {
            return new LawDocumentInfo(null, null, null);
        }
        Map<String, String> metadata = parseMetadataValues(document.getMetadataValues());
        String lawTitle = firstNonBlank(
                metadata.get("法律名称"),
                metadata.get("lawTitle"),
                lawDocumentStructureAnalyzer.analyze(document.getName(), List.of(), null).lawTitle());
        String lawAlias = firstNonBlank(
                metadata.get("法律简称"),
                metadata.get("lawAlias"),
                simplifyLawAlias(lawTitle));
        return new LawDocumentInfo(lawTitle, lawAlias, resolveLawVersion(document.getName()));
    }

    private Map<String, String> parseMetadataValues(String metadataValues) {
        if (!StringUtils.hasText(metadataValues)) {
            return Map.of();
        }
        try {
            JSONObject jsonObject = JSON.parseObject(metadataValues);
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                values.put(key, value == null ? null : String.valueOf(value));
            }
            return values;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String simplifyLawAlias(String lawTitle) {
        if (!StringUtils.hasText(lawTitle)) {
            return null;
        }
        if (lawTitle.startsWith("中华人民共和国") && lawTitle.length() > "中华人民共和国".length()) {
            return lawTitle.substring("中华人民共和国".length());
        }
        return lawTitle;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ChunkLawMetadata parseChunkLawMetadata(String metadataValues) {
        if (!StringUtils.hasText(metadataValues)) {
            return new ChunkLawMetadata(null, null, null, null, null, null, null);
        }
        try {
            JSONObject jsonObject = JSON.parseObject(metadataValues);
            return new ChunkLawMetadata(
                    jsonObject.getString("documentDomain"),
                    jsonObject.getInteger("chapterNo"),
                    jsonObject.getString("chapterTitle"),
                    jsonObject.getInteger("articleNo"),
                    jsonObject.getString("articleCn"),
                    jsonObject.getString("articleKey"),
                    jsonObject.getString("articleTextType"));
        } catch (Exception ex) {
            return new ChunkLawMetadata(null, null, null, null, null, null, null);
        }
    }

    private record LawDocumentInfo(String lawTitle, String lawAlias, String lawVersion) {}

    private record ChunkLawMetadata(
            String documentDomain,
            Integer chapterNo,
            String chapterTitle,
            Integer articleNo,
            String articleCn,
            String articleKey,
            String articleTextType) {}

    private record RerankSelection(List<RetrievalCandidate> candidates, boolean applied, Set<String> keys) {}

    public static final class RetrievalCandidate {
        private final String key;
        private final Map<String, Object> source;
        private Double bm25Score;
        private Double vectorScore;
        private Integer bm25Rank;
        private Integer vectorRank;
        private Double rrfScore;
        private Double rerankScore;
        private boolean rerankApplied;

        private RetrievalCandidate(String key, Map<String, Object> source) {
            this.key = key;
            this.source = source;
            this.rrfScore = 0D;
        }

        private RetrievalCandidate copy() {
            RetrievalCandidate clone = new RetrievalCandidate(this.key, this.source);
            clone.bm25Score = this.bm25Score;
            clone.vectorScore = this.vectorScore;
            clone.bm25Rank = this.bm25Rank;
            clone.vectorRank = this.vectorRank;
            clone.rrfScore = this.rrfScore;
            clone.rerankScore = this.rerankScore;
            clone.rerankApplied = this.rerankApplied;
            return clone;
        }

        public RetrievalCandidate withRerankApplied(boolean rerankApplied) {
            this.rerankApplied = rerankApplied;
            return this;
        }

        public String key() {
            return key;
        }

        public Map<String, Object> source() {
            return source;
        }

        public Double rrfScore() {
            return rrfScore == null ? 0D : rrfScore;
        }

        public Double getBm25Score() {
            return bm25Score;
        }

        private void setBm25Score(Double bm25Score) {
            this.bm25Score = bm25Score;
        }

        public Double getVectorScore() {
            return vectorScore;
        }

        private void setVectorScore(Double vectorScore) {
            this.vectorScore = vectorScore;
        }

        public Integer getBm25Rank() {
            return bm25Rank;
        }

        private void setBm25Rank(Integer bm25Rank) {
            this.bm25Rank = bm25Rank;
        }

        public Integer getVectorRank() {
            return vectorRank;
        }

        private void setVectorRank(Integer vectorRank) {
            this.vectorRank = vectorRank;
        }

        public Double getRrfScore() {
            return rrfScore;
        }

        private void setRrfScore(Double rrfScore) {
            this.rrfScore = rrfScore;
        }

        public Double getRerankScore() {
            return rerankScore;
        }

        private void setRerankScore(Double rerankScore) {
            this.rerankScore = rerankScore;
        }

        public boolean isRerankApplied() {
            return rerankApplied;
        }
    }
}
