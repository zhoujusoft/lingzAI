package lingzhou.agent.backend.business.chat.execution.nativefs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class NativeFileExecutorTest {

    private final NativeFileExecutor executor = new NativeFileExecutor(null, null);

    @Test
    void shouldNotBlockBenignStringContainingEmptyDot() throws Exception {
        String script =
                """
                import sys

                print("Output ZIP is empty.", file=sys.stderr)
                """;

        String risk = invokeValidatePythonScriptWrite("/workspace/task.py", script);

        assertThat(risk).isBlank();
    }

    @Test
    void shouldStillBlockRealPtyCall() throws Exception {
        String script = """
                import pty

                pty.spawn("/bin/bash")
                """;

        String risk = invokeValidatePythonScriptWrite("/workspace/task.py", script);

        assertThat(risk).contains("import pty");
    }

    @Test
    void shouldAllowShutilRmtreeForOwnedTempDirectoryCleanup() throws Exception {
        String script =
                """
                import shutil
                import tempfile

                temp_dir = tempfile.mkdtemp()
                shutil.rmtree(temp_dir, ignore_errors=True)
                """;

        String risk = invokeValidatePythonScriptWrite("/workspace/task.py", script);

        assertThat(risk).isBlank();
    }

    @Test
    void shouldAllowOsUnlinkForOwnedTempFileCleanup() throws Exception {
        String script =
                """
                import os
                import tempfile

                fd, temp_path = tempfile.mkstemp()
                os.close(fd)
                os.unlink(temp_path)
                """;

        String risk = invokeValidatePythonScriptWrite("/workspace/task.py", script);

        assertThat(risk).isBlank();
    }

    private String invokeValidatePythonScriptWrite(String logicalPath, String content) throws Exception {
        Method method =
                NativeFileExecutor.class.getDeclaredMethod("validatePythonScriptWrite", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(executor, logicalPath, content);
    }
}
