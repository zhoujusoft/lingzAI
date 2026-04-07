package lingzhou.agent.backend.business.datasets.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.mapper.DocumentChunkMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeDocumentMapper;
import lingzhou.agent.backend.business.datasets.service.IDocumentChunkService;
import lingzhou.agent.backend.business.datasets.service.knowledge.ElasticsearchChunkIndexService;
import lingzhou.agent.backend.capability.rag.embedding.DocumentEmbeddingService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequestFactory;
import lingzhou.agent.backend.capability.rag.chunk.tool.LawDocumentStructureAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentChunkServiceImpl implements IDocumentChunkService {

    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final LawDocumentStructureAnalyzer lawDocumentStructureAnalyzer = new LawDocumentStructureAnalyzer();

    public DocumentChunkServiceImpl(
            DocumentChunkMapper documentChunkMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            DocumentEmbeddingService documentEmbeddingService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService) {
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentEmbeddingService = documentEmbeddingService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    @Override
    public DocumentChunk selectDocumentChunkByChunkId(Long chunkId) {
        return documentChunkMapper.selectDocumentChunkByChunkId(chunkId);
    }

    @Override
    public List<DocumentChunk> selectDocumentChunkList(DocumentChunk documentChunk) {
        return documentChunkMapper.selectDocumentChunkList(documentChunk);
    }

    @Override
    public IPage<DocumentChunk> selectDocumentChunkPage(DocumentChunk documentChunk, long pageNum, long pageSize) {
        return documentChunkMapper.selectDocumentChunkPage(documentChunk, pageNum, pageSize);
    }

    @Override
    public int insertDocumentChunk(DocumentChunk documentChunk) {
        return documentChunkMapper.insertDocumentChunk(documentChunk);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDocumentChunk(String kbId, DocumentChunk documentChunk) throws TaskException {
        if (documentChunk == null || documentChunk.getChunkId() == null) {
            throw new TaskException("分块不存在", TaskException.Code.UNKNOWN);
        }
        if (StringUtils.isBlank(documentChunk.getChunkContent())) {
            throw new TaskException("分块内容不能为空", TaskException.Code.UNKNOWN);
        }

        DocumentChunk persisted = documentChunkMapper.selectDocumentChunkByChunkId(documentChunk.getChunkId());
        if (persisted == null) {
            throw new TaskException("分块不存在", TaskException.Code.UNKNOWN);
        }

        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(persisted.getDocId());
        if (document == null || Long.valueOf(1L).equals(document.getIsFolder())) {
            throw new TaskException("所属文档不存在", TaskException.Code.UNKNOWN);
        }
        if (StringUtils.isNotBlank(kbId) && !String.valueOf(document.getKbId()).equals(kbId)) {
            throw new TaskException("分块不属于当前知识库", TaskException.Code.UNKNOWN);
        }
        if (!Integer.valueOf(2).equals(document.getStatus())) {
            throw new TaskException("仅已完成的文档支持编辑分块", TaskException.Code.UNKNOWN);
        }

        elasticsearchChunkIndexService.assertAvailable();

        String nextContent = documentChunk.getChunkContent().trim();
        String nextChunkType = StringUtils.defaultIfBlank(documentChunk.getChunkType(), persisted.getChunkType());
        if (StringUtils.isBlank(nextChunkType)) {
            nextChunkType = "TEXT";
        }

        persisted.setChunkContent(nextContent);
        persisted.setChunkType(nextChunkType);
        persisted.setCharCount((long) TableChunkContentSupport.resolveVisibleLength(nextChunkType, nextContent));
        if (documentChunk.getHeadings() != null) {
            persisted.setHeadings(documentChunk.getHeadings());
        }
        if (documentChunk.getKeywords() != null) {
            persisted.setKeywords(documentChunk.getKeywords());
        }
        refreshLawChunkFields(document, persisted);

        int affected = documentChunkMapper.updateDocumentChunk(persisted);
        if (affected <= 0) {
            throw new TaskException("分块更新失败", TaskException.Code.UNKNOWN);
        }

        DocumentEmbeddingService.VectorizedChunk vectorizedChunk =
                documentEmbeddingService.embedChunks(List.of(persisted)).get(0);
        elasticsearchChunkIndexService.indexDocumentChunks(document, List.of(vectorizedChunk));
        return affected;
    }

    @Override
    public int deleteDocumentChunkByChunkIds(Long[] chunkIds) {
        return documentChunkMapper.deleteDocumentChunkByChunkIds(chunkIds);
    }

    @Override
    public int deleteDocumentChunkByChunkId(Long chunkId) {
        return documentChunkMapper.deleteDocumentChunkByChunkId(chunkId);
    }

    @Override
    public DocumentChunk selectDocumentChunkByIndexId(String indexId) {
        DocumentChunk param = new DocumentChunk();
        param.setIndexId(indexId);
        List<DocumentChunk> list = documentChunkMapper.selectDocumentChunkList(param);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private void refreshLawChunkFields(KnowledgeDocument document, DocumentChunk chunk) {
        if (document == null || chunk == null) {
            return;
        }

        ChunkRequest request =
                ChunkRequestFactory.build(document.getFileType(), document.getChunkStrategy(), document.getChunkConfig());
        if (!request.isLawDocument()) {
            chunk.setMetadataValues(null);
            return;
        }

        LawDocumentStructureAnalyzer.LawMetadata metadata = lawDocumentStructureAnalyzer.analyze(
                document.getName(),
                parseHeadings(chunk.getHeadings()),
                chunk.getChunkContent());
        chunk.setMetadataValues(buildLawChunkMetadata(metadata));
    }

    private List<String> parseHeadings(String rawHeadings) {
        if (StringUtils.isBlank(rawHeadings)) {
            return List.of();
        }
        try {
            return JSON.parseArray(rawHeadings, String.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String buildLawChunkMetadata(LawDocumentStructureAnalyzer.LawMetadata metadata) {
        JSONObject values = new JSONObject(true);
        values.put("documentDomain", ChunkRequest.DOCUMENT_DOMAIN_LAW);
        values.put("chapterNo", metadata.chapterNo());
        values.put("chapterTitle", metadata.chapterTitle());
        values.put("articleNo", metadata.articleNo());
        values.put("articleCn", metadata.articleCn());
        values.put("articleKey", metadata.articleKey());
        values.put("articleTextType", metadata.articleTextType());
        return values.toJSONString();
    }
}
