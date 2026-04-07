package lingzhou.agent.backend.business.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class GeneralChatController {

    private final ChatConversationService chatConversationService;

    public GeneralChatController(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestBody(required = false) ChatConversationService.GeneralChatRequest request,
            HttpServletRequest httpRequest) {
        Long userId = chatConversationService.resolveUserId(httpRequest);
        return chatConversationService.streamGeneral(request, userId);
    }
}
