package lingzhou.agent.backend.business.chat.execution.workspace;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lingzhou.agent.backend.app.SkillProperties;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceBootstrapService.class);

    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final SkillProperties skillProperties;

    public WorkspaceBootstrapService(
            RuntimeExecutionProperties runtimeExecutionProperties, SkillProperties skillProperties) {
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.skillProperties = skillProperties;
    }

    @PostConstruct
    public void ensurePublicWorkspace() {
        Path workspaceBaseDir = Path.of(runtimeExecutionProperties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize();
        Path publicDir = workspaceBaseDir.resolve("public");
        Path usersDir = workspaceBaseDir.resolve("users");
        Path skillRoot = Path.of(skillProperties.getRootDir()).toAbsolutePath().normalize();
        Path skillStudioRoot =
                Path.of(SkillStudioWorkspacePaths.ROOT).toAbsolutePath().normalize();
        createDirectories(
                publicDir,
                usersDir,
                skillRoot,
                skillStudioRoot,
                skillStudioRoot.resolve("draft"),
                skillStudioRoot.resolve("skills"),
                skillStudioRoot.resolve("templates"),
                skillStudioRoot.resolve("protocols"),
                skillStudioRoot.resolve("architecture"),
                skillStudioRoot.resolve("memory"),
                skillStudioRoot.resolve("logs"),
                publicDir.resolve("runtime-envs"),
                publicDir.resolve("runtime-envs").resolve("python"),
                publicDir.resolve("runtime-envs").resolve("python").resolve("default"),
                publicDir
                        .resolve("runtime-envs")
                        .resolve("python")
                        .resolve("default")
                        .resolve("vendor"),
                publicDir.resolve("runtime-envs").resolve("python").resolve("general-code"),
                publicDir
                        .resolve("runtime-envs")
                        .resolve("python")
                        .resolve("general-code")
                        .resolve("vendor"),
                publicDir.resolve("runtime-envs").resolve("python").resolve("skills"),
                publicDir.resolve("runtime-envs").resolve("caches"),
                publicDir.resolve("runtime-envs").resolve("caches").resolve("pip"),
                publicDir.resolve("runtime-envs").resolve("caches").resolve("wheels"),
                publicDir.resolve("logs"));
        ensureFile(
                publicDir
                        .resolve("runtime-envs")
                        .resolve("python")
                        .resolve("default")
                        .resolve("requirements.txt"),
                """
                # 默认 runtime Python 依赖
                markitdown[all]==0.1.5
                pypdf==5.4.0
                pdf2image==1.17.0
                Pillow==11.2.1
                python-docx==1.1.2
                openpyxl==3.1.5
                python-pptx==1.0.2
                pandas==2.2.3
                lxml==6.0.2
                defusedxml==0.7.1
                markdown==3.7
                xlrd==2.0.1
                """);
        ensureFile(
                publicDir
                        .resolve("runtime-envs")
                        .resolve("python")
                        .resolve("general-code")
                        .resolve("requirements.txt"),
                """
                # 个人 Agent 通用 CODE 环境依赖
                pypdf==5.4.0
                pdfplumber==0.11.7
                pdf2image==1.17.0
                Pillow==11.2.1
                python-pptx==1.0.2
                pandas==2.2.3
                openpyxl==3.1.5
                python-docx==1.1.2
                matplotlib==3.9.2
                lxml==6.0.2
                defusedxml==0.7.1
                markdown==3.7
                xlrd==2.0.1
                """);
    }

    public void ensureUserProfile(Long userId) {
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        Path workspaceBaseDir = Path.of(runtimeExecutionProperties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize();
        Path profileDir = workspaceBaseDir
                .resolve("users")
                .resolve(String.valueOf(safeUserId))
                .resolve("profile");
        Path userRuntimePythonDir = workspaceBaseDir
                .resolve("users")
                .resolve(String.valueOf(safeUserId))
                .resolve("runtime-envs")
                .resolve("python")
                .resolve("general-code");
        createDirectories(profileDir, userRuntimePythonDir);
        ensureFile(profileDir.resolve("preferences.md"), "# 用户偏好\n");
        ensureFile(profileDir.resolve("assistant-style.md"), "# 助理风格\n");
        ensureFile(profileDir.resolve("memory.md"), "# 长期记忆\n");
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
                throw new IllegalStateException("初始化工作区目录失败: " + directory, ex);
            }
        }
        logger.debug("工作区目录初始化完成");
    }

    private void ensureFile(Path file, String defaultContent) {
        if (file == null || Files.exists(file)) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, defaultContent == null ? "" : defaultContent, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("初始化工作区文件失败: " + file, ex);
        }
    }
}
