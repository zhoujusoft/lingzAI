package lingzhou.agent.backend.business.channel.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lingzhou.agent.backend.business.channel.adapter.ChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.service.ChannelConfigService;
import org.junit.jupiter.api.Test;

class ChannelManagerTests {

    @Test
    void getOrStartAdapterStartsOnlyOneAdapterUnderConcurrentCalls() throws Exception {
        ChannelConfigService channelConfigService = mock(ChannelConfigService.class);
        when(channelConfigService.getRequired(1L)).thenReturn(channelConfig());
        ChannelManager channelManager = new ChannelManager(channelConfigService, null, null, null, null);
        int taskCount = 12;
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        Set<ChannelAdapter> adapters = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < taskCount; i++) {
            executorService.submit(() -> {
                ready.countDown();
                start.await();
                adapters.add(channelManager.getOrStartAdapter(1L));
                return null;
            });
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(adapters).hasSize(1);
        assertThat(channelManager.getActiveAdapters()).hasSize(1);
    }

    private ChannelConfig channelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setId(1L);
        config.setName("Webhook");
        config.setChannelType("webhook");
        config.setRouteType("GENERAL_CHAT");
        config.setEnabled(Boolean.TRUE);
        return config;
    }
}
