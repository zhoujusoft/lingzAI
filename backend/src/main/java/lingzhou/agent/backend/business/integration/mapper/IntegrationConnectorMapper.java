package lingzhou.agent.backend.business.integration.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.common.enums.ResourcePermissionScope;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface IntegrationConnectorMapper extends BaseMapper<IntegrationConnector> {

    default List<IntegrationConnector> search(String keyword, String status) {
        QueryWrapper<IntegrationConnector> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("name", normalizedKeyword).or().like("alias", normalizedKeyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim());
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default IPage<IntegrationConnector> searchPage(
            int page,
            int pageSize,
            String keyword,
            String status,
            boolean admin,
            Long operatorUserId) {
        QueryWrapper<IntegrationConnector> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("name", normalizedKeyword)
                    .or()
                    .like("alias", normalizedKeyword)
                    .or()
                    .like("base_url", normalizedKeyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim());
        }
        if (!admin) {
            wrapper.and(query -> query.isNull("permission_scope")
                    .or()
                    .ne("permission_scope", ResourcePermissionScope.OWNER_ONLY.code())
                    .or()
                    .eq("owner_user_id", operatorUserId));
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectPage(new Page<>(page, pageSize), wrapper);
    }

    default IntegrationConnector selectByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        QueryWrapper<IntegrationConnector> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default IntegrationConnector selectByAlias(String alias) {
        if (!StringUtils.hasText(alias)) {
            return null;
        }
        QueryWrapper<IntegrationConnector> wrapper = new QueryWrapper<>();
        wrapper.eq("alias", alias.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
