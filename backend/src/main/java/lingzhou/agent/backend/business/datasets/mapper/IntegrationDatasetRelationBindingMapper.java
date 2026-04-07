package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetRelationBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationDatasetRelationBindingMapper extends BaseMapper<IntegrationDatasetRelationBinding> {

    default List<IntegrationDatasetRelationBinding> selectByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetRelationBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId).orderByAsc("id");
        return this.selectList(wrapper);
    }

    default int deleteByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetRelationBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId);
        return this.delete(wrapper);
    }
}
