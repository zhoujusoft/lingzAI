package lingzhou.agent.backend.business.channel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelConfigMapper extends BaseMapper<ChannelConfig> {

    default ChannelConfig selectByChannelType(String channelType) {
        if (channelType == null || channelType.isBlank()) {
            return null;
        }
        QueryWrapper<ChannelConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_type", channelType.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ChannelConfig> selectEnabledList() {
        QueryWrapper<ChannelConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1).orderByAsc("id");
        return this.selectList(wrapper);
    }
}
