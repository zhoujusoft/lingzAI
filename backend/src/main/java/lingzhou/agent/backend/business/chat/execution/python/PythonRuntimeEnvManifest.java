package lingzhou.agent.backend.business.chat.execution.python;

public record PythonRuntimeEnvManifest(
        String skillName,
        String pythonVersion,
        String requirementsSha256,
        String requirementsSourcePath,
        String vendorPath,
        String vendorSha256,
        String venvPath,
        String installedAt,
        String installStatus,
        String installMode,
        String installPolicy) {}
