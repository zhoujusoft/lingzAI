package lingzhou.agent.backend.business.chat.execution.python;

import java.io.File;
import java.nio.file.Path;

public final class PythonVenvPathSupport {

    private PythonVenvPathSupport() {}

    public static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    public static Path resolvePythonExecutable(Path venvPath) {
        if (venvPath == null) {
            throw new IllegalArgumentException("venvPath 不能为空");
        }
        return isWindows()
                ? venvPath.resolve("Scripts").resolve("python.exe")
                : venvPath.resolve("bin").resolve("python");
    }

    public static Path resolveBinDirectory(Path venvPath) {
        if (venvPath == null) {
            throw new IllegalArgumentException("venvPath 不能为空");
        }
        return isWindows() ? venvPath.resolve("Scripts") : venvPath.resolve("bin");
    }
}
