package lingzhou.agent.backend.business.channel.service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import lingzhou.agent.backend.business.channel.mapper.ChannelSessionBindingMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelSessionBindingService {

    private final ChannelSessionBindingMapper channelSessionBindingMapper;
    private final ConcurrentHashMap<String, ChannelSessionBinding> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> cacheKeyById = new ConcurrentHashMap<>();

    public ChannelSessionBindingService(ChannelSessionBindingMapper channelSessionBindingMapper) {
        this.channelSessionBindingMapper = channelSessionBindingMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<ChannelSessionBinding> bindings = channelSessionBindingMapper.selectRecent(5000);
        for (ChannelSessionBinding binding : bindings) {
            putToCache(binding);
        }
    }

    public ChannelSessionBinding findByExternalSessionKey(Long channelId, String externalSessionKey) {
        if (channelId == null || !StringUtils.hasText(externalSessionKey)) {
            return null;
        }
        String cacheKey = cacheKey(channelId, externalSessionKey);
        ChannelSessionBinding cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ChannelSessionBinding binding =
                channelSessionBindingMapper.selectByExternalSessionKey(channelId, externalSessionKey.trim());
        if (binding != null) {
            putToCache(binding);
        }
        return binding;
    }

    public List<ChannelSessionBinding> listByChannelId(Long channelId, Integer limit) {
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);
        if (channelId == null || channelId <= 0) {
            return channelSessionBindingMapper.selectRecent(safeLimit);
        }
        return channelSessionBindingMapper.selectByChannelId(channelId, safeLimit);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelSessionBinding touch(
            Long channelId,
            String channelType,
            String externalSessionKey,
            String senderId,
            String senderName,
            String replyTarget,
            Long ownerUserId) {
        ChannelSessionBinding existing = findByExternalSessionKey(channelId, externalSessionKey);
        Date now = new Date();
        if (existing != null) {
            existing.setExternalSenderId(senderId);
            existing.setExternalSenderName(senderName);
            existing.setReplyTarget(replyTarget);
            // Keep historical owner binding when inbound message does not carry owner info.
            if (ownerUserId != null && ownerUserId > 0) {
                existing.setOwnerUserId(ownerUserId);
            }
            existing.setLastActiveTime(now);
            channelSessionBindingMapper.updateById(existing);
            putToCache(existing);
            return existing;
        }

        ChannelSessionBinding created = new ChannelSessionBinding();
        created.setChannelId(channelId);
        created.setChannelType(channelType);
        created.setExternalSessionKey(externalSessionKey);
        created.setExternalSenderId(senderId);
        created.setExternalSenderName(senderName);
        created.setReplyTarget(replyTarget);
        created.setOwnerUserId(ownerUserId);
        created.setLastActiveTime(now);
        channelSessionBindingMapper.insert(created);
        putToCache(created);
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindSessionCode(Long id, String sessionCode) {
        if (id == null || !StringUtils.hasText(sessionCode)) {
            return;
        }
        ChannelSessionBinding update = new ChannelSessionBinding();
        update.setId(id);
        update.setChatSessionCode(sessionCode.trim());
        update.setLastActiveTime(new Date());
        channelSessionBindingMapper.updateById(update);
        syncCachedSessionCode(id, sessionCode.trim(), update.getLastActiveTime());
    }

    public String resolveReplyTarget(Long channelId, String externalSessionKey) {
        ChannelSessionBinding binding = findByExternalSessionKey(channelId, externalSessionKey);
        return binding == null ? null : binding.getReplyTarget();
    }

    private String cacheKey(Long channelId, String externalSessionKey) {
        return channelId + ":" + externalSessionKey.trim();
    }

    private void putToCache(ChannelSessionBinding binding) {
        if (binding == null
                || binding.getChannelId() == null
                || !StringUtils.hasText(binding.getExternalSessionKey())) {
            return;
        }
        String key = cacheKey(binding.getChannelId(), binding.getExternalSessionKey());
        cache.put(key, binding);
        if (binding.getId() != null) {
            cacheKeyById.put(binding.getId(), key);
        }
    }

    private void syncCachedSessionCode(Long id, String sessionCode, Date lastActiveTime) {
        if (id == null || !StringUtils.hasText(sessionCode)) {
            return;
        }
        String mappedKey = cacheKeyById.get(id);
        if (StringUtils.hasText(mappedKey)) {
            ChannelSessionBinding cached = cache.get(mappedKey);
            if (cached != null) {
                cached.setChatSessionCode(sessionCode);
                cached.setLastActiveTime(lastActiveTime);
                return;
            }
        }
        ChannelSessionBinding latest = channelSessionBindingMapper.selectById(id);
        if (latest != null) {
            putToCache(latest);
        }
    }
}
