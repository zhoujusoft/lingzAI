package lingzhou.agent.spring.ai.wechat.ilink.core.listener;

import java.util.List;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.WeixinMessage;

public interface OnMessageListener {
    void onMessages(List<WeixinMessage> messages);
}
