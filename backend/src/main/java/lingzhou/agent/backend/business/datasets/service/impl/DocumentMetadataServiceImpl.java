package lingzhou.agent.backend.business.datasets.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentMetadata;
import lingzhou.agent.backend.business.datasets.mapper.DocumentMetadataMapper;
import lingzhou.agent.backend.business.datasets.service.IDocumentMetadataService;
import org.springframework.stereotype.Service;

@Service
public class DocumentMetadataServiceImpl implements IDocumentMetadataService {

    private final DocumentMetadataMapper documentMetadataMapper;

    public DocumentMetadataServiceImpl(DocumentMetadataMapper documentMetadataMapper) {
        this.documentMetadataMapper = documentMetadataMapper;
    }

    @Override
    public DocumentMetadata selectDocumentMetadataByMetadataId(Long metadataId) {
        return documentMetadataMapper.selectDocumentMetadataByMetadataId(metadataId);
    }

    @Override
    public List<DocumentMetadata> selectDocumentMetadataList(DocumentMetadata documentMetadata) {
        return documentMetadataMapper.selectDocumentMetadataList(documentMetadata);
    }

    @Override
    public IPage<DocumentMetadata> selectDocumentMetadataPage(
            DocumentMetadata documentMetadata, long pageNum, long pageSize) {
        return documentMetadataMapper.selectDocumentMetadataPage(documentMetadata, pageNum, pageSize);
    }

    @Override
    public int insertDocumentMetadata(DocumentMetadata documentMetadata) {
        return documentMetadataMapper.insertDocumentMetadata(documentMetadata);
    }

    @Override
    public int updateDocumentMetadata(DocumentMetadata documentMetadata) {
        return documentMetadataMapper.updateDocumentMetadata(documentMetadata);
    }

    @Override
    public int deleteDocumentMetadataByMetadataIds(Long[] metadataIds) {
        return documentMetadataMapper.deleteDocumentMetadataByMetadataIds(metadataIds);
    }

    @Override
    public int deleteDocumentMetadataByMetadataId(Long metadataId) {
        return documentMetadataMapper.deleteDocumentMetadataByMetadataId(metadataId);
    }

    @Override
    public void insertDocumentMetadataBatch(Long kbId, String name) {
        String[] keys;
        if (name != null && name.contains("技术规范书")) {
            keys = new String[] {"技术规范书编号", "父文档编号"};
        } else {
            keys = new String[] {"文件编号", "文件年份", "问题分类", "资料类型"};
        }
        for (String key : keys) {
            DocumentMetadata item = new DocumentMetadata();
            item.setKbId(kbId);
            item.setMetaKey(key);
            item.setMetaType("STRING");
            item.setIsRequired(0);
            List<DocumentMetadata> existed = documentMetadataMapper.selectDocumentMetadataList(item);
            if (existed == null || existed.isEmpty()) {
                documentMetadataMapper.insertDocumentMetadata(item);
            }
        }
    }
}
