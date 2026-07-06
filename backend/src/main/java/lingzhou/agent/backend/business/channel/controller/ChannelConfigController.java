package lingzhou.agent.backend.business.channel.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.adapter.ChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.dingtalk.DingTalkAppRegistrationService;
import lingzhou.agent.backend.business.channel.adapter.wechat.WechatIlinkChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.wecom.WeComChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.domain.enums.ChannelRouteType;
import lingzhou.agent.backend.business.channel.runtime.ChannelManager;
import lingzhou.agent.backend.business.channel.service.ChannelConfigService;
import lingzhou.agent.backend.business.channel.service.ChannelSessionBindingService;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/channel/configs")
public class ChannelConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ChannelConfigController.class);

    private final ChannelConfigService channelConfigService;
    private final ChannelManager channelManager;
    private final ChannelSessionBindingService channelSessionBindingService;
    private final ChannelUserBindingService channelUserBindingService;
    private final DingTalkAppRegistrationService dingTalkAppRegistrationService;

    public ChannelConfigController(
            ChannelConfigService channelConfigService,
            ChannelManager channelManager,
            ChannelSessionBindingService channelSessionBindingService,
            ChannelUserBindingService channelUserBindingService,
            DingTalkAppRegistrationService dingTalkAppRegistrationService) {
        this.channelConfigService = channelConfigService;
        this.channelManager = channelManager;
        this.channelSessionBindingService = channelSessionBindingService;
        this.channelUserBindingService = channelUserBindingService;
        this.dingTalkAppRegistrationService = dingTalkAppRegistrationService;
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<>();
        result.put("items", channelConfigService.listAll());
        return result;
    }

    @GetMapping("/bindings/me")
    public Map<String, Object> listMyBindings(HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        List<Map<String, Object>> items = channelUserBindingService.listByOwnerUserId(userId).stream()
                .map(binding -> buildMyBindingStatus(binding, userId))
                .toList();
        return Map.of("items", items);
    }

    @PostMapping
    public ChannelConfig create(@RequestBody ChannelConfig request, HttpServletRequest httpRequest) {
        bindOwnerIfAbsent(request, httpRequest);
        ChannelConfig created = channelConfigService.create(request);
        syncChannelSafely(created);
        return created;
    }

    @PutMapping("/{id}")
    public ChannelConfig update(
            @PathVariable("id") Long id, @RequestBody ChannelConfig request, HttpServletRequest httpRequest) {
        bindOwnerIfAbsent(request, httpRequest);
        ChannelConfig updated = channelConfigService.update(id, request);
        syncChannelSafely(updated);
        return updated;
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable("id") Long id) {
        ChannelAdapter adapter = channelManager.startChannel(id);
        return Map.of(
                "success", Boolean.TRUE,
                "channelId", adapter.getChannelId(),
                "channelType", adapter.getChannelType(),
                "running", adapter.isRunning());
    }

    @PostMapping("/{id}/stop")
    public Map<String, Object> stop(@PathVariable("id") Long id) {
        channelManager.stopChannel(id);
        return Map.of("success", Boolean.TRUE, "channelId", id);
    }

    @PostMapping("/{id}/restart")
    public Map<String, Object> restart(@PathVariable("id") Long id) {
        ChannelAdapter adapter = channelManager.restartChannel(id);
        return Map.of(
                "success", Boolean.TRUE,
                "channelId", adapter.getChannelId(),
                "channelType", adapter.getChannelType(),
                "running", adapter.isRunning());
    }

    @PostMapping("/{id}/weixin/login")
    public Map<String, Object> beginWechatLogin(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        WechatIlinkChannelAdapter adapter = requireWechatAdapter(id);
        Map<String, Object> result = new HashMap<>(adapter.beginLoginPayload(userId));
        result.put("success", Boolean.TRUE);
        result.put("channelId", id);
        result.put("ownerUserId", userId);
        return result;
    }

    @GetMapping("/{id}/weixin/status")
    public Map<String, Object> getWechatLoginStatus(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        ChannelAdapter runningAdapter = channelManager.getAdapter(id).orElse(null);
        if (runningAdapter == null) {
            return Map.of(
                    "channelId",
                    id,
                    "status",
                    "NOT_STARTED",
                    "ownerUserId",
                    userId,
                    "loggedIn",
                    Boolean.FALSE,
                    "running",
                    Boolean.FALSE);
        }
        WechatIlinkChannelAdapter adapter = requireWechatAdapter(id);
        LoginStatus loginStatus = adapter.getLoginStatus(userId);
        boolean loggedIn = adapter.isLoggedIn(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("channelId", id);
        result.put("ownerUserId", userId);
        result.put("status", resolveWechatStatus(loginStatus, loggedIn));
        result.put("errorMessage", loginStatus.getErrorMessage());
        result.put("loggedIn", loggedIn);
        result.put("running", adapter.isRunning());
        return result;
    }

    @GetMapping("/{id}/wecom/status")
    public Map<String, Object> getWecomRuntimeStatus(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        ChannelConfig config = channelConfigService.getRequired(id);
        if (!"wecom".equalsIgnoreCase(config.getChannelType())) {
            throw new IllegalArgumentException("当前渠道不是企业微信: " + id);
        }
        boolean runtimeBound = channelUserBindingService.hasScopedRuntimeContext(id, userId, "wecom");
        ChannelAdapter runningAdapter = channelManager.getAdapter(id).orElse(null);
        if (runningAdapter == null) {
            return Map.of(
                    "channelId",
                    id,
                    "ownerUserId",
                    userId,
                    "running",
                    Boolean.FALSE,
                    "status",
                    "NOT_STARTED",
                    "connected",
                    Boolean.FALSE,
                    "authenticated",
                    Boolean.FALSE,
                    "runtimeBound",
                    runtimeBound);
        }
        if (!(runningAdapter instanceof WeComChannelAdapter weComChannelAdapter)) {
            throw new IllegalStateException("企业微信渠道适配器类型异常: " + id);
        }
        if (runtimeBound) {
            try {
                weComChannelAdapter.switchActiveOwner(userId);
            } catch (Exception ex) {
                logger.warn("企业微信渠道切换账号运行态失败：channelId={}, ownerUserId={}, reason={}", id, userId, ex.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>(weComChannelAdapter.getRuntimeStatus(userId));
        result.put("channelId", id);
        result.put("ownerUserId", userId);
        result.put("runtimeBound", runtimeBound);
        return result;
    }

    @PutMapping("/{id}/wecom/binding/me")
    public Map<String, Object> bindWecomCredential(
            @PathVariable("id") Long id, @RequestBody WecomBindingRequest request, HttpServletRequest httpRequest) {
        Long userId = resolveCurrentUserId(httpRequest);
        ChannelConfig channelConfig = channelConfigService.getRequired(id);
        if (!"wecom".equalsIgnoreCase(channelConfig.getChannelType())) {
            throw new IllegalArgumentException("当前渠道不是企业微信: " + id);
        }
        channelUserBindingService.saveWecomCredential(id, userId, request.botId(), request.secret(), request.source());
        ChannelConfig updatedConfig = channelConfigService.bindOwnerUserIfAbsent(id, userId);
        ChannelAdapter runningAdapter = channelManager.getAdapter(id).orElse(null);
        if (runningAdapter instanceof WeComChannelAdapter weComChannelAdapter && weComChannelAdapter.isRunning()) {
            weComChannelAdapter.switchActiveOwner(userId);
        } else {
            syncChannelSafely(updatedConfig);
        }
        ChannelUserBindingService.WecomCredential credential = channelUserBindingService.getWecomCredential(id, userId);
        return Map.of(
                "success", Boolean.TRUE, "channelId", id, "ownerUserId", userId, "runtimeBound", credential != null);
    }

    @PostMapping("/{id}/dingtalk/register/begin")
    public Map<String, Object> beginDingtalkRegister(@PathVariable("id") Long id) {
        ChannelConfig config = channelConfigService.getRequired(id);
        if (!"dingtalk".equalsIgnoreCase(config.getChannelType())) {
            throw new IllegalArgumentException("当前渠道不是钉钉: " + id);
        }
        try {
            DingTalkAppRegistrationService.RegistrationSession session = dingTalkAppRegistrationService.begin();
            return Map.of(
                    "success", Boolean.TRUE,
                    "channelId", id,
                    "sessionId", session.sessionId,
                    "session_id", session.sessionId,
                    "verificationUrl", session.qrcodeUrl,
                    "verification_url", session.qrcodeUrl);
        } catch (Exception ex) {
            logger.error("钉钉扫码注册启动失败：channelId={}, error={}", id, ex.getMessage(), ex);
            throw new IllegalStateException("钉钉扫码注册启动失败: " + ex.getMessage(), ex);
        }
    }

    @GetMapping("/{id}/dingtalk/register/status")
    public Map<String, Object> getDingtalkRegisterStatus(
            @PathVariable("id") Long id, @org.springframework.web.bind.annotation.RequestParam("session") String sessionId) {
        ChannelConfig config = channelConfigService.getRequired(id);
        if (!"dingtalk".equalsIgnoreCase(config.getChannelType())) {
            throw new IllegalArgumentException("当前渠道不是钉钉: " + id);
        }
        DingTalkAppRegistrationService.RegistrationSession session = dingTalkAppRegistrationService.getSession(sessionId);
        if (session == null) {
            return Map.of(
                    "channelId", id,
                    "status", "expired",
                    "error", "session not found or expired");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channelId", id);
        result.put("status", session.status.name().toLowerCase());
        if (session.qrcodeUrl != null) {
            result.put("qrcode_url", session.qrcodeUrl);
            if (session.qrcodeImageDataUri == null) {
                try {
                    session.qrcodeImageDataUri = "data:image/png;base64," + generateQrCodeBase64(session.qrcodeUrl);
                } catch (Exception ex) {
                    logger.warn("钉钉扫码注册地址二维码生成失败：channelId={}, error={}", id, ex.getMessage());
                }
            }
            if (session.qrcodeImageDataUri != null) {
                result.put("qrcode_img", session.qrcodeImageDataUri);
            }
        }
        if (session.status == DingTalkAppRegistrationService.Status.CONFIRMED) {
            result.put("client_id", session.clientId);
            result.put("client_secret", session.clientSecret);
            result.put("clientId", session.clientId);
            result.put("clientSecret", session.clientSecret);
        }
        if (session.errorMessage != null) {
            result.put("error", session.errorMessage);
        }
        return result;
    }

    @PostMapping("/{id}/weixin/poll")
    public Map<String, Object> pollWechatOnce(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        WechatIlinkChannelAdapter adapter = requireWechatAdapter(id);
        adapter.pollOnce(userId);
        return Map.of("success", Boolean.TRUE, "channelId", id, "ownerUserId", userId);
    }

    @GetMapping("/{id}/binding/me")
    public Map<String, Object> getMyBinding(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        ChannelConfig channelConfig = channelConfigService.getRequired(id);
        ChannelUserBinding binding = channelUserBindingService.findByChannelAndUser(id, userId);
        boolean runtimeBound =
                channelUserBindingService.hasScopedRuntimeContext(id, userId, channelConfig.getChannelType());
        Map<String, Object> result = new HashMap<>();
        result.put("channelId", id);
        result.put("ownerUserId", userId);
        result.put("runtimeBound", runtimeBound);
        if (binding == null) {
            result.put(
                    "routeType",
                    ChannelRouteType.fromValue(channelConfig.getRouteType()).name());
            result.put("routeTargetId", channelConfig.getRouteTargetId());
            return result;
        }
        result.put("id", binding.getId());
        result.put(
                "routeType", ChannelRouteType.fromValue(binding.getRouteType()).name());
        result.put("routeTargetId", binding.getRouteTargetId());
        return result;
    }

    @PutMapping("/{id}/binding/me")
    public Map<String, Object> saveMyBinding(
            @PathVariable("id") Long id, @RequestBody ChannelBindingRequest request, HttpServletRequest httpRequest) {
        Long userId = resolveCurrentUserId(httpRequest);
        ChannelConfig channelConfig = channelConfigService.getRequired(id);
        ChannelUserBinding binding = channelUserBindingService.saveBinding(
                id,
                channelConfig.getChannelType(),
                userId,
                ChannelRouteType.fromValue(request.routeType()).name(),
                request.routeTargetId());
        ChannelConfig updatedConfig = channelConfigService.bindOwnerUserIfAbsent(id, userId);
        syncChannelSafely(updatedConfig);
        Map<String, Object> result = new HashMap<>();
        result.put("id", binding.getId());
        result.put("channelId", id);
        result.put("ownerUserId", userId);
        result.put("routeType", binding.getRouteType());
        result.put("routeTargetId", binding.getRouteTargetId());
        return result;
    }

    @DeleteMapping("/{id}/binding/me")
    public Map<String, Object> closeMyBinding(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        ChannelConfig channelConfig = channelConfigService.getRequired(id);
        boolean deleted = channelUserBindingService.deleteBinding(id, userId);
        channelManager.disconnectUser(id, userId);
        if ("dingtalk".equalsIgnoreCase(channelConfig.getChannelType())
                && userId.equals(channelConfig.getOwnerUserId())) {
            channelManager.syncChannel(channelConfigService.disable(id));
        }
        return Map.of("success", Boolean.TRUE, "channelId", id, "closed", deleted);
    }

    @PostMapping("/{id}/send")
    public Map<String, Object> send(
            @PathVariable("id") Long id,
            @RequestBody SendChannelMessageRequest request,
            HttpServletRequest httpRequest) {
        Long userId = resolveCurrentUserId(httpRequest);
        String targetId = request.targetId();
        if ((targetId == null || targetId.isBlank())
                && request.externalSessionKey() != null
                && !request.externalSessionKey().isBlank()) {
            targetId = channelSessionBindingService.resolveReplyTarget(id, request.externalSessionKey());
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("缺少可用的 targetId");
        }
        channelManager.sendMessage(id, userId, targetId, request.content());
        return Map.of("success", Boolean.TRUE, "channelId", id, "targetId", targetId, "ownerUserId", userId);
    }

    private WechatIlinkChannelAdapter requireWechatAdapter(Long channelId) {
        ChannelAdapter adapter = channelManager.getOrStartAdapter(channelId);
        if (adapter instanceof WechatIlinkChannelAdapter wechatAdapter) {
            return wechatAdapter;
        }
        throw new IllegalArgumentException("当前渠道不是微信 iLink: " + channelId);
    }

    private Map<String, Object> buildMyBindingStatus(ChannelUserBinding binding, Long userId) {
        ChannelConfig config = channelConfigService.getRequired(binding.getChannelId());
        String channelType = config.getChannelType();
        boolean connected = false;
        String status = "NOT_CONNECTED";
        ChannelAdapter adapter = channelManager.getAdapter(config.getId()).orElse(null);
        if ("weixin".equalsIgnoreCase(channelType) && adapter instanceof WechatIlinkChannelAdapter wechatAdapter) {
            connected = wechatAdapter.isLoggedIn(userId);
            status = connected ? "CONNECTED" : "NOT_CONNECTED";
        } else if ("wecom".equalsIgnoreCase(channelType) && adapter instanceof WeComChannelAdapter weComAdapter) {
            Map<String, Object> runtimeStatus = weComAdapter.getRuntimeStatus(userId);
            connected = Boolean.TRUE.equals(runtimeStatus.get("connected"))
                    || Boolean.TRUE.equals(runtimeStatus.get("authenticated"));
            status = connected ? "CONNECTED" : String.valueOf(runtimeStatus.getOrDefault("status", "NOT_CONNECTED"));
        } else if ("dingtalk".equalsIgnoreCase(channelType)) {
            connected = Boolean.TRUE.equals(config.getEnabled()) && adapter != null && adapter.isRunning();
            status = connected ? "CONNECTED" : "NOT_CONNECTED";
        } else if (adapter != null && adapter.isRunning()) {
            connected = true;
            status = "CONNECTED";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channelId", config.getId());
        result.put("channelType", channelType);
        result.put("name", config.getName());
        result.put("routeType", binding.getRouteType());
        result.put("routeTargetId", binding.getRouteTargetId());
        result.put("connected", connected);
        result.put("status", status);
        result.put("statusLabel", resolveBindingStatusLabel(status, connected));
        return result;
    }

    private String resolveBindingStatusLabel(String status, boolean connected) {
        if (connected) {
            return "正常";
        }
        if ("CONNECTING".equalsIgnoreCase(status)) {
            return "连接中";
        }
        if ("STOPPED".equalsIgnoreCase(status)) {
            return "已停止";
        }
        return "未连接";
    }

    private String resolveWechatStatus(LoginStatus loginStatus, boolean loggedIn) {
        if (loggedIn) {
            return "LOGGED_IN";
        }
        if (loginStatus == null || loginStatus.getStatus() == null) {
            return "NOT_LOGIN";
        }
        return loginStatus.getStatus().name();
    }

    private Long resolveCurrentUserId(HttpServletRequest request) {
        Object userIdValue = request.getAttribute("UserId");
        if (userIdValue == null) {
            throw new IllegalArgumentException("未授权");
        }
        try {
            return Long.parseLong(String.valueOf(userIdValue));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("无效的用户身份");
        }
    }

    private void bindOwnerIfAbsent(ChannelConfig request, HttpServletRequest httpRequest) {
        if (request == null) {
            return;
        }
        if (request.getOwnerUserId() != null && request.getOwnerUserId() > 0) {
            return;
        }
        request.setOwnerUserId(resolveCurrentUserId(httpRequest));
    }

    private void syncChannelSafely(ChannelConfig config) {
        try {
            channelManager.syncChannel(config);
        } catch (IllegalArgumentException ex) {
            if (isWecomPendingBinding(config, ex)) {
                logger.info(
                        "企业微信渠道暂未绑定账号登录态，已保存配置，等待扫码绑定：channelId={}, ownerUserId={}, reason={}",
                        config == null ? null : config.getId(),
                        config == null ? null : config.getOwnerUserId(),
                        ex.getMessage());
                return;
            }
            if (isDingtalkPendingCredential(config, ex)) {
                logger.info(
                        "钉钉渠道暂未填写 Stream 凭证，已保存配置，等待授权接入：channelId={}, ownerUserId={}, reason={}",
                        config == null ? null : config.getId(),
                        config == null ? null : config.getOwnerUserId(),
                        ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private boolean isWecomPendingBinding(ChannelConfig config, IllegalArgumentException ex) {
        if (config == null || ex == null) {
            return false;
        }
        if (!"wecom".equalsIgnoreCase(config.getChannelType())) {
            return false;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("未绑定企业微信登录态")
                || message.contains("暂无可用企业微信登录态")
                || message.contains("缺少 ownerUserId")
                || message.contains("重新扫码绑定");
    }

    private boolean isDingtalkPendingCredential(ChannelConfig config, IllegalArgumentException ex) {
        if (config == null || ex == null) {
            return false;
        }
        if (!"dingtalk".equalsIgnoreCase(config.getChannelType())) {
            return false;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("clientId/clientSecret");
    }

    private String generateQrCodeBase64(String content) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints =
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2);
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("二维码生成失败: " + ex.getMessage(), ex);
        }
    }

    private record SendChannelMessageRequest(String targetId, String externalSessionKey, String content) {}

    private record ChannelBindingRequest(String routeType, Long routeTargetId) {}

    private record WecomBindingRequest(String botId, String secret, String source) {}
}
