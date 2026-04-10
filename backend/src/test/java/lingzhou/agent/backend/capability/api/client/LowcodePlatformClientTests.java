package lingzhou.agent.backend.capability.api.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.system.model.PlatformAuthConfig;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class LowcodePlatformClientTests {

    private static final String PLATFORM_URL = "http://125.75.152.167:11682/api";
    private static final String APP_KEY = "gb7h02fk";
    private static final String APP_SECRET = "huuif5c31rjz";
    private static final String TN_CODE = "00000000";

    @Disabled("手工联调测试：替换低代码平台地址、AppKey、密钥后再执行")
    @Test
    void getAppListCallsRealLowcodePlatform() throws TaskException {
        LowcodePlatformClient client = new LowcodePlatformClient(new ObjectMapper());

        PlatformAuthConfig authConfig = new PlatformAuthConfig();
        authConfig.setTncode(TN_CODE);
        authConfig.setAppKey(APP_KEY);
        authConfig.setAppSecret(APP_SECRET);

        PlatformEndpointItem platform = new PlatformEndpointItem();
        platform.setKey("manual-lowcode");
        platform.setApiUrl(PLATFORM_URL);
        platform.setAuthConfig(authConfig);

        List<Map<String, Object>> apps = client.getAppList(platform, "");
        System.out.println(apps);
        assertThat(apps).isNotNull();
    }
}
