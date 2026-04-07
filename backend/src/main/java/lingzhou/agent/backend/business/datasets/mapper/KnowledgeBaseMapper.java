package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    default KnowledgeBase selectKnowledgeBaseByKbId(Long kbId) {
        return this.selectById(kbId);
    }

    default List<KnowledgeBase> selectKnowledgeBaseList(KnowledgeBase knowledgeBase) {
        QueryWrapper<KnowledgeBase> wrapper = buildQuery(knowledgeBase);
        wrapper.orderByDesc("kb_id");
        return this.selectList(wrapper);
    }

    default IPage<KnowledgeBase> selectKnowledgeBasePage(KnowledgeBase knowledgeBase, long pageNum, long pageSize) {
        QueryWrapper<KnowledgeBase> wrapper = buildQuery(knowledgeBase);
        wrapper.orderByDesc("kb_id");
        return this.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    default int insertKnowledgeBase(KnowledgeBase knowledgeBase) {
        return this.insert(knowledgeBase);
    }

    default int updateKnowledgeBase(KnowledgeBase knowledgeBase) {
        return this.updateById(knowledgeBase);
    }

    default int deleteKnowledgeBaseByKbId(Long kbId) {
        return this.deleteById(kbId);
    }

    default int deleteKnowledgeBaseByKbIds(Long[] kbIds) {
        if (kbIds == null || kbIds.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(kbIds));
    }

    default int checkKbNameUnique(KnowledgeBase knowledgeBase) {
        QueryWrapper<KnowledgeBase> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_name", knowledgeBase.getKbName());
        if (knowledgeBase.getKbId() != null) {
            wrapper.ne("kb_id", knowledgeBase.getKbId());
        }
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default String selectKbName(String categoryName) {
        QueryWrapper<KnowledgeBase> wrapper = new QueryWrapper<>();
        wrapper.select("kb_name").eq("kb_name", categoryName).last("limit 1");
        KnowledgeBase base = this.selectOne(wrapper);
        return base == null ? null : base.getKbName();
    }

    private QueryWrapper<KnowledgeBase> buildQuery(KnowledgeBase knowledgeBase) {
        QueryWrapper<KnowledgeBase> wrapper = new QueryWrapper<>();
        if (knowledgeBase == null) {
            return wrapper;
        }
        if (StringUtils.isNotBlank(knowledgeBase.getKbName())) {
            wrapper.like("kb_name", knowledgeBase.getKbName());
        }
        if (StringUtils.isNotBlank(knowledgeBase.getDescription())) {
            wrapper.like("description", knowledgeBase.getDescription());
        }
        return wrapper;
    }
}
