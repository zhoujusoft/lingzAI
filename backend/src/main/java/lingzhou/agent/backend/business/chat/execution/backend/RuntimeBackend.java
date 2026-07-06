package lingzhou.agent.backend.business.chat.execution.backend;

import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;

public interface RuntimeBackend {

    RuntimeProviderType provider();

    RuntimeExecutionResult execute(RuntimeExecutionRequest request);
}
