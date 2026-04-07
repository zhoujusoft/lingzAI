package lingzhou.agent.backend.business.model.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.model.domain.ModelDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ModelDefinitionMapper extends BaseMapper<ModelDefinition> {

    default List<ModelDefinition> search(String keyword, String capabilityType, Long vendorId, String status) {
        QueryWrapper<ModelDefinition> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("display_name", normalizedKeyword)
                    .or()
                    .like("model_code", normalizedKeyword)
                    .or()
                    .like("model_name", normalizedKeyword));
        }
        if (StringUtils.hasText(capabilityType)) {
            wrapper.eq("capability_type", capabilityType.trim().toUpperCase());
        }
        if (vendorId != null && vendorId > 0) {
            wrapper.eq("vendor_id", vendorId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default ModelDefinition selectByModelCode(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            return null;
        }
        QueryWrapper<ModelDefinition> wrapper = new QueryWrapper<>();
        wrapper.eq("model_code", modelCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
