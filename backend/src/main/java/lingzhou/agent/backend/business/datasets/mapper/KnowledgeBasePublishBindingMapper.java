package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBasePublishBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBasePublishBindingMapper extends BaseMapper<KnowledgeBasePublishBinding> {

    default KnowledgeBasePublishBinding selectByKbId(Long kbId) {
        QueryWrapper<KnowledgeBasePublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<KnowledgeBasePublishBinding> selectByKbIds(Collection<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<KnowledgeBasePublishBinding> wrapper = new QueryWrapper<>();
        wrapper.in("kb_id", kbIds);
        return this.selectList(wrapper);
    }
}
