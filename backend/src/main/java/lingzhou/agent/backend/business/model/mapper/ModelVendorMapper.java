package lingzhou.agent.backend.business.model.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.model.domain.ModelVendor;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ModelVendorMapper extends BaseMapper<ModelVendor> {

    default List<ModelVendor> selectAllOrdered() {
        QueryWrapper<ModelVendor> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default ModelVendor selectByVendorCode(String vendorCode) {
        if (!StringUtils.hasText(vendorCode)) {
            return null;
        }
        QueryWrapper<ModelVendor> wrapper = new QueryWrapper<>();
        wrapper.eq("vendor_code", vendorCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
