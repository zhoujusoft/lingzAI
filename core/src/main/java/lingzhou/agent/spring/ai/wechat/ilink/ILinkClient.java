package lingzhou.agent.spring.ai.wechat.ilink;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.ContextPoolManager;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.GetUpdatesCursorStore;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.ILinkException;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.NotLoginException;
import lingzhou.agent.spring.ai.wechat.ilink.core.executor.ExecutorManager;
import lingzhou.agent.spring.ai.wechat.ilink.core.http.BusinessApiClient;
import lingzhou.agent.spring.ai.wechat.ilink.core.http.HttpClientFacade;
import lingzhou.agent.spring.ai.wechat.ilink.core.lifecycle.HealthChecker;
import lingzhou.agent.spring.ai.wechat.ilink.core.lifecycle.HeartbeatService;
import lingzhou.agent.spring.ai.wechat.ilink.core.listener.ListenerRegistry;
import lingzhou.agent.spring.ai.wechat.ilink.core.listener.OnLoginListener;
import lingzhou.agent.spring.ai.wechat.ilink.core.listener.OnMessageListener;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginStatus;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.QRCodeResponse;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.CDNMedia;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.FileItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.ImageItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.MessageItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VideoItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VoiceItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.retry.ExponentialBackoffStrategy;
import lingzhou.agent.spring.ai.wechat.ilink.core.retry.RetryPolicy;
import lingzhou.agent.spring.ai.wechat.ilink.core.serializer.JsonSerializer;
import lingzhou.agent.spring.ai.wechat.ilink.core.serializer.Serializer;
import lingzhou.agent.spring.ai.wechat.ilink.core.state.ClientStateManager;
import lingzhou.agent.spring.ai.wechat.ilink.core.state.ConnectionStatus;
import lingzhou.agent.spring.ai.wechat.ilink.service.LoginService;
import lingzhou.agent.spring.ai.wechat.ilink.service.MediaService;
import lingzhou.agent.spring.ai.wechat.ilink.service.MessageService;
import lingzhou.agent.spring.ai.wechat.ilink.service.TypingService;
import lingzhou.agent.spring.ai.wechat.ilink.service.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ILinkClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ILinkClient.class);

    private final ILinkConfig config;
    private final ListenerRegistry listenerRegistry;
    private final ExecutorManager executorManager;
    private final ClientStateManager stateManager = new ClientStateManager();
    private final ContextPoolManager contextPoolManager = ContextPoolManager.getInstance();
    private final GetUpdatesCursorStore cursorStore = new GetUpdatesCursorStore();

    private final Serializer serializer;
    private final RetryPolicy retryPolicy;
    private final HttpClientFacade httpClientFacade;
    private final BusinessApiClient businessApiClient;

    private final LoginService loginService;
    private final LoginStatus loginStatus = new LoginStatus();
    private final AtomicReference<LoginContext> loginContext = new AtomicReference<LoginContext>();
    private final UpdateService updateService;
    private final MediaService mediaService;
    private final MessageService messageService;
    private final TypingService typingService;

    private volatile CompletableFuture<LoginContext> loginFuture;
    private volatile String qrcode;
    private HeartbeatService heartbeatService;

    public static ILinkClientBuilder builder() {
        return new ILinkClientBuilder();
    }

    public ILinkClient(ILinkConfig config, ListenerRegistry listenerRegistry) {
        this.config = config;
        this.listenerRegistry = listenerRegistry;
        this.executorManager = new ExecutorManager(config);
        this.serializer = new JsonSerializer();
        this.retryPolicy = new RetryPolicy(
                config.getHttpMaxRetries(),
                new ExponentialBackoffStrategy(
                        config.getRetryBaseDelayMs(), config.getRetryMaxDelayMs(), config.isRetryJitterEnabled()));
        this.httpClientFacade = new HttpClientFacade(config, retryPolicy);
        this.businessApiClient = new BusinessApiClient(config, serializer, httpClientFacade);
        this.loginService = new LoginService(config, serializer, httpClientFacade, executorManager.ioExecutor());
        this.updateService = new UpdateService(config, businessApiClient, cursorStore);
        this.mediaService = new MediaService(config, businessApiClient, httpClientFacade);
        this.messageService = new MessageService(config, businessApiClient, mediaService);
        this.typingService = new TypingService(config, businessApiClient);
        initHeartbeat();
    }

    private void initHeartbeat() {
        if (!config.isHeartbeatEnabled()) return;
        this.heartbeatService = new HeartbeatService(
                executorManager.scheduler(),
                config.getHeartbeatIntervalMs(),
                new HealthChecker() {
                    @Override
                    public void check() throws Exception {
                        if (!stateManager.isLoggedIn()) return;
                        LoginContext ctx = loginContext.get();
                        if (ctx == null) return;
                        updateService.poll(ctx);
                    }
                },
                listenerRegistry);
    }

    public String executeLogin() {
        stateManager.set(ConnectionStatus.CONNECTING);
        try {
            QRCodeResponse response = loginService.getQRCode();
            this.qrcode = response.getQrcode();
            this.loginFuture = loginService.startLoginPolling(qrcode, loginStatus, loginContext);
            this.loginFuture.whenComplete((ctx, ex) -> {
                if (ex != null) {
                    stateManager.set(ConnectionStatus.DISCONNECTED);
                    for (OnLoginListener l : listenerRegistry.getLoginListeners()) {
                        l.onLoginFailure(ex);
                    }
                    return;
                }
                stateManager.set(ConnectionStatus.LOGGED_IN);
                for (OnLoginListener l : listenerRegistry.getLoginListeners()) {
                    l.onLoginSuccess(ctx);
                }
                if (heartbeatService != null) {
                    heartbeatService.start();
                }
            });
            return response.getQrcodeImgContent();
        } catch (RuntimeException | IOException e) {
            stateManager.set(ConnectionStatus.DISCONNECTED);
            for (OnLoginListener l : listenerRegistry.getLoginListeners()) {
                l.onLoginFailure(e);
            }
            throw new RuntimeException("start login failed", e);
        }
    }

    public List<lingzhou.agent.spring.ai.wechat.ilink.core.model.WeixinMessage> getUpdates() throws IOException {
        List<lingzhou.agent.spring.ai.wechat.ilink.core.model.WeixinMessage> messages =
                updateService.poll(requireLogin());
        if (messages != null && !messages.isEmpty()) {
            for (OnMessageListener l : listenerRegistry.getMessageListeners()) {
                l.onMessages(messages);
            }
        }
        return messages;
    }

    public void sendText(String toUserId, String text) throws IOException {
        messageService.sendText(requireLogin(), toUserId, text);
    }

    public void sendTextWithTyping(String toUserId, String text, long typingMillis) throws IOException {
        typingService.startTyping(requireLogin(), toUserId);
        try {
            if (typingMillis > 0) {
                try {
                    Thread.sleep(typingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            messageService.sendText(requireLogin(), toUserId, text);
        } finally {
            typingService.stopTyping(requireLogin(), toUserId);
        }
    }

    public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) throws IOException {
        messageService.sendImage(requireLogin(), toUserId, imageBytes, fileName, caption);
    }

    public void sendFile(String toUserId, byte[] fileBytes, String fileName, String caption) throws IOException {
        messageService.sendFile(requireLogin(), toUserId, fileBytes, fileName, caption);
    }

    public void sendVoice(String toUserId, byte[] voiceBytes, String fileName, Integer playTimeMs, Integer sampleRate)
            throws IOException {
        messageService.sendVoice(requireLogin(), toUserId, voiceBytes, fileName, playTimeMs, sampleRate);
    }

    public void sendVideo(String toUserId, byte[] videoBytes, String fileName, Integer playLengthMs, String caption)
            throws IOException {
        messageService.sendVideo(requireLogin(), toUserId, videoBytes, fileName, playLengthMs, caption);
    }

    public void startTyping(String toUserId) throws IOException {
        typingService.startTyping(requireLogin(), toUserId);
    }

    public void stopTyping(String toUserId) throws IOException {
        typingService.stopTyping(requireLogin(), toUserId);
    }

    public byte[] downloadMedia(CDNMedia media) throws IOException {
        return mediaService.downloadMedia(media);
    }

    public byte[] downloadMediaFromMessageItem(MessageItem item) throws IOException {
        if (item == null) {
            throw new ILinkException("message item is null");
        }

        ImageItem imageItem = item.getImage_item();
        if (imageItem != null && imageItem.getMedia() != null) {
            return mediaService.downloadMedia(imageItem.getMedia());
        }

        FileItem fileItem = item.getFile_item();
        if (fileItem != null && fileItem.getMedia() != null) {
            return mediaService.downloadMedia(fileItem.getMedia());
        }

        VoiceItem voiceItem = item.getVoice_item();
        if (voiceItem != null && voiceItem.getMedia() != null) {
            return mediaService.downloadMedia(voiceItem.getMedia());
        }

        VideoItem videoItem = item.getVideo_item();
        if (videoItem != null && videoItem.getMedia() != null) {
            return mediaService.downloadMedia(videoItem.getMedia());
        }

        throw new ILinkException("message item does not contain downloadable media");
    }

    public byte[] downloadImageFromMessageItem(MessageItem item) throws IOException {
        if (item == null || item.getImage_item() == null || item.getImage_item().getMedia() == null) {
            throw new ILinkException("message item does not contain image media");
        }
        return mediaService.downloadMedia(item.getImage_item().getMedia());
    }

    public byte[] downloadFileFromMessageItem(MessageItem item) throws IOException {
        if (item == null || item.getFile_item() == null || item.getFile_item().getMedia() == null) {
            throw new ILinkException("message item does not contain file media");
        }
        return mediaService.downloadMedia(item.getFile_item().getMedia());
    }

    public byte[] downloadVoiceFromMessageItem(MessageItem item) throws IOException {
        if (item == null || item.getVoice_item() == null || item.getVoice_item().getMedia() == null) {
            throw new ILinkException("message item does not contain voice media");
        }
        return mediaService.downloadMedia(item.getVoice_item().getMedia());
    }

    public byte[] downloadVideoFromMessageItem(MessageItem item) throws IOException {
        if (item == null || item.getVideo_item() == null || item.getVideo_item().getMedia() == null) {
            throw new ILinkException("message item does not contain video media");
        }
        return mediaService.downloadMedia(item.getVideo_item().getMedia());
    }

    public CompletableFuture<LoginContext> getLoginFuture() {
        return loginFuture;
    }

    public LoginContext getLoginContext() {
        return loginContext.get();
    }

    public LoginStatus getLoginStatus() {
        return loginStatus;
    }

    public synchronized void restoreLogin(LoginContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("login context is required");
        }
        loginService.cancelCurrentLogin();
        loginContext.set(ctx);
        loginStatus.reset();
        loginStatus.toLoggedIn();
        stateManager.set(ConnectionStatus.LOGGED_IN);
        loginFuture = CompletableFuture.completedFuture(ctx);
        if (heartbeatService != null) {
            heartbeatService.start();
        }
    }

    public ConnectionStatus getConnectionStatus() {
        return stateManager.get();
    }

    public boolean isLoggedIn() {
        return stateManager.isLoggedIn();
    }

    public String getQrcode() {
        return qrcode;
    }

    public ILinkConfig getConfig() {
        return config;
    }

    public void clearContext(String userId) {
        LoginContext ctx = loginContext.get();
        if (ctx != null) {
            contextPoolManager.remove(ctx.getBotId(), userId);
        }
    }

    public void clearAllContexts() {
        contextPoolManager.clearAll();
    }

    public void cancelLogin() {
        loginService.cancelCurrentLogin();
    }

    private LoginContext requireLogin() {
        LoginContext ctx = loginContext.get();
        if (ctx == null) {
            throw new NotLoginException("not logged in");
        }
        return ctx;
    }

    @Override
    public void close() {
        log.info("closing ILinkClient");
        stateManager.set(ConnectionStatus.DISCONNECTING);
        if (heartbeatService != null) {
            heartbeatService.close();
        }
        loginService.close();
        cursorStore.clear();
        contextPoolManager.clearAll();
        executorManager.close();
        stateManager.set(ConnectionStatus.CLOSED);
    }
}
