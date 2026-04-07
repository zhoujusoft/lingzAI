package lingzhou.agent.backend.business.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ChatFileController {

    private final ChatFileService chatFileService;

    public ChatFileController(ChatFileService chatFileService) {
        this.chatFileService = chatFileService;
    }

    @PostMapping("/files/upload")
    public ResponseEntity<ChatFileService.UploadResponse> upload(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return chatFileService.upload(file, resolveUserId(request));
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
