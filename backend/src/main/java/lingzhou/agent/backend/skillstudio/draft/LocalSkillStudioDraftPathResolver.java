package lingzhou.agent.backend.skillstudio.draft;

import java.nio.file.Path;
import java.nio.file.Paths;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalSkillStudioDraftPathResolver implements SkillStudioDraftPathResolver {

    @Override
    public Path resolveDraftRoot() {
        return Paths.get(SkillStudioWorkspacePaths.DRAFT_ROOT).toAbsolutePath().normalize();
    }

    @Override
    public Path resolveSkillDraftDir(String skillName) {
        return resolveDraftRoot().resolve(normalizeSkillName(skillName)).normalize();
    }

    @Override
    public Path resolveSkillFile(String skillName) {
        return resolveSkillDraftDir(skillName).resolve("SKILL.md").normalize();
    }

    @Override
    public Path resolveReferenceFile(String skillName, String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        return resolveSkillDraftDir(skillName)
                .resolve("references")
                .resolve(normalized)
                .normalize();
    }

    @Override
    public boolean isAllowedDraftPath(String skillName, Path targetPath) {
        if (targetPath == null) {
            return false;
        }
        Path draftDir = resolveSkillDraftDir(skillName);
        Path normalized = targetPath.toAbsolutePath().normalize();
        return normalized.startsWith(draftDir);
    }

    private String normalizeSkillName(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            throw new IllegalArgumentException("skillName 不能为空");
        }
        return skillName.trim();
    }

    private String normalizeRelativePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("relativePath 不能为空");
        }
        String normalized = relativePath.trim().replace("\\", "/");
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("reference 路径非法: " + relativePath);
        }
        return normalized;
    }
}
