package lingzhou.agent.backend.business.datasets.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.alibaba.fastjson.JSON;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.MateDataParam;
import lingzhou.agent.backend.business.datasets.domain.VO.AppendDocumentChunkRequest;
import lingzhou.agent.backend.business.datasets.domain.VO.ChunkPreviewVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentDetailVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentTreeNodeVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentVo;
import lingzhou.agent.backend.business.datasets.mapper.DocumentChunkMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeDocumentMapper;
import lingzhou.agent.backend.business.datasets.service.IDocumentMetadataService;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeDocumentService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.datasets.service.knowledge.ElasticsearchChunkIndexService;
import lingzhou.agent.backend.business.monitor.service.ISysJobService;
import lingzhou.agent.backend.capability.rag.embedding.DocumentEmbeddingService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequestFactory;
import lingzhou.agent.backend.capability.rag.chunk.model.ChunkedSection;
import lingzhou.agent.backend.capability.rag.chunk.service.DocumentParseChunkServiceV2;
import lingzhou.agent.backend.capability.rag.chunk.tool.LawDocumentStructureAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class KnowledgeDocumentServiceImpl implements IKnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ISysJobService sysJobService;
    private final IDocumentMetadataService documentMetadataService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final MinioService minioService;
    private final DocumentParseChunkServiceV2 parseChunkService;
    private final LawDocumentStructureAnalyzer lawDocumentStructureAnalyzer = new LawDocumentStructureAnalyzer();

    public KnowledgeDocumentServiceImpl(
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            DocumentChunkMapper documentChunkMapper,
            ISysJobService sysJobService,
            IDocumentMetadataService documentMetadataService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            DocumentEmbeddingService documentEmbeddingService,
            MinioService minioService,
            DocumentParseChunkServiceV2 parseChunkService) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.sysJobService = sysJobService;
        this.documentMetadataService = documentMetadataService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.minioService = minioService;
        this.parseChunkService = parseChunkService;
    }

    @Override
    public KnowledgeDocument selectKnowledgeDocumentByDocId(Long docId) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
    }

    @Override
    public List<KnowledgeDocument> selectKnowledgeDocumentList(KnowledgeDocument knowledgeDocument) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentList(knowledgeDocument);
    }

    @Override
    public IPage<KnowledgeDocument> selectKnowledgeDocumentPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentPage(knowledgeDocument, pageNum, pageSize);
    }

    @Override
    public List<KnowledgeDocument> selectKnowledgeDocumentListByKbId(KnowledgeDocument knowledgeDocument) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentListByKbId(knowledgeDocument);
    }

    @Override
    public IPage<KnowledgeDocument> selectKnowledgeDocumentByKbIdPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentByKbIdPage(knowledgeDocument, pageNum, pageSize);
    }

    @Override
    public List<KnowledgeDocument> selectKnowledgeDocumentListByMetadata(MateDataParam mateDataParam) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentListByMetadata(mateDataParam);
    }

    @Override
    public List<String> selectKnowledgeDocumentIdsByMetadata(MateDataParam mateDataParam) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentIdsByMetadata(mateDataParam);
    }

    @Override
    public int insertKnowledgeDocument(KnowledgeDocument knowledgeDocument) throws SchedulerException, TaskException {
        if (Long.valueOf(1L).equals(knowledgeDocument.getIsFolder())) {
            documentMetadataService.insertDocumentMetadataBatch(
                    knowledgeDocument.getKbId(), knowledgeDocument.getName());
        } else {
            JSONObject metadata = new JSONObject();
            metadata.put("文件编号", "");
            metadata.put("文件年份", "");
            knowledgeDocument.setMetadataValues(metadata.toJSONString());
        }

        int row = knowledgeDocumentMapper.insertKnowledgeDocument(knowledgeDocument);
        if (row > 0
                && Long.valueOf(0L).equals(knowledgeDocument.getIsFolder())
                && StringUtils.isNotBlank(knowledgeDocument.getFileId())) {
            sysJobService.runParseDocument(knowledgeDocument.getDocId());
        }
        return row;
    }

    @Override
    public int updateKnowledgeDocument(KnowledgeDocument knowledgeDocument) {
        return knowledgeDocumentMapper.updateKnowledgeDocument(knowledgeDocument);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeDocumentByDocIds(Long[] docIds) throws Exception {
        if (docIds == null || docIds.length == 0) {
            return 0;
        }

        int affected = 0;
        for (Long docId : docIds) {
            if (docId == null) {
                continue;
            }
            affected += deleteKnowledgeDocumentByDocId(null, docId);
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeDocumentByDocId(Long kbId, Long docId) throws Exception {
        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (document == null) {
            return 0;
        }
        if (kbId != null && !kbId.equals(document.getKbId())) {
            throw new TaskException("文档不属于当前知识库", TaskException.Code.UNKNOWN);
        }
        deleteNodeRecursively(document);
        return 1;
    }

    @Override
    public KnowledgeDocumentVo selectKnowledgeDocumentVoByDocId(Long docId) {
        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (knowledgeDocument == null) {
            return null;
        }
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo(knowledgeDocument);
        vo.setDocumentChunks(documentChunkMapper.selectDocumentChunkByDocId(docId));
        return vo;
    }

    @Override
    public KnowledgeDocumentDetailVo selectKnowledgeDocumentDetailByDocId(Long docId) throws TaskException {
        KnowledgeDocument document = requireProcessableDocument(docId);
        KnowledgeDocumentDetailVo detail = new KnowledgeDocumentDetailVo(document);
        detail.setChunkCount(documentChunkMapper.countByDocId(docId));
        detail.setCharCount(documentChunkMapper.sumCharCountByDocId(docId));
        return detail;
    }

    @Override
    public List<KnowledgeDocument> findByPrefixes(List<String> prefixes, Long kbId, String fileCode) {
        return knowledgeDocumentMapper.findByPrefixes(prefixes, kbId, fileCode);
    }

    @Override
    public List<String> findDocIdsByTechSpec() {
        return knowledgeDocumentMapper.findDocIdsByTechSpec();
    }

    @Override
    public List<String> findDocIdsByTechSpecByKbId(String kbId) {
        return knowledgeDocumentMapper.findDocIdsByTechSpecByKbId(kbId);
    }

    @Override
    public List<KnowledgeDocument> selectKnowledgeDocumentListByParentDocId(String parentDocId, String kbId) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentListByParentDocId(parentDocId, kbId);
    }

    @Override
    public List<KnowledgeDocumentTreeNodeVo> selectKnowledgeDocumentTree(Long kbId) {
        KnowledgeDocument query = new KnowledgeDocument();
        query.setKbId(kbId);
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectKnowledgeDocumentListByKbId(query);
        Map<Long, KnowledgeDocumentTreeNodeVo> nodeMap = new HashMap<>();
        List<KnowledgeDocumentTreeNodeVo> roots = new ArrayList<>();

        for (KnowledgeDocument document : documents) {
            nodeMap.put(document.getDocId(), new KnowledgeDocumentTreeNodeVo(document));
        }
        for (KnowledgeDocument document : documents) {
            KnowledgeDocumentTreeNodeVo current = nodeMap.get(document.getDocId());
            if (document.getParentId() == null) {
                roots.add(current);
                continue;
            }
            KnowledgeDocumentTreeNodeVo parent = nodeMap.get(document.getParentId());
            if (parent == null) {
                roots.add(current);
                continue;
            }
            parent.getChildren().add(current);
        }
        sortTreeNodes(roots);
        return roots;
    }

    @Override
    public List<KnowledgeDocument> selectKnowledgeDocumentChildren(Long kbId, Long parentId) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentChildren(kbId, parentId);
    }

    @Override
    public List<String> selectDocIdByKbId(String kbId) {
        return knowledgeDocumentMapper.selectDocIdByKbId(kbId);
    }

    @Override
    public List<String> selectJiaoCaiIds(Long kbId) {
        return knowledgeDocumentMapper.selectJiaoCaiIds(kbId);
    }

    @Override
    public List<String> selectKnowledgeDocumentIds(KnowledgeDocument knowledgeDocument) {
        return knowledgeDocumentMapper.selectKnowledgeDocumentIds(knowledgeDocument);
    }

    @Override
    public int updateStatus(Long docId, Integer status, String errorMessage) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocId(docId);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        return knowledgeDocumentMapper.updateKnowledgeDocument(document);
    }

    @Override
    public List<ChunkPreviewVo> previewChunks(Long docId, String chunkStrategy, String chunkConfig) throws Exception {
        KnowledgeDocument document = requireProcessableDocument(docId);
        ChunkRequest request = ChunkRequestFactory.build(document.getFileType(), chunkStrategy, chunkConfig);

        try (InputStream inputStream = minioService.getFile(document.getFileId())) {
            List<ChunkedSection> sections = parseChunkService.parseAndChunk(inputStream, document.getName(), request);
            List<ChunkPreviewVo> previews = new ArrayList<>();
            for (int i = 0; i < sections.size() && i < 10; i++) {
                previews.add(new ChunkPreviewVo(sections.get(i), i + 1));
            }
            return previews;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitChunkConfigAndParse(Long docId, String chunkStrategy, String chunkConfig)
            throws SchedulerException, TaskException {
        KnowledgeDocument document = requireProcessableDocument(docId);
        if (document.getStatus() != null && document.getStatus() == 1) {
            throw new TaskException("文档处理中，不能重复提交", TaskException.Code.UNKNOWN);
        }

        elasticsearchChunkIndexService.assertAvailable();
        cleanupDocumentChunks(docId);

        KnowledgeDocument update = new KnowledgeDocument();
        update.setDocId(docId);
        update.setChunkStrategy(StringUtils.defaultIfBlank(chunkStrategy, "AUTO"));
        update.setChunkConfig(chunkConfig);
        update.setStatus(0);
        update.setErrorMessage(null);
        knowledgeDocumentMapper.updateKnowledgeDocument(update);
        sysJobService.runParseDocument(docId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createKnowledgeDocumentWithFile(
            Long kbId, Long parentId, MultipartFile file, String chunkStrategy, String chunkConfig) throws Exception {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKbId(kbId);
        document.setParentId(parentId);
        document.setName(file.getOriginalFilename());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setFileSize(file.getSize());
        document.setStatus(0);
        document.setChunkStrategy(chunkStrategy);
        document.setChunkConfig(chunkConfig);
        document.setIsFolder(0L);

        String objectName = null;
        try {
            populateHierarchy(document);
            insertKnowledgeDocument(document);
            objectName = minioService.uploadFile(file, kbId, document.getDocId());
            document.setFileId(objectName);
            knowledgeDocumentMapper.updateKnowledgeDocument(document);
            return document;
        } catch (Exception ex) {
            rollbackCreatedDocument(document, objectName);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createFolder(Long kbId, Long parentId, String name) throws Exception {
        KnowledgeDocument folder = new KnowledgeDocument();
        folder.setKbId(kbId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setIsFolder(1L);
        folder.setStatus(2);
        populateHierarchy(folder);
        insertKnowledgeDocument(folder);
        return folder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int renameFolder(Long kbId, Long docId, String name) throws TaskException {
        KnowledgeDocument folder = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (folder == null || !kbId.equals(folder.getKbId())) {
            throw new TaskException("目录不存在", TaskException.Code.UNKNOWN);
        }
        if (!Long.valueOf(1L).equals(folder.getIsFolder())) {
            throw new TaskException("仅目录支持重命名", TaskException.Code.UNKNOWN);
        }

        String normalizedName = normalizeNodeName(name);
        String oldPath = folder.getPath();
        String newPath = buildNodePath(folder.getKbId(), folder.getParentId(), normalizedName);

        KnowledgeDocument update = new KnowledgeDocument();
        update.setDocId(docId);
        update.setName(normalizedName);
        update.setPath(newPath);
        int affected = knowledgeDocumentMapper.updateKnowledgeDocument(update);

        String descendantPrefix = oldPath + "/";
        List<KnowledgeDocument> descendants =
                knowledgeDocumentMapper.selectDescendantsByPathPrefix(kbId, descendantPrefix, docId);
        for (KnowledgeDocument descendant : descendants) {
            KnowledgeDocument descendantUpdate = new KnowledgeDocument();
            descendantUpdate.setDocId(descendant.getDocId());
            descendantUpdate.setPath(newPath + descendant.getPath().substring(oldPath.length()));
            knowledgeDocumentMapper.updateKnowledgeDocument(descendantUpdate);
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int moveNode(Long kbId, Long docId, Long targetParentId) throws TaskException {
        KnowledgeDocument node = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (node == null || !kbId.equals(node.getKbId())) {
            throw new TaskException("节点不存在", TaskException.Code.UNKNOWN);
        }
        if (targetParentId != null && targetParentId.equals(docId)) {
            throw new TaskException("不能移动到自身目录", TaskException.Code.UNKNOWN);
        }
        if (targetParentId != null) {
            KnowledgeDocument targetParent = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(targetParentId);
            if (targetParent == null || !kbId.equals(targetParent.getKbId())) {
                throw new TaskException("目标目录不存在", TaskException.Code.UNKNOWN);
            }
            if (!Long.valueOf(1L).equals(targetParent.getIsFolder())) {
                throw new TaskException("目标节点不是目录", TaskException.Code.UNKNOWN);
            }
            if (Long.valueOf(1L).equals(node.getIsFolder())
                    && StringUtils.isNotBlank(targetParent.getPath())
                    && StringUtils.isNotBlank(node.getPath())
                    && targetParent.getPath().startsWith(node.getPath() + "/")) {
                throw new TaskException("目录不能移动到自己的子目录", TaskException.Code.UNKNOWN);
            }
        }

        String oldPath = node.getPath();
        String newPath = buildNodePath(kbId, targetParentId, node.getName());
        if (StringUtils.equals(oldPath, newPath)
                && ((node.getParentId() == null && targetParentId == null)
                        || (node.getParentId() != null && node.getParentId().equals(targetParentId)))) {
            return 0;
        }

        KnowledgeDocument update = new KnowledgeDocument();
        update.setDocId(docId);
        update.setParentId(targetParentId);
        update.setPath(newPath);
        int affected = knowledgeDocumentMapper.updateKnowledgeDocument(update);

        if (Long.valueOf(1L).equals(node.getIsFolder()) && StringUtils.isNotBlank(oldPath)) {
            String descendantPrefix = oldPath + "/";
            List<KnowledgeDocument> descendants =
                    knowledgeDocumentMapper.selectDescendantsByPathPrefix(kbId, descendantPrefix, docId);
            for (KnowledgeDocument descendant : descendants) {
                KnowledgeDocument descendantUpdate = new KnowledgeDocument();
                descendantUpdate.setDocId(descendant.getDocId());
                descendantUpdate.setPath(newPath + descendant.getPath().substring(oldPath.length()));
                knowledgeDocumentMapper.updateKnowledgeDocument(descendantUpdate);
            }
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryParseDocument(Long docId) throws SchedulerException, TaskException {
        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (document == null) {
            throw new TaskException("文档不存在", TaskException.Code.UNKNOWN);
        }
        if (!Integer.valueOf(3).equals(document.getStatus())) {
            throw new TaskException("仅失败状态的文档可重试", TaskException.Code.UNKNOWN);
        }

        elasticsearchChunkIndexService.assertAvailable();
        cleanupDocumentChunks(docId);
        document.setStatus(0);
        document.setErrorMessage(null);
        knowledgeDocumentMapper.updateKnowledgeDocument(document);
        sysJobService.runParseDocument(docId);
        return 1;
    }

    @Override
    public int startParseDocument(Long docId) throws SchedulerException, TaskException {
        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (document == null) {
            throw new TaskException("文档不存在", TaskException.Code.UNKNOWN);
        }
        if (StringUtils.isBlank(document.getFileId())) {
            throw new TaskException("文档文件未上传完成", TaskException.Code.UNKNOWN);
        }
        sysJobService.runParseDocument(docId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentChunk appendDocumentChunk(Long docId, AppendDocumentChunkRequest request) throws TaskException {
        KnowledgeDocument document = requireProcessableDocument(docId);
        if (!Integer.valueOf(2).equals(document.getStatus())) {
            throw new TaskException("仅已完成的文档支持追加分块", TaskException.Code.UNKNOWN);
        }
        if (request == null || StringUtils.isBlank(request.getChunkContent())) {
            throw new TaskException("分块内容不能为空", TaskException.Code.UNKNOWN);
        }

        String content = request.getChunkContent().trim();
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocId(docId);
        chunk.setChunkContent(content);
        chunk.setChunkOrder(documentChunkMapper.selectMaxChunkOrderByDocId(docId) + 1);
        chunk.setIndexId(buildManualChunkIndexId(docId));
        String chunkType = StringUtils.defaultIfBlank(request.getChunkType(), "MANUAL");
        chunk.setCharCount((long) TableChunkContentSupport.resolveVisibleLength(chunkType, content));
        chunk.setChunkType(chunkType);
        chunk.setHeadings(toJsonArray(request.getHeadings()));
        chunk.setKeywords(toJsonArray(request.getKeywords()));
        fillLawChunkFields(document, chunk, request.getHeadings(), content);

        documentChunkMapper.insertDocumentChunk(chunk);
        DocumentEmbeddingService.VectorizedChunk vectorizedChunk =
                documentEmbeddingService.embedChunks(List.of(chunk)).get(0);
        elasticsearchChunkIndexService.indexDocumentChunks(document, List.of(vectorizedChunk));
        return chunk;
    }

    private void deleteNodeRecursively(KnowledgeDocument document) throws Exception {
        List<KnowledgeDocument> children =
                knowledgeDocumentMapper.selectKnowledgeDocumentChildren(document.getKbId(), document.getDocId());
        for (KnowledgeDocument child : children) {
            deleteNodeRecursively(child);
        }
        deleteSourceFileQuietly(document);
        if (!Long.valueOf(1L).equals(document.getIsFolder())) {
            cleanupDocumentChunksForDeletion(document.getDocId());
        }
        knowledgeDocumentMapper.deleteKnowledgeDocumentByDocId(document.getDocId());
    }

    private void cleanupDocumentChunks(Long docId) throws TaskException {
        List<DocumentChunk> chunks = documentChunkMapper.selectDocumentChunkByDocId(docId);
        List<String> indexIds = chunks.stream()
                .map(DocumentChunk::getIndexId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        try {
            if (!indexIds.isEmpty()) {
                elasticsearchChunkIndexService.deleteByIndexIds(indexIds);
            } else {
                elasticsearchChunkIndexService.deleteByDocId(docId);
            }
        } catch (IllegalStateException ex) {
            throw new TaskException(ex.getMessage(), TaskException.Code.CONFIG_ERROR, ex);
        }
        documentChunkMapper.deleteDocumentChunkByDocId(docId);
    }

    private void cleanupDocumentChunksForDeletion(Long docId) {
        List<DocumentChunk> chunks = documentChunkMapper.selectDocumentChunkByDocId(docId);
        List<String> indexIds = chunks.stream()
                .map(DocumentChunk::getIndexId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        try {
            if (!indexIds.isEmpty()) {
                elasticsearchChunkIndexService.deleteByIndexIds(indexIds);
            } else {
                elasticsearchChunkIndexService.deleteByDocId(docId);
            }
        } catch (Exception ex) {
            log.warn("删除文档 ES 分块失败，继续删除数据库记录：docId={}, error={}", docId, ex.getMessage(), ex);
        }
        documentChunkMapper.deleteDocumentChunkByDocId(docId);
    }

    private void deleteSourceFileQuietly(KnowledgeDocument document) {
        if (document == null || StringUtils.isBlank(document.getFileId())) {
            return;
        }
        try {
            minioService.deleteFile(document.getFileId());
        } catch (Exception ex) {
            log.warn(
                    "删除知识库源文件失败，继续删除数据库记录：docId={}, objectName={}, error={}",
                    document.getDocId(),
                    document.getFileId(),
                    ex.getMessage(),
                    ex);
        }
    }

    private void rollbackCreatedDocument(KnowledgeDocument document, String objectName) {
        try {
            if (StringUtils.isNotBlank(objectName)) {
                minioService.deleteFile(objectName);
            }
        } catch (Exception ignored) {
        }

        try {
            if (document != null && document.getDocId() != null) {
                knowledgeDocumentMapper.deleteKnowledgeDocumentByDocId(document.getDocId());
            }
        } catch (Exception ignored) {
        }
    }

    private void populateHierarchy(KnowledgeDocument document) throws TaskException {
        document.setName(normalizeNodeName(document.getName()));
        document.setPath(buildNodePath(document.getKbId(), document.getParentId(), document.getName()));
    }

    private String buildManualChunkIndexId(Long docId) {
        return "manual-" + docId + "-" + UUID.randomUUID();
    }

    private String toJsonArray(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalized = values.stream()
                .map(StringUtils::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : JSON.toJSONString(normalized);
    }

    private void fillLawChunkFields(
            KnowledgeDocument document, DocumentChunk chunk, List<String> headings, String content) {
        if (document == null || chunk == null) {
            return;
        }
        ChunkRequest chunkRequest =
                ChunkRequestFactory.build(document.getFileType(), document.getChunkStrategy(), document.getChunkConfig());
        if (!chunkRequest.isLawDocument()) {
            return;
        }

        LawDocumentStructureAnalyzer.LawMetadata metadata =
                lawDocumentStructureAnalyzer.analyze(document.getName(), headings, content);
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

    private String normalizeNodeName(String name) throws TaskException {
        String normalized = StringUtils.trimToNull(name);
        if (normalized == null) {
            throw new TaskException("名称不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String buildNodePath(Long kbId, Long parentId, String name) throws TaskException {
        if (parentId == null) {
            return "/" + name;
        }
        KnowledgeDocument parent = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(parentId);
        if (parent == null || !kbId.equals(parent.getKbId())) {
            throw new TaskException("父目录不存在", TaskException.Code.UNKNOWN);
        }
        if (!Long.valueOf(1L).equals(parent.getIsFolder())) {
            throw new TaskException("父节点不是目录", TaskException.Code.UNKNOWN);
        }
        if (StringUtils.isBlank(parent.getPath())) {
            return "/" + name;
        }
        return parent.getPath() + "/" + name;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private void sortTreeNodes(List<KnowledgeDocumentTreeNodeVo> nodes) {
        nodes.sort(Comparator.comparing(KnowledgeDocumentTreeNodeVo::getIsFolder).reversed()
                .thenComparing(KnowledgeDocumentTreeNodeVo::getName, String.CASE_INSENSITIVE_ORDER));
        for (KnowledgeDocumentTreeNodeVo node : nodes) {
            sortTreeNodes(node.getChildren());
        }
    }

    private KnowledgeDocument requireProcessableDocument(Long docId) throws TaskException {
        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (document == null) {
            throw new TaskException("文档不存在", TaskException.Code.UNKNOWN);
        }
        if (Long.valueOf(1L).equals(document.getIsFolder())) {
            throw new TaskException("目录不支持该操作", TaskException.Code.UNKNOWN);
        }
        if (StringUtils.isBlank(document.getFileId())) {
            throw new TaskException("文档文件未上传完成", TaskException.Code.UNKNOWN);
        }
        return document;
    }

}
