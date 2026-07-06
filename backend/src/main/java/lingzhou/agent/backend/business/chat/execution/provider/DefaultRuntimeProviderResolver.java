package lingzhou.agent.backend.business.chat.execution.provider;

import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Service;

@Service
public class DefaultRuntimeProviderResolver implements RuntimeProviderResolver {

    private final RuntimeExecutionProperties properties;

    public DefaultRuntimeProviderResolver(RuntimeExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeProviderType resolve(RuntimeToolContext toolContext) {
        RuntimeProviderType requested =
                RuntimeProviderType.fromMode(toolContext == null ? null : toolContext.runtimeMode());
        if (requested != null) {
            return requested;
        }
        RuntimeProviderType configured = properties.getProvider();
        if (configured != null) {
            return configured;
        }
        RuntimeExecutionMode legacyMode = properties.getMode();
        RuntimeProviderType legacyProvider = RuntimeProviderType.fromMode(legacyMode);
        return legacyProvider == null ? RuntimeProviderType.NATIVE : legacyProvider;
    }
}
