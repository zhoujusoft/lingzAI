package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequestResolver;
import org.springframework.stereotype.Component;

@Component
public class RuntimeV2PreparedRequestResolver {

    private final ChatRuntimePreparedRequestResolver preparedRequestResolver;

    public RuntimeV2PreparedRequestResolver(ChatRuntimePreparedRequestResolver preparedRequestResolver) {
        this.preparedRequestResolver = preparedRequestResolver;
    }

    public ChatRuntimePreparedRequest resolve(ChatRuntimePreparedRequest prepared, Long userId) {
        return preparedRequestResolver.resolve(prepared, userId);
    }
}
