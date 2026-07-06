package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillPackageInstall;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface SkillPackageInstallMapper extends BaseMapper<SkillPackageInstall> {

    default SkillPackageInstall selectLatestSuccessful(String packageId) {
        QueryWrapper<SkillPackageInstall> wrapper = new QueryWrapper<>();
        wrapper.eq("package_id", packageId)
                .in("install_status", "SUCCESS", "PARTIAL_SUCCESS")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default SkillPackageInstall selectLatestSuccessfulByRuntimeSkillName(String runtimeSkillName) {
        QueryWrapper<SkillPackageInstall> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName)
                .in("install_status", "SUCCESS", "PARTIAL_SUCCESS")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<SkillPackageInstall> selectByRuntimeSkillName(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return List.of();
        }
        QueryWrapper<SkillPackageInstall> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName.trim()).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default int deleteByRuntimeSkillName(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return 0;
        }
        QueryWrapper<SkillPackageInstall> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName.trim());
        return this.delete(wrapper);
    }
}
