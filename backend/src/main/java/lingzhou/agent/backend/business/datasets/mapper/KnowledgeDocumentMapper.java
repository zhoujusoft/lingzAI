package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.MateDataParam;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    default KnowledgeDocument selectKnowledgeDocumentByDocId(Long docId) {
        return this.selectById(docId);
    }

    default List<KnowledgeDocument> selectKnowledgeDocumentList(KnowledgeDocument knowledgeDocument) {
        QueryWrapper<KnowledgeDocument> wrapper = buildBaseQuery(knowledgeDocument);
        wrapper.orderByDesc("doc_id");
        return this.selectList(wrapper);
    }

    default IPage<KnowledgeDocument> selectKnowledgeDocumentPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize) {
        QueryWrapper<KnowledgeDocument> wrapper = buildBaseQuery(knowledgeDocument);
        wrapper.orderByDesc("doc_id");
        return this.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    default List<KnowledgeDocument> selectKnowledgeDocumentListByKbId(KnowledgeDocument knowledgeDocument) {
        QueryWrapper<KnowledgeDocument> wrapper = buildBaseQuery(knowledgeDocument);
        if (knowledgeDocument != null && knowledgeDocument.getKbId() != null) {
            wrapper.eq("kb_id", knowledgeDocument.getKbId());
        }
        wrapper.orderByDesc("doc_id");
        return this.selectList(wrapper);
    }

    default IPage<KnowledgeDocument> selectKnowledgeDocumentByKbIdPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize) {
        QueryWrapper<KnowledgeDocument> wrapper = buildBaseQuery(knowledgeDocument);
        if (knowledgeDocument != null && knowledgeDocument.getKbId() != null) {
            wrapper.eq("kb_id", knowledgeDocument.getKbId());
        }
        wrapper.orderByDesc("doc_id");
        return this.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    default int insertKnowledgeDocument(KnowledgeDocument knowledgeDocument) {
        return this.insert(knowledgeDocument);
    }

    default int updateKnowledgeDocument(KnowledgeDocument knowledgeDocument) {
        return this.updateById(knowledgeDocument);
    }

    default int deleteKnowledgeDocumentByDocId(Long docId) {
        return this.deleteById(docId);
    }

    default int deleteKnowledgeDocumentByDocIds(Long[] docIds) {
        if (docIds == null || docIds.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(docIds));
    }

    default List<KnowledgeDocument> selectKnowledgeDocumentListByMetadata(MateDataParam param) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        applyMetaFilter(wrapper, param);
        wrapper.orderByDesc("doc_id");
        return this.selectList(wrapper);
    }

    default List<String> selectKnowledgeDocumentIdsByMetadata(MateDataParam param) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id");
        applyMetaFilter(wrapper, param);
        List<KnowledgeDocument> list = this.selectList(wrapper);
        return list.stream().map(item -> String.valueOf(item.getDocId())).toList();
    }

    default List<KnowledgeDocument> findByPrefixes(List<String> prefixes, Long kbId, String fileCode) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        if (kbId != null) {
            wrapper.eq("kb_id", kbId);
        }
        if (StringUtils.isNotBlank(fileCode)) {
            wrapper.eq("file_id", fileCode);
        }
        if (prefixes != null && !prefixes.isEmpty()) {
            wrapper.and(w -> {
                for (int i = 0; i < prefixes.size(); i++) {
                    String prefix = prefixes.get(i);
                    if (i == 0) {
                        w.likeRight("name", prefix);
                    } else {
                        w.or().likeRight("name", prefix);
                    }
                }
            });
        }
        return this.selectList(wrapper);
    }

    default List<String> findDocIdsByTechSpec() {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id").eq("is_folder", 0).like("path", "技术规范书");
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default List<String> findDocIdsByTechSpecByKbId(String kbId) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id").eq("is_folder", 0).like("path", "技术规范书");
        if (StringUtils.isNotBlank(kbId)) {
            wrapper.eq("kb_id", kbId);
        }
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default List<KnowledgeDocument> selectKnowledgeDocumentListByParentDocId(String parentDocId, String kbId) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(parentDocId)) {
            wrapper.eq("parent_id", parentDocId);
        }
        if (StringUtils.isNotBlank(kbId)) {
            wrapper.eq("kb_id", kbId);
        }
        wrapper.orderByDesc("doc_id");
        return this.selectList(wrapper);
    }

    default List<String> selectDocIdByKbId(String kbId) {
        if (StringUtils.isBlank(kbId)) {
            return Collections.emptyList();
        }
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id").eq("kb_id", kbId);
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default List<String> selectRootDocIdByKbId(Long kbId) {
        if (kbId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id").eq("kb_id", kbId).isNull("parent_id");
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default int selectJsgfsById(String id) {
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("name", id);
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default List<String> selectJiaoCaiIds(Long kbId) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.select("doc_id").eq("is_folder", 0).like("path", "教材");
        if (kbId != null) {
            wrapper.eq("kb_id", kbId);
        }
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default List<String> selectKnowledgeDocumentIds(KnowledgeDocument knowledgeDocument) {
        QueryWrapper<KnowledgeDocument> wrapper = buildBaseQuery(knowledgeDocument);
        wrapper.select("doc_id");
        return this.selectList(wrapper).stream()
                .map(item -> String.valueOf(item.getDocId()))
                .toList();
    }

    default long countFileDocumentsByKbId(Long kbId) {
        if (kbId == null) {
            return 0L;
        }
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId).eq("is_folder", 0);
        return this.selectCount(wrapper);
    }

    default List<KnowledgeDocument> selectKnowledgeDocumentChildren(Long kbId, Long parentId) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId);
        if (parentId == null) {
            wrapper.isNull("parent_id");
        } else {
            wrapper.eq("parent_id", parentId);
        }
        wrapper.orderByDesc("is_folder").orderByAsc("name").orderByDesc("doc_id");
        return this.selectList(wrapper);
    }

    default List<KnowledgeDocument> selectDescendantsByPathPrefix(Long kbId, String pathPrefix, Long excludedDocId) {
        if (kbId == null || StringUtils.isBlank(pathPrefix)) {
            return Collections.emptyList();
        }
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId).likeRight("path", pathPrefix);
        if (excludedDocId != null) {
            wrapper.ne("doc_id", excludedDocId);
        }
        wrapper.orderByAsc("path");
        return this.selectList(wrapper);
    }

    private QueryWrapper<KnowledgeDocument> buildBaseQuery(KnowledgeDocument doc) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        if (doc == null) {
            return wrapper;
        }
        if (doc.getKbId() != null) {
            wrapper.eq("kb_id", doc.getKbId());
        }
        if (doc.getParentId() != null) {
            wrapper.eq("parent_id", doc.getParentId());
        }
        if (doc.getIsFolder() != null) {
            wrapper.eq("is_folder", doc.getIsFolder());
        }
        if (StringUtils.isNotBlank(doc.getName())) {
            wrapper.like("name", doc.getName());
        }
        if (doc.getStatus() != null) {
            wrapper.eq("status", doc.getStatus());
        }
        if (StringUtils.isNotBlank(doc.getFileType())) {
            wrapper.eq("file_type", doc.getFileType());
        }
        return wrapper;
    }

    private void applyMetaFilter(QueryWrapper<KnowledgeDocument> wrapper, MateDataParam param) {
        if (param == null) {
            return;
        }
        if (StringUtils.isNotBlank(param.getKbId())) {
            wrapper.eq("kb_id", param.getKbId());
        }
        likeMeta(wrapper, "问题分类", param.getQuestionType());
        likeMeta(wrapper, "资料类型", param.getDataType());
        likeMeta(wrapper, "文件编号", param.getFileCode());
        likeMeta(wrapper, "文件年份", param.getYear());
        likeMeta(wrapper, "技术规范书编号", param.getTechSpecCode());
        likeMeta(wrapper, "父文档编号", param.getParentDocId());
    }

    private void likeMeta(QueryWrapper<KnowledgeDocument> wrapper, String key, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        wrapper.like("metadata_values", "\"" + key + "\":\"" + value + "\"");
    }
}
