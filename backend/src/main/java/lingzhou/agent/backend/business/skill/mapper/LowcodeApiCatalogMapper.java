package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface LowcodeApiCatalogMapper extends BaseMapper<LowcodeApiCatalog> {

    default List<LowcodeApiCatalog> selectAllOrdered() {
        QueryWrapper<LowcodeApiCatalog> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("platform_key").orderByAsc("app_name").orderByAsc("api_name").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<LowcodeApiCatalog> selectByPlatformKey(String platformKey) {
        QueryWrapper<LowcodeApiCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("platform_key", platformKey).orderByAsc("app_name").orderByAsc("api_name").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default LowcodeApiCatalog selectByToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        QueryWrapper<LowcodeApiCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("tool_name", toolName.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default LowcodeApiCatalog selectByPlatformKeyAndApiCode(String platformKey, String apiCode) {
        QueryWrapper<LowcodeApiCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("platform_key", platformKey).eq("api_code", apiCode).last("limit 1");
        return this.selectOne(wrapper);
    }

    default int deleteByPlatformKeyAndApiCode(String platformKey, String apiCode) {
        QueryWrapper<LowcodeApiCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("platform_key", platformKey).eq("api_code", apiCode);
        return this.delete(wrapper);
    }
}
