package lingzhou.agent.backend.business.chat.execution.backend;

import lingzhou.agent.backend.business.chat.execution.artifact.RuntimeArtifactService;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.nativefs.NativeCommandExecutor;
import lingzhou.agent.backend.business.chat.execution.nativefs.NativeFileExecutor;
import lingzhou.agent.backend.business.chat.execution.nativefs.NativePythonExecutor;
import lingzhou.agent.backend.business.chat.execution.nativefs.NativeSearchExecutor;
import lingzhou.agent.backend.business.chat.execution.nativefs.PathJail;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;
import org.springframework.stereotype.Service;

@Service
public class NativeRuntimeBackend implements RuntimeBackend {

    private final NativeFileExecutor nativeFileExecutor;
    private final NativeSearchExecutor nativeSearchExecutor;
    private final RuntimeArtifactService runtimeArtifactService;
    private final NativeCommandExecutor nativeCommandExecutor;
    private final NativePythonExecutor nativePythonExecutor;

    public NativeRuntimeBackend(
            NativeFileExecutor nativeFileExecutor,
            NativeSearchExecutor nativeSearchExecutor,
            RuntimeArtifactService runtimeArtifactService,
            NativeCommandExecutor nativeCommandExecutor,
            NativePythonExecutor nativePythonExecutor) {
        this.nativeFileExecutor = nativeFileExecutor;
        this.nativeSearchExecutor = nativeSearchExecutor;
        this.runtimeArtifactService = runtimeArtifactService;
        this.nativeCommandExecutor = nativeCommandExecutor;
        this.nativePythonExecutor = nativePythonExecutor;
    }

    @Override
    public RuntimeProviderType provider() {
        return RuntimeProviderType.NATIVE;
    }

    @Override
    public RuntimeExecutionResult execute(RuntimeExecutionRequest request) {
        if (request == null || request.workspace() == null) {
            return RuntimeExecutionResult.failure(
                    request == null ? null : request.action(), "RUNTIME_WORKSPACE_EMPTY", "Runtime workspace is empty");
        }
        PathJail jail = new PathJail(request.workspace().sandboxRoots());
        return switch (request.action()) {
            case FILE_READ, FILE_WRITE, LIST_DIR, STAT -> nativeFileExecutor.execute(jail, request);
            case SEARCH -> nativeSearchExecutor.search(jail, request);
            case WRITE_ARTIFACT -> runtimeArtifactService.writeArtifact(jail, request);
            case BASH -> nativeCommandExecutor.execute(jail, request);
            case RUN_PYTHON -> nativePythonExecutor.execute(jail, request);
        };
    }
}
