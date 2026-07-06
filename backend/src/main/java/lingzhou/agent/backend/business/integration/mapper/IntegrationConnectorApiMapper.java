package lingzhou.agent.backend.business.integration.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface IntegrationConnectorApiMapper extends BaseMapper<IntegrationConnectorApi> {

    default List<IntegrationConnectorApi> selectByConnectorId(Long connectorId) {
        QueryWrapper<IntegrationConnectorApi> wrapper = new QueryWrapper<>();
        wrapper.eq("connector_id", connectorId).orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default IntegrationConnectorApi selectByConnectorIdAndApiCode(Long connectorId, String apiCode) {
        if (connectorId == null || !StringUtils.hasText(apiCode)) {
            return null;
        }
        QueryWrapper<IntegrationConnectorApi> wrapper = new QueryWrapper<>();
        wrapper.eq("connector_id", connectorId).eq("api_code", apiCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default IntegrationConnectorApi selectByConnectorIdAndApiName(Long connectorId, String apiName) {
        if (connectorId == null || !StringUtils.hasText(apiName)) {
            return null;
        }
        QueryWrapper<IntegrationConnectorApi> wrapper = new QueryWrapper<>();
        wrapper.eq("connector_id", connectorId).eq("api_name", apiName.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default IntegrationConnectorApi selectByToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        QueryWrapper<IntegrationConnectorApi> wrapper = new QueryWrapper<>();
        wrapper.eq("tool_name", toolName.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }
}
