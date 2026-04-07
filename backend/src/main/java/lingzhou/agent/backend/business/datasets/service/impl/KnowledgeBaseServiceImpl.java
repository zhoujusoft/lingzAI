package lingzhou.agent.backend.business.datasets.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBasePublishBinding;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.mapper.DocumentChunkMapper;
import lingzhou.agent.backend.business.datasets.mapper.DocumentMetadataMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBaseMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBasePublishBindingMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeDocumentMapper;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetadataMapper documentMetadataMapper;
    private final IKnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBasePublishBindingMapper knowledgeBasePublishBindingMapper;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMetadataMapper documentMetadataMapper,
            IKnowledgeDocumentService knowledgeDocumentService,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            DocumentChunkMapper documentChunkMapper,
            KnowledgeBasePublishBindingMapper knowledgeBasePublishBindingMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMetadataMapper = documentMetadataMapper;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBasePublishBindingMapper = knowledgeBasePublishBindingMapper;
    }

    @Override
    public KnowledgeBase selectKnowledgeBaseByKbId(Long kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        return enrichKnowledgeBaseStats(knowledgeBase);
    }

    @Override
    public List<KnowledgeBase> selectKnowledgeBaseList(KnowledgeBase knowledgeBase) {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectKnowledgeBaseList(knowledgeBase);
        knowledgeBases.forEach(this::enrichKnowledgeBaseStats);
        Map<Long, KnowledgeBasePublishBinding> bindingMap = knowledgeBasePublishBindingMapper
                .selectByKbIds(knowledgeBases.stream().map(KnowledgeBase::getKbId).toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeBasePublishBinding::getKbId, Function.identity()));
        knowledgeBases.forEach(item -> applyPublishBinding(item, bindingMap.get(item.getKbId())));
        return knowledgeBases;
    }

    @Override
    public IPage<KnowledgeBase> selectKnowledgeBasePage(KnowledgeBase knowledgeBase, long pageNum, long pageSize) {
        IPage<KnowledgeBase> page = knowledgeBaseMapper.selectKnowledgeBasePage(knowledgeBase, pageNum, pageSize);
        page.getRecords().forEach(this::enrichKnowledgeBaseStats);
        return page;
    }

    @Override
    public int insertKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBaseMapper.checkKbNameUnique(knowledgeBase) > 0) {
            return 0;
        }
        Date now = new Date();
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        int rows = knowledgeBaseMapper.insertKnowledgeBase(knowledgeBase);
        if (rows > 0) {
            documentMetadataMapper.insertKnowledgeDocumentBatch(knowledgeBase.getKbId());
        }
        return rows;
    }

    @Override
    public int updateKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBaseMapper.checkKbNameUnique(knowledgeBase) > 0) {
            return 0;
        }
        knowledgeBase.setUpdatedAt(new Date());
        return knowledgeBaseMapper.updateKnowledgeBase(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbIds(Long[] kbIds) throws Exception {
        if (kbIds == null || kbIds.length == 0) {
            return 0;
        }
        int affected = 0;
        for (Long kbId : kbIds) {
            affected += deleteKnowledgeBaseByKbId(kbId);
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbId(Long kbId) throws Exception {
        List<String> docIds = knowledgeDocumentMapper.selectRootDocIdByKbId(kbId);
        for (String docId : docIds) {
            knowledgeDocumentService.deleteKnowledgeDocumentByDocId(kbId, Long.valueOf(docId));
        }
        documentMetadataMapper.deleteDocumentMetadataByKbId(kbId);
        return knowledgeBaseMapper.deleteKnowledgeBaseByKbId(kbId);
    }

    @Override
    public String selectKbName(String categoryName) {
        return knowledgeBaseMapper.selectKbName(categoryName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createKnowledgeBaseWithDocument(
            KnowledgeBase knowledgeBase, MultipartFile file, String chunkStrategy, String chunkConfig) throws Exception {
        try {
            int rows = insertKnowledgeBase(knowledgeBase);
            if (rows <= 0 || knowledgeBase.getKbId() == null) {
                throw new IllegalStateException("知识库创建失败");
            }
            return knowledgeDocumentService.createKnowledgeDocumentWithFile(
                    knowledgeBase.getKbId(), null, file, chunkStrategy, chunkConfig);
        } catch (Exception ex) {
            rollbackCreateKnowledgeBase(knowledgeBase);
            throw ex;
        }
    }

    private void rollbackCreateKnowledgeBase(KnowledgeBase knowledgeBase) {
        try {
            if (knowledgeBase != null && knowledgeBase.getKbId() != null) {
                documentMetadataMapper.deleteDocumentMetadataByKbId(knowledgeBase.getKbId());
                knowledgeBaseMapper.deleteKnowledgeBaseByKbId(knowledgeBase.getKbId());
            }
        } catch (Exception ignored) {
        }
    }

    private KnowledgeBase enrichKnowledgeBaseStats(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.getKbId() == null) {
            return knowledgeBase;
        }
        knowledgeBase.setDocCount(knowledgeDocumentMapper.countFileDocumentsByKbId(knowledgeBase.getKbId()));
        knowledgeBase.setCharCount(documentChunkMapper.sumCharCountByKbId(knowledgeBase.getKbId()));
        knowledgeBase.setAppCount(0L);
        applyPublishBinding(knowledgeBase, knowledgeBasePublishBindingMapper.selectByKbId(knowledgeBase.getKbId()));
        return knowledgeBase;
    }

    private void applyPublishBinding(KnowledgeBase knowledgeBase, KnowledgeBasePublishBinding binding) {
        if (knowledgeBase == null) {
            return;
        }
        if (binding == null) {
            knowledgeBase.setPublishStatus("DRAFT");
            knowledgeBase.setPublishedVersion(0);
            knowledgeBase.setLastPublishMessage("");
            knowledgeBase.setPublishedAt(null);
            knowledgeBase.setLastCompiledAt(null);
            return;
        }
        knowledgeBase.setPublishStatus(binding.getPublishStatus());
        knowledgeBase.setPublishedVersion(binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion());
        knowledgeBase.setLastPublishMessage(binding.getLastPublishMessage());
        knowledgeBase.setPublishedAt(binding.getPublishedAt());
        knowledgeBase.setLastCompiledAt(binding.getLastCompiledAt());
    }
}
