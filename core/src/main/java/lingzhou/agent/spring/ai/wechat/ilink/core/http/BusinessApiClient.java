package lingzhou.agent.spring.ai.wechat.ilink.core.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.ProtocolException;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.SessionExpiredException;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.ApiResponse;
import lingzhou.agent.spring.ai.wechat.ilink.core.serializer.Serializer;

public class BusinessApiClient {
    private final ILinkConfig config;
    private final Serializer serializer;
    private final HttpClientFacade httpClientFacade;

    public BusinessApiClient(ILinkConfig config, Serializer serializer, HttpClientFacade httpClientFacade) {
        this.config = config;
        this.serializer = serializer;
        this.httpClientFacade = httpClientFacade;
    }

    public <T> T post(LoginContext loginContext, String path, Object requestBody, Class<T> responseType)
            throws IOException {
        String body = serializer.serialize(requestBody);
        Map<String, String> headers =
                RequestHeaderFactory.businessHeaders(config, loginContext, body.getBytes(StandardCharsets.UTF_8));
        String json = httpClientFacade.post(normalize(loginContext.getBaseUrl()) + path, headers, body);
        T response = serializer.deserialize(json, responseType);
        if (response instanceof ApiResponse) {
            ApiResponse api = (ApiResponse) response;
            Integer err = api.getErrcode();
            if (api.getRet() == -14 || (err != null && err.intValue() == -14))
                throw new SessionExpiredException("session expired");
            if (api.getRet() != 0 || (err != null && err.intValue() != 0))
                throw new ProtocolException("ret=" + api.getRet() + ", errcode=" + err + ", errmsg=" + api.getErrmsg());
        }
        return response;
    }

    private String normalize(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
