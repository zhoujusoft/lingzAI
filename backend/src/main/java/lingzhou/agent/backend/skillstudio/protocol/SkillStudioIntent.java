package lingzhou.agent.backend.skillstudio.protocol;

import java.util.List;

public record SkillStudioIntent(
        String baseTemplate, String capabilityTemplate, double confidence, List<String> reasons) {}
