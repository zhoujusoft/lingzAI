package lingzhou.agent.backend.skillstudio.project.service;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService.ConversationContext;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import lingzhou.agent.backend.business.chat.service.UserTokenQuotaService;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TokenUsageCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshotMerger;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.application.SkillStudioCreatorDebugService;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import lingzhou.agent.backend.skillstudio.runtime.SkillStudioCreatorExecutionException;
import lingzhou.agent.backend.skillstudio.runtime.SkillStudioCreatorSkillDebugResult;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class SkillStudioProjectChatService {

    private static final ConversationSessionType SESSION_TYPE = ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT;
    private static final String FIRST_ROUND_STRICT_GUARD_MARKER =
            "##__SKILLSTUDIO_FIRST_ROUND_STRICT_RUNTIME_TOOL_GUARD__##";

    private final SkillStudioProjectMapper projectMapper;
    private final ConversationHistoryService conversationHistoryService;
    private final SkillStudioCreatorDebugService creatorDebugService;
    private final SkillStudioProjectSettingsService projectSettingsService;
    private final SkillStudioProjectAutoToolBindingService autoToolBindingService;
    private final SkillStudioProjectPendingUsageService pendingUsageService;
    private final LicenseService licenseService;
    private final UserTokenQuotaService userTokenQuotaService;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;

    public SkillStudioProjectChatService(
            SkillStudioProjectMapper projectMapper,
            ConversationHistoryService conversationHistoryService,
            SkillStudioCreatorDebugService creatorDebugService,
            SkillStudioProjectSettingsService projectSettingsService,
            SkillStudioProjectAutoToolBindingService autoToolBindingService,
            SkillStudioProjectPendingUsageService pendingUsageService,
            LicenseService licenseService,
            UserTokenQuotaService userTokenQuotaService,
            TokenUsageCapabilityAdapter tokenUsageCapability) {
        this.projectMapper = projectMapper;
        this.conversationHistoryService = conversationHistoryService;
        this.creatorDebugService = creatorDebugService;
        this.projectSettingsService = projectSettingsService;
        this.autoToolBindingService = autoToolBindingService;
        this.pendingUsageService = pendingUsageService;
        this.licenseService = licenseService;
        this.userTokenQuotaService = userTokenQuotaService;
        this.tokenUsageCapability = tokenUsageCapability;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectChatReply sendMessage(Long userId, Long projectId, ProjectChatCommand command) throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        validateQuota(userId);
        String message = normalizeRequiredMessage(command == null ? null : command.message());
        boolean firstRoundWithoutHistory = isFirstRoundWithoutHistory(userId, projectId, command);
        String creatorMessage = appendFirstRoundStrictGuardMarker(message, firstRoundWithoutHistory);
        ChatRuntimePreparedRequest prepared = buildPreparedRequest(project, command, message);
        RuntimeRunUsageSnapshot metadataUsage = consumePendingMetadataUsage(project, firstRoundWithoutHistory);
        SkillStudioProjectAutoToolBindingService.AutoBindingResult autoBindingResult =
                autoToolBindingService.tryAutoBindFromMessage(userId, projectId, message);
        RuntimeRunUsageSnapshot preludeUsage = mergeUsageSnapshots(
                metadataUsage, autoBindingResult == null ? null : autoBindingResult.usageSnapshot());
        settleUsage(userId, preludeUsage);
        validateQuota(userId);

        ConversationContext context = conversationHistoryService.startMessage(
                userId,
                SESSION_TYPE,
                command == null ? null : command.sessionId(),
                projectId,
                project.getName(),
                message,
                "SKILL_STUDIO_CREATOR",
                message,
                "PROJECT_CREATION",
                null,
                null,
                null);

        try {
            SkillStudioCreatorDebugService.PreviewCommand previewCommand =
                    new SkillStudioCreatorDebugService.PreviewCommand(
                            userId,
                            projectId,
                            project.getDraftSkillName(),
                            isEditMode(command)
                                    ? lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode.EDIT
                                    : lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode.CREATE,
                            creatorMessage,
                            resolvePreferredTemplate(command),
                            preferMinimalChange(command),
                            allowCreateReferences(command));

            SkillStudioCreatorSkillDebugResult debugResult = creatorDebugService.applyBySkill(previewCommand);
            if (debugResult != null && debugResult.applied()) {
                projectSettingsService.markGenerated(project);
            }

            String answer = buildAssistantAnswer(project, debugResult);
            String paramsJson = buildAssistantParams(debugResult);
            RuntimeRunUsageSnapshot creatorUsage = debugResult == null ? null : debugResult.usageSnapshot();
            settleUsage(userId, creatorUsage);
            RuntimeRunUsageSnapshot usageSnapshot = mergeUsageSnapshots(preludeUsage, creatorUsage);
            ConversationMessageUsagePayload usagePayload = tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
            conversationHistoryService.completeMessage(context, answer, null, null, paramsJson, 0L, usagePayload);
            persistRunUsage(context, prepared, usageSnapshot);
            return new ProjectChatReply(context.sessionCode(), debugResult, answer);
        } catch (Exception error) {
            String errorMessage = resolveErrorMessage(error);
            RuntimeRunUsageSnapshot creatorUsage = resolveUsageSnapshot(error);
            settleUsage(userId, creatorUsage);
            RuntimeRunUsageSnapshot usageSnapshot = mergeUsageSnapshots(preludeUsage, creatorUsage);
            ConversationMessageUsagePayload usagePayload = tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
            conversationHistoryService.failMessage(context, errorMessage, "", null, 0L, usagePayload);
            persistRunUsage(context, prepared, usageSnapshot);
            if (error instanceof TaskException taskException) {
                throw taskException;
            }
            throw new TaskException("技能工坊项目对话执行失败: " + errorMessage, TaskException.Code.UNKNOWN);
        }
    }

    public Flux<ServerSentEvent<String>> streamMessage(Long userId, Long projectId, ProjectChatCommand command)
            throws TaskException {
        SkillStudioProject project = requireOwnedProject(userId, projectId);
        validateQuota(userId);
        String message = normalizeRequiredMessage(command == null ? null : command.message());
        boolean firstRoundWithoutHistory = isFirstRoundWithoutHistory(userId, projectId, command);
        String creatorMessage = appendFirstRoundStrictGuardMarker(message, firstRoundWithoutHistory);
        ChatRuntimePreparedRequest prepared = buildPreparedRequest(project, command, message);
        RuntimeRunUsageSnapshot metadataUsage = consumePendingMetadataUsage(project, firstRoundWithoutHistory);
        SkillStudioProjectAutoToolBindingService.AutoBindingResult autoBindingResult =
                autoToolBindingService.tryAutoBindFromMessage(userId, projectId, message);
        RuntimeRunUsageSnapshot preludeUsage = mergeUsageSnapshots(
                metadataUsage, autoBindingResult == null ? null : autoBindingResult.usageSnapshot());
        settleUsage(userId, preludeUsage);
        validateQuota(userId);
        ConversationContext context = conversationHistoryService.startMessage(
                userId,
                SESSION_TYPE,
                command == null ? null : command.sessionId(),
                projectId,
                project.getName(),
                message,
                "SKILL_STUDIO_CREATOR",
                message,
                "PROJECT_CREATION",
                null,
                null,
                null);

        SkillStudioCreatorDebugService.PreviewCommand previewCommand =
                new SkillStudioCreatorDebugService.PreviewCommand(
                        userId,
                        projectId,
                        project.getDraftSkillName(),
                        isEditMode(command)
                                ? lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode.EDIT
                                : lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode.CREATE,
                        creatorMessage,
                        resolvePreferredTemplate(command),
                        preferMinimalChange(command),
                        allowCreateReferences(command));

        AtomicReference<SkillStudioCreatorSkillDebugResult> resultRef = new AtomicReference<>();
        AtomicReference<String> errorMessageRef = new AtomicReference<>("");
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean finalized = new AtomicBoolean(false);

        Flux<ServerSentEvent<String>> meta = Flux.just(metaEvent(conversationHistoryService.buildMetaPayload(context)));
        Flux<ServerSentEvent<String>> stream = creatorDebugService
                .streamBySkill(previewCommand, resultRef::set, error -> {
                    errorRef.set(error);
                    errorMessageRef.set(resolveErrorMessage(error));
                })
                .filter(event -> !isCreatorMessageEvent(event));

        return meta.concatWith(stream).concatWith(Flux.defer(() -> {
            if (StringUtils.hasText(errorMessageRef.get())) {
                if (finalized.compareAndSet(false, true)) {
                    RuntimeRunUsageSnapshot creatorUsage = resolveUsageSnapshot(errorRef.get());
                    settleUsage(userId, creatorUsage);
                    RuntimeRunUsageSnapshot usageSnapshot = mergeUsageSnapshots(preludeUsage, creatorUsage);
                    ConversationMessageUsagePayload usagePayload =
                            tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                    conversationHistoryService.failMessage(context, errorMessageRef.get(), "", null, 0L, usagePayload);
                    persistRunUsage(context, prepared, usageSnapshot);
                }
                return Flux.just(doneEvent());
            }

            SkillStudioCreatorSkillDebugResult rawResult = resultRef.get();
            if (rawResult == null) {
                String errorMessage = "技能工坊项目对话执行失败";
                if (finalized.compareAndSet(false, true)) {
                    conversationHistoryService.failMessage(context, errorMessage, "", null, 0L);
                }
                return Flux.just(errorEvent(errorMessage), doneEvent());
            }

            SkillStudioCreatorSkillDebugResult finalResult = finalizeResultForApply(rawResult, command);
            if (finalResult != null && finalResult.applied()) {
                try {
                    projectSettingsService.markGenerated(project);
                } catch (TaskException ex) {
                    errorMessageRef.set(ex.getMessage());
                    if (finalized.compareAndSet(false, true)) {
                        RuntimeRunUsageSnapshot creatorUsage = finalResult.usageSnapshot();
                        settleUsage(userId, creatorUsage);
                        RuntimeRunUsageSnapshot usageSnapshot = mergeUsageSnapshots(preludeUsage, creatorUsage);
                        ConversationMessageUsagePayload usagePayload =
                                tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                        conversationHistoryService.failMessage(context, ex.getMessage(), "", null, 0L, usagePayload);
                        persistRunUsage(context, prepared, usageSnapshot);
                    }
                    return Flux.just(errorEvent(ex.getMessage()), doneEvent());
                }
            }
            String answer = buildAssistantAnswer(project, finalResult);
            String paramsJson = buildAssistantParams(finalResult);
            if (finalized.compareAndSet(false, true)) {
                RuntimeRunUsageSnapshot creatorUsage = finalResult == null ? null : finalResult.usageSnapshot();
                settleUsage(userId, creatorUsage);
                RuntimeRunUsageSnapshot usageSnapshot = mergeUsageSnapshots(preludeUsage, creatorUsage);
                ConversationMessageUsagePayload usagePayload =
                        tokenUsageCapability.toMessageUsagePayload(usageSnapshot);
                conversationHistoryService.completeMessage(context, answer, null, null, paramsJson, 0L, usagePayload);
                persistRunUsage(context, prepared, usageSnapshot);
            }

            return Flux.just(messageEvent(answer), doneEvent());
        }));
    }

    private void validateQuota(Long userId) throws TaskException {
        String licenseError = licenseService.validateConversationAccess(userId, SESSION_TYPE);
        if (StringUtils.hasText(licenseError)) {
            throw new TaskException(licenseError, TaskException.Code.UNKNOWN);
        }
        String quotaMessage = userTokenQuotaService.validateQuota(userId, SESSION_TYPE);
        if (StringUtils.hasText(quotaMessage)) {
            throw new TaskException(quotaMessage, TaskException.Code.UNKNOWN);
        }
    }

    private void settleUsage(Long userId, RuntimeRunUsageSnapshot usageSnapshot) {
        userTokenQuotaService.settleUsage(userId, SESSION_TYPE, usageSnapshot);
    }

    private void persistRunUsage(
            ConversationContext context, ChatRuntimePreparedRequest prepared, RuntimeRunUsageSnapshot usageSnapshot) {
        if (usageSnapshot == null) {
            return;
        }
        tokenUsageCapability.persistRun(context, prepared, usageSnapshot);
    }

    private RuntimeRunUsageSnapshot resolveUsageSnapshot(Throwable error) {
        if (error instanceof SkillStudioCreatorExecutionException creatorExecutionException) {
            return creatorExecutionException.getUsageSnapshot();
        }
        return null;
    }

    private String resolveErrorMessage(Throwable error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return "技能工坊项目对话执行失败";
        }
        return error.getMessage();
    }

    private RuntimeRunUsageSnapshot consumePendingMetadataUsage(
            SkillStudioProject project, boolean firstRoundWithoutHistory) {
        if (!firstRoundWithoutHistory || project == null || !StringUtils.hasText(project.getDraftSkillName())) {
            return null;
        }
        return pendingUsageService
                .consumePendingMetadataUsage(project.getDraftSkillName())
                .orElse(null);
    }

    private RuntimeRunUsageSnapshot mergeUsageSnapshots(RuntimeRunUsageSnapshot... snapshots) {
        return RuntimeRunUsageSnapshotMerger.merge(java.util.Arrays.stream(snapshots)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    private ChatRuntimePreparedRequest buildPreparedRequest(
            SkillStudioProject project, ProjectChatCommand command, String message) {
        return new ChatRuntimePreparedRequest(
                SESSION_TYPE,
                LingzRuntimeScopeType.SKILL_STUDIO_PROJECT,
                command == null ? null : command.sessionId(),
                project == null ? null : project.getId(),
                project == null ? null : project.getName(),
                message,
                message,
                "SKILL_STUDIO_CREATOR",
                "PROJECT_CREATION",
                null,
                null,
                List.of(),
                null,
                null,
                project == null ? null : project.getDraftSkillName(),
                List.of(),
                List.of(),
                null,
                false,
                "");
    }

    private boolean isCreatorMessageEvent(ServerSentEvent<String> event) {
        if (event == null || !StringUtils.hasText(event.event())) {
            return false;
        }
        return "message".equalsIgnoreCase(event.event());
    }

    private SkillStudioCreatorSkillDebugResult finalizeResultForApply(
            SkillStudioCreatorSkillDebugResult rawResult, ProjectChatCommand command) {
        return rawResult;
    }

    private SkillStudioProject requireOwnedProject(Long userId, Long projectId) throws TaskException {
        SkillStudioProject project = projectMapper.selectOwnedProject(userId, projectId);
        if (project == null) {
            throw new TaskException("技能工坊项目不存在", TaskException.Code.UNKNOWN);
        }
        return project;
    }

    private String normalizeRequiredMessage(String message) throws TaskException {
        String normalized = StringUtils.hasText(message) ? message.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("项目消息不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private boolean isFirstRoundWithoutHistory(Long userId, Long projectId, ProjectChatCommand command) {
        if (command != null && StringUtils.hasText(command.sessionId())) {
            return false;
        }
        return conversationHistoryService
                .listSessions(userId, SESSION_TYPE, projectId, 1)
                .isEmpty();
    }

    private String appendFirstRoundStrictGuardMarker(String message, boolean firstRoundWithoutHistory) {
        if (!firstRoundWithoutHistory || !StringUtils.hasText(message)) {
            return message;
        }
        if (message.contains(FIRST_ROUND_STRICT_GUARD_MARKER)) {
            return message;
        }
        return message + "\n\n" + FIRST_ROUND_STRICT_GUARD_MARKER;
    }

    private String buildAssistantAnswer(SkillStudioProject project, SkillStudioCreatorSkillDebugResult result) {
        if (result == null || !result.applied()) {
            if (result != null && StringUtils.hasText(result.rawOutput())) {
                return result.rawOutput().trim();
            }
            return """
                    已收到你的需求，本轮未产生技能文件改动。

                    你可以继续告诉我要调整的业务目标、适用对象或输出要求，我会基于这些信息再更新技能草稿。
                    """
                    .trim();
        }
        List<String> writtenFiles = extractWrittenFiles(result);
        boolean touchedSkillMd = writtenFiles.stream().anyMatch(path -> path.endsWith("SKILL.md"));
        long referenceCount = writtenFiles.stream()
                .filter(path -> path.contains("/references/"))
                .count();
        long scriptCount = writtenFiles.stream()
                .filter(path -> path.contains("/scripts/") && path.endsWith(".py"))
                .count();
        boolean touchedRequirements = writtenFiles.stream().anyMatch(path -> path.endsWith("requirements.txt"));

        StringBuilder builder = new StringBuilder();
        builder.append("已根据你的需求完成技能草稿更新（")
                .append(project.getName())
                .append("）。")
                .append("\n\n");
        builder.append("本次业务侧更新：").append("\n");
        if (touchedSkillMd) {
            builder.append("- 已更新技能主说明，重点强化业务场景、处理目标和回答规则。").append("\n");
        }
        if (referenceCount > 0) {
            builder.append("- 已补充 ")
                    .append(referenceCount)
                    .append(" 个参考文件，用于沉淀稳定业务口径。")
                    .append("\n");
        }
        if (scriptCount > 0) {
            builder.append("- 已补充 ")
                    .append(scriptCount)
                    .append(" 个 Python 脚本，用于承载稳定、可执行的处理逻辑。")
                    .append("\n");
        }
        if (touchedRequirements) {
            builder.append("- 已维护 `requirements.txt`，用于声明当前 skill 的 Python 依赖。").append("\n");
        }
        if (!touchedSkillMd && referenceCount == 0 && scriptCount == 0 && !touchedRequirements) {
            builder.append("- 已处理你的需求，但本轮未识别到需要落盘的业务改动。").append("\n");
        }
        builder.append("\n");
        builder.append("当前状态：改动已写入工坊草稿，发布后才会正式生效。");
        return builder.toString().trim();
    }

    private List<String> extractWrittenFiles(SkillStudioCreatorSkillDebugResult result) {
        if (result == null || result.toolLogs() == null || result.toolLogs().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String logLine : result.toolLogs()) {
            if (!StringUtils.hasText(logLine)) {
                continue;
            }
            int start = logLine.indexOf("writeFile ok:");
            if (start < 0) {
                continue;
            }
            String tail = logLine.substring(start + "writeFile ok:".length()).trim();
            int comma = tail.indexOf(',');
            String path = comma >= 0 ? tail.substring(0, comma).trim() : tail;
            if (StringUtils.hasText(path)) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    private String buildAssistantParams(SkillStudioCreatorSkillDebugResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runtimeSkillName", result.runtimeSkillName());
        payload.put("skillPath", result.skillPath());
        payload.put("toolNames", result.toolNames() == null ? java.util.List.of() : result.toolNames());
        payload.put("toolEvents", result.toolEvents() == null ? java.util.List.of() : result.toolEvents());
        payload.put("toolLogs", result.toolLogs() == null ? java.util.List.of() : result.toolLogs());
        payload.put("executionLogs", result.executionLogs() == null ? java.util.List.of() : result.executionLogs());
        payload.put("applied", result.applied());
        payload.put("applyMessage", result.applyMessage());
        if (StringUtils.hasText(result.parseError())) {
            payload.put("parseError", result.parseError());
        }
        return JSON.toJSONString(payload);
    }

    private boolean preferMinimalChange(ProjectChatCommand command) {
        if (command == null) {
            return true;
        }
        ProjectChatOptions options = command.options();
        if (options == null || options.preferMinimalChange() == null) {
            return true;
        }
        return options.preferMinimalChange();
    }

    private boolean allowCreateReferences(ProjectChatCommand command) {
        if (command == null) {
            return false;
        }
        ProjectChatOptions options = command.options();
        return options != null && Boolean.TRUE.equals(options.allowCreateReferences());
    }

    private boolean isPreviewOnly(ProjectChatCommand command) {
        if (command == null) {
            return false;
        }
        ProjectChatOptions options = command.options();
        return options != null && Boolean.TRUE.equals(options.previewOnly());
    }

    private boolean isEditMode(ProjectChatCommand command) {
        if (command == null) {
            return false;
        }
        ProjectChatOptions options = command.options();
        return options == null || !Boolean.FALSE.equals(options.editMode());
    }

    private String resolvePreferredTemplate(ProjectChatCommand command) {
        if (command == null || command.options() == null) {
            return null;
        }
        String preferredTemplate = command.options().preferredTemplate();
        return StringUtils.hasText(preferredTemplate) ? preferredTemplate.trim() : null;
    }

    private ServerSentEvent<String> metaEvent(Object content) {
        return typedEvent("meta", "meta", content);
    }

    private ServerSentEvent<String> messageEvent(Object content) {
        return typedEvent("message", "message", content);
    }

    private ServerSentEvent<String> toolEvent(Object content) {
        return typedEvent("tool", "tool", content);
    }

    private ServerSentEvent<String> resultEvent(Object content) {
        return typedEvent("result", "result", content);
    }

    private ServerSentEvent<String> errorEvent(String error) {
        return typedEvent("error", "error", error);
    }

    private ServerSentEvent<String> doneEvent() {
        return typedEvent("done", "done", "[DONE]");
    }

    private ServerSentEvent<String> typedEvent(String eventName, String type, Object content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return ServerSentEvent.builder(JSON.toJSONString(payload))
                .event(eventName)
                .build();
    }

    public record ProjectChatCommand(
            String sessionId,
            String message,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            ProjectChatOptions options) {}

    public record ProjectChatOptions(
            String preferredTemplate,
            Boolean preferMinimalChange,
            Boolean allowCreateReferences,
            Boolean previewOnly,
            Boolean editMode) {}

    public record ProjectChatReply(String sessionId, SkillStudioCreatorSkillDebugResult debugResult, String answer) {}
}
