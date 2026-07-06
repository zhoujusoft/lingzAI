package lingzhou.agent.backend.business.channel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelSessionBindingMapper extends BaseMapper<ChannelSessionBinding> {

    default ChannelSessionBinding selectByExternalSessionKey(Long channelId, String externalSessionKey) {
        QueryWrapper<ChannelSessionBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId)
                .eq("external_session_key", externalSessionKey)
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ChannelSessionBinding selectByChatSessionCode(String chatSessionCode) {
        if (chatSessionCode == null || chatSessionCode.isBlank()) {
            return null;
        }
        QueryWrapper<ChannelSessionBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("chat_session_code", chatSessionCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ChannelSessionBinding> selectByChannelId(Long channelId, int limit) {
        QueryWrapper<ChannelSessionBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId)
                .orderByDesc("last_active_time")
                .orderByDesc("id")
                .last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default List<ChannelSessionBinding> selectRecent(int limit) {
        QueryWrapper<ChannelSessionBinding> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("last_active_time").orderByDesc("id").last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }
}
