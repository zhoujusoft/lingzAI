package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.enums.ResourcePermissionScope;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {

    default List<McpServer> selectAllOrdered() {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("server_key").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<McpServer> selectEnabledOrdered() {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1).orderByAsc("server_key").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default IPage<McpServer> searchPage(
            int page, int pageSize, String keyword, boolean admin, Long operatorUserId) {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query.like("server_key", normalizedKeyword)
                    .or()
                    .like("display_name", normalizedKeyword)
                    .or()
                    .like("description", normalizedKeyword)
                    .or()
                    .like("endpoint", normalizedKeyword));
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

    default McpServer selectByServerKey(String serverKey) {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.eq("server_key", serverKey).last("limit 1");
        return this.selectOne(wrapper);
    }
}
