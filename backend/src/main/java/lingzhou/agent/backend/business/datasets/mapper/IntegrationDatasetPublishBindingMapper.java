package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetPublishBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationDatasetPublishBindingMapper extends BaseMapper<IntegrationDatasetPublishBinding> {

    default IntegrationDatasetPublishBinding selectByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<IntegrationDatasetPublishBinding> selectByDatasetIds(Collection<Long> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<IntegrationDatasetPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.in("dataset_id", datasetIds);
        return this.selectList(wrapper);
    }

    default int deleteByDatasetId(Long datasetId) {
        QueryWrapper<IntegrationDatasetPublishBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_id", datasetId);
        return this.delete(wrapper);
    }
}
