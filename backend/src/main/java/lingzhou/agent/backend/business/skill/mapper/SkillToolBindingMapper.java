package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 检查工具是否绑定到指定技能
     *
     * @param runtimeSkillName 技能的运行时名称
     * @param toolName 工具名称
     * @return 如果绑定存在返回 true，否则返回 false
     */
    @Select("""
        SELECT COUNT(*) > 0
        FROM skill_tool_binding stb
        JOIN skill_catalog sc ON stb.skill_id = sc.id
        WHERE sc.runtime_skill_name = #{runtimeSkillName}
          AND stb.tool_name = #{toolName}
        """)
    boolean existsBinding(@Param("runtimeSkillName") String runtimeSkillName,
                          @Param("toolName") String toolName);
}
