package lingzhou.agent.backend.business.license.service;

import com.alibaba.fastjson.JSON;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.app.LicenseProperties;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.mapper.ConversationRunUsageMapper;
import lingzhou.agent.backend.business.license.model.LicenseImportResult;
import lingzhou.agent.backend.business.license.model.LicenseRequestView;
import lingzhou.agent.backend.business.license.model.LicenseSnapshot;
import lingzhou.agent.backend.business.license.model.LicenseStatusView;
import lingzhou.agent.backend.business.license.model.LicenseType;
import lingzhou.agent.backend.business.license.model.StoredLicensePayload;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.dao.SystemConfigMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.SystemConfigModel;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.security.RSAEncryptor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LicenseService {

    private static final String CURRENT_LICENSE_CONFIG_KEY = "service_license_current";
    private static final String BOOTSTRAP_STATE_CONFIG_KEY = "service_license_bootstrap_state";
    private static final String BOOTSTRAP_STATE_PENDING = "0";
    private static final String BOOTSTRAP_STATE_CONSUMED = "1";
    private static final String BOOTSTRAP_STATE_PENDING_CIPHER =
            "Om11pqp5cAnWQSDTRDAXQXTOdIslEct7Z1d2oBeaiUVvQCRTyoWsKUZJUSXJsNAg46mMR56zzCBVndaOvH3OMCvsR8BNOytp5/ltFa+Frc/faSflKzVR9O7PaGv4H93fZpnxu4G2ZqWtXj0lxjrz2Uz+X90IsUTpv4fSCQH213cfOuq4gztu2/a+k7m4aHcDuDY0ECRJbwcr4QuUqRIbxg3/1zjl+YLB8xLKIPJMZAmYvCZ02GOjohCYnnjq+AG6D7pEYiiwMdrazcfZJe22zjnMpd4BisssrETU7gDZK3fLoM1VWI3+nkdKxEp9kSANQ0GjbSHYVbD6xxjE/rtwEA==";
    private static final String BOOTSTRAP_STATE_CONSUMED_CIPHER =
            "N1YmftdXgLSv+uFJKurE0F+7pWTnE3X9O0B2gegeQ4jpK/DY81SWxGEl9L5sL2smTkhucsLT+qbdyamAYUS7YWh149SFEXj/WaQwoWfm//5TBUjRc9QpfspPpy4er1Ze3jWz6Huu3zHjnheeQX1kBLZCFeJe6jciLIkIB5zCXj1k+PmjqO0q8qiyLj3mY1+yuNFS0VSPpoaD5SFBEwnTROdcumtpWfT+ePwCbsb74Qd0tUTW5KePDZ1xcf37Em4HCiWMEDxKw1ssXXjyAWM9gRXs/mWuJ42+bXxgTeQamQj8GEU1sUaMCP/35kRaIaU6tFeRMorilh4JlNI01fpTtA==";
    private static final String SOURCE_BOOTSTRAP = "BOOTSTRAP";
    private static final String SOURCE_IMPORTED = "IMPORTED";
    private static final String BOOTSTRAP_EDITION = "BOOTSTRAP";
    private static final String BOOTSTRAP_CUSTOMER_NAME = "系统初始化默认授权";
    private static final int BOOTSTRAP_MAX_ACTIVE_USERS = 50;
    private static final List<String> BOOTSTRAP_FEATURES = List.of("chat", "knowledge");
    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_EXPIRING_SOON = "EXPIRING_SOON";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_NOT_EFFECTIVE = "NOT_EFFECTIVE";
    private static final String STATUS_INVALID_SIGNATURE = "INVALID_SIGNATURE";
    private static final String STATUS_INSTANCE_MISMATCH = "INSTANCE_MISMATCH";
    private static final String STATUS_PRODUCT_MISMATCH = "PRODUCT_MISMATCH";

    private final LicenseProperties licenseProperties;
    private final LicenseCryptoService licenseCryptoService;
    private final LicenseInstanceService licenseInstanceService;
    private final SystemConfigMapper systemConfigMapper;
    private final ConversationRunUsageMapper conversationRunUsageMapper;
    private final SysUserMapper sysUserMapper;
    private final AtomicReference<LicenseSnapshot> snapshotCache = new AtomicReference<>();

    public LicenseService(
            LicenseProperties licenseProperties,
            LicenseCryptoService licenseCryptoService,
            LicenseInstanceService licenseInstanceService,
            SystemConfigMapper systemConfigMapper,
            ConversationRunUsageMapper conversationRunUsageMapper,
            SysUserMapper sysUserMapper) {
        this.licenseProperties = licenseProperties;
        this.licenseCryptoService = licenseCryptoService;
        this.licenseInstanceService = licenseInstanceService;
        this.systemConfigMapper = systemConfigMapper;
        this.conversationRunUsageMapper = conversationRunUsageMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public boolean isEnabled() {
        return licenseProperties.isEnabled();
    }

    public LicenseSnapshot currentSnapshot() {
        LicenseSnapshot cached = snapshotCache.get();
        if (cached != null) {
            return cached;
        }
        LicenseSnapshot snapshot = loadCurrentSnapshot();
        if (snapshot == null) {
            return null;
        }
        snapshotCache.compareAndSet(null, snapshot);
        return snapshotCache.get();
    }

    public void refreshCache() {
        snapshotCache.set(loadCurrentSnapshot());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean initializeDefaultLicenseIfAbsent() throws Exception {
        if (!isEnabled()) {
            return false;
        }
        SystemConfigModel currentConfig = systemConfigMapper.selectByConfigKeyForUpdate(CURRENT_LICENSE_CONFIG_KEY);
        if (currentConfig != null && StringUtils.isNotBlank(currentConfig.getConfigValue())) {
            return false;
        }

        String bootstrapState = resolveBootstrapStateForUpdate();
        if (!BOOTSTRAP_STATE_PENDING.equals(bootstrapState)) {
            return false;
        }

        saveCurrentLicenseForUpdate(buildBootstrapPayload());
        updateBootstrapStateForUpdate(BOOTSTRAP_STATE_CONSUMED);
        refreshCache();
        return true;
    }

    public void assertSystemAccessible() {
        if (!isEnabled()) {
            return;
        }
        LicenseSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "系统尚未导入 license");
        }
        String status = evaluateStatus(snapshot);
        if (STATUS_VALID.equals(status) || STATUS_EXPIRING_SOON.equals(status)) {
            return;
        }
        throw new LicenseException(resolveErrorCode(status), resolveStatusMessage(status));
    }

    public String validateConversationAccess(Long userId, ConversationSessionType sessionType) {
        if (!isEnabled() || sessionType == null) {
            return null;
        }
        LicenseSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            return "系统尚未导入 license";
        }
        String status = evaluateStatus(snapshot);
        if (!STATUS_VALID.equals(status) && !STATUS_EXPIRING_SOON.equals(status)) {
            return resolveStatusMessage(status);
        }
        long consumedTokens = sumConsumedTokens(snapshot.importedAt());
        if (snapshot.maxTotalTokens() != null
                && snapshot.maxTotalTokens() > 0
                && consumedTokens >= snapshot.maxTotalTokens()) {
            return "license token 总额度已用尽，请联系管理员导入新授权";
        }
        return null;
    }

    public void assertUserCreationAllowed() {
        if (!isEnabled()) {
            return;
        }
        LicenseSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "系统尚未导入 license");
        }
        String status = evaluateStatus(snapshot);
        if (!STATUS_VALID.equals(status) && !STATUS_EXPIRING_SOON.equals(status)) {
            throw new LicenseException(resolveErrorCode(status), resolveStatusMessage(status));
        }
        if (snapshot.maxActiveUsers() == null || snapshot.maxActiveUsers() < 0) {
            return;
        }
        int activeUsers = countActiveUsers();
        if (activeUsers >= snapshot.maxActiveUsers()) {
            throw new LicenseException(LicenseConstants.CODE_USER_LIMIT, "当前授权人数已达上限");
        }
    }

    public LicenseStatusView getStatusView() {
        LicenseStatusView view = new LicenseStatusView();
        view.setEnabled(isEnabled());
        view.setInstanceCode(licenseInstanceService.resolveInstanceCode());
        view.setProductCode(licenseProperties.getProductCode());
        LicenseSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            view.setStatus("NOT_IMPORTED");
            view.setActiveUsers(countActiveUsers());
            return view;
        }
        long consumedTokens = sumConsumedTokens(snapshot.importedAt());
        view.setStatus(evaluateStatus(snapshot));
        view.setLicenseId(snapshot.licenseId());
        view.setRevision(snapshot.revision());
        view.setCustomerName(snapshot.customerName());
        view.setEdition(snapshot.edition());
        view.setLicType(snapshot.licType());
        view.setEffectiveAt(snapshot.effectiveAt());
        view.setExpiresAt(isFormalLicense(snapshot.licType()) ? null : snapshot.expiresAt());
        view.setImportedAt(snapshot.importedAt());
        view.setMaxActiveUsers(snapshot.maxActiveUsers());
        view.setActiveUsers(countActiveUsers());
        view.setMaxTotalTokens(snapshot.maxTotalTokens());
        view.setConsumedTokens(consumedTokens);
        view.setRemainingTokens(calculateRemainingTokens(snapshot.maxTotalTokens(), consumedTokens));
        view.setFeatureFlags(snapshot.featureFlags());
        return view;
    }

    public LicenseRequestView buildLicenseRequest() {
        LicenseSnapshot snapshot = currentSnapshot();
        String customerName = snapshot == null ? "" : nullToEmpty(snapshot.customerName());
        String licenseId = snapshot == null ? "" : nullToEmpty(snapshot.licenseId());
        LicenseType licType = snapshot == null ? LicenseType.TRIAL : normalizeLicType(snapshot.licType());
        String expiresAt =
                snapshot == null || isFormalLicense(snapshot.licType()) || snapshot.expiresAt() == null
                        ? ""
                        : formatDate(snapshot.expiresAt());
        return new LicenseRequestView(
                licenseProperties.getProductCode(),
                licenseInstanceService.resolveInstanceCode(),
                customerName,
                licenseId,
                licType,
                expiresAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public LicenseImportResult importLicense(Long operatorUserId, MultipartFile file) throws Exception {
        if (!isEnabled()) {
            throw new TaskException("license 功能未启用", TaskException.Code.UNKNOWN);
        }
        if (file == null || file.isEmpty()) {
            throw new TaskException("请选择 license 文件", TaskException.Code.UNKNOWN);
        }
        String rawContent;
        try {
            rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new TaskException("读取 license 文件失败", TaskException.Code.UNKNOWN, ex);
        }
        String fileSha256 = sha256(rawContent);
        try {
            LicenseCryptoService.ParsedLicenseEnvelope envelope = licenseCryptoService.parseAndVerify(rawContent);
            Map<String, Object> payload = envelope.payload();
            String productCode = text(payload.get("productCode"));
            String instanceCode = text(payload.get("instanceCode"));
            if (!licenseProperties.getProductCode().equalsIgnoreCase(productCode)) {
                throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 产品编码不匹配");
            }
            if (!licenseInstanceService.resolveInstanceCode().equalsIgnoreCase(instanceCode)) {
                throw new LicenseException(LicenseConstants.CODE_INSTANCE_MISMATCH, "license 与当前实例不匹配");
            }

            StoredLicensePayload storedPayload = buildImportedPayload(operatorUserId, envelope, fileSha256);
            StoredLicensePayload currentPayload = loadCurrentPayloadSafely();
            if (sameLicenseRevision(currentPayload, storedPayload)) {
                throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 已导入，无需重复导入");
            }
            if (isOlderRevision(currentPayload, storedPayload)) {
                throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "新 license 版本低于当前版本");
            }

            LicenseSnapshot snapshot = toSnapshot(storedPayload);
            String status = evaluateStatus(snapshot);
            if (STATUS_EXPIRED.equals(status)) {
                throw new LicenseException(LicenseConstants.CODE_EXPIRED, "导入的 license 已过期");
            }
            if (STATUS_NOT_EFFECTIVE.equals(status)) {
                throw new LicenseException(LicenseConstants.CODE_NOT_EFFECTIVE, "license 尚未生效，当前实现仅支持单份当前授权");
            }
            if (STATUS_INVALID_SIGNATURE.equals(status)) {
                throw new LicenseException(LicenseConstants.CODE_INVALID_SIGNATURE, "license 签名校验失败");
            }

            saveCurrentLicense(storedPayload);
            updateBootstrapState(BOOTSTRAP_STATE_CONSUMED);
            refreshCache();
            return new LicenseImportResult(
                    storedPayload.getLicenseId(),
                    storedPayload.getRevision(),
                    status,
                    storedPayload.getCustomerName(),
                    normalizeLicType(storedPayload.getLicType()),
                    formatDate(storedPayload.getExpiresAt()));
        } catch (LicenseException ex) {
            throw new TaskException(ex.getMessage(), TaskException.Code.UNKNOWN);
        } catch (Exception ex) {
            throw new TaskException("导入 license 失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    public void settleRunUsage(Long userId, String sourceId, RuntimeRunUsageSnapshot snapshot) {
        // 已切换为基于 conversation_run_usage 的实时汇总，这里不再写 license 专用账户流水。
    }

    public boolean isAdminUser(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        return user != null && user.getUserType() != null && user.getUserType() == 0;
    }

    private LicenseSnapshot loadCurrentSnapshot() {
        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CURRENT_LICENSE_CONFIG_KEY);
        if (config == null || StringUtils.isBlank(config.getConfigValue())) {
            return null;
        }
        try {
            String decrypted = RSAEncryptor.decryptLargeText(config.getConfigValue().trim());
            StoredLicensePayload payload = parseStoredPayload(decrypted);
            if (payload == null) {
                return invalidSnapshot();
            }
            return toSnapshot(payload);
        } catch (Exception ex) {
            return invalidSnapshot();
        }
    }

    private StoredLicensePayload loadCurrentPayloadSafely() {
        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CURRENT_LICENSE_CONFIG_KEY);
        if (config == null || StringUtils.isBlank(config.getConfigValue())) {
            return null;
        }
        try {
            String decrypted = RSAEncryptor.decryptLargeText(config.getConfigValue().trim());
            return parseStoredPayload(decrypted);
        } catch (Exception ex) {
            return null;
        }
    }

    private StoredLicensePayload buildBootstrapPayload() {
        Date now = new Date();
        StoredLicensePayload payload = new StoredLicensePayload();
        payload.setSource(SOURCE_BOOTSTRAP);
        payload.setLicenseId("BOOTSTRAP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        payload.setSerialNo("BOOTSTRAP");
        payload.setRevision(1);
        payload.setProductCode(licenseProperties.getProductCode());
        payload.setEdition(BOOTSTRAP_EDITION);
        payload.setLicType(LicenseType.TRIAL);
        payload.setCustomerName(BOOTSTRAP_CUSTOMER_NAME);
        payload.setInstanceCode(licenseInstanceService.resolveInstanceCode());
        payload.setIssuedAt(now);
        payload.setEffectiveAt(now);
        payload.setExpiresAt(Date.from(ZonedDateTime.now().plusMonths(1).toInstant()));
        payload.setMaxActiveUsers(BOOTSTRAP_MAX_ACTIVE_USERS);
        payload.setMaxTotalTokens(0L);
        payload.setFeatureFlags(BOOTSTRAP_FEATURES);
        payload.setImportedBy(0L);
        payload.setImportedAt(now);
        payload.setLastVerifiedAt(now);
        return payload;
    }

    private StoredLicensePayload buildImportedPayload(
            Long operatorUserId, LicenseCryptoService.ParsedLicenseEnvelope envelope, String fileSha256) {
        Map<String, Object> payload = envelope.payload();
        StoredLicensePayload stored = new StoredLicensePayload();
        stored.setSource(SOURCE_IMPORTED);
        stored.setLicenseId(text(payload.get("licenseId")));
        stored.setSerialNo(text(payload.get("serialNo")));
        stored.setRevision(intValue(payload.get("revision"), 1));
        stored.setProductCode(text(payload.get("productCode")));
        stored.setEdition(text(payload.get("edition")));
        stored.setLicType(normalizeLicType(payload.get("licType")));
        stored.setCustomerName(text(payload.get("customerName")));
        stored.setInstanceCode(text(payload.get("instanceCode")));
        stored.setIssuedAt(dateValue(payload.get("issuedAt")));
        stored.setEffectiveAt(dateValue(payload.get("effectiveAt")));
        stored.setExpiresAt(dateValue(payload.get("expiresAt")));
        stored.setMaxActiveUsers(intValue(nested(payload, "limits", "maxUsers"), 0));
        stored.setMaxTotalTokens(longValue(nested(payload, "limits", "maxTotalTokens"), 0L));
        stored.setFeatureFlags(envelope.featureFlags());
        stored.setRawPayload(envelope.payloadBase64());
        stored.setRawSignature(envelope.signatureBase64());
        stored.setFileSha256(fileSha256);
        stored.setImportedBy(operatorUserId);
        stored.setImportedAt(new Date());
        stored.setLastVerifiedAt(new Date());
        return stored;
    }

    private LicenseSnapshot toSnapshot(StoredLicensePayload payload) {
        if (payload == null) {
            return null;
        }
        if (SOURCE_IMPORTED.equalsIgnoreCase(payload.getSource())) {
            return toImportedSnapshot(payload);
        }
        return new LicenseSnapshot(
                null,
                payload.getLicenseId(),
                payload.getRevision(),
                payload.getProductCode(),
                payload.getCustomerName(),
                payload.getEdition(),
                normalizeLicType(payload.getLicType()),
                payload.getInstanceCode(),
                STATUS_VALID,
                payload.getEffectiveAt(),
                payload.getExpiresAt(),
                payload.getMaxActiveUsers(),
                payload.getMaxTotalTokens(),
                payload.getFeatureFlags() == null ? List.of() : List.copyOf(payload.getFeatureFlags()),
                payload.getImportedAt());
    }

    private LicenseSnapshot toImportedSnapshot(StoredLicensePayload storedPayload) {
        try {
            LicenseCryptoService.ParsedLicenseEnvelope envelope =
                    licenseCryptoService.parseAndVerify(
                            buildRawLicense(storedPayload.getRawPayload(), storedPayload.getRawSignature()));
            Map<String, Object> payload = envelope.payload();
            return new LicenseSnapshot(
                    null,
                    text(payload.get("licenseId")),
                    intValue(payload.get("revision"), 1),
                    text(payload.get("productCode")),
                    text(payload.get("customerName")),
                    text(payload.get("edition")),
                    normalizeLicType(payload.get("licType")),
                    text(payload.get("instanceCode")),
                    STATUS_VALID,
                    dateValue(payload.get("effectiveAt")),
                    dateValue(payload.get("expiresAt")),
                    intValue(nested(payload, "limits", "maxUsers"), 0),
                    longValue(nested(payload, "limits", "maxTotalTokens"), 0L),
                    envelope.featureFlags(),
                    storedPayload.getImportedAt());
        } catch (Exception ex) {
            return new LicenseSnapshot(
                    null,
                    storedPayload.getLicenseId(),
                    storedPayload.getRevision(),
                    storedPayload.getProductCode(),
                    storedPayload.getCustomerName(),
                    storedPayload.getEdition(),
                    normalizeLicType(storedPayload.getLicType()),
                    storedPayload.getInstanceCode(),
                    STATUS_INVALID_SIGNATURE,
                    storedPayload.getEffectiveAt(),
                    storedPayload.getExpiresAt(),
                    storedPayload.getMaxActiveUsers(),
                    storedPayload.getMaxTotalTokens(),
                    storedPayload.getFeatureFlags() == null ? List.of() : List.copyOf(storedPayload.getFeatureFlags()),
                    storedPayload.getImportedAt());
        }
    }

    private String buildRawLicense(String payloadBase64, String signatureBase64) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("alg", "SHA256withRSA");
        raw.put("payload", nullToEmpty(payloadBase64));
        raw.put("signature", nullToEmpty(signatureBase64));
        return JSON.toJSONString(raw);
    }

    private LicenseSnapshot invalidSnapshot() {
        return new LicenseSnapshot(
                null,
                "",
                0,
                licenseProperties.getProductCode(),
                "",
                "",
                LicenseType.TRIAL,
                licenseInstanceService.resolveInstanceCode(),
                STATUS_INVALID_SIGNATURE,
                null,
                null,
                0,
                0L,
                List.of(),
                null);
    }

    private void saveCurrentLicense(StoredLicensePayload payload) throws Exception {
        saveCurrentLicenseForUpdate(payload);
    }

    private void saveCurrentLicenseForUpdate(StoredLicensePayload payload) throws Exception {
        String encrypted = RSAEncryptor.encryptLargeText(serializeStoredPayload(payload));
        upsertSystemConfigForUpdate(CURRENT_LICENSE_CONFIG_KEY, encrypted, 1);
    }

    private String resolveBootstrapStateForUpdate() {
        SystemConfigModel config = systemConfigMapper.selectByConfigKeyForUpdate(BOOTSTRAP_STATE_CONFIG_KEY);
        if (config == null) {
            upsertSystemConfigForUpdate(BOOTSTRAP_STATE_CONFIG_KEY, BOOTSTRAP_STATE_PENDING_CIPHER, 1);
            return BOOTSTRAP_STATE_PENDING;
        }
        return decryptBootstrapState(config.getConfigValue());
    }

    private void updateBootstrapState(String state) {
        updateBootstrapStateForUpdate(state);
    }

    private void updateBootstrapStateForUpdate(String state) {
        upsertSystemConfigForUpdate(BOOTSTRAP_STATE_CONFIG_KEY, bootstrapStateCipher(state), 1);
    }

    private void upsertSystemConfigForUpdate(String configKey, String configValue, Integer status) {
        SystemConfigModel existing = systemConfigMapper.selectByConfigKeyForUpdate(configKey);
        if (existing == null) {
            SystemConfigModel entity = new SystemConfigModel();
            entity.setConfigKey(configKey);
            entity.setConfigValue(configValue);
            entity.setStatus(status);
            systemConfigMapper.insert(entity);
            return;
        }
        existing.setConfigValue(configValue);
        existing.setStatus(status);
        systemConfigMapper.updateById(existing);
    }

    private String bootstrapStateCipher(String state) {
        if (BOOTSTRAP_STATE_CONSUMED.equals(state)) {
            return BOOTSTRAP_STATE_CONSUMED_CIPHER;
        }
        return BOOTSTRAP_STATE_PENDING_CIPHER;
    }

    private String decryptBootstrapState(String encryptedValue) {
        if (StringUtils.isBlank(encryptedValue)) {
            return "";
        }
        try {
            return StringUtils.trim(RSAEncryptor.decryptLargeText(encryptedValue.trim()));
        } catch (Exception ex) {
            return "";
        }
    }

    private String evaluateStatus(LicenseSnapshot snapshot) {
        if (snapshot == null) {
            return STATUS_NOT_EFFECTIVE;
        }
        if (STATUS_INVALID_SIGNATURE.equals(snapshot.status())) {
            return STATUS_INVALID_SIGNATURE;
        }
        if (!licenseProperties.getProductCode().equalsIgnoreCase(snapshot.productCode())) {
            return STATUS_PRODUCT_MISMATCH;
        }
        if (!licenseInstanceService.resolveInstanceCode().equalsIgnoreCase(snapshot.instanceCode())) {
            return STATUS_INSTANCE_MISMATCH;
        }
        Instant now = Instant.now();
        if (snapshot.effectiveAt() != null && snapshot.effectiveAt().toInstant().isAfter(now)) {
            return STATUS_NOT_EFFECTIVE;
        }
        if (!isFormalLicense(snapshot.licType())
                && snapshot.expiresAt() != null
                && snapshot.expiresAt().toInstant().isBefore(now)) {
            return STATUS_EXPIRED;
        }
        if (!isFormalLicense(snapshot.licType())
                && snapshot.expiresAt() != null
                && snapshot.expiresAt().toInstant().isBefore(addDays(now, licenseProperties.getExpiringSoonDays()))) {
            return STATUS_EXPIRING_SOON;
        }
        return STATUS_VALID;
    }

    private int resolveErrorCode(String status) {
        return switch (status) {
            case STATUS_EXPIRED -> LicenseConstants.CODE_EXPIRED;
            case STATUS_INSTANCE_MISMATCH -> LicenseConstants.CODE_INSTANCE_MISMATCH;
            case STATUS_NOT_EFFECTIVE -> LicenseConstants.CODE_NOT_EFFECTIVE;
            case STATUS_INVALID_SIGNATURE -> LicenseConstants.CODE_INVALID_SIGNATURE;
            default -> LicenseConstants.CODE_NOT_FOUND;
        };
    }

    private String resolveStatusMessage(String status) {
        return switch (status) {
            case STATUS_EXPIRED -> "license 已过期，请联系管理员导入新授权";
            case STATUS_INSTANCE_MISMATCH -> "license 与当前实例不匹配";
            case STATUS_NOT_EFFECTIVE -> "license 尚未生效";
            case STATUS_PRODUCT_MISMATCH -> "license 产品编码不匹配";
            case STATUS_INVALID_SIGNATURE -> "license 签名校验失败";
            default -> "license 状态无效";
        };
    }

    private int countActiveUsers() {
        return (int) sysUserMapper.selectList(null).stream()
                .filter(user -> user != null && user.getState() != null && user.getState() == 1)
                .filter(user -> user.getLicenseExempt() == null || user.getLicenseExempt() != 1)
                .count();
    }

    private long sumConsumedTokens(Date createdAtInclusive) {
        return conversationRunUsageMapper.sumConsumedTokensSince(createdAtInclusive);
    }

    private Long calculateRemainingTokens(Long maxTotalTokens, long consumedTokens) {
        if (maxTotalTokens == null || maxTotalTokens <= 0) {
            return 0L;
        }
        return Math.max(maxTotalTokens - Math.max(consumedTokens, 0L), 0L);
    }

    private boolean sameLicenseRevision(StoredLicensePayload current, StoredLicensePayload candidate) {
        return current != null
                && candidate != null
                && StringUtils.equalsIgnoreCase(text(current.getLicenseId()), text(candidate.getLicenseId()))
                && current.getRevision() != null
                && candidate.getRevision() != null
                && current.getRevision().intValue() == candidate.getRevision().intValue();
    }

    private boolean isOlderRevision(StoredLicensePayload current, StoredLicensePayload candidate) {
        return current != null
                && candidate != null
                && StringUtils.equalsIgnoreCase(text(current.getLicenseId()), text(candidate.getLicenseId()))
                && current.getRevision() != null
                && candidate.getRevision() != null
                && candidate.getRevision() < current.getRevision();
    }

    private String serializeStoredPayload(StoredLicensePayload payload) {
        return JSON.toJSONString(payload.toMap());
    }

    @SuppressWarnings("unchecked")
    private StoredLicensePayload parseStoredPayload(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        Map<String, Object> map = JSON.parseObject(json, Map.class);
        if (map == null || map.isEmpty()) {
            return null;
        }
        StoredLicensePayload payload = new StoredLicensePayload();
        payload.setSource(text(map.get("source")));
        payload.setLicenseId(text(map.get("licenseId")));
        payload.setSerialNo(text(map.get("serialNo")));
        payload.setRevision(intValue(map.get("revision"), 1));
        payload.setProductCode(text(map.get("productCode")));
        payload.setEdition(text(map.get("edition")));
        payload.setLicType(normalizeLicType(map.get("licType")));
        payload.setCustomerName(text(map.get("customerName")));
        payload.setInstanceCode(text(map.get("instanceCode")));
        payload.setIssuedAt(dateValue(map.get("issuedAt")));
        payload.setEffectiveAt(dateValue(map.get("effectiveAt")));
        payload.setExpiresAt(dateValue(map.get("expiresAt")));
        payload.setMaxActiveUsers(intValue(map.get("maxActiveUsers"), 0));
        payload.setMaxTotalTokens(longValue(map.get("maxTotalTokens"), 0L));
        payload.setRawPayload(textOrNull(map.get("rawPayload")));
        payload.setRawSignature(textOrNull(map.get("rawSignature")));
        payload.setFileSha256(textOrNull(map.get("fileSha256")));
        payload.setImportedBy(longObjectValue(map.get("importedBy")));
        payload.setImportedAt(dateValue(map.get("importedAt")));
        payload.setLastVerifiedAt(dateValue(map.get("lastVerifiedAt")));
        Object featureFlags = map.get("featureFlags");
        if (featureFlags instanceof List<?> values) {
            payload.setFeatureFlags(values.stream().map(String::valueOf).toList());
        } else {
            payload.setFeatureFlags(List.of());
        }
        return payload;
    }

    private List<String> parseFeatureFlags(String featureFlagsJson) {
        if (StringUtils.isBlank(featureFlagsJson)) {
            return List.of();
        }
        try {
            return JSON.parseArray(featureFlagsJson, String.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isFormalLicense(LicenseType licType) {
        return normalizeLicType(licType).isFormal();
    }

    private LicenseType normalizeLicType(Object rawLicType) {
        return LicenseType.fromValue(rawLicType);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrNull(Object value) {
        String text = text(value);
        return StringUtils.isBlank(text) ? null : text;
    }

    private Integer intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Long longObjectValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> payload, String parentKey, String childKey) {
        if (payload == null) {
            return null;
        }
        Object parent = payload.get(parentKey);
        if (!(parent instanceof Map<?, ?> map)) {
            return null;
        }
        return ((Map<String, Object>) map).get(childKey);
    }

    private Date dateValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof Number number) {
            return new Date(number.longValue());
        }
        try {
            return Date.from(Instant.parse(String.valueOf(value).trim()));
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private Instant addDays(Instant instant, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(Date.from(instant));
        calendar.add(Calendar.DAY_OF_YEAR, Math.max(days, 0));
        return calendar.toInstant();
    }

}
