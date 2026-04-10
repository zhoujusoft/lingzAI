package lingzhou.agent.backend.capability.dataset.runtime;

import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.knowledge.KnowledgeChunkSearchService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseToolRuntimeService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private final ToolCatalogMapper toolCatalogMapper;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;

    public KnowledgeBaseToolRuntimeService(
            ToolCatalogMapper toolCatalogMapper,
            IKnowledgeBaseService knowledgeBaseService,
            KnowledgeChunkSearchService knowledgeChunkSearchService) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
    }

    public SearchKnowledgeBaseResult search(String toolName, SearchKnowledgeBaseRequest request) throws TaskException {
        ResolvedKnowledgeBase resolved = resolveKnowledgeBase(toolName);
        String query = normalizeRequired(request == null ? null : request.query(), "query 不能为空");
        int topK = normalizeTopK(request == null ? null : request.topK());
        List<ChunkHit> hits = knowledgeChunkSearchService.recall(resolved.kbId(), query, topK).stream()
                .map(this::toChunkHit)
                .toList();
        return new SearchKnowledgeBaseResult(resolved.kbId(), resolved.kbName(), query, topK, hits);
    }

    private ResolvedKnowledgeBase resolveKnowledgeBase(String toolName) throws TaskException {
        if (!StringUtils.hasText(toolName)) {
            throw new TaskException("toolName 不能为空", TaskException.Code.UNKNOWN);
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null || !StringUtils.hasText(catalog.getSource()) || !catalog.getSource().startsWith("knowledge_base:")) {
            throw new TaskException("未找到对应的知识库工具：" + toolName, TaskException.Code.UNKNOWN);
        }
        String kbCode = catalog.getSource().substring("knowledge_base:".length()).trim();
        if (!StringUtils.hasText(kbCode)) {
            throw new TaskException("知识库工具来源无效：" + catalog.getSource(), TaskException.Code.UNKNOWN);
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.selectKnowledgeBaseByKbCode(kbCode);
        if (knowledgeBase == null) {
            throw new TaskException("知识库不存在：" + kbCode, TaskException.Code.UNKNOWN);
        }
        return new ResolvedKnowledgeBase(knowledgeBase.getKbId(), knowledgeBase.getKbName());
    }

    private ChunkHit toChunkHit(RecallChunkVo item) {
        return new ChunkHit(
                item.getChunkId(),
                item.getDocId(),
                item.getFileName(),
                item.getChunkLabel(),
                item.getContent(),
                item.getScore(),
                item.getTags());
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.min(Math.max(topK, 1), MAX_TOP_K);
    }

    public record SearchKnowledgeBaseRequest(String query, Integer topK) {}

    public record SearchKnowledgeBaseResult(
            Long kbId, String kbName, String query, Integer topK, List<ChunkHit> hits) {}

    public record ChunkHit(
            Long chunkId,
            Long docId,
            String documentName,
            String chunkLabel,
            String content,
            Double score,
            List<String> tags) {}

    private record ResolvedKnowledgeBase(Long kbId, String kbName) {}
}
