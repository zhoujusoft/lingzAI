package lingzhou.agent.backend.skillstudio.context;

import java.util.Optional;

public interface SkillStudioMemoryService {

    Optional<String> readMemory(String skillName);

    void writeMemory(String skillName, String content);

    boolean exists(String skillName);
}
