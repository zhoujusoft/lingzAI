package lingzhou.agent.backend.business.channel.model;

import java.util.List;

public record ChannelDispatchResult(
        String sessionId, String replyContent, List<ChannelDispatchResult.GeneratedFile> generatedFiles) {

    public ChannelDispatchResult(String sessionId, String replyContent) {
        this(sessionId, replyContent, List.of());
    }

    public ChannelDispatchResult {
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
    }

    public record GeneratedFile(
            String id,
            String fileName,
            String downloadUrl,
            String previewUrl,
            String contentType,
            String objectName,
            String assetCode) {}
}
