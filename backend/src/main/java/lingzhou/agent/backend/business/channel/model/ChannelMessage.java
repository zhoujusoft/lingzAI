package lingzhou.agent.backend.business.channel.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMessage {

    private String messageId;

    private String channelType;

    private String senderId;

    private String senderName;

    private String externalSessionKey;

    private String replyTarget;

    private Long ownerUserId;

    private String routeType;

    private Long routeTargetId;

    private String content;

    private String contentType;

    @Builder.Default
    private String inputMode = "text";

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Builder.Default
    private List<String> fileIds = List.of();

    private LocalDateTime timestamp;

    private Object rawPayload;
}
