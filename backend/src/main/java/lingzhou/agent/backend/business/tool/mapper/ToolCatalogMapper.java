package lingzhou.agent.backend.business.tool.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Collection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Set;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ToolCatalogMapper extends BaseMapper<ToolCatalog> {

    default ToolCatalog selectByToolName(String toolName) {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("tool_name", toolName).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ToolCatalog> selectAllOrdered() {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<ToolCatalog> selectByToolNames(Collection<String> toolNames) {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.in("tool_name", toolNames).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<ToolCatalog> selectBySource(String source) {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("source", source).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default int deleteBySource(String source) {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("source", source);
        return this.delete(wrapper);
    }

    default int deleteByToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return 0;
        }
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("tool_name", toolName.trim());
        return this.delete(wrapper);
    }

    default List<ToolCatalog> selectBySources(Collection<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.in("source", sources).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<ToolCatalog> selectBindableOrdered() {
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("bindable", 1).orderByAsc("sort_order").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default ToolCatalog selectBindableByToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        QueryWrapper<ToolCatalog> wrapper = new QueryWrapper<>();
        wrapper.eq("tool_name", toolName.trim()).eq("bindable", 1).last("limit 1");
        return this.selectOne(wrapper);
    }

    default Set<String> selectToolNamesBySources(Collection<String> sources) {
        return this.selectBySources(sources).stream().map(ToolCatalog::getToolName).collect(java.util.stream.Collectors.toSet());
    }
}
