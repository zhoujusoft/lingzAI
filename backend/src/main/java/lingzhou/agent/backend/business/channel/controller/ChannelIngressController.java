package lingzhou.agent.backend.business.channel.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.adapter.ChannelAdapter;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.runtime.ChannelManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/channel/ingress")
public class ChannelIngressController {

    private final ChannelManager channelManager;

    public ChannelIngressController(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @PostMapping("/{channelId}")
    public Map<String, Object> ingest(
            @PathVariable("channelId") Long channelId, @RequestBody ChannelIngressRequest request) {
        ChannelAdapter adapter = channelManager
                .getAdapter(channelId)
                .orElseThrow(() -> new IllegalArgumentException("渠道未启动: " + channelId));
        ChannelMessage message = ChannelMessage.builder()
                .messageId(request.messageId())
                .channelType(adapter.getChannelType())
                .senderId(request.senderId())
                .senderName(request.senderName())
                .externalSessionKey(request.externalSessionKey())
                .replyTarget(request.replyTarget())
                .content(request.content())
                .contentType(request.contentType())
                .inputMode(request.inputMode())
                .metadata(request.metadata() == null ? Map.of() : request.metadata())
                .fileIds(request.fileIds() == null ? List.of() : request.fileIds())
                .timestamp(LocalDateTime.now())
                .rawPayload(request.rawPayload())
                .build();
        adapter.onMessage(message);
        return Map.of("success", Boolean.TRUE, "channelId", channelId);
    }

    public record ChannelIngressRequest(
            String messageId,
            String senderId,
            String senderName,
            String externalSessionKey,
            String replyTarget,
            String content,
            String contentType,
            String inputMode,
            List<String> fileIds,
            Map<String, Object> metadata,
            Object rawPayload) {}
}
