package lingzhou.agent.backend.business.channel.adapter.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.spring.ai.wechat.ilink.ILinkClient;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.SessionExpiredException;
import lingzhou.agent.spring.ai.wechat.ilink.core.listener.ListenerRegistry;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.WeixinMessage;
import org.junit.jupiter.api.Test;

class WechatIlinkChannelAdapterTests {

    @Test
    void pollOnceRemovesRuntimeWhenSessionExpired() throws Exception {
        RecordingChannelUserBindingService bindingService = new RecordingChannelUserBindingService();
        WechatIlinkChannelAdapter adapter = new WechatIlinkChannelAdapter(channelConfig(), null, null, bindingService);
        Long ownerUserId = 7L;
        ExpiringILinkClient client = new ExpiringILinkClient();
        runtimeMap(adapter).put(ownerUserId, userRuntime(ownerUserId, client));

        adapter.pollOnce(ownerUserId);

        assertThat(bindingService.clearedChannelId).isEqualTo(1L);
        assertThat(bindingService.clearedOwnerUserId).isEqualTo(ownerUserId);
        assertThat(runtimeMap(adapter)).doesNotContainKey(ownerUserId);
        assertThat(client.closed).isTrue();
    }

    @Test
    void staleLoginFutureDoesNotOverwriteLatestLoginContext() throws Exception {
        RecordingChannelUserBindingService bindingService = new RecordingChannelUserBindingService();
        WechatIlinkChannelAdapter adapter = new WechatIlinkChannelAdapter(channelConfig(), null, null, bindingService);
        Long ownerUserId = 7L;
        LoginILinkClient client = new LoginILinkClient();
        runtimeMap(adapter).put(ownerUserId, userRuntime(ownerUserId, client));

        adapter.beginLoginPayload(ownerUserId);
        CompletableFuture<LoginContext> staleFuture = client.lastLoginFuture;
        adapter.beginLoginPayload(ownerUserId);
        CompletableFuture<LoginContext> latestFuture = client.lastLoginFuture;

        staleFuture.complete(new LoginContext("stale-token", "stale-user", "stale-bot", "https://stale.example"));
        latestFuture.complete(new LoginContext("latest-token", "latest-user", "latest-bot", "https://latest.example"));

        assertThat(bindingService.savedLoginContexts).hasSize(1);
        assertThat(bindingService.savedLoginContexts.peek().getBotId()).isEqualTo("latest-bot");
    }

    @Test
    void multipleUsersUseIndependentPollExecutorsAndSameConfigRefreshKeepsThem() throws Exception {
        RecordingChannelUserBindingService bindingService = new RecordingChannelUserBindingService();
        WechatIlinkChannelAdapter adapter = new WechatIlinkChannelAdapter(channelConfig(), null, null, bindingService);
        runtimeMap(adapter).put(5L, userRuntime(5L, new IdleILinkClient()));
        runtimeMap(adapter).put(20L, userRuntime(20L, new IdleILinkClient()));

        adapter.start();
        try {
            Map<Long, ExecutorService> initialExecutors = Map.copyOf(pollExecutorMap(adapter));

            adapter.refreshConfig(channelConfig());

            assertThat(initialExecutors).hasSize(2);
            assertThat(pollExecutorMap(adapter)).containsAllEntriesOf(initialExecutors);
        } finally {
            adapter.stop();
        }
    }

    private ChannelConfig channelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setId(1L);
        config.setChannelType("weixin");
        config.setRouteType("GENERAL_CHAT");
        config.setConfigJson("{\"heartbeatEnabled\":false,"
                + "\"httpMaxRetries\":1,"
                + "\"connectTimeoutMs\":1000,"
                + "\"readTimeoutMs\":1000,"
                + "\"writeTimeoutMs\":1000}");
        return config;
    }

    private Object userRuntime(Long ownerUserId, ILinkClient client) throws Exception {
        Class<?> runtimeType = Class.forName(WechatIlinkChannelAdapter.class.getName() + "$UserRuntime");
        Constructor<?> constructor = runtimeType.getDeclaredConstructor(Long.class, ILinkClient.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ownerUserId, client);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> runtimeMap(WechatIlinkChannelAdapter adapter) throws Exception {
        Field field = WechatIlinkChannelAdapter.class.getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<Long, Object>) field.get(adapter);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ExecutorService> pollExecutorMap(WechatIlinkChannelAdapter adapter) throws Exception {
        Field field = WechatIlinkChannelAdapter.class.getDeclaredField("pollExecutors");
        field.setAccessible(true);
        return (Map<Long, ExecutorService>) field.get(adapter);
    }

    private static final class RecordingChannelUserBindingService extends ChannelUserBindingService {

        private Long clearedChannelId;
        private Long clearedOwnerUserId;
        private final Queue<LoginContext> savedLoginContexts = new ArrayDeque<>();

        private RecordingChannelUserBindingService() {
            super(null);
        }

        @Override
        public List<ChannelUserBinding> listWithRuntime(Long channelId) {
            return List.of();
        }

        @Override
        public void clearLoginContext(Long channelId, Long ownerUserId) {
            clearedChannelId = channelId;
            clearedOwnerUserId = ownerUserId;
        }

        @Override
        public void saveLoginContext(Long channelId, Long ownerUserId, LoginContext loginContext) {
            savedLoginContexts.add(loginContext);
        }
    }

    private static final class ExpiringILinkClient extends ILinkClient {

        private boolean closed;

        private ExpiringILinkClient() {
            super(ILinkConfig.builder().heartbeatEnabled(false).build(), new ListenerRegistry());
        }

        @Override
        public boolean isLoggedIn() {
            return !closed;
        }

        @Override
        public List<WeixinMessage> getUpdates() {
            throw new SessionExpiredException("session expired");
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    private static final class LoginILinkClient extends ILinkClient {

        private CompletableFuture<LoginContext> lastLoginFuture = new CompletableFuture<>();
        private int loginCount;

        private LoginILinkClient() {
            super(ILinkConfig.builder().heartbeatEnabled(false).build(), new ListenerRegistry());
        }

        @Override
        public String executeLogin() {
            loginCount++;
            lastLoginFuture = new CompletableFuture<>();
            return "https://login.example/qrcode-" + loginCount;
        }

        @Override
        public CompletableFuture<LoginContext> getLoginFuture() {
            return lastLoginFuture;
        }

        @Override
        public String getQrcode() {
            return "qrcode-" + loginCount;
        }
    }

    private static final class IdleILinkClient extends ILinkClient {

        private IdleILinkClient() {
            super(ILinkConfig.builder().heartbeatEnabled(false).build(), new ListenerRegistry());
        }

        @Override
        public boolean isLoggedIn() {
            return true;
        }

        @Override
        public List<WeixinMessage> getUpdates() {
            return List.of();
        }
    }
}
