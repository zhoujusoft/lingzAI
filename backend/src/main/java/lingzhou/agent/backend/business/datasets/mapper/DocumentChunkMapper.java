package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    default DocumentChunk selectDocumentChunkByChunkId(Long chunkId) {
        return this.selectById(chunkId);
    }

    default List<DocumentChunk> selectDocumentChunkList(DocumentChunk documentChunk) {
        QueryWrapper<DocumentChunk> wrapper = buildQuery(documentChunk);
        wrapper.orderByAsc("chunk_order");
        return this.selectList(wrapper);
    }

    default IPage<DocumentChunk> selectDocumentChunkPage(DocumentChunk documentChunk, long pageNum, long pageSize) {
        QueryWrapper<DocumentChunk> wrapper = buildQuery(documentChunk);
        wrapper.orderByAsc("chunk_order");
        return this.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    default int insertDocumentChunk(DocumentChunk documentChunk) {
        return this.insert(documentChunk);
    }

    default int updateDocumentChunk(DocumentChunk documentChunk) {
        return this.updateById(documentChunk);
    }

    default int deleteDocumentChunkByChunkId(Long chunkId) {
        return this.deleteById(chunkId);
    }

    default int deleteDocumentChunkByChunkIds(Long[] chunkIds) {
        if (chunkIds == null || chunkIds.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(chunkIds));
    }

    default List<DocumentChunk> selectDocumentChunkByDocId(Long docId) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("doc_id", docId).orderByAsc("chunk_order");
        return this.selectList(wrapper);
    }

    default int deleteDocumentChunkByDocId(Long docId) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("doc_id", docId);
        return this.delete(wrapper);
    }

    default Long[] selectChunkIdByDocId(String docId) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.select("chunk_id").eq("doc_id", docId);
        List<DocumentChunk> list = this.selectList(wrapper);
        return list.stream().map(DocumentChunk::getChunkId).toArray(Long[]::new);
    }

    default long sumCharCountByKbId(Long kbId) {
        if (kbId == null) {
            return 0L;
        }
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(char_count), 0)");
        wrapper.inSql("doc_id", "select doc_id from knowledge_document where kb_id = " + kbId + " and is_folder = 0");
        List<Object> result = this.selectObjs(wrapper);
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0L;
        }
        Object value = result.get(0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    default long countByDocId(Long docId) {
        if (docId == null) {
            return 0L;
        }
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("doc_id", docId);
        return this.selectCount(wrapper);
    }

    default long sumCharCountByDocId(Long docId) {
        if (docId == null) {
            return 0L;
        }
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(char_count), 0)").eq("doc_id", docId);
        List<Object> result = this.selectObjs(wrapper);
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0L;
        }
        Object value = result.get(0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    default int selectMaxChunkOrderByDocId(Long docId) {
        if (docId == null) {
            return 0;
        }
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(MAX(chunk_order), 0)").eq("doc_id", docId);
        List<Object> result = this.selectObjs(wrapper);
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0;
        }
        Object value = result.get(0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private QueryWrapper<DocumentChunk> buildQuery(DocumentChunk documentChunk) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        if (documentChunk == null) {
            return wrapper;
        }
        if (documentChunk.getDocId() != null) {
            wrapper.eq("doc_id", documentChunk.getDocId());
        }
        if (StringUtils.isNotBlank(documentChunk.getIndexId())) {
            wrapper.eq("index_id", documentChunk.getIndexId());
        }
        if (documentChunk.getChunkOrder() != null) {
            wrapper.eq("chunk_order", documentChunk.getChunkOrder());
        }
        return wrapper;
    }
}
