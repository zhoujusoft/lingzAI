package lingzhou.agent.backend.business.datasets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface IntegrationDatasetMapper extends BaseMapper<IntegrationDataset> {

    default List<IntegrationDataset> selectAllOrdered() {
        QueryWrapper<IntegrationDataset> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<IntegrationDataset> search(String keyword, String sourceKind, Long aiDataSourceId, String lowcodePlatformKey) {
        QueryWrapper<IntegrationDataset> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("name", normalizedKeyword)
                    .or()
                    .like("dataset_code", normalizedKeyword)
                    .or()
                    .like("description", normalizedKeyword));
        }
        if (StringUtils.hasText(sourceKind)) {
            wrapper.eq("source_kind", sourceKind.trim());
        }
        if (aiDataSourceId != null) {
            wrapper.eq("ai_data_source_id", aiDataSourceId);
        }
        if (StringUtils.hasText(lowcodePlatformKey)) {
            wrapper.eq("lowcode_platform_key", lowcodePlatformKey.trim());
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<IntegrationDataset> selectBySourceKind(String sourceKind) {
        QueryWrapper<IntegrationDataset> wrapper = new QueryWrapper<>();
        wrapper.eq("source_kind", sourceKind).orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default IntegrationDataset selectByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        QueryWrapper<IntegrationDataset> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default IntegrationDataset selectByDatasetCode(String datasetCode) {
        if (!StringUtils.hasText(datasetCode)) {
            return null;
        }
        QueryWrapper<IntegrationDataset> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_code", datasetCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
