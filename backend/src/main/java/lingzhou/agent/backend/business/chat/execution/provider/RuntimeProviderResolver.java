package lingzhou.agent.backend.business.chat.execution.provider;

import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;

public interface RuntimeProviderResolver {

    RuntimeProviderType resolve(RuntimeToolContext toolContext);
}
