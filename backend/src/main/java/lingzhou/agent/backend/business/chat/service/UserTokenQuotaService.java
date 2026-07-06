package lingzhou.agent.backend.business.chat.service;

import java.util.EnumSet;
import java.util.Set;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.dao.UserTokenAccountMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.TokenQuotaSettingsDto;
import lingzhou.agent.backend.business.system.model.UserTokenAccount;
import lingzhou.agent.backend.business.system.model.UserTokenQuotaSummaryDto;
import lingzhou.agent.backend.business.system.service.SystemConfigService;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTokenQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UserTokenQuotaService.class);
    private static final String QUOTA_EXCEEDED_MESSAGE = "当前 token 额度已用完，请联系管理员";
    private static final Set<ConversationSessionType> SUPPORTED_SESSION_TYPES = EnumSet.of(
            ConversationSessionType.GENERAL_CHAT,
            ConversationSessionType.GENERAL_CHAT_V2,
            ConversationSessionType.SKILL_CHAT,
            ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT,
            ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT,
            ConversationSessionType.DATASET_CHAT,
            ConversationSessionType.KNOWLEDGE_QA);

    private final UserTokenAccountMapper userTokenAccountMapper;
    private final SysUserMapper sysUserMapper;
    private final SystemConfigService systemConfigService;

    public UserTokenQuotaService(
            UserTokenAccountMapper userTokenAccountMapper,
            SysUserMapper sysUserMapper,
            SystemConfigService systemConfigService) {
        this.userTokenAccountMapper = userTokenAccountMapper;
        this.sysUserMapper = sysUserMapper;
        this.systemConfigService = systemConfigService;
    }

    public String validateQuota(Long userId, ConversationSessionType sessionType) {
        if (!supportsQuota(sessionType)) {
            return null;
        }
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return null;
        }
        UserTokenAccount account = ensureAccount(userId, settings.getInitialGrantTokens());
        if (account != null && isUnlimited(account)) {
            return null;
        }
        long remainingTokens = resolveEffectiveRemainingTokens(account);
        return remainingTokens > 0 ? null : QUOTA_EXCEEDED_MESSAGE;
    }

    public UserTokenQuotaSummaryDto getQuotaSummary(Long userId) {
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        return buildQuotaSummary(userId, settings, false);
    }

    public UserTokenQuotaSummaryDto getAdminQuotaSummary(Long userId) {
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        return buildQuotaSummary(userId, settings, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public String grantTokens(Long userId, Long grantTokens) {
        if (userId == null || userId <= 0) {
            return "用户不存在";
        }
        long normalizedGrantTokens = safeLong(grantTokens);
        if (normalizedGrantTokens <= 0L) {
            return "发放额度必须大于 0";
        }
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        ensureAccount(userId, settings.getInitialGrantTokens());
        int affectedRows = userTokenAccountMapper.incrementGrantedTokens(userId, normalizedGrantTokens);
        if (affectedRows <= 0) {
            logger.warn("用户 token 额度发放失败：userId={}, tokens={}", userId, normalizedGrantTokens);
            return "发放额度失败";
        }
        logger.info("用户 token 额度发放成功：userId={}, tokens={}", userId, normalizedGrantTokens);
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public String updateQuota(Long userId, Long remainingTokens, Boolean unlimited) {
        if (userId == null || userId <= 0) {
            return "用户不存在";
        }
        long normalizedRemainingTokens = safeLong(remainingTokens);
        if (remainingTokens != null && remainingTokens < 0L) {
            return "剩余额度不能小于 0";
        }
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        ensureAccount(userId, settings.getInitialGrantTokens());
        int affectedRows = userTokenAccountMapper.updateQuotaSettings(
                userId, normalizedRemainingTokens, toUnlimitedValue(unlimited));
        if (affectedRows <= 0) {
            logger.warn(
                    "用户 token 额度修改失败：userId={}, remainingTokens={}, unlimited={}",
                    userId,
                    normalizedRemainingTokens,
                    unlimited);
            return "修改额度失败";
        }
        logger.info(
                "用户 token 额度修改成功：userId={}, remainingTokens={}, unlimited={}",
                userId,
                normalizedRemainingTokens,
                unlimited);
        return null;
    }

    private UserTokenQuotaSummaryDto buildQuotaSummary(
            Long userId, TokenQuotaSettingsDto settings, boolean includeAmountsWhenDisabled) {
        UserTokenQuotaSummaryDto dto = new UserTokenQuotaSummaryDto();
        dto.setEnabled(Boolean.TRUE.equals(settings.getEnabled()));
        if (!Boolean.TRUE.equals(settings.getEnabled()) && !includeAmountsWhenDisabled) {
            return dto;
        }
        UserTokenAccount account = ensureAccount(userId, settings.getInitialGrantTokens());
        dto.setUnlimited(account != null && isUnlimited(account));
        dto.setGrantedTokens(safeLong(account == null ? null : account.getGrantedTokens()));
        dto.setConsumedTokens(safeLong(account == null ? null : account.getConsumedTokens()));
        dto.setRemainingTokens(resolveEffectiveRemainingTokens(account));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initializeAccountForUser(Long userId) {
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        ensureAccount(userId, settings.getInitialGrantTokens());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccountByUserId(Long userId) {
        userTokenAccountMapper.deleteByUserId(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void settleUsage(Long userId, ConversationSessionType sessionType, RuntimeRunUsageSnapshot snapshot) {
        if (!supportsQuota(sessionType) || userId == null || userId <= 0 || snapshot == null) {
            return;
        }
        TokenQuotaSettingsDto settings = systemConfigService.getTokenQuotaSettingsSnapshot();
        if (!snapshot.usageAvailable() || snapshot.totalTokens() == null || snapshot.totalTokens() <= 0) {
            logger.info(
                    "跳过 token 额度结算：userId={}, sessionType={}, usageAvailable={}, totalTokens={}",
                    userId,
                    sessionType,
                    snapshot.usageAvailable(),
                    snapshot.totalTokens());
            return;
        }
        ensureAccount(userId, settings.getInitialGrantTokens());
        long consumedTokens = Math.max(0, snapshot.totalTokens());
        int affectedRows = userTokenAccountMapper.incrementConsumedTokens(userId, consumedTokens);
        if (affectedRows <= 0) {
            logger.warn("用户 token 额度结算失败：userId={}, sessionType={}, tokens={}", userId, sessionType, consumedTokens);
            return;
        }
        logger.info("用户 token 额度已结算：userId={}, sessionType={}, tokens={}", userId, sessionType, consumedTokens);
    }

    public boolean supportsQuota(ConversationSessionType sessionType) {
        return sessionType != null && SUPPORTED_SESSION_TYPES.contains(sessionType);
    }

    private UserTokenAccount ensureAccount(Long userId, Long initialGrantTokens) {
        if (userId == null || userId <= 0) {
            return null;
        }
        UserTokenAccount existing = userTokenAccountMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        long grantedTokens = Math.max(0L, safeLong(initialGrantTokens));
        UserTokenAccount entity = new UserTokenAccount();
        entity.setUserId(userId);
        entity.setGrantedTokens(grantedTokens);
        entity.setConsumedTokens(0L);
        entity.setRemainingTokens(grantedTokens);
        entity.setUnlimited(0);
        try {
            userTokenAccountMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            logger.info("用户 token 账户已存在，跳过重复初始化：userId={}", userId);
        }
        return userTokenAccountMapper.selectByUserId(userId);
    }

    private boolean isUnlimited(UserTokenAccount account) {
        return account != null && account.getUnlimited() != null && account.getUnlimited() == 1;
    }

    private long resolveEffectiveRemainingTokens(UserTokenAccount account) {
        if (account == null) {
            return 0L;
        }
        if (isUnlimited(account)) {
            return safeLong(account.getRemainingTokens());
        }
        long grantedTokens = safeLong(account.getGrantedTokens());
        long consumedTokens = safeLong(account.getConsumedTokens());
        return Math.max(grantedTokens - consumedTokens, 0L);
    }

    private int toUnlimitedValue(Boolean unlimited) {
        return Boolean.TRUE.equals(unlimited) ? 1 : 0;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
