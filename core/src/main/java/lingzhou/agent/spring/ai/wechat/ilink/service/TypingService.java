package lingzhou.agent.spring.ai.wechat.ilink.service;

import java.io.IOException;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.ContextPoolManager;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.ConversationContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.http.BusinessApiClient;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.*;

public class TypingService {
    private final ILinkConfig config;
    private final BusinessApiClient apiClient;
    private final ContextPoolManager contextPoolManager = ContextPoolManager.getInstance();

    public TypingService(ILinkConfig config, BusinessApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
    }

    public String ensureTypingTicket(LoginContext loginContext, String userId) throws IOException {
        ConversationContext ctx = contextPoolManager.getOrCreate(loginContext.getBotId(), userId);
        if (ctx.getTypingTicket() != null) return ctx.getTypingTicket();
        GetConfigResponse resp = apiClient.post(
                loginContext,
                "/ilink/bot/getconfig",
                new GetConfigRequest(userId, ctx.getLatestContextToken(), new BaseInfo(config.getChannelVersion())),
                GetConfigResponse.class);
        ctx.setTypingTicket(resp.getTyping_ticket());
        return resp.getTyping_ticket();
    }

    public void startTyping(LoginContext loginContext, String userId) throws IOException {
        apiClient.post(
                loginContext,
                "/ilink/bot/sendtyping",
                new SendTypingRequest(
                        userId, ensureTypingTicket(loginContext, userId), 1, new BaseInfo(config.getChannelVersion())),
                ApiResponse.class);
    }

    public void stopTyping(LoginContext loginContext, String userId) throws IOException {
        ConversationContext ctx = contextPoolManager.getOrCreate(loginContext.getBotId(), userId);
        if (ctx.getTypingTicket() == null) return;
        apiClient.post(
                loginContext,
                "/ilink/bot/sendtyping",
                new SendTypingRequest(userId, ctx.getTypingTicket(), 2, new BaseInfo(config.getChannelVersion())),
                ApiResponse.class);
    }
}
