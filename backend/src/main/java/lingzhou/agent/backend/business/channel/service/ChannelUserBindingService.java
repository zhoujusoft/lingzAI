package lingzhou.agent.backend.business.channel.service;

import com.alibaba.fastjson.JSON;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.mapper.ChannelUserBindingMapper;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelUserBindingService {

    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final String SCHEMA_VERSION_ACCOUNT_SCOPED_V1 = "ACCOUNT_SCOPED_V1";
    private static final String CHANNEL_TYPE_KEY = "channelType";
    private static final String LOGIN_CONTEXT_KEY = "loginContext";
    private static final String WECOM_CREDENTIAL_KEY = "wecomCredential";

    private final ChannelUserBindingMapper channelUserBindingMapper;

    public ChannelUserBindingService(ChannelUserBindingMapper channelUserBindingMapper) {
        this.channelUserBindingMapper = channelUserBindingMapper;
    }

    public ChannelUserBinding findByChannelAndUser(Long channelId, Long ownerUserId) {
        if (channelId == null || ownerUserId == null || ownerUserId <= 0) {
            return null;
        }
        return channelUserBindingMapper.selectByChannelAndUser(channelId, ownerUserId);
    }

    public List<ChannelUserBinding> listByChannelId(Long channelId) {
        if (channelId == null || channelId <= 0) {
            return List.of();
        }
        return channelUserBindingMapper.selectByChannelId(channelId);
    }

    public List<ChannelUserBinding> listByOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return List.of();
        }
        return channelUserBindingMapper.selectByOwnerUserId(ownerUserId);
    }

    public List<ChannelUserBinding> listWithRuntime(Long channelId) {
        if (channelId == null || channelId <= 0) {
            return List.of();
        }
        return channelUserBindingMapper.selectWithRuntime(channelId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelUserBinding saveBinding(
            Long channelId, String channelType, Long ownerUserId, String routeType, Long routeTargetId) {
        if (channelId == null || ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少用户绑定参数");
        }
        ChannelUserBinding existing = findByChannelAndUser(channelId, ownerUserId);
        if (existing != null) {
            existing.setChannelType(channelType);
            existing.setRouteType(routeType);
            existing.setRouteTargetId(routeTargetId);
            channelUserBindingMapper.updateById(existing);
            return existing;
        }

        ChannelUserBinding created = new ChannelUserBinding();
        created.setChannelId(channelId);
        created.setChannelType(channelType);
        created.setOwnerUserId(ownerUserId);
        created.setRouteType(routeType);
        created.setRouteTargetId(routeTargetId);
        channelUserBindingMapper.insert(created);
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveLoginContext(Long channelId, Long ownerUserId, LoginContext loginContext) {
        if (channelId == null || ownerUserId == null || ownerUserId <= 0 || loginContext == null) {
            return;
        }
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            binding = saveBinding(channelId, "weixin", ownerUserId, "GENERAL_CHAT", null);
        }
        Date now = new Date();
        binding.setRuntimeContextJson(JSON.toJSONString(Map.of(
                SCHEMA_VERSION_KEY,
                SCHEMA_VERSION_ACCOUNT_SCOPED_V1,
                CHANNEL_TYPE_KEY,
                "weixin",
                LOGIN_CONTEXT_KEY,
                Map.of(
                        "botToken", loginContext.getBotToken(),
                        "botId", loginContext.getBotId(),
                        "userId", loginContext.getUserId(),
                        "baseUrl", loginContext.getBaseUrl()))));
        binding.setRuntimeContextUpdatedAt(now);
        channelUserBindingMapper.updateById(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveWecomCredential(Long channelId, Long ownerUserId, String botId, String secret, String source) {
        if (channelId == null
                || ownerUserId == null
                || ownerUserId <= 0
                || !StringUtils.hasText(botId)
                || !StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("缺少企业微信绑定参数");
        }
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            binding = saveBinding(channelId, "wecom", ownerUserId, "GENERAL_CHAT", null);
        }
        Date now = new Date();
        binding.setChannelType("wecom");
        binding.setRuntimeContextJson(JSON.toJSONString(Map.of(
                SCHEMA_VERSION_KEY,
                SCHEMA_VERSION_ACCOUNT_SCOPED_V1,
                CHANNEL_TYPE_KEY,
                "wecom",
                WECOM_CREDENTIAL_KEY,
                Map.of(
                        "botId", botId.trim(),
                        "secret", secret.trim(),
                        "source", StringUtils.hasText(source) ? source.trim() : "wecom_sdk"))));
        binding.setRuntimeContextUpdatedAt(now);
        channelUserBindingMapper.updateById(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearLoginContext(Long channelId, Long ownerUserId) {
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            return;
        }
        binding.setRuntimeContextJson(null);
        binding.setRuntimeContextUpdatedAt(null);
        channelUserBindingMapper.updateById(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBinding(Long channelId, Long ownerUserId) {
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            return false;
        }
        return channelUserBindingMapper.deleteById(binding.getId()) > 0;
    }

    public LoginContext getLoginContext(Long channelId, Long ownerUserId) {
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        return binding == null ? null : parseLoginContext(binding);
    }

    public WecomCredential getWecomCredential(Long channelId, Long ownerUserId) {
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            return null;
        }
        Map<String, Object> runtimeContext = parseRuntimeContext(binding, "wecom", true);
        if (runtimeContext == null) {
            return null;
        }
        Object wecomCredentialObject = runtimeContext.get(WECOM_CREDENTIAL_KEY);
        if (!(wecomCredentialObject instanceof Map<?, ?> credentialMap)) {
            return null;
        }
        String botId = stringValue(credentialMap.get("botId"));
        String secret = stringValue(credentialMap.get("secret"));
        String source = stringValue(credentialMap.get("source"));
        if (!StringUtils.hasText(botId) || !StringUtils.hasText(secret)) {
            return null;
        }
        return new WecomCredential(botId.trim(), secret.trim(), StringUtils.hasText(source) ? source.trim() : null);
    }

    public boolean hasScopedRuntimeContext(Long channelId, Long ownerUserId, String channelType) {
        ChannelUserBinding binding = findByChannelAndUser(channelId, ownerUserId);
        if (binding == null) {
            return false;
        }
        return parseRuntimeContext(binding, channelType, true) != null;
    }

    private LoginContext parseLoginContext(ChannelUserBinding binding) {
        Map<String, Object> runtimeContext = parseRuntimeContext(binding, "weixin", true);
        if (runtimeContext == null) {
            return null;
        }
        try {
            Object loginContextObject = runtimeContext.get(LOGIN_CONTEXT_KEY);
            if (!(loginContextObject instanceof Map<?, ?> loginMap)) {
                return null;
            }
            String botToken = stringValue(loginMap.get("botToken"));
            String botId = stringValue(loginMap.get("botId"));
            String userId = stringValue(loginMap.get("userId"));
            String baseUrl = stringValue(loginMap.get("baseUrl"));
            if (!StringUtils.hasText(botToken) || !StringUtils.hasText(botId) || !StringUtils.hasText(baseUrl)) {
                return null;
            }
            return new LoginContext(botToken, userId, botId, baseUrl);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> parseRuntimeContext(
            ChannelUserBinding binding, String expectedChannelType, boolean requireAccountScopedVersion) {
        if (binding == null || !StringUtils.hasText(binding.getRuntimeContextJson())) {
            return null;
        }
        try {
            Map<String, Object> runtimeContext = JSON.parseObject(binding.getRuntimeContextJson());
            if (runtimeContext == null || runtimeContext.isEmpty()) {
                return null;
            }
            String channelType = stringValue(runtimeContext.get(CHANNEL_TYPE_KEY));
            if (StringUtils.hasText(expectedChannelType)
                    && !expectedChannelType.equalsIgnoreCase(StringUtils.trimWhitespace(channelType))) {
                return null;
            }
            if (requireAccountScopedVersion) {
                String schemaVersion = stringValue(runtimeContext.get(SCHEMA_VERSION_KEY));
                if (!SCHEMA_VERSION_ACCOUNT_SCOPED_V1.equals(schemaVersion)) {
                    return null;
                }
            }
            return runtimeContext;
        } catch (Exception ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record WecomCredential(String botId, String secret, String source) {}
}
