package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkillCatalogMapper extends BaseMapper<SkillCatalog> {

    default SkillCatalog selectByRuntimeSkillName(String runtimeSkillName) {
        QueryWrapper<SkillCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<SkillCatalog> selectAllOrdered() {
        QueryWrapper<SkillCatalog> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<SkillCatalog> selectVisibleOrdered() {
        QueryWrapper<SkillCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("visible", 1).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<SkillCatalog> selectByIds(Collection<Long> ids) {
        QueryWrapper<SkillCatalog> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        return this.selectList(wrapper);
    }
}
