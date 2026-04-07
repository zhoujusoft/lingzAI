package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.SkillPackageFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkillPackageFileMapper extends BaseMapper<SkillPackageFile> {

    default List<SkillPackageFile> selectByInstallId(Long installId) {
        QueryWrapper<SkillPackageFile> wrapper = new QueryWrapper<>();
        wrapper.eq("install_id", installId).orderByAsc("id");
        return this.selectList(wrapper);
    }
}
