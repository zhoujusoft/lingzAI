package lingzhou.agent.backend.business.datasets.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.mapper.DocumentChunkMapper;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeDocumentService;
import lingzhou.agent.backend.business.datasets.service.IDocumentChunkService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.datasets.service.ProgressManager;
import lingzhou.agent.backend.business.datasets.service.knowledge.ElasticsearchChunkIndexService;
import lingzhou.agent.backend.capability.rag.embedding.DocumentEmbeddingService;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequestFactory;
import lingzhou.agent.backend.capability.rag.chunk.model.ChunkedSection;
import lingzhou.agent.backend.capability.rag.chunk.service.DocumentParseChunkServiceV2;
import lingzhou.agent.backend.capability.rag.chunk.tool.LawDocumentStructureAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档解析任务
 */
@Component("ryTask")
@RequiredArgsConstructor
@Slf4j
public class DocumentParseTask {

    private final IKnowledgeDocumentService knowledgeDocumentService;
    private final IDocumentChunkService documentChunkService;
    private final MinioService minioService;
    private final ProgressManager progressManager;
    private final DocumentParseChunkServiceV2 parseChunkService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final DocumentChunkMapper documentChunkMapper;
    private final LawDocumentStructureAnalyzer lawDocumentStructureAnalyzer = new LawDocumentStructureAnalyzer();

    /**
     * 解析文档
     *
     * @param docId 文档 ID
     */
    public void parseDocument(Long docId) {
        log.info("开始解析文档：docId={}", docId);
        KnowledgeDocument document = null;
        List<DocumentChunk> persistedChunks = Collections.emptyList();

        try {
            // 1. 查询文档信息
            document = knowledgeDocumentService.selectKnowledgeDocumentByDocId(docId);
            if (document == null) {
                throw new RuntimeException("文档不存在：docId=" + docId);
            }

            // 2. 更新状态为处理中
            knowledgeDocumentService.updateStatus(docId, 1, null);
            progressManager.updateProgress(docId, 0, "PARSING", "开始解析");

            // 3. 从 MinIO 读取文件
            String objectName = document.getFileId();
            if (objectName == null || objectName.isEmpty()) {
                throw new RuntimeException("文件未上传");
            }

            InputStream inputStream = minioService.getFile(objectName);

            // 4. 构建切片请求
            ChunkRequest request =
                    ChunkRequestFactory.build(document.getFileType(), document.getChunkStrategy(), document.getChunkConfig());

            // 5. 执行解析和切片
            progressManager.updateProgress(docId, 10, "PARSING", "正在读取文件");
            List<ChunkedSection> sections = parseChunkService.parseAndChunk(inputStream, document.getName(), request);
            inputStream.close();

            // 6. 批量入库
            progressManager.updateProgress(docId, 50, "CHUNKING", "正在分块");
            persistedChunks = saveChunks(document, request, sections);

            // 7. 生成向量
            progressManager.updateProgress(docId, 75, "EMBEDDING", "正在生成向量");
            List<DocumentEmbeddingService.VectorizedChunk> vectorizedChunks =
                    documentEmbeddingService.embedChunks(persistedChunks);

            // 8. 写入 Elasticsearch
            progressManager.updateProgress(docId, 90, "INDEXING", "正在写入向量索引");
            elasticsearchChunkIndexService.indexDocumentChunks(document, vectorizedChunks);

            // 9. 更新状态为完成
            knowledgeDocumentService.updateStatus(docId, 2, null);
            progressManager.complete(docId);

            log.info("文档解析完成：docId={}, chunks={}", docId, sections.size());

        } catch (Exception e) {
            cleanupFailedState(docId, persistedChunks, document);
            log.error("文档解析失败：docId={}, error={}", docId, e.getMessage(), e);
            knowledgeDocumentService.updateStatus(docId, 3, e.getMessage());
            progressManager.fail(docId, e.getMessage());
        }
    }

    /**
     * 保存切片到数据库
     */
    @Transactional(rollbackFor = Exception.class)
    private List<DocumentChunk> saveChunks(KnowledgeDocument document, ChunkRequest request, List<ChunkedSection> sections) {
        int batchSize = 200;
        int order = 1;
        Long docId = document.getDocId();
        List<DocumentChunk> persistedChunks = new java.util.ArrayList<>(sections.size());

        for (int i = 0; i < sections.size(); i++) {
            ChunkedSection section = sections.get(i);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocId(docId);
            chunk.setChunkContent(section.getContent());
            chunk.setChunkOrder(order++);
            chunk.setIndexId(section.getId());
            chunk.setCharCount((long) TableChunkContentSupport.resolveVisibleLength(
                    section.getBlockType(), section.getContent()));

            // 设置标题层级
            if (section.getHeadings() != null && !section.getHeadings().isEmpty()) {
                chunk.setHeadings(JSON.toJSONString(section.getHeadings()));
            }

            // 设置分块类型
            if (section.getBlockType() != null) {
                chunk.setChunkType(section.getBlockType());
            }

            fillLawChunkFields(document, request, section, chunk);
            documentChunkService.insertDocumentChunk(chunk);
            persistedChunks.add(chunk);

            // 批量提交
            if ((i + 1) % batchSize == 0) {
                progressManager.updateProgress(docId, 50 + (i * 40 / sections.size()), "CHUNKING",
                        "已处理 " + (i + 1) + "/" + sections.size() + " 个分块");
            }
        }
        return persistedChunks;
    }

    private void fillLawChunkFields(
            KnowledgeDocument document, ChunkRequest request, ChunkedSection section, DocumentChunk chunk) {
        if (document == null || request == null || section == null || chunk == null || !request.isLawDocument()) {
            return;
        }

        LawDocumentStructureAnalyzer.LawMetadata metadata = lawDocumentStructureAnalyzer.analyze(
                document.getName(),
                section.getHeadings(),
                section.getContent());
        if (metadata.chapterNo() == null && metadata.articleNo() == null) {
            return;
        }

        chunk.setMetadataValues(buildLawChunkMetadata(metadata));
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

    private void cleanupFailedState(Long docId, List<DocumentChunk> persistedChunks, KnowledgeDocument document) {
        try {
            if (persistedChunks != null && !persistedChunks.isEmpty()) {
                List<String> indexIds = persistedChunks.stream()
                        .map(DocumentChunk::getIndexId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
                if (!indexIds.isEmpty()) {
                    elasticsearchChunkIndexService.deleteByIndexIds(indexIds);
                } else {
                    elasticsearchChunkIndexService.deleteByDocId(docId);
                }
            } else if (document != null) {
                elasticsearchChunkIndexService.deleteByDocId(docId);
            }
        } catch (Exception cleanupEx) {
            log.error("清理 Elasticsearch 索引失败：docId={}, error={}", docId, cleanupEx.getMessage(), cleanupEx);
        }

        try {
            documentChunkMapper.deleteDocumentChunkByDocId(docId);
        } catch (Exception cleanupEx) {
            log.error("清理分块数据失败：docId={}, error={}", docId, cleanupEx.getMessage(), cleanupEx);
        }
    }

}
