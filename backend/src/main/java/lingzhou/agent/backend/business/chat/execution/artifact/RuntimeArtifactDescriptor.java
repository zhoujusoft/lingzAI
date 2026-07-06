package lingzhou.agent.backend.business.chat.execution.artifact;

public record RuntimeArtifactDescriptor(
        String id,
        String bucket,
        String objectName,
        String fileName,
        String path,
        String downloadUrl,
        String previewUrl,
        String contentType,
        boolean previewable) {}
