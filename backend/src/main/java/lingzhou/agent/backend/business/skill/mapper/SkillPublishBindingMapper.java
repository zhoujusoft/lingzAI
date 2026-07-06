package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillPublishBinding;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface SkillPublishBindingMapper extends BaseMapper<SkillPublishBinding> {

    default SkillPublishBinding selectBySkillId(Long skillId) {
        if (skillId == null || skillId <= 0) {
            return null;
        }
        QueryWrapper<SkillPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default SkillPublishBinding selectByAppCode(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return null;
        }
        QueryWrapper<SkillPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", appCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<SkillPublishBinding> selectBySkillIds(Collection<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<SkillPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.in("skill_id", skillIds);
        return this.selectList(wrapper);
    }

    default int deleteBySkillId(Long skillId) {
        if (skillId == null || skillId <= 0) {
            return 0;
        }
        QueryWrapper<SkillPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("skill_id", skillId);
        return this.delete(wrapper);
    }
}
