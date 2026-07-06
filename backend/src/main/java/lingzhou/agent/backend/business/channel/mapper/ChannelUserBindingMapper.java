package lingzhou.agent.backend.business.channel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelUserBindingMapper extends BaseMapper<ChannelUserBinding> {

    default ChannelUserBinding selectByChannelAndUser(Long channelId, Long ownerUserId) {
        QueryWrapper<ChannelUserBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId).eq("owner_user_id", ownerUserId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ChannelUserBinding> selectByChannelId(Long channelId) {
        QueryWrapper<ChannelUserBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId).orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<ChannelUserBinding> selectByOwnerUserId(Long ownerUserId) {
        QueryWrapper<ChannelUserBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", ownerUserId).orderByDesc("updated_at").orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<ChannelUserBinding> selectWithRuntime(Long channelId) {
        QueryWrapper<ChannelUserBinding> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId)
                .isNotNull("runtime_context_json")
                .orderByDesc("runtime_context_updated_at")
                .orderByDesc("id");
        return this.selectList(wrapper);
    }
}
