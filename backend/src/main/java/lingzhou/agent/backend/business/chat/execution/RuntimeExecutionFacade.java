package lingzhou.agent.backend.business.chat.execution;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.backend.RuntimeBackendRouter;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderResolver;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeWorkspaceResolver;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.chat.service.RuntimeFileAssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExecutionFacade {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeExecutionFacade.class);

    private final RuntimeWorkspaceResolver workspaceResolver;
    private final RuntimeBackendRouter backendRouter;
    private final RuntimeProviderResolver runtimeProviderResolver;
    private final ChatFileService chatFileService;
    private final RuntimeFileAssetService runtimeFileAssetService;
    private final RuntimeExecutionProperties properties;

    public RuntimeExecutionFacade(
            RuntimeWorkspaceResolver workspaceResolver,
            RuntimeBackendRouter backendRouter,
            RuntimeProviderResolver runtimeProviderResolver,
            ChatFileService chatFileService,
            RuntimeFileAssetService runtimeFileAssetService,
            RuntimeExecutionProperties properties) {
        this.workspaceResolver = workspaceResolver;
        this.backendRouter = backendRouter;
        this.runtimeProviderResolver = runtimeProviderResolver;
        this.chatFileService = chatFileService;
        this.runtimeFileAssetService = runtimeFileAssetService;
        this.properties = properties;
    }

    public RuntimeExecutionResult execute(
            RuntimeToolContext toolContext, RuntimeExecutionAction action, Map<String, Object> payload) {
        if (toolContext == null) {
            return RuntimeExecutionResult.failure(action, "RUNTIME_CONTEXT_EMPTY", "Runtime tool context is empty");
        }
        try {
            RuntimeWorkspace workspace = workspaceResolver.resolve(
                    toolContext.userId(),
                    toolContext.sessionId(),
                    toolContext.currentRuntimeSkillName(),
                    toolContext.scopeType(),
                    toolContext.scopeId());
            materializeUploads(toolContext.fileListJson(), Path.of(workspace.uploadsRoot()));
            RuntimeProviderType provider = runtimeProviderResolver.resolve(toolContext);
            RuntimeExecutionMode mode = provider == null
                    ? (toolContext.runtimeMode() == null ? properties.getMode() : toolContext.runtimeMode())
                    : provider.toMode();
            Map<String, RuntimeFileAssetService.FileSnapshot> tempBeforeSnapshot = shouldTrackTemp(action)
                    ? runtimeFileAssetService.snapshotDirectory(Path.of(workspace.tempRoot()))
                    : Map.of();
            RuntimeExecutionRequest request = new RuntimeExecutionRequest(
                    toolContext.sessionId(),
                    toolContext.userId(),
                    toolContext.runId(),
                    toolContext.currentRuntimeSkillName(),
                    toolContext.scopeType(),
                    toolContext.scopeId(),
                    provider,
                    mode,
                    action,
                    workspace,
                    payload == null ? Map.of() : new LinkedHashMap<>(payload),
                    toolContext.requestMessageId(),
                    toolContext.assistantMessageId());
            RuntimeExecutionResult result = backendRouter.execute(request);
            if (shouldTrackTemp(action)) {
                Map<String, RuntimeFileAssetService.FileSnapshot> tempAfterSnapshot =
                        runtimeFileAssetService.snapshotDirectory(Path.of(workspace.tempRoot()));
                try {
                    runtimeFileAssetService.syncTempFiles(request, tempBeforeSnapshot, tempAfterSnapshot);
                } catch (Exception ex) {
                    logger.error(
                            "TEMP 文件同步失败：sessionId={}, action={}, error={}",
                            request.sessionId(),
                            request.action(),
                            ex.getMessage(),
                            ex);
                    if (result != null && result.success()) {
                        return RuntimeExecutionResult.failure(
                                action, "TEMP_FILE_SYNC_FAILED", "TEMP 文件同步失败: " + ex.getMessage());
                    }
                }
            }
            if (action == RuntimeExecutionAction.FILE_WRITE && result != null && result.success()) {
                try {
                    runtimeFileAssetService.syncWorkspaceFileWrite(request, result);
                } catch (Exception ex) {
                    logger.error(
                            "WORKSPACE 文件同步失败：sessionId={}, action={}, error={}",
                            request.sessionId(),
                            request.action(),
                            ex.getMessage(),
                            ex);
                    return RuntimeExecutionResult.failure(
                            action, "WORKSPACE_FILE_SYNC_FAILED", "WORKSPACE 文件同步失败: " + ex.getMessage());
                }
            }
            return result;
        } catch (Exception ex) {
            return RuntimeExecutionResult.failure(action, "RUNTIME_EXECUTION_FAILED", ex.getMessage());
        }
    }

    public void prepareWorkspace(RuntimeToolContext toolContext) {
        if (toolContext == null) {
            return;
        }
        RuntimeWorkspace workspace = workspaceResolver.resolve(
                toolContext.userId(),
                toolContext.sessionId(),
                toolContext.currentRuntimeSkillName(),
                toolContext.scopeType(),
                toolContext.scopeId());
        materializeUploads(toolContext.fileListJson(), Path.of(workspace.uploadsRoot()));
    }

    private void materializeUploads(String fileListJson, Path uploadsDir) {
        try {
            chatFileService.materializePersistedFiles(fileListJson, uploadsDir);
        } catch (Exception ex) {
            throw new IllegalStateException("准备 runtime uploads 失败: " + ex.getMessage(), ex);
        }
    }

    private boolean shouldTrackTemp(RuntimeExecutionAction action) {
        return action == RuntimeExecutionAction.FILE_WRITE
                || action == RuntimeExecutionAction.WRITE_ARTIFACT
                || action == RuntimeExecutionAction.BASH
                || action == RuntimeExecutionAction.RUN_PYTHON;
    }
}
