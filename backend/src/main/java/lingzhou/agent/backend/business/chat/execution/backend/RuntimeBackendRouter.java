package lingzhou.agent.backend.business.chat.execution.backend;

import java.util.List;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;
import org.springframework.stereotype.Service;

@Service
public class RuntimeBackendRouter {

    private final List<RuntimeBackend> backends;

    public RuntimeBackendRouter(List<RuntimeBackend> backends) {
        this.backends = backends == null ? List.of() : List.copyOf(backends);
    }

    public RuntimeExecutionResult execute(RuntimeExecutionRequest request) {
        if (request == null) {
            return RuntimeExecutionResult.failure(null, "RUNTIME_REQUEST_EMPTY", "Runtime request is empty");
        }
        RuntimeProviderType provider = request.provider();
        for (RuntimeBackend backend : backends) {
            if (backend != null && backend.provider() == provider) {
                return backend.execute(request);
            }
        }
        return RuntimeExecutionResult.failure(
                request.action(), "RUNTIME_BACKEND_NOT_FOUND", "未找到 runtime provider " + provider + " 对应的 backend");
    }
}
