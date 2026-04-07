package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkillToolBindingMapper extends BaseMapper<SkillToolBinding> {

    default List<SkillToolBinding> selectBySkillId(Long skillId) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<SkillToolBinding> selectBySkillIdAndBindingType(Long skillId, String bindingType) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId).eq("binding_type", bindingType).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<SkillToolBinding> selectBySkillIds(Collection<Long> skillIds) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.in("skill_id", skillIds).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<SkillToolBinding> selectBySkillIdsAndBindingType(Collection<Long> skillIds, String bindingType) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.in("skill_id", skillIds).eq("binding_type", bindingType).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default int deleteBySkillId(Long skillId) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId);
        return this.delete(wrapper);
    }

    default int deleteBySkillIdAndBindingType(Long skillId, String bindingType) {
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId).eq("binding_type", bindingType);
        return this.delete(wrapper);
    }

    default int deleteByToolNames(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return 0;
        }
        QueryWrapper<SkillToolBinding> wrapper = new QueryWrapper<>();
        wrapper.in("tool_name", toolNames);
        return this.delete(wrapper);
    }
}
