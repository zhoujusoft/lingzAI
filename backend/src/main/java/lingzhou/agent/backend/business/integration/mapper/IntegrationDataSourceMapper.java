package lingzhou.agent.backend.business.integration.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface IntegrationDataSourceMapper extends BaseMapper<IntegrationDataSource> {

    default List<IntegrationDataSource> selectAllOrdered() {
        QueryWrapper<IntegrationDataSource> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<IntegrationDataSource> search(String keyword, String dbType, String status) {
        QueryWrapper<IntegrationDataSource> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("name", normalizedKeyword).or().like("alias", normalizedKeyword));
        }
        if (StringUtils.hasText(dbType)) {
            wrapper.eq("db_type", dbType.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim());
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default IntegrationDataSource selectByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        QueryWrapper<IntegrationDataSource> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
