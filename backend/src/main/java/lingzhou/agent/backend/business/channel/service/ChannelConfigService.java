package lingzhou.agent.backend.business.channel.service;

import java.util.List;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.enums.ChannelRouteType;
import lingzhou.agent.backend.business.channel.domain.enums.ChannelType;
import lingzhou.agent.backend.business.channel.mapper.ChannelConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;

    public ChannelConfigService(ChannelConfigMapper channelConfigMapper) {
        this.channelConfigMapper = channelConfigMapper;
    }

    public List<ChannelConfig> listAll() {
        return channelConfigMapper.selectList(null);
    }

    public List<ChannelConfig> listEnabled() {
        return channelConfigMapper.selectEnabledList();
    }

    public ChannelConfig getRequired(Long id) {
        ChannelConfig config = channelConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("渠道不存在: " + id);
        }
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelConfig create(ChannelConfig config) {
        normalize(config);
        ChannelConfig existing = channelConfigMapper.selectByChannelType(config.getChannelType());
        if (existing != null) {
            existing.setName(config.getName());
            existing.setRouteType(config.getRouteType());
            existing.setRouteTargetId(config.getRouteTargetId());
            if ((existing.getOwnerUserId() == null || existing.getOwnerUserId() <= 0)
                    && config.getOwnerUserId() != null
                    && config.getOwnerUserId() > 0) {
                existing.setOwnerUserId(config.getOwnerUserId());
            }
            existing.setBotPrefix(config.getBotPrefix());
            existing.setConfigJson(config.getConfigJson());
            existing.setEnabled(config.getEnabled());
            existing.setDescription(config.getDescription());
            normalize(existing);
            channelConfigMapper.updateById(existing);
            return channelConfigMapper.selectById(existing.getId());
        }
        channelConfigMapper.insert(config);
        return channelConfigMapper.selectById(config.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelConfig update(Long id, ChannelConfig patch) {
        ChannelConfig existing = getRequired(id);
        existing.setName(patch.getName());
        existing.setChannelType(patch.getChannelType());
        existing.setRouteType(patch.getRouteType());
        existing.setRouteTargetId(patch.getRouteTargetId());
        if ((existing.getOwnerUserId() == null || existing.getOwnerUserId() <= 0)
                && patch.getOwnerUserId() != null
                && patch.getOwnerUserId() > 0) {
            existing.setOwnerUserId(patch.getOwnerUserId());
        }
        existing.setBotPrefix(patch.getBotPrefix());
        existing.setConfigJson(patch.getConfigJson());
        existing.setEnabled(patch.getEnabled());
        existing.setDescription(patch.getDescription());
        normalize(existing);
        channelConfigMapper.updateById(existing);
        return channelConfigMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelConfig bindOwnerUserIfAbsent(Long id, Long ownerUserId) {
        ChannelConfig existing = getRequired(id);
        if (ownerUserId == null || ownerUserId <= 0) {
            return existing;
        }
        if (existing.getOwnerUserId() != null && existing.getOwnerUserId() > 0) {
            return existing;
        }
        existing.setOwnerUserId(ownerUserId);
        channelConfigMapper.updateById(existing);
        return channelConfigMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelConfig disable(Long id) {
        ChannelConfig existing = getRequired(id);
        existing.setEnabled(Boolean.FALSE);
        channelConfigMapper.updateById(existing);
        return channelConfigMapper.selectById(id);
    }

    private void normalize(ChannelConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("渠道名称不能为空");
        }
        config.setChannelType(ChannelType.normalize(config.getChannelType()));
        config.setRouteType(ChannelRouteType.fromValue(config.getRouteType()).name());
        if (config.getEnabled() == null) {
            config.setEnabled(Boolean.TRUE);
        }
    }
}
