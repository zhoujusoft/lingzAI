package lingzhou.agent.backend.business.datasets.service.knowledge;

import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeChunkSearchService {

    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final LawQueryParser lawQueryParser = new LawQueryParser();

    public KnowledgeChunkSearchService(ElasticsearchChunkIndexService elasticsearchChunkIndexService) {
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    public SearchResult searchForQa(Long kbId, String query, Integer topK) {
        if (kbId == null || !StringUtils.hasText(query)) {
            return new SearchResult(List.of(), "NONE");
        }

        String retrievalStrategy = "HYBRID";
        List<RecallChunkVo> recalls = List.of();

        LawQueryParser.ParsedLawQuery parsedLawQuery = lawQueryParser.parse(query);
        if (parsedLawQuery.matched()) {
            recalls = elasticsearchChunkIndexService.searchLawArticle(
                    kbId, parsedLawQuery.lawTitleCandidate(), parsedLawQuery.articleNo(), topK);
            if (!recalls.isEmpty()) {
                retrievalStrategy = "LAW_ARTICLE";
            }
        }

        if (recalls.isEmpty()) {
            recalls = elasticsearchChunkIndexService.hybridSearch(kbId, query, topK);
        }

        return new SearchResult(recalls, retrievalStrategy);
    }

    public List<RecallChunkVo> recall(Long kbId, String query, Integer topK) {
        return elasticsearchChunkIndexService.hybridSearch(kbId, query, topK);
    }

    public record SearchResult(List<RecallChunkVo> recalls, String retrievalStrategy) {}
}
