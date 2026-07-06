package lingzhou.agent.spring.ai.wechat.ilink.core.listener;

import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;

public interface OnLoginListener {
    void onLoginSuccess(LoginContext context);

    void onLoginFailure(Throwable throwable);
}
