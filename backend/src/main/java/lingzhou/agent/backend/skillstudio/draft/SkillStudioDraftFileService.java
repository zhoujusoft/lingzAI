package lingzhou.agent.backend.skillstudio.draft;

import java.util.List;
import java.util.Optional;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileChange;

public interface SkillStudioDraftFileService {

    Optional<String> readSkillMd(String skillName);

    Optional<String> readReference(String skillName, String relativePath);

    Optional<String> readFile(String skillName, String relativePath);

    List<String> listReferenceFiles(String skillName);

    List<String> listAllFiles(String skillName);

    List<String> listAllEntries(String skillName);

    void writeChanges(String skillName, List<SkillStudioFileChange> changes);

    void initializeDraftStructure(String skillName);

    boolean exists(String skillName);
}
