package lingzhou.agent.backend.business.system.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lingzhou.agent.backend.business.system.model.SystemConfigModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigModel> {

    default SystemConfigModel selectByConfigKey(String configKey) {
        return this.selectOne(new LambdaQueryWrapper<SystemConfigModel>()
                .eq(SystemConfigModel::getConfigKey, configKey)
                .last("limit 1"));
    }
}
