package lingzhou.agent.backend.business.chat.execution.python;

import java.nio.file.Path;

public record PythonRuntimeEnv(
        String envName,
        String skillName,
        boolean dedicated,
        Path envRoot,
        Path venvPath,
        Path pythonPath,
        Path requirementsPath,
        String requirementsSha256,
        Path vendorDir,
        String vendorSha256,
        Path manifestPath,
        Path installLogPath) {}
