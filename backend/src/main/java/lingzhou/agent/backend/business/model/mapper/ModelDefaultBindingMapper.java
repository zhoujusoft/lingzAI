package lingzhou.agent.backend.business.model.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.model.domain.ModelDefaultBinding;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ModelDefaultBindingMapper extends BaseMapper<ModelDefaultBinding> {

    default List<ModelDefaultBinding> selectAllOrdered() {
        QueryWrapper<ModelDefaultBinding> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("id");
        return this.selectList(wrapper);
    }

    default ModelDefaultBinding selectByCapabilityType(String capabilityType) {
        if (!StringUtils.hasText(capabilityType)) {
            return null;
        }
        QueryWrapper<ModelDefaultBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("capability_type", capabilityType.trim().toUpperCase()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
