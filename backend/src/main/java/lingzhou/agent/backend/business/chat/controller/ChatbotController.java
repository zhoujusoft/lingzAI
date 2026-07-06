package lingzhou.agent.backend.business.chat.controller;

import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.skill.service.SkillPublishService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chatbot")
public class ChatbotController {

    private static final String APP_CODE_HEADER = "X-App-Code";
    private static final String PASSPORT_HEADER = "X-App-Passport";

    private final SkillPublishService skillPublishService;
    private final ChatConversationService chatConversationService;
    private final ConversationHistoryService conversationHistoryService;
    private final ChatFileService chatFileService;

    public ChatbotController(
            SkillPublishService skillPublishService,
            ChatConversationService chatConversationService,
            ConversationHistoryService conversationHistoryService,
            ChatFileService chatFileService) {
        this.skillPublishService = skillPublishService;
        this.chatConversationService = chatConversationService;
        this.conversationHistoryService = conversationHistoryService;
        this.chatFileService = chatFileService;
    }

    @NotLogin
    @GetMapping("/publish-status")
    public SkillPublishService.PublishAccessStatusView publishStatus(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode) throws TaskException {
        return skillPublishService.getPublishAccessStatus(appCode);
    }

    @NotLogin
    @GetMapping("/context")
    public SkillPublishService.PublishedSkillContext context(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport)
            throws TaskException {
        skillPublishService.validatePassport(appCode, passport);
        return skillPublishService.resolvePublishedSkillContext(appCode);
    }

    @NotLogin
    @GetMapping("/passport")
    public Map<String, Object> issuePassport(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @RequestParam(value = "userId", required = false) Long userId)
            throws TaskException {
        SkillPublishService.PassportIssueResult issueResult = skillPublishService.issuePassport(appCode, userId);
        return Map.of(
                "appCode", issueResult.appCode(),
                "passport", issueResult.passport(),
                "userId", issueResult.userId());
    }

    @NotLogin
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @RequestBody(required = false) ChatConversationService.SkillChatRequest request,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        return chatConversationService.streamPublishedSkill(appCode, request, claims.userId());
    }

    @NotLogin
    @PostMapping("/files/upload")
    public ResponseEntity<ChatFileService.UploadResponse> upload(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "messageId", required = false) Long messageId,
            @RequestParam(value = "eventId", required = false) Long eventId)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        return chatFileService.upload(
                file,
                claims.userId(),
                new ChatFileService.UploadBinding(sessionId, messageId, eventId));
    }

    @NotLogin
    @GetMapping("/sessions")
    public ConversationApiModels.SessionListResponse listSessions(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        SkillPublishService.PublishedSkillContext context = skillPublishService.resolvePublishedSkillContext(appCode);
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = normalizeSessionPageSize(pageSize == null ? limit : pageSize);
        ConversationHistoryService.SessionPageResult page = conversationHistoryService.listSessions(
                claims.userId(),
                ConversationSessionType.PUBLISHED_SKILL_CHAT,
                context.skillId(),
                safePageNo,
                safePageSize);
        return ConversationApiModels.sessionList(page.items(), safePageNo, safePageSize, page.total());
    }

    @NotLogin
    @GetMapping("/sessions/{sessionId}/messages")
    public ConversationApiModels.MessageListResponse listMessages(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        SkillPublishService.PublishedSkillContext context = skillPublishService.resolvePublishedSkillContext(appCode);
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = normalizeMessagePageSize(pageSize);
        ConversationHistoryService.MessagePageResult page = conversationHistoryService.listMessages(
                claims.userId(),
                ConversationSessionType.PUBLISHED_SKILL_CHAT,
                sessionId,
                context.skillId(),
                safePageNo,
                safePageSize);
        return ConversationApiModels.messageList(page.items(), safePageNo, safePageSize, page.total());
    }

    @NotLogin
    @DeleteMapping("/sessions/{sessionId}")
    public ConversationApiModels.DeleteSessionResponse deleteSession(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        SkillPublishService.PublishedSkillContext context = skillPublishService.resolvePublishedSkillContext(appCode);
        ConversationHistoryService.DeleteResult result = conversationHistoryService.deleteSession(
                claims.userId(), ConversationSessionType.PUBLISHED_SKILL_CHAT, sessionId, context.skillId());
        return ConversationApiModels.deleteResult(result);
    }

    @NotLogin
    @PutMapping("/sessions/{sessionId}/name")
    public ConversationApiModels.RenameSessionResponse renameSession(
            @RequestHeader(value = APP_CODE_HEADER, required = false) String appCode,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = PASSPORT_HEADER, required = false) String passport,
            @RequestBody(required = false) ConversationApiModels.RenameSessionRequest requestBody)
            throws TaskException {
        SkillPublishService.PassportClaims claims = skillPublishService.validatePassport(appCode, passport);
        SkillPublishService.PublishedSkillContext context = skillPublishService.resolvePublishedSkillContext(appCode);
        ConversationHistoryService.RenameResult result = conversationHistoryService.renameSession(
                claims.userId(),
                ConversationSessionType.PUBLISHED_SKILL_CHAT,
                sessionId,
                context.skillId(),
                requestBody == null ? null : requestBody.name());
        return ConversationApiModels.renameResult(result);
    }

    private int normalizeMessagePageSize(Integer pageSize) {
        if (pageSize == null) {
            return 10;
        }
        return Math.max(1, Math.min(pageSize, 100));
    }

    private int normalizeSessionPageSize(Integer pageSize) {
        if (pageSize == null) {
            return 20;
        }
        return Math.max(1, Math.min(pageSize, 100));
    }
}
