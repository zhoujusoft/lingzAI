package lingzhou.agent.spring.ai.wechat.ilink.core.http;

import java.util.HashMap;
import java.util.Map;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.utils.RandomUtils;

public final class RequestHeaderFactory {
    private RequestHeaderFactory() {}

    public static Map<String, String> businessHeaders(ILinkConfig config, LoginContext loginContext, byte[] utf8Body) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("AuthorizationType", "ilink_bot_token");
        headers.put("Authorization", "Bearer " + loginContext.getBotToken());
        headers.put("X-WECHAT-UIN", RandomUtils.randomWechatUin());
        headers.put("Content-Length", String.valueOf(utf8Body == null ? 0 : utf8Body.length));
        if (config.getRouteTag() != null && !config.getRouteTag().trim().isEmpty())
            headers.put("SKRouteTag", config.getRouteTag());
        return headers;
    }
}
