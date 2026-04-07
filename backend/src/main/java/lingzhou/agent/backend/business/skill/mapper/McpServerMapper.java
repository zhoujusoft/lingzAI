package lingzhou.agent.backend.business.skill.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import org.apache.ibatis.annotations.Mapper;

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

    default McpServer selectByServerKey(String serverKey) {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.eq("server_key", serverKey).last("limit 1");
        return this.selectOne(wrapper);
    }
}
