package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lingzhou.agent.backend.business.skill.domain.SkillPackageInstall;
import org.apache.ibatis.annotations.Mapper;

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
}
