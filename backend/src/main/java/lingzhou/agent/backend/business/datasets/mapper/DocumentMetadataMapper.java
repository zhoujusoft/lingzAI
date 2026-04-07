package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentMetadata;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMetadataMapper extends BaseMapper<DocumentMetadata> {

    default DocumentMetadata selectDocumentMetadataByMetadataId(Long metadataId) {
        return this.selectById(metadataId);
    }

    default List<DocumentMetadata> selectDocumentMetadataList(DocumentMetadata documentMetadata) {
        QueryWrapper<DocumentMetadata> wrapper = buildQuery(documentMetadata);
        wrapper.orderByAsc("metadata_id");
        return this.selectList(wrapper);
    }

    default IPage<DocumentMetadata> selectDocumentMetadataPage(
            DocumentMetadata documentMetadata, long pageNum, long pageSize) {
        QueryWrapper<DocumentMetadata> wrapper = buildQuery(documentMetadata);
        wrapper.orderByAsc("metadata_id");
        return this.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    default int insertDocumentMetadata(DocumentMetadata documentMetadata) {
        return this.insert(documentMetadata);
    }

    default int insertKnowledgeDocumentBatch(Long kbId) {
        String[] keys = {"文件编号", "文件年份", "问题分类", "资料类型"};
        int affected = 0;
        for (String key : keys) {
            QueryWrapper<DocumentMetadata> query = new QueryWrapper<>();
            query.eq("kb_id", kbId).eq("meta_key", key);
            if (this.selectCount(query) > 0) {
                continue;
            }
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setKbId(kbId);
            metadata.setMetaKey(key);
            metadata.setMetaType("STRING");
            metadata.setIsRequired(0);
            affected += this.insert(metadata);
        }
        return affected;
    }

    default int updateDocumentMetadata(DocumentMetadata documentMetadata) {
        return this.updateById(documentMetadata);
    }

    default int deleteDocumentMetadataByMetadataId(Long metadataId) {
        return this.deleteById(metadataId);
    }

    default int deleteDocumentMetadataByMetadataIds(Long[] metadataIds) {
        if (metadataIds == null || metadataIds.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(metadataIds));
    }

    default int deleteDocumentMetadataByKbId(Long kbId) {
        if (kbId == null) {
            return 0;
        }
        QueryWrapper<DocumentMetadata> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId);
        return this.delete(wrapper);
    }

    private QueryWrapper<DocumentMetadata> buildQuery(DocumentMetadata documentMetadata) {
        QueryWrapper<DocumentMetadata> wrapper = new QueryWrapper<>();
        if (documentMetadata == null) {
            return wrapper;
        }
        if (documentMetadata.getKbId() != null) {
            wrapper.eq("kb_id", documentMetadata.getKbId());
        }
        if (StringUtils.isNotBlank(documentMetadata.getMetaKey())) {
            wrapper.like("meta_key", documentMetadata.getMetaKey());
        }
        if (StringUtils.isNotBlank(documentMetadata.getMetaType())) {
            wrapper.eq("meta_type", documentMetadata.getMetaType());
        }
        return wrapper;
    }
}
