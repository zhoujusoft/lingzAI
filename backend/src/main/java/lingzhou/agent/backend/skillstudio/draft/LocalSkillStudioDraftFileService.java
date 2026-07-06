package lingzhou.agent.backend.skillstudio.draft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileChange;
import org.springframework.stereotype.Service;

@Service
public class LocalSkillStudioDraftFileService implements SkillStudioDraftFileService {

    private final SkillStudioDraftPathResolver pathResolver;

    public LocalSkillStudioDraftFileService(SkillStudioDraftPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @Override
    public Optional<String> readSkillMd(String skillName) {
        Path target = pathResolver.resolveSkillFile(skillName);
        return readIfExists(target);
    }

    @Override
    public Optional<String> readReference(String skillName, String relativePath) {
        Path target = pathResolver.resolveReferenceFile(skillName, relativePath);
        return readIfExists(target);
    }

    @Override
    public Optional<String> readFile(String skillName, String relativePath) {
        if (!org.springframework.util.StringUtils.hasText(relativePath)) {
            return Optional.empty();
        }
        String normalized = relativePath.trim().replace("\\", "/");
        Path target =
                pathResolver.resolveSkillDraftDir(skillName).resolve(normalized).normalize();
        if (!pathResolver.isAllowedDraftPath(skillName, target)) {
            throw new IllegalArgumentException("不允许读取 draft 目录外文件: " + relativePath);
        }
        return readIfExists(target);
    }

    @Override
    public List<String> listReferenceFiles(String skillName) {
        Path referencesDir = pathResolver.resolveSkillDraftDir(skillName).resolve("references");
        if (!Files.isDirectory(referencesDir)) {
            return List.of();
        }
        try (var stream = Files.walk(referencesDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(referencesDir::relativize)
                    .map(path -> path.toString().replace("\\", "/"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 draft references 失败: " + skillName, ex);
        }
    }

    @Override
    public List<String> listAllFiles(String skillName) {
        Path draftDir = pathResolver.resolveSkillDraftDir(skillName);
        if (!Files.isDirectory(draftDir)) {
            return List.of();
        }
        try (var stream = Files.walk(draftDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(draftDir::relativize)
                    .map(path -> path.toString().replace("\\", "/"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 draft 文件列表失败: " + skillName, ex);
        }
    }

    @Override
    public List<String> listAllEntries(String skillName) {
        Path draftDir = pathResolver.resolveSkillDraftDir(skillName);
        if (!Files.isDirectory(draftDir)) {
            return List.of();
        }
        try (var stream = Files.walk(draftDir)) {
            return stream.filter(path -> !path.equals(draftDir))
                    .map(draftDir::relativize)
                    .map(path -> path.toString().replace("\\", "/"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 draft 目录树失败: " + skillName, ex);
        }
    }

    @Override
    public void writeChanges(String skillName, List<SkillStudioFileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        List<SkillStudioFileChange> sortedChanges = new ArrayList<>(changes);
        sortedChanges.sort(Comparator.comparing(SkillStudioFileChange::path));
        for (SkillStudioFileChange change : sortedChanges) {
            Path target = resolveTarget(skillName, change);
            if (!pathResolver.isAllowedDraftPath(skillName, target)) {
                throw new IllegalArgumentException("不允许写入 draft 目录外文件: " + change.path());
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, change.content(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("写入 draft 文件失败: " + target, ex);
            }
        }
    }

    @Override
    public void initializeDraftStructure(String skillName) {
        Path draftDir = pathResolver.resolveSkillDraftDir(skillName);
        try {
            Files.createDirectories(draftDir);
            Files.createDirectories(draftDir.resolve("references"));
            Files.createDirectories(draftDir.resolve("scripts"));
            Path skillFile = pathResolver.resolveSkillFile(skillName);
            if (!Files.exists(skillFile)) {
                Files.writeString(
                        skillFile,
                        """
                        ---
                        name: %s
                        description: ""
                        ---

                        # %s

                        用于处理以下场景：
                        - 待补充
                        """
                                .formatted(skillName, skillName),
                        StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("初始化 draft 目录失败: " + skillName, ex);
        }
    }

    @Override
    public boolean exists(String skillName) {
        return Files.isDirectory(pathResolver.resolveSkillDraftDir(skillName));
    }

    private Optional<String> readIfExists(Path target) {
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("读取 draft 文件失败: " + target, ex);
        }
    }

    private Path resolveTarget(String skillName, SkillStudioFileChange change) {
        return switch (change.fileType()) {
            case SKILL -> pathResolver.resolveSkillFile(skillName);
            case REFERENCE -> resolveReferenceTarget(skillName, change.path());
            case SCRIPT -> resolveScriptTarget(skillName, change.path());
            case REQUIREMENTS -> pathResolver
                    .resolveSkillDraftDir(skillName)
                    .resolve("requirements.txt")
                    .normalize();
        };
    }

    private Path resolveReferenceTarget(String skillName, String path) {
        String normalized = path == null ? "" : path.trim().replace("\\", "/");
        String relative = normalized.startsWith(SkillStudioWorkspacePaths.DRAFT_ROOT + "/")
                ? normalized.substring(normalized.indexOf("/references/") + "/references/".length())
                : normalized.replaceFirst("^references/", "");
        return pathResolver.resolveReferenceFile(skillName, relative);
    }

    private Path resolveScriptTarget(String skillName, String path) {
        String normalized = path == null ? "" : path.trim().replace("\\", "/");
        String relative;
        int scriptIndex = normalized.indexOf("/scripts/");
        if (normalized.startsWith(SkillStudioWorkspacePaths.DRAFT_ROOT + "/") && scriptIndex >= 0) {
            relative = normalized.substring(scriptIndex + "/scripts/".length());
        } else {
            relative = normalized.replaceFirst("^scripts/", "");
        }
        return pathResolver
                .resolveSkillDraftDir(skillName)
                .resolve("scripts")
                .resolve(relative)
                .normalize();
    }
}
