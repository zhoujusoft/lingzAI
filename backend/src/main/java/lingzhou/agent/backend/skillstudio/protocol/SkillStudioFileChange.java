package lingzhou.agent.backend.skillstudio.protocol;

public record SkillStudioFileChange(
        String path, SkillStudioChangeType changeType, SkillStudioFileType fileType, String content) {}
