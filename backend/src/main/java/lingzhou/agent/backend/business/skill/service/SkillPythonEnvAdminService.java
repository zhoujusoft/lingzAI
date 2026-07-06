package lingzhou.agent.backend.business.skill.service;

import java.nio.file.Path;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnv;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvManager;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvResolver;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillPythonEnvAdminService {

    private final SkillCatalogMapper skillCatalogMapper;
    private final PythonRuntimeEnvResolver pythonRuntimeEnvResolver;
    private final PythonRuntimeEnvManager pythonRuntimeEnvManager;
    private final RuntimeExecutionProperties runtimeExecutionProperties;

    public SkillPythonEnvAdminService(
            SkillCatalogMapper skillCatalogMapper,
            PythonRuntimeEnvResolver pythonRuntimeEnvResolver,
            PythonRuntimeEnvManager pythonRuntimeEnvManager,
            RuntimeExecutionProperties runtimeExecutionProperties) {
        this.skillCatalogMapper = skillCatalogMapper;
        this.pythonRuntimeEnvResolver = pythonRuntimeEnvResolver;
        this.pythonRuntimeEnvManager = pythonRuntimeEnvManager;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
    }

    public SkillPythonEnvView getEnvStatus(Long skillId) throws TaskException {
        SkillCatalog catalog = requireCatalog(skillId);
        PythonRuntimeEnv env = resolveEnv(catalog);
        PythonRuntimeEnvManager.EnvStatus status = pythonRuntimeEnvManager.readStatus(env);
        return toView(catalog, status);
    }

    public SkillPythonEnvView rebuild(Long skillId) throws TaskException {
        SkillCatalog catalog = requireCatalog(skillId);
        PythonRuntimeEnv env = resolveEnv(catalog);
        pythonRuntimeEnvManager.rebuild(env);
        return getEnvStatus(skillId);
    }

    public SkillPythonEnvView prewarmForPublish(Long skillId) throws TaskException {
        SkillCatalog catalog = requireCatalog(skillId);
        PythonRuntimeEnv env = resolveEnv(catalog);
        if (env.dedicated()) {
            pythonRuntimeEnvManager.rebuild(env);
        } else {
            pythonRuntimeEnvManager.ensureReady(env);
        }
        return getEnvStatus(skillId);
    }

    private PythonRuntimeEnv resolveEnv(SkillCatalog catalog) throws TaskException {
        if (catalog == null || !StringUtils.hasText(catalog.getRuntimeSkillName())) {
            throw new TaskException("技能运行时名称不存在", TaskException.Code.UNKNOWN);
        }
        Path skillRoot = Path.of(runtimeExecutionProperties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize()
                .resolve("public")
                .resolve("skills")
                .resolve(catalog.getRuntimeSkillName())
                .normalize();
        return pythonRuntimeEnvResolver.resolve(catalog.getRuntimeSkillName(), skillRoot);
    }

    private SkillCatalog requireCatalog(Long skillId) throws TaskException {
        if (skillId == null || skillId <= 0) {
            throw new TaskException("技能ID无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        return catalog;
    }

    private SkillPythonEnvView toView(SkillCatalog catalog, PythonRuntimeEnvManager.EnvStatus status) {
        return new SkillPythonEnvView(
                catalog.getId(),
                catalog.getRuntimeSkillName(),
                catalog.getDisplayName(),
                status.dedicated(),
                status.envRoot(),
                status.venvPath(),
                status.pythonPath(),
                status.requirementsPath(),
                status.requirementsSha256(),
                status.vendorPath(),
                status.vendorSha256(),
                status.manifestPath(),
                status.installLogPath(),
                status.pythonReady(),
                status.venvExists(),
                status.reusable(),
                status.manifest());
    }

    public record SkillPythonEnvView(
            Long skillId,
            String runtimeSkillName,
            String displayName,
            boolean dedicated,
            String envRoot,
            String venvPath,
            String pythonPath,
            String requirementsPath,
            String requirementsSha256,
            String vendorPath,
            String vendorSha256,
            String manifestPath,
            String installLogPath,
            boolean pythonReady,
            boolean venvExists,
            boolean reusable,
            lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvManifest manifest) {}
}
