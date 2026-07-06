package lingzhou.agent.backend.skillstudio.draft;

import java.nio.file.Path;

public interface SkillStudioDraftPathResolver {

    Path resolveDraftRoot();

    Path resolveSkillDraftDir(String skillName);

    Path resolveSkillFile(String skillName);

    Path resolveReferenceFile(String skillName, String relativePath);

    boolean isAllowedDraftPath(String skillName, Path targetPath);
}
