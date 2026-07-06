package lingzhou.agent.backend.skillstudio.project.service;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequestAssembler;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimeRequestMapper;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeRequest;
import lingzhou.agent.backend.business.chat.service.ChatRuntimeExecutor;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.draft.SkillStudioDraftFileService;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class SkillStudioProjectPreviewService {

    private static final ConversationSessionType SESSION_TYPE =
            ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT;

    private final SkillStudioProjectMapper projectMapper;
    private final SkillStudioDraftFileService draftFileService;
    private final SkillCatalogService skillCatalogService;
    private final SkillStudioProjectSettingsService projectSettingsService;
    private final ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler;
    private final ChatRuntimeExecutor chatRuntimeExecutor;

    public SkillStudioProjectPreviewService(
            SkillStudioProjectMapper projectMapper,
            SkillStudioDraftFileService draftFileService,
            SkillCatalogService skillCatalogService,
            SkillStudioProjectSettingsService projectSettingsService,
            ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler,
            ChatRuntimeExecutor chatRuntimeExecutor) {
        this.projectMapper = projectMapper;
        this.draftFileService = draftFileService;
        this.skillCatalogService = skillCatalogService;
        this.projectSettingsService = projectSettingsService;
        this.chatRuntimePreparedRequestAssembler = chatRuntimePreparedRequestAssembler;
        this.chatRuntimeExecutor = chatRuntimeExecutor;
    }

    public Flux<ServerSentEvent<String>> streamPreviewRun(Long userId, Long projectId, PreviewRunCommand command)
            throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        String message = normalizeMessage(command == null ? null : command.message());
        List<String> fileIds = command == null || command.fileIds() == null ? List.of() : command.fileIds();
        if (!StringUtils.hasText(message) && fileIds.isEmpty()) {
            throw new TaskException("试运行消息或文件不能为空", TaskException.Code.UNKNOWN);
        }
        String skillContent = draftFileService
                .readSkillMd(project.getDraftSkillName())
                .orElseThrow(() -> new TaskException("草稿技能缺少 SKILL.md，无法试运行", TaskException.Code.UNKNOWN));

        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkillStudioPreview(
                command == null ? null : command.sessionId(),
                message,
                fileIds,
                command == null ? null : command.messageType(),
                command == null ? null : command.eventPayload(),
                command == null ? null : command.systemPromptAppend(),
                command == null ? null : command.options(),
                projectId,
                project.getDraftSkillName());
        List<String> boundToolNames = projectSettingsService.listEnabledToolNames(userId, projectId);
        SkillCatalogService.SkillChatContext context = skillCatalogService.buildAdHocSkillChatContext(
                project.getId(),
                project.getDraftSkillName(),
                project.getName(),
                project.getDescription(),
                skillContent,
                boundToolNames);
        ChatRuntimePreparedRequest prepared =
                chatRuntimePreparedRequestAssembler.buildSkill(SESSION_TYPE, normalized, context);
        return chatRuntimeExecutor.stream(prepared, userId);
    }

    private SkillStudioProject requireOwnedProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = projectMapper.selectOwnedProject(userId, projectId);
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private String normalizeMessage(String message) {
        return StringUtils.hasText(message) ? message.trim() : "";
    }

    public record PreviewRunCommand(
            String sessionId,
            String message,
            List<String> fileIds,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options) {}
}
