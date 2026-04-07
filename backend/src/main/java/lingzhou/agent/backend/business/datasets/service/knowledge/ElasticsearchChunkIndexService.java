package lingzhou.agent.backend.business.datasets.service.knowledge;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.capability.rag.embedding.DocumentEmbeddingService;
import lingzhou.agent.backend.capability.rag.retriever.ElasticsearchChunkRetriever;
import lingzhou.agent.backend.capability.rag.retriever.ElasticsearchChunkRetriever.RetrievalCandidate;
import lingzhou.agent.backend.common.lzException.TaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ElasticsearchChunkIndexService {

    private final ElasticsearchChunkRetriever retriever;

    public ElasticsearchChunkIndexService(ElasticsearchChunkRetriever retriever) {
        this.retriever = retriever;
    }

    public List<RecallChunkVo> hybridSearch(Long kbId, String queryText, Integer topK) {
        return retriever.hybridSearch(kbId, queryText, topK).stream()
                .map(this::toRecallChunkVo)
                .toList();
    }

    public List<RecallChunkVo> searchLawArticle(Long kbId, String lawTitleCandidate, Integer articleNo, Integer topK) {
        return retriever.searchLawArticle(kbId, lawTitleCandidate, articleNo, topK).stream()
                .map(this::toRecallChunkVo)
                .toList();
    }

    public void assertAvailable() throws TaskException {
        retriever.assertAvailable();
    }

    public void indexDocumentChunks(KnowledgeDocument document, List<DocumentEmbeddingService.VectorizedChunk> vectorizedChunks) {
        retriever.indexDocumentChunks(document, vectorizedChunks);
    }

    public void deleteByDocId(Long docId) {
        retriever.deleteByDocId(docId);
    }

    public void deleteByIndexIds(Collection<String> indexIds) {
        retriever.deleteByIndexIds(indexIds);
    }

    private RecallChunkVo toRecallChunkVo(RetrievalCandidate candidate) {
        Map<String, Object> source = candidate.source();
        RecallChunkVo item = new RecallChunkVo();
        item.setId(asString(source.get("indexId")));
        item.setDocId(asLong(source.get("docId")));
        item.setChunkId(asLong(source.get("chunkId")));
        item.setChunkLabel(buildChunkLabel(source));
        item.setContent(asString(source.get("content")));
        item.setTags(parseTags(source));
        item.setFileName(asString(source.get("documentName")));
        item.setLawTitle(asString(source.get("lawTitle")));
        item.setArticleCn(asString(source.get("articleCn")));
        item.setArticleNo(asInteger(source.get("articleNo")));
        item.setBm25Rank(candidate.getBm25Rank());
        item.setVectorRank(candidate.getVectorRank());
        item.setRrfScore(candidate.getRrfScore());
        item.setRerankScore(candidate.getRerankScore());
        item.setRerankApplied(candidate.isRerankApplied());

        if (candidate.isRerankApplied() && candidate.getRerankScore() != null) {
            item.setScore(candidate.getRerankScore());
        } else if (candidate.getRrfScore() != null) {
            item.setScore(candidate.getRrfScore());
        } else if (candidate.getVectorScore() != null) {
            item.setScore(candidate.getVectorScore());
        } else {
            item.setScore(candidate.getBm25Score());
        }
        return item;
    }

    private String buildChunkLabel(Map<String, Object> source) {
        Integer articleNo = asInteger(source.get("articleNo"));
        String articleCn = asString(source.get("articleCn"));
        if (articleNo != null && StringUtils.hasText(articleCn)) {
            return articleCn;
        }
        String blockId = asString(source.get("indexId"));
        Long charCount = asLong(source.get("charCount"));
        if (!StringUtils.hasText(blockId)) {
            blockId = "CHUNK";
        }
        long length = charCount == null ? 0L : charCount;
        return blockId + " · " + length + " 字符";
    }

    private List<String> parseTags(Map<String, Object> source) {
        List<String> tags = new ArrayList<>();
        addPrefixedTags(tags, source.get("headings"));
        addPrefixedTags(tags, source.get("keywords"));
        String blockType = asString(source.get("blockType"));
        if (StringUtils.hasText(blockType)) {
            tags.add("#" + blockType);
        }
        String articleCn = asString(source.get("articleCn"));
        if (StringUtils.hasText(articleCn)) {
            tags.add("#" + articleCn);
        }
        String lawTitle = asString(source.get("lawAlias"));
        if (!StringUtils.hasText(lawTitle)) {
            lawTitle = asString(source.get("lawTitle"));
        }
        if (StringUtils.hasText(lawTitle)) {
            tags.add("#" + lawTitle);
        }
        return tags.stream().distinct().limit(6).toList();
    }

    private void addPrefixedTags(List<String> target, Object rawValues) {
        if (!(rawValues instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            String text = asString(value);
            if (StringUtils.hasText(text)) {
                target.add("#" + text);
            }
        }
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
}
