package lingzhou.agent.backend.business.chat.execution.tool;

import static org.assertj.core.api.Assertions.assertThat;

import lingzhou.agent.backend.business.chat.execution.RuntimeExecutionFacade;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeSystemToolProviderTest {

    @AfterEach
    void clearContext() {
        RuntimeToolInvocationContextHolder.clear();
    }

    @Test
    void shouldAllowDirectRunPythonBeforeRuntimeExecution() {
        TrackingRuntimeExecutionFacade facade = new TrackingRuntimeExecutionFacade();
        RuntimeSystemToolProvider provider = new RuntimeSystemToolProvider(facade, new ChatFileService(null, null));
        RuntimeToolInvocationContextHolder.set(buildContext("{}"));

        String result =
                provider.runPython("/workspace/task.py", java.util.List.of("/uploads/a.xlsx"), "/workspace", 120);

        assertThat(result).contains("\"success\":true");
        assertThat(facade.executeCalled).isTrue();
        assertThat(facade.lastAction).isEqualTo(RuntimeExecutionAction.RUN_PYTHON);
    }

    @Test
    void shouldAllowRunPythonWhenCodeExecutionAlreadyActivated() {
        TrackingRuntimeExecutionFacade facade = new TrackingRuntimeExecutionFacade();
        RuntimeSystemToolProvider provider = new RuntimeSystemToolProvider(facade, new ChatFileService(null, null));
        RuntimeToolInvocationContextHolder.set(buildContext("{\"codeExecutionActive\":true}"));

        String result =
                provider.runPython("/workspace/task.py", java.util.List.of("/uploads/a.xlsx"), "/workspace", 120);

        assertThat(result).contains("\"success\":true");
        assertThat(facade.executeCalled).isTrue();
        assertThat(facade.lastAction).isEqualTo(RuntimeExecutionAction.RUN_PYTHON);
    }

    @Test
    void shouldCanonicalizeSingleUploadedFilePathForRunPythonArgs() {
        TrackingRuntimeExecutionFacade facade = new TrackingRuntimeExecutionFacade();
        RuntimeSystemToolProvider provider = new RuntimeSystemToolProvider(facade, new ChatFileService(null, null));
        RuntimeToolInvocationContextHolder.set(buildContextWithFileList(
                "[{\"id\":\"f1\",\"name\":\"06.21武汉活动名单.xlsx\",\"path\":\"chat-upload://chat-files/1/f1.xlsx\",\"size\":1,\"objectName\":\"chat-files/1/f1.xlsx\"}]"));

        provider.runPython(
                "/workspace/task.py",
                java.util.List.of("/uploads/企业名单.xlsx", "/outputs/hubei_companies.xlsx"),
                "/workspace",
                600);

        assertThat(facade.lastPayload.get("args"))
                .isEqualTo(java.util.List.of("/uploads/06.21武汉活动名单.xlsx", "/outputs/hubei_companies.xlsx"));
    }

    private RuntimeToolContext buildContext(String paramsJson) {
        return buildContextWithFileList("[]", paramsJson);
    }

    private RuntimeToolContext buildContextWithFileList(String fileListJson) {
        return buildContextWithFileList(fileListJson, "{}");
    }

    private RuntimeToolContext buildContextWithFileList(String fileListJson, String paramsJson) {
        return new RuntimeToolContext(
                "session-1",
                1L,
                1L,
                LingzRuntimeScopeType.GENERAL,
                null,
                "",
                () -> "",
                RuntimeExecutionMode.NATIVE,
                fileListJson,
                paramsJson,
                false,
                "",
                1L,
                2L);
    }

    private static final class TrackingRuntimeExecutionFacade extends RuntimeExecutionFacade {

        private boolean executeCalled;
        private RuntimeExecutionAction lastAction;
        private java.util.Map<String, Object> lastPayload;

        private TrackingRuntimeExecutionFacade() {
            super(null, null, null, null, null, null);
        }

        @Override
        public RuntimeExecutionResult execute(
                RuntimeToolContext toolContext, RuntimeExecutionAction action, java.util.Map<String, Object> payload) {
            this.executeCalled = true;
            this.lastAction = action;
            this.lastPayload = payload;
            return RuntimeExecutionResult.success(
                    action, "ok", java.util.Map.of("scriptPath", payload.get("scriptPath")));
        }
    }
}
