package lingzhou.agent.backend.business.chat.execution.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import lingzhou.agent.backend.business.chat.execution.model.SandboxRoot;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultRuntimeWorkspaceResolver implements RuntimeWorkspaceResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRuntimeWorkspaceResolver.class);

    private final RuntimeExecutionProperties properties;

    public DefaultRuntimeWorkspaceResolver(RuntimeExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeWorkspace resolve(
            Long userId, String sessionId, String runtimeSkillName, LingzRuntimeScopeType scopeType, Long scopeId) {
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        String safeSessionId = StringUtils.hasText(sessionId) ? sessionId.trim() : "default";
        String safeSkillName = StringUtils.hasText(runtimeSkillName) ? runtimeSkillName.trim() : "";

        Path baseDir =
                Path.of(properties.getWorkspaceBaseDir()).toAbsolutePath().normalize();
        Path sessionBase = baseDir.resolve("users")
                .resolve(String.valueOf(safeUserId))
                .resolve("sessions")
                .resolve(safeSessionId);

        Path workspace = sessionBase.resolve("workspace");
        Path uploads = sessionBase.resolve("uploads");
        Path outputs = sessionBase.resolve("outputs");
        Path temp = sessionBase.resolve("temp");
        Path logs = sessionBase.resolve("logs");
        Path meta = sessionBase.resolve("meta");
        Path profile =
                baseDir.resolve("users").resolve(String.valueOf(safeUserId)).resolve("profile");
        Path skill = baseDir.resolve("public").resolve("skills").resolve(safeSkillName);

        if (scopeType == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT) {
            String draftSkillName = StringUtils.hasText(safeSkillName) ? safeSkillName : "skillstudio-project";
            workspace = Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT)
                    .toAbsolutePath()
                    .normalize()
                    .resolve(draftSkillName);
            skill = Path.of(SkillStudioWorkspacePaths.SKILLS_ROOT)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("zhuoju-skill-creator");
        }
        if (scopeType == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT_PREVIEW) {
            String draftSkillName = StringUtils.hasText(safeSkillName) ? safeSkillName : "skillstudio-project";
            skill = Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT)
                    .toAbsolutePath()
                    .normalize()
                    .resolve(draftSkillName);
        }

        createDirectories(workspace, uploads, outputs, temp, logs, meta, profile);
        if (scopeType == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT) {
            createDirectories(workspace.resolve("references"), workspace.resolve("scripts"));
        }

        List<SandboxRoot> roots = List.of(
                new SandboxRoot("session-workspace", workspace.toString(), "/workspace", "/workspace", true, true),
                new SandboxRoot("session-uploads", uploads.toString(), "/uploads", "/uploads", true, false),
                new SandboxRoot("session-outputs", outputs.toString(), "/outputs", "/outputs", true, true),
                new SandboxRoot("session-temp", temp.toString(), "/temp", "/temp", true, true),
                new SandboxRoot("session-logs", logs.toString(), "/logs", "/logs", true, true),
                new SandboxRoot("user-profile", profile.toString(), "/profile", "/profile", true, false),
                new SandboxRoot("skill-definition", skill.toString(), "/skill", "/skill", true, false));

        logger.debug(
                "解析 runtime 工作区：userId={}, sessionId={}, scopeType={}, scopeId={}, skill={}",
                safeUserId,
                safeSessionId,
                scopeType,
                scopeId,
                safeSkillName);

        return new RuntimeWorkspace(
                safeSessionId,
                safeUserId,
                safeSkillName,
                "/workspace",
                workspace.toString(),
                uploads.toString(),
                outputs.toString(),
                temp.toString(),
                logs.toString(),
                skill.toString(),
                profile.toString(),
                roots);
    }

    private void createDirectories(Path... directories) {
        if (directories == null) {
            return;
        }
        for (Path directory : directories) {
            if (directory == null) {
                continue;
            }
            try {
                Files.createDirectories(directory);
            } catch (IOException ex) {
                throw new IllegalStateException("创建 runtime 工作区目录失败: " + directory, ex);
            }
        }
    }
}
