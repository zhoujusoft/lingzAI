package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetFieldBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationDatasetFieldBindingMapper extends BaseMapper<IntegrationDatasetFieldBinding> {

    default List<IntegrationDatasetFieldBinding> selectByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetFieldBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default int deleteByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetFieldBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId);
        return this.delete(wrapper);
    }
}
