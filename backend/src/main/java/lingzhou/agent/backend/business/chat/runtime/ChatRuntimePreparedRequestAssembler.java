package lingzhou.agent.backend.business.chat.runtime;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.business.chat.attachment.FileParseMode;
import lingzhou.agent.backend.business.chat.attachment.FileParseResult;
import lingzhou.agent.backend.business.chat.attachment.FileParseService;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvDescriptorService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.ToolSimpleDto;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.capability.agentruntime.prompt.PromptEngineeringService;
import lingzhou.agent.backend.capability.dataset.runtime.IntegrationDatasetAgentToolRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.capability.tool.registry.ToolLibraryCallbackResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ChatRuntimePreparedRequestAssembler {

    private static final String FILENAME_SOUL = "SOUL.md";
    private static final String FILENAME_PROFILE = "PROFILE.md";
    private static final String EXECUTION_MODE_HINT_DIRECT = "DIRECT";
    private static final String EXECUTION_MODE_HINT_TOOL = "TOOL";
    private static final String EXECUTION_WORLD_DIRECT = "GENERAL_DIRECT";
    private static final String EXECUTION_WORLD_TOOL = "GENERAL_TOOL";
    private static final String EXECUTION_WORLD_TOOL_WITH_CODE_FALLBACK = "GENERAL_TOOL_WITH_CODE_FALLBACK";
    private static final Set<String> DIRECT_FAST_TOKENS = Set.of(
            "你好", "您好", "你好呀", "你好啊", "嗨", "哈喽", "hi", "hihi", "hello", "hey", "yo", "在吗", "在嘛", "在不在", "早", "早安",
            "早上好", "中午好", "午安", "晚上好", "晚安", "谢谢", "多谢", "thx", "thanks");
    private static final String GENERAL_SKILL_CATALOG_PROMPT =
            """
            ## Skills

            可选技能目录如下。

            当当前问题命中某个 Skill 领域时：
            1. 调用 `loadSkillContent(skillName)`
            2. 按 Skill 说明执行
            3. 使用 Skill 暴露出的能力完成任务

            未命中 Skill 时，保持 General 模式。
            已加载 Skill 仅代表历史状态，每轮都应基于当前用户问题重新判断是否继续使用。
            如果用户询问可用技能，调用 `listActiveSkills`；同一轮最多调用一次，拿到结果后直接回答。
            如果用户点名的 Skill 不存在，说明未找到，再基于可用技能选择替代方案或请用户确认名称。

            可选技能目录：
            """;

    private final ChatFileService chatFileService;
    private final ConversationHistoryService conversationHistoryService;
    private final FileParseService fileParseService;
    private final IntegrationDatasetAgentToolRegistry integrationDatasetAgentToolRegistry;
    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final PromptEngineeringService promptEngineeringService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final UserAgentConfigService userAgentConfigService;
    private final SkillCatalogService skillCatalogService;
    private final GlobalToolRegistry globalToolRegistry;
    private final ToolLibraryCallbackResolver toolLibraryCallbackResolver;
    private final ToolToCodeEscalationPolicy toolToCodeEscalationPolicy;
    private final PythonRuntimeEnvDescriptorService pythonRuntimeEnvDescriptorService;

    public ChatRuntimePreparedRequestAssembler(
            ChatFileService chatFileService,
            ConversationHistoryService conversationHistoryService,
            FileParseService fileParseService,
            IntegrationDatasetAgentToolRegistry integrationDatasetAgentToolRegistry,
            IntegrationDataSourceMapper integrationDataSourceMapper,
            PromptEngineeringService promptEngineeringService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            UserAgentConfigService userAgentConfigService,
            SkillCatalogService skillCatalogService,
            GlobalToolRegistry globalToolRegistry,
            ToolLibraryCallbackResolver toolLibraryCallbackResolver,
            ToolToCodeEscalationPolicy toolToCodeEscalationPolicy,
            PythonRuntimeEnvDescriptorService pythonRuntimeEnvDescriptorService) {
        this.chatFileService = chatFileService;
        this.conversationHistoryService = conversationHistoryService;
        this.fileParseService = fileParseService;
        this.integrationDatasetAgentToolRegistry = integrationDatasetAgentToolRegistry;
        this.integrationDataSourceMapper = integrationDataSourceMapper;
        this.promptEngineeringService = promptEngineeringService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.userAgentConfigService = userAgentConfigService;
        this.skillCatalogService = skillCatalogService;
        this.globalToolRegistry = globalToolRegistry;
        this.toolLibraryCallbackResolver = toolLibraryCallbackResolver;
        this.toolToCodeEscalationPolicy = toolToCodeEscalationPolicy;
        this.pythonRuntimeEnvDescriptorService = pythonRuntimeEnvDescriptorService;
    }

    public boolean hasRequestContent(String message, List<String> fileIds) {
        return StringUtils.hasText(message) || (fileIds != null && !fileIds.isEmpty());
    }

    public boolean hasSkillRequestContent(LingzRuntimeRequest request) {
        if (request == null) {
            return false;
        }
        if (hasRequestContent(request.message(), request.fileIds())) {
            return true;
        }
        return StringUtils.hasText(normalizeMessageType(request.messageType())) && request.eventPayload() != null;
    }

    public String resolveRuntimeMessageType(String messageType) {
        String normalized = normalizeMessageType(messageType);
        return StringUtils.hasText(normalized) ? normalized : "normal";
    }

    public ChatRuntimePreparedRequest buildGeneral(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized, Long userId) {
        List<String> effectiveFileIds = resolveEffectiveFileIds(sessionType, normalized, userId);
        String message = resolveMessage(normalized.message(), effectiveFileIds, null);
        String fileListJson = chatFileService.buildFileListJson(effectiveFileIds);
        List<FileParseResult> parsedFiles = resolveParsedFiles(effectiveFileIds, normalized);
        boolean artifactRequired = isOptionEnabled(normalized.options(), "artifactRequired");
        boolean channelArtifactRequired = isOptionEnabled(normalized.options(), "channelArtifactRequired");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "general");
        params.put("fileIds", effectiveFileIds);
        if (artifactRequired) {
            params.put("artifactRequired", Boolean.TRUE);
        }
        if (channelArtifactRequired) {
            params.put("channelArtifactRequired", Boolean.TRUE);
        }
        params.put(
                "inheritedFileContext",
                (normalized.fileIds() == null ? List.<String>of() : normalized.fileIds()).isEmpty()
                        && !effectiveFileIds.isEmpty());
        if (!parsedFiles.isEmpty()) {
            params.put("parsedAttachments", fileParseService.toSerializablePayload(parsedFiles));
        }
        List<RuntimeSkillDescriptor> availableSkills = resolveAvailableSkills(userId);
        RuntimeSkillDescriptor mentionedSkill = resolveMentionedSkill(availableSkills, normalized.mentionedSkillId());
        boolean directFastMatched = matchesDirectFast(message, effectiveFileIds, parsedFiles, mentionedSkill);
        String executionModeHint = resolveExecutionModeHint(directFastMatched, mentionedSkill, parsedFiles, message);
        if (artifactRequired) {
            executionModeHint = EXECUTION_MODE_HINT_TOOL;
        }
        ToolToCodeEscalationDecision toolToCodeDecision =
                toolToCodeEscalationPolicy.evaluate(message, effectiveFileIds, parsedFiles, mentionedSkill);
        String executionWorld = resolveExecutionWorld(executionModeHint, toolToCodeDecision);
        params.put("availableSkills", availableSkills);
        params.put("executionModeHint", executionModeHint);
        params.put("executionWorld", executionWorld);
        params.put("directFastMatched", directFastMatched);
        params.put("toolToCodeDecision", toSerializableToolToCodeDecision(toolToCodeDecision));
        String paramsJson = JSON.toJSONString(params);
        if (mentionedSkill != null && requestScopedSkillRuntimeService != null) {
            paramsJson = requestScopedSkillRuntimeService.mergeSkillHintParams(
                    paramsJson, mentionedSkill.skillId(), mentionedSkill.skillId(), mentionedSkill.runtimeSkillName());
        } else if (mentionedSkill != null) {
            params.put("mentionedSkillId", mentionedSkill.skillId());
            params.put("selectedSkillHintId", mentionedSkill.skillId());
            params.put("selectedSkillHintRuntimeSkillName", mentionedSkill.runtimeSkillName());
            paramsJson = JSON.toJSONString(params);
        }
        Map<String, ToolCallback> toolCallbacks = new LinkedHashMap<>();
        if (EXECUTION_MODE_HINT_TOOL.equalsIgnoreCase(executionModeHint)) {
            for (ToolCallback callback : globalToolRegistry.getSystemRuntimeToolCallbacks()) {
                String toolName = callback == null || callback.getToolDefinition() == null
                        ? null
                        : callback.getToolDefinition().name();
                if (StringUtils.hasText(toolName)) {
                    toolCallbacks.putIfAbsent(toolName, callback);
                }
            }
            for (ToolCallback callback : toolLibraryCallbackResolver.listEnabledGlobalCallbacks(userId)) {
                String toolName = callback == null || callback.getToolDefinition() == null
                        ? null
                        : callback.getToolDefinition().name();
                if (StringUtils.hasText(toolName)) {
                    toolCallbacks.putIfAbsent(toolName, callback);
                }
            }
        }
        String systemPrompt = appendToolCatalogPrompt(
                appendArtifactRequiredPrompt(
                        buildGeneralSystemPrompt(
                                userId, availableSkills, executionModeHint, toolToCodeDecision, mentionedSkill),
                        artifactRequired),
                List.copyOf(toolCallbacks.values()));

        if (artifactRequired) {
            log.info(
                    "产物型运行时请求已组装：sessionId={}, scopeType={}, executionModeHint={}, toolCount={}, hasWriteArtifactTool={}, fileCount={}",
                    normalized.sessionId(),
                    normalized.scopeType(),
                    executionModeHint,
                    toolCallbacks.size(),
                    hasToolCallback(toolCallbacks, "write_artifact"),
                    effectiveFileIds.size());
        }
        log.debug(
                "[运行时画像] General请求已组装：会话ID={}, mentionedSkill={}, executionModeHint={}, executionWorld={}, directFastMatched={}, toolToCodePath={}, codeEscalationCandidate={}, paramsJson中含availableSkills={}, toolCallbacks={}",
                normalized.sessionId(),
                mentionedSkill == null ? "" : mentionedSkill.runtimeSkillName(),
                executionModeHint,
                executionWorld,
                directFastMatched,
                toolToCodeDecision.recommendedPath(),
                toolToCodeDecision.codeEscalationCandidate(),
                !availableSkills.isEmpty(),
                toolCallbacks.size());

        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized.scopeType(),
                normalized.sessionId(),
                null,
                null,
                message,
                chatFileService.buildUserMessage(
                                message.equals(normalizeMessage(normalized.message())) ? message : "",
                                effectiveFileIds,
                                false)
                        + fileParseService.buildPromptContext(parsedFiles),
                resolveRuntimeMessageType(normalized.messageType()),
                sessionType.name(),
                paramsJson,
                fileListJson,
                List.copyOf(toolCallbacks.values()),
                systemPrompt,
                normalized.systemPromptAppend(),
                null,
                availableSkills,
                List.of(),
                normalized.chatModelId(),
                normalized.isPersonalAgentRequest(),
                normalized.resolvePersonalAgentMode());
    }

    public ChatRuntimePreparedRequest buildSkill(
            ConversationSessionType sessionType,
            LingzRuntimeRequest normalized,
            SkillCatalogService.SkillChatContext context) {
        String messageType = resolveRuntimeMessageType(normalized.messageType());
        String message = resolveSkillMessage(normalized, context.runtimeSkillName(), messageType);
        String fileListJson = chatFileService.buildFileListJson(normalized.fileIds());
        List<FileParseResult> parsedAttachments = resolveParsedFiles(normalized.fileIds(), normalized);
        boolean artifactRequired = isOptionEnabled(normalized.options(), "artifactRequired");
        boolean channelArtifactRequired = isOptionEnabled(normalized.options(), "channelArtifactRequired");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skillId", context.skillId());
        params.put("runtimeSkillName", context.runtimeSkillName());
        params.put("fileIds", normalized.fileIds() == null ? List.of() : normalized.fileIds());
        if (artifactRequired) {
            params.put("artifactRequired", Boolean.TRUE);
        }
        if (channelArtifactRequired) {
            params.put("channelArtifactRequired", Boolean.TRUE);
        }
        if (!parsedAttachments.isEmpty()) {
            params.put("parsedAttachments", fileParseService.toSerializablePayload(parsedAttachments));
        }
        params.put("messageType", messageType);
        if (normalized.eventPayload() != null) {
            params.put("eventPayload", normalized.eventPayload());
        }
        String rawMessage = normalizeMessage(normalized.message());
        String userMessage = buildSkillUserMessage(
                normalized, rawMessage, messageType, parsedAttachments, context.readFileAvailable());
        List<ToolCallback> toolCallbacks = context.toolCallbacks() == null ? List.of() : context.toolCallbacks();
        if (artifactRequired) {
            log.info(
                    "产物型技能运行时请求已组装：sessionId={}, skillId={}, runtimeSkillName={}, toolCount={}, hasWriteArtifactTool={}, fileCount={}",
                    normalized.sessionId(),
                    context.skillId(),
                    context.runtimeSkillName(),
                    toolCallbacks.size(),
                    hasToolCallback(toolCallbacks, "write_artifact"),
                    normalized.fileIds() == null ? 0 : normalized.fileIds().size());
        }
        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized.scopeType(),
                normalized.sessionId(),
                context.skillId(),
                context.displayName(),
                message,
                userMessage,
                StringUtils.hasText(messageType) ? messageType : "normal",
                context.runtimeSkillName(),
                JSON.toJSONString(params),
                fileListJson,
                toolCallbacks,
                appendArtifactRequiredPrompt(context.systemPrompt(), artifactRequired),
                normalized.systemPromptAppend(),
                context.runtimeSkillName(),
                List.of(),
                List.of(),
                normalized.chatModelId(),
                normalized.isPersonalAgentRequest(),
                normalized.resolvePersonalAgentMode());
    }

    public ChatRuntimePreparedRequest buildExpertPackage(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized, AgentDetailDto expertPackage) {
        List<String> fileIds = normalized.fileIds() == null ? List.of() : normalized.fileIds();
        String message = resolveMessage(normalized.message(), fileIds, null);
        String fileListJson = chatFileService.buildFileListJson(fileIds);
        List<FileParseResult> parsedFiles = resolveParsedFiles(fileIds, normalized);
        boolean artifactRequired = isOptionEnabled(normalized.options(), "artifactRequired");
        List<RuntimeSkillDescriptor> availableSkills = resolveExpertPackageSkills(expertPackage);
        Map<String, ToolCallback> toolCallbacks = resolveExpertPackageToolCallbacks(expertPackage);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "expert-skill-package");
        params.put("expertPackageId", expertPackage.getId());
        params.put("expertPackageCode", expertPackage.getAgentCode());
        params.put("expertPackageName", expertPackage.getAgentName());
        params.put("fileIds", fileIds);
        params.put("availableSkills", availableSkills);
        params.put("toolCount", toolCallbacks.size());
        params.put("executionModeHint", EXECUTION_MODE_HINT_TOOL);
        params.put("executionWorld", EXECUTION_WORLD_TOOL);
        if (artifactRequired) {
            params.put("artifactRequired", Boolean.TRUE);
        }
        if (!parsedFiles.isEmpty()) {
            params.put("parsedAttachments", fileParseService.toSerializablePayload(parsedFiles));
        }
        if (normalized.eventPayload() != null) {
            params.put("eventPayload", normalized.eventPayload());
        }

        String systemPrompt = appendArtifactRequiredPrompt(
                buildExpertPackageSystemPrompt(expertPackage, availableSkills), artifactRequired);

        log.debug(
                "[运行时画像] 专家技能包请求已组装：sessionId={}, packageId={}, skillCount={}, toolCount={}",
                normalized.sessionId(),
                expertPackage.getId(),
                availableSkills.size(),
                toolCallbacks.size());

        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized.scopeType(),
                normalized.sessionId(),
                expertPackage.getId(),
                expertPackage.getAgentName(),
                message,
                chatFileService.buildUserMessage(
                                message.equals(normalizeMessage(normalized.message())) ? message : "",
                                fileIds,
                                false)
                        + fileParseService.buildPromptContext(parsedFiles),
                resolveRuntimeMessageType(normalized.messageType()),
                sessionType.name(),
                JSON.toJSONString(params),
                fileListJson,
                List.copyOf(toolCallbacks.values()),
                systemPrompt,
                normalized.systemPromptAppend(),
                null,
                availableSkills,
                List.of(),
                normalized.chatModelId(),
                normalized.isPersonalAgentRequest(),
                normalized.resolvePersonalAgentMode());
    }

    public ChatRuntimePreparedRequest buildDataset(
            ConversationSessionType sessionType,
            LingzRuntimeRequest normalized,
            IntegrationDatasetService.DatasetDetail dataset) {
        String rawMessage = normalizeMessage(normalized.message());
        String message = StringUtils.hasText(rawMessage) ? rawMessage : "请分析当前数据集并回答问题";
        String sqlDialect = resolveDatasetSqlDialect(dataset);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("datasetId", dataset.id());
        params.put("datasetCode", dataset.datasetCode());
        params.put("datasetName", dataset.name());
        params.put("sourceKind", dataset.sourceKind());
        params.put("sqlDialect", sqlDialect);
        if (normalized.eventPayload() != null) {
            params.put("eventPayload", normalized.eventPayload());
        }
        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized.scopeType(),
                normalized.sessionId(),
                dataset.id(),
                dataset.name(),
                message,
                rawMessage,
                resolveRuntimeMessageType(normalized.messageType()),
                ConversationSessionType.DATASET_CHAT.name(),
                JSON.toJSONString(params),
                null,
                integrationDatasetAgentToolRegistry.buildCallbacks(dataset.datasetCode()),
                promptEngineeringService.buildDatasetSystemPrompt(dataset, sqlDialect),
                normalized.systemPromptAppend(),
                null,
                List.of(),
                List.of(),
                normalized.chatModelId(),
                normalized.isPersonalAgentRequest(),
                normalized.resolvePersonalAgentMode());
    }

    public ChatRuntimePreparedRequest buildSkillStudio(
            ConversationSessionType sessionType,
            LingzRuntimeRequest normalized,
            String projectName,
            String creatorSystemPrompt,
            List<ToolCallback> toolCallbacks) {
        String rawMessage = normalizeMessage(normalized.message());
        String message = StringUtils.hasText(rawMessage) ? rawMessage : "请继续处理当前技能工坊项目";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "skillstudio");
        params.put("projectId", normalized.scopeId());
        params.put("runtimeSkillName", normalized.runtimeSkillName());
        if (normalized.eventPayload() != null) {
            params.put("eventPayload", normalized.eventPayload());
        }
        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized.scopeType(),
                normalized.sessionId(),
                normalized.scopeId(),
                projectName,
                message,
                buildSkillStudioUserMessage(rawMessage, normalized.eventPayload()),
                resolveRuntimeMessageType(normalized.messageType()),
                "SKILL_STUDIO_CREATOR",
                JSON.toJSONString(params),
                null,
                toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks),
                creatorSystemPrompt,
                normalized.systemPromptAppend(),
                normalized.runtimeSkillName(),
                List.of(),
                List.of(),
                normalized.chatModelId(),
                normalized.isPersonalAgentRequest(),
                normalized.resolvePersonalAgentMode());
    }

    private String normalizeMessageType(String messageType) {
        return StringUtils.hasText(messageType) ? messageType.trim() : "";
    }

    private boolean isOptionEnabled(Map<String, Object> options, String key) {
        if (options == null || options.isEmpty() || !StringUtils.hasText(key)) {
            return false;
        }
        Object value = options.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String appendArtifactRequiredPrompt(String systemPrompt, boolean artifactRequired) {
        if (!artifactRequired) {
            return systemPrompt;
        }
        String artifactPrompt =
                """
                ## 渠道产物交付约束
                当前用户明确要求生成、导出或下载文件，因此必须产出真实可下载文件。
                - 必须调用 `write_artifact` 发布最终文件；如果需要先处理附件或生成中间文件，可先使用 `parse_file`、`file_write`、`run_python` 等工具。
                - 只有 `write_artifact` 成功返回后，才能告诉用户文件已生成。
                - 不要把大模型回复正文、上传文件摘要或下载 URL 当作最终文件；没有成功的产物工具结果时，应如实说明文件未生成。
                - 回复正文不要输出裸下载 URL，下载入口由渠道层根据产物结果追加。
                """;
        if (!StringUtils.hasText(systemPrompt)) {
            return artifactPrompt.trim();
        }
        return systemPrompt.trim() + "\n\n" + artifactPrompt.trim();
    }

    private boolean hasToolCallback(Map<String, ToolCallback> toolCallbacks, String toolName) {
        return toolCallbacks != null && StringUtils.hasText(toolName) && toolCallbacks.containsKey(toolName);
    }

    private String appendToolCatalogPrompt(String systemPrompt, List<ToolCallback> toolCallbacks) {
        String toolCatalogPrompt = buildInjectedToolCatalogPrompt(toolCallbacks);
        if (!StringUtils.hasText(toolCatalogPrompt)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return toolCatalogPrompt;
        }
        return systemPrompt.trim() + "\n\n" + toolCatalogPrompt;
    }

    private String buildInjectedToolCatalogPrompt(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 当前可用工具\n\n");
        builder.append("以下工具已经按当前用户权限注入本轮 ToolCallbacks，可在需要时直接调用；未列出的资源型工具表示当前轮不可用或未授权。\n");
        int count = 0;
        for (ToolCallback callback : toolCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String toolName = callback.getToolDefinition().name();
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            builder.append("\n- `").append(toolName.trim()).append("`");
            String description = normalizeToolDescription(callback.getToolDefinition().description());
            if (StringUtils.hasText(description)) {
                builder.append("：").append(description);
            }
            count++;
        }
        return count == 0 ? "" : builder.toString().trim();
    }

    private String normalizeToolDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }
        String normalized = description.replaceAll("\\s+", " ").trim();
        int maxLength = 160;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private boolean hasToolCallback(List<ToolCallback> toolCallbacks, String toolName) {
        if (toolCallbacks == null || toolCallbacks.isEmpty() || !StringUtils.hasText(toolName)) {
            return false;
        }
        for (ToolCallback callback : toolCallbacks) {
            String resolvedName = callback == null || callback.getToolDefinition() == null
                    ? null
                    : callback.getToolDefinition().name();
            if (toolName.equals(resolvedName)) {
                return true;
            }
        }
        return false;
    }

    private String buildSkillStudioUserMessage(String rawMessage, Map<String, Object> eventPayload) {
        if (eventPayload == null || eventPayload.isEmpty()) {
            return rawMessage;
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(rawMessage)) {
            builder.append(rawMessage.trim()).append("\n\n");
        }
        builder.append("技能工坊事件上下文(JSON)：\n").append(JSON.toJSONString(eventPayload));
        return builder.toString();
    }

    private String resolveSkillMessage(LingzRuntimeRequest request, String runtimeSkillName, String messageType) {
        String rawMessage = normalizeMessage(request == null ? null : request.message());
        if (StringUtils.hasText(rawMessage)) {
            return rawMessage;
        }
        if ("event".equals(messageType) && request != null && request.eventPayload() != null) {
            return JSON.toJSONString(request.eventPayload());
        }
        return resolveMessage(
                request == null ? null : request.message(),
                request == null ? null : request.fileIds(),
                runtimeSkillName);
    }

    private String buildSkillUserMessage(
            LingzRuntimeRequest request,
            String rawMessage,
            String messageType,
            List<FileParseResult> parsedAttachments,
            boolean readFileAvailable) {
        if ("event".equals(messageType)) {
            return buildSkillEventUserMessage(rawMessage, request == null ? null : request.eventPayload());
        }
        return chatFileService.buildUserMessage(
                        rawMessage, request == null ? null : request.fileIds(), readFileAvailable)
                + fileParseService.buildPromptContext(parsedAttachments);
    }

    private String buildSkillEventUserMessage(String rawMessage, Map<String, Object> eventPayload) {
        String normalizedMessage = normalizeMessage(rawMessage);
        if (eventPayload == null || eventPayload.isEmpty()) {
            return normalizedMessage;
        }
        String actionCode = normalizeEventText(eventPayload.get("actionCode"));
        String targetToolName = normalizeEventText(eventPayload.get("targetToolName"));
        String title = normalizeEventText(eventPayload.get("title"));
        String templateCode = normalizeEventText(eventPayload.get("templateCode"));
        String componentCode = normalizeEventText(eventPayload.get("componentCode"));
        String dataJson = JSON.toJSONString(eventPayload.get("data"));
        String payloadJson = JSON.toJSONString(eventPayload);

        StringBuilder builder = new StringBuilder();
        builder.append("收到一个前端卡片事件，请严格依据下面的结构化事件继续处理。");
        if (StringUtils.hasText(normalizedMessage)) {
            builder.append("\n\n事件消息：\n").append(normalizedMessage);
        }
        builder.append("\n\n事件元信息：");
        if (StringUtils.hasText(title)) {
            builder.append("\n- 卡片标题：").append(title);
        }
        if (StringUtils.hasText(actionCode)) {
            builder.append("\n- 动作：").append(actionCode);
        }
        if (StringUtils.hasText(targetToolName)) {
            builder.append("\n- 目标工具：").append(targetToolName);
        }
        if (StringUtils.hasText(templateCode)) {
            builder.append("\n- 模板编码：").append(templateCode);
        }
        if (StringUtils.hasText(componentCode)) {
            builder.append("\n- 组件编码：").append(componentCode);
        }
        if (StringUtils.hasText(dataJson)) {
            builder.append("\n\n最终确认数据(JSON)：\n").append(dataJson);
        }
        builder.append("\n\n完整 eventPayload(JSON)：\n").append(payloadJson);
        builder.append("\n\n处理要求：");
        builder.append("\n- 不要忽略 eventPayload.data 中的最终确认数据。");
        builder.append("\n- 如果 actionCode=confirm，表示用户已经确认当前卡片。");
        builder.append("\n- 如果 targetToolName 非空，且当前流程需要提交当前卡片对应的数据，优先调用这个目标工具。");
        builder.append("\n- 只有在目标工具实际调用成功后，才能宣称已提交、已录入或已完成。");
        return builder.toString();
    }

    private String normalizeEventText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }

    private List<FileParseResult> resolveParsedFiles(List<String> fileIds, LingzRuntimeRequest request) {
        if (request == null || fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        FileParseMode parseMode = resolveAttachmentParseMode(request.options());
        if (parseMode == null) {
            return List.of();
        }
        return fileParseService.parseUploads(fileIds, parseMode);
    }

    private List<String> resolveEffectiveFileIds(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized, Long userId) {
        List<String> currentFileIds =
                normalized == null || normalized.fileIds() == null ? List.of() : normalized.fileIds();
        if (!currentFileIds.isEmpty()) {
            return List.copyOf(currentFileIds);
        }
        if (!shouldInheritLatestAttachments(sessionType, normalized, userId)) {
            return List.of();
        }
        var latestUserMessage = conversationHistoryService.findLatestUserMessage(
                userId,
                sessionType,
                normalized == null ? null : normalized.sessionId(),
                normalized == null ? null : normalized.scopeId());
        if (latestUserMessage == null || !StringUtils.hasText(latestUserMessage.getAttachmentsJson())) {
            return List.of();
        }
        List<ChatFileService.UploadedFile> inheritedFiles =
                chatFileService.resolveFilesFromFileListJson(latestUserMessage.getAttachmentsJson());
        if (inheritedFiles.isEmpty()) {
            return List.of();
        }
        List<String> inheritedFileIds = inheritedFiles.stream()
                .map(ChatFileService.UploadedFile::id)
                .filter(StringUtils::hasText)
                .toList();
        if (inheritedFileIds.isEmpty()) {
            return List.of();
        }
        log.debug(
                "[运行时画像] 继承上一轮附件上下文：会话ID={}, 原消息ID={}, fileIds={}",
                normalized == null ? null : normalized.sessionId(),
                latestUserMessage.getId(),
                inheritedFileIds);
        return List.copyOf(inheritedFileIds);
    }

    private boolean shouldInheritLatestAttachments(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized, Long userId) {
        if (normalized == null || userId == null || userId <= 0) {
            return false;
        }
        if (normalized.fileIds() != null && !normalized.fileIds().isEmpty()) {
            return false;
        }
        if (!StringUtils.hasText(normalized.sessionId())) {
            return false;
        }
        if (!supportsAttachmentInheritance(sessionType)) {
            return false;
        }
        return isAttachmentFollowUpMessage(normalized.message());
    }

    private static boolean matchesAnyText(String source, String... candidates) {
        if (!StringUtils.hasText(source) || candidates == null || candidates.length == 0) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)
                    && source.contains(candidate.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static boolean supportsAttachmentInheritance(ConversationSessionType sessionType) {
        return sessionType == ConversationSessionType.GENERAL_CHAT
                || sessionType == ConversationSessionType.GENERAL_CHAT_V2
                || sessionType == ConversationSessionType.CHANNEL_CHAT;
    }

    static boolean isAttachmentFollowUpMessage(String message) {
        String normalized = StringUtils.hasText(message) ? message.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return matchesAnyText(
                lower,
                "这个",
                "这个处理得不对",
                "里面",
                "继续",
                "重新处理",
                "重新分析",
                "重新来",
                "重新筛选",
                "重新过滤",
                "重筛",
                "嵌套",
                "解压",
                "提取",
                "抽取",
                "zip",
                "pdf",
                "发票",
                "文件",
                "附件",
                "文档",
                "表格",
                "只需要",
                "最终我只需要");
    }

    private FileParseMode resolveAttachmentParseMode(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        Object parseAttachments = options.get("parseAttachments");
        if (parseAttachments instanceof Boolean boolValue) {
            return boolValue ? FileParseMode.STRUCTURED : null;
        }
        if (parseAttachments instanceof String textValue) {
            String normalized = textValue.trim();
            if (normalized.isEmpty() || "false".equalsIgnoreCase(normalized)) {
                return null;
            }
            if ("true".equalsIgnoreCase(normalized)) {
                return FileParseMode.STRUCTURED;
            }
            return FileParseMode.fromValue(normalized);
        }
        Object parseFile = options.get("parseFile");
        if (parseFile instanceof Map<?, ?> parseFileMap) {
            Object enabled = parseFileMap.get("enabled");
            if (enabled instanceof Boolean enabledValue && !enabledValue) {
                return null;
            }
            Object mode = parseFileMap.get("mode");
            if (mode != null) {
                return FileParseMode.fromValue(String.valueOf(mode));
            }
            return FileParseMode.STRUCTURED;
        }
        Object attachmentParse = options.get("attachmentParse");
        if (attachmentParse instanceof Map<?, ?> attachmentParseMap) {
            Object enabled = attachmentParseMap.get("enabled");
            if (enabled instanceof Boolean enabledValue && !enabledValue) {
                return null;
            }
            Object mode = attachmentParseMap.get("mode");
            if (mode != null) {
                return FileParseMode.fromValue(String.valueOf(mode));
            }
            return FileParseMode.STRUCTURED;
        }
        return null;
    }

    private String resolveMessage(String message, List<String> fileIds, String runtimeSkillName) {
        String normalized = normalizeMessage(message);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        List<ChatFileService.UploadedFile> files = chatFileService.resolveFiles(fileIds);
        if (files.isEmpty()) {
            return "请基于我上传的附件继续分析";
        }
        String joinedNames = files.stream()
                .map(ChatFileService.UploadedFile::name)
                .filter(StringUtils::hasText)
                .limit(3)
                .reduce((left, right) -> left + "、" + right)
                .orElse("附件");
        if ("form-app-assistant".equals(StringUtils.trimWhitespace(runtimeSkillName))) {
            return "请基于我上传的表单参考附件分析语义，并推荐字段与布局：" + joinedNames;
        }
        return "请基于我上传的附件继续分析：" + joinedNames;
    }

    private String normalizeMessage(String message) {
        return StringUtils.hasText(message) ? message.trim() : "";
    }

    private String resolveDatasetSqlDialect(IntegrationDatasetService.DatasetDetail dataset) {
        if (dataset == null) {
            return "MYSQL";
        }
        String sourceKind =
                StringUtils.hasText(dataset.sourceKind()) ? dataset.sourceKind().trim() : "";
        if ("LOWCODE_APP".equalsIgnoreCase(sourceKind)) {
            return "MYSQL";
        }
        if (!"AI_SOURCE".equalsIgnoreCase(sourceKind) || dataset.aiDataSourceId() == null) {
            return "MYSQL";
        }
        IntegrationDataSource dataSource = integrationDataSourceMapper.selectById(dataset.aiDataSourceId());
        if (dataSource == null || !StringUtils.hasText(dataSource.getDbType())) {
            return "MYSQL";
        }
        return dataSource.getDbType().trim().toUpperCase();
    }

    private String buildGeneralSystemPrompt(
            Long userId,
            List<RuntimeSkillDescriptor> availableSkills,
            String executionModeHint,
            ToolToCodeEscalationDecision toolToCodeDecision,
            RuntimeSkillDescriptor mentionedSkill) {
        String basePrompt = buildUserAgentSystemPrompt(userId);
        String executionPolicyPrompt =
                buildGeneralExecutionPolicyPrompt(executionModeHint, toolToCodeDecision, mentionedSkill);
        String skillCatalogPrompt = buildAvailableSkillsPrompt(availableSkills);
        if (!StringUtils.hasText(basePrompt)) {
            if (!StringUtils.hasText(executionPolicyPrompt)) {
                return skillCatalogPrompt;
            }
            if (!StringUtils.hasText(skillCatalogPrompt)) {
                return executionPolicyPrompt;
            }
            return executionPolicyPrompt.trim() + "\n\n" + skillCatalogPrompt.trim();
        }
        if (!StringUtils.hasText(skillCatalogPrompt) && !StringUtils.hasText(executionPolicyPrompt)) {
            return basePrompt;
        }
        StringBuilder builder = new StringBuilder(basePrompt.trim());
        if (StringUtils.hasText(executionPolicyPrompt)) {
            builder.append("\n\n").append(executionPolicyPrompt.trim());
        }
        if (StringUtils.hasText(skillCatalogPrompt)) {
            builder.append("\n\n").append(skillCatalogPrompt.trim());
        }
        return builder.toString();
    }

    private String buildGeneralExecutionPolicyPrompt(
            String executionModeHint,
            ToolToCodeEscalationDecision toolToCodeDecision,
            RuntimeSkillDescriptor mentionedSkill) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 能力选择策略\n\n");
        builder.append("先判断当前问题是否可以直接回答。\n");
        builder.append("如果可以，直接回答。\n");
        builder.append("如果不能，按需选择以下能力：Skill、Tool、Knowledge Base、Dataset、Runtime File、Code。\n");
        builder.append("优先使用成本最低且最合适的能力，不要为了调用能力而调用能力。\n");
        if (EXECUTION_MODE_HINT_DIRECT.equalsIgnoreCase(executionModeHint)) {
            builder.append("当前请求更接近直接回答，可优先自然回复；若确实需要外部能力，再自行切换到工具执行。\n");
        } else {
            builder.append("当前请求更接近执行型请求，应优先从现有能力中选择合适工具或技能完成任务。\n");
        }
        if (mentionedSkill != null) {
            builder.append("\n用户本轮显式指定优先使用技能：`")
                    .append(mentionedSkill.runtimeSkillName())
                    .append("`");
            if (StringUtils.hasText(mentionedSkill.displayName())
                    && !mentionedSkill.displayName().trim().equals(mentionedSkill.runtimeSkillName().trim())) {
                builder.append("（").append(mentionedSkill.displayName().trim()).append("）");
            }
            builder.append("。这只表示本轮能力选择应优先考虑该 Skill，不表示 Skill 说明已经自动读取。\n");
            builder.append("本轮若继续按该 Skill 执行，第一步必须调用 `loadSkillContent(\"")
                    .append(mentionedSkill.runtimeSkillName())
                    .append("\")`。\n");
        }

        builder.append("\n## Skill 使用规则\n\n");
        builder.append("当当前问题命中某个 Skill 领域时：\n");
        builder.append("1. 调用 `loadSkillContent(skillName)`\n");
        builder.append("2. 按 Skill 说明执行\n");
        builder.append("3. 使用 Skill 暴露出的能力完成任务\n\n");
        builder.append("未命中 Skill 时，保持 General 模式。\n");
        builder.append("已加载 Skill 仅代表历史状态，每轮都应基于当前用户问题重新判断是否继续使用。\n");
        builder.append("一个请求包含多个子任务时，按子任务分别判断是否命中 Skill。\n");

        builder.append("\n## Tool 使用规则\n\n");
        builder.append("需要工具时直接调用工具，不要只描述计划，例如“我将查询”“我准备获取”“接下来会调用”。\n");
        builder.append("只有在工具执行完成、阶段切换或发生错误时，再向用户说明进展。\n");
        builder.append("运行时文件/目录/脚本能力统一使用独立工具：`file_read`、`file_write`、`list_dir`、`stat`、`run_python`、`write_artifact`。\n");
        builder.append("不要生成 `runtime_tool(...)` 包装调用。\n");
        builder.append("对于 `.xlsx/.xls/.csv/.docx/.pdf` 这类附件或 runtime 产物，不要用 `file_read` 直接读取二进制内容；优先使用文件解析能力或受控脚本处理。\n");

        builder.append("\n## Code 执行规则\n\n");
        builder.append("Code 是最后手段。\n");
        builder.append("只有当 Skill、Tool、Knowledge Base、Dataset 或 Runtime File 无法完成时，才进入 Code。\n");
        builder.append("不要执行用户提供的脚本；应重新生成最小、可审计的脚本完成任务。\n");
        builder.append("如果已激活 Skill 明确指定固定脚本或 `run_python` 流程，按 Skill 说明执行，这不属于额外 Code 升级。\n");
        if (toolToCodeDecision != null && toolToCodeDecision.codeEscalationCandidate()) {
            builder.append("\n当前请求具备 Code 升级候选特征：如果文件解析只返回空壳摘要、没有可用正文/表格内容，或无法解析目标文件，可以进入 Code。\n");
            builder.append("进入 Code 后，围绕一个最小可执行脚本推进：明确输入、输出和处理目标，执行一次，依据错误再修正。\n");
            builder.append("脚本输入输出路径通过参数传递，不要在脚本里写死 `/uploads`、`/outputs`、`/temp` 等逻辑路径。\n");
            builder.append("\n").append(pythonRuntimeEnvDescriptorService.buildGeneralCodePrompt());
        }
        return builder.toString();
    }

    private String buildExpertPackageSystemPrompt(
            AgentDetailDto expertPackage, List<RuntimeSkillDescriptor> availableSkills) {
        StringBuilder prompt = new StringBuilder();
        appendPromptSeparator(prompt);
        prompt.append("## 专家技能包身份\n\n");
        prompt.append("你正在以一个专家技能包的能力边界与用户协作。\n");
        prompt.append("专家技能包定义了你的专家身份、可用技能和可用工具。不要主动提及内部 Runtime、Tool Routing 或实现细节，除非用户明确询问。\n\n");
        if (expertPackage != null && StringUtils.hasText(expertPackage.getAgentName())) {
            prompt.append("- 专家技能包名称：").append(expertPackage.getAgentName().trim()).append("\n");
        }
        if (expertPackage != null && StringUtils.hasText(expertPackage.getDescription())) {
            prompt.append("- 专家技能包描述：").append(expertPackage.getDescription().trim()).append("\n");
        }
        if (expertPackage != null && StringUtils.hasText(expertPackage.getOpeningMessage())) {
            prompt.append("- 开场引导：").append(expertPackage.getOpeningMessage().trim()).append("\n");
        }
        if (expertPackage != null && StringUtils.hasText(expertPackage.getSoulTemplate())) {
            prompt.append("\n").append(expertPackage.getSoulTemplate().trim()).append("\n");
        }
        if (expertPackage != null && StringUtils.hasText(expertPackage.getProfileTemplate())) {
            appendPromptSeparator(prompt);
            prompt.append("## 场景档案\n\n");
            prompt.append(expertPackage.getProfileTemplate().trim()).append("\n");
        }

        appendPromptSeparator(prompt);
        prompt.append("## 专家能力边界\n\n");
        prompt.append("当前对话只能使用该专家技能包绑定的技能和工具。需要外部能力时，优先从包内技能或包内工具中选择。\n");
        prompt.append("如果用户请求超出包内能力边界，应说明当前专家技能包无法直接完成，并给出可行替代建议。\n");
        prompt.append("需要工具时直接调用工具，不要只描述计划。\n");
        String skillCatalogPrompt = buildAvailableSkillsPrompt(availableSkills);
        if (StringUtils.hasText(skillCatalogPrompt)) {
            prompt.append("\n\n").append(skillCatalogPrompt.trim());
        }
        return prompt.toString().trim();
    }

    private List<RuntimeSkillDescriptor> resolveExpertPackageSkills(AgentDetailDto expertPackage) {
        if (expertPackage == null || expertPackage.getSkills() == null || expertPackage.getSkills().isEmpty()) {
            return List.of();
        }
        return expertPackage.getSkills().stream()
                .filter(skill -> skill != null && StringUtils.hasText(skill.getRuntimeSkillName()))
                .map(skill -> new RuntimeSkillDescriptor(
                        skill.getId(),
                        skill.getRuntimeSkillName().trim(),
                        skill.getDisplayName(),
                        skill.getDescription()))
                .toList();
    }

    private Map<String, ToolCallback> resolveExpertPackageToolCallbacks(AgentDetailDto expertPackage) {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolCallback callback : globalToolRegistry.getSystemRuntimeToolCallbacks()) {
            String toolName = callback == null || callback.getToolDefinition() == null
                    ? null
                    : callback.getToolDefinition().name();
            if (StringUtils.hasText(toolName)) {
                callbacks.putIfAbsent(toolName, callback);
            }
        }
        if (expertPackage == null || expertPackage.getTools() == null || expertPackage.getTools().isEmpty()) {
            return callbacks;
        }
        for (ToolSimpleDto tool : expertPackage.getTools()) {
            if (tool == null || !StringUtils.hasText(tool.getToolName())) {
                continue;
            }
            ToolCallback callback = toolLibraryCallbackResolver.findByName(tool.getToolName());
            String resolvedToolName = callback == null || callback.getToolDefinition() == null
                    ? null
                    : callback.getToolDefinition().name();
            if (!StringUtils.hasText(resolvedToolName)) {
                log.warn(
                        "专家技能包绑定工具无法解析为 ToolCallback：packageId={}, toolName={}",
                        expertPackage.getId(),
                        tool.getToolName());
                continue;
            }
            callbacks.putIfAbsent(resolvedToolName, callback);
        }
        return callbacks;
    }

    private String buildUserAgentSystemPrompt(Long userId) {
        List<UserAgentFile> files = userAgentConfigService.getUserAgentFiles(userId);
        if (files == null) {
            files = List.of();
        }
        AgentDetailDto agent = userAgentConfigService.getUserAgentTemplate(userId);
        String soulContent = resolveUserAgentFileContent(files, FILENAME_SOUL);
        String profileContent = resolveUserAgentFileContent(files, FILENAME_PROFILE);
        StringBuilder prompt = new StringBuilder();

        if (agent != null || StringUtils.hasText(soulContent)) {
            appendAgentSoulPrompt(prompt, agent, soulContent);
        }

        if (StringUtils.hasText(profileContent)) {
            appendUserProfilePrompt(prompt, profileContent);
        }

        return prompt.length() > 0 ? prompt.toString().trim() : null;
    }

    private String resolveUserAgentFileContent(List<UserAgentFile> files, String filename) {
        if (files == null || files.isEmpty() || !StringUtils.hasText(filename)) {
            return null;
        }
        for (UserAgentFile file : files) {
            if (file == null || file.getEnabled() == null || file.getEnabled() != 1) {
                continue;
            }
            if (!filename.equals(file.getFilename()) || !StringUtils.hasText(file.getContent())) {
                continue;
            }
            return file.getContent().trim();
        }
        return null;
    }

    private void appendAgentSoulPrompt(StringBuilder prompt, AgentDetailDto agent, String soulContent) {
        appendPromptSeparator(prompt);
        prompt.append("## Agent 身份\n\n");
        prompt.append("以下内容定义你的身份、表达方式与协作风格。\n");
        prompt.append("Lingz Agent 是运行平台，不是你的身份。\n");
        prompt.append("你的身份优先来自 Agent 配置与灵魂设定。\n");
        prompt.append("不要主动提及 Lingz Agent、Runtime、Skill、Workflow 或 Tool Routing，除非用户明确询问。\n\n");
        if (agent != null && StringUtils.hasText(agent.getDisplayName())) {
            prompt.append("- Agent 名称：").append(agent.getDisplayName().trim()).append("\n");
        }
        if (agent != null && StringUtils.hasText(agent.getAgentName())
                && !agent.getAgentName().trim().equals(StringUtils.trimWhitespace(agent.getDisplayName()))) {
            prompt.append("- 模板名称：").append(agent.getAgentName().trim()).append("\n");
        }
        if (agent != null && StringUtils.hasText(agent.getDescription())) {
            prompt.append("- Agent 描述：").append(agent.getDescription().trim()).append("\n");
        }
        if (StringUtils.hasText(soulContent)) {
            if (agent != null
                    && (StringUtils.hasText(agent.getDisplayName())
                            || StringUtils.hasText(agent.getAgentName())
                            || StringUtils.hasText(agent.getDescription()))) {
                prompt.append("\n");
            }
            prompt.append(soulContent.trim()).append("\n");
        }
    }

    private void appendUserProfilePrompt(StringBuilder prompt, String profileContent) {
        Map<String, String> sections = parseMarkdownSections(profileContent);
        String identityContent = sections.remove("身份");
        String responsibilityContent = sections.remove("岗位职责");

        appendPromptSeparator(prompt);
        prompt.append("## 当前用户档案\n\n");
        prompt.append("以下信息描述的是“正在与你对话的用户”，不是你的身份。\n");
        prompt.append("这些信息用于帮助你理解用户的工作场景、关注重点和表达偏好，不应冒充用户本人。\n\n");
        if (StringUtils.hasText(identityContent)) {
            prompt.append(identityContent.trim()).append("\n");
        } else if (StringUtils.hasText(profileContent)) {
            prompt.append(profileContent.trim()).append("\n");
            return;
        }

        if (StringUtils.hasText(responsibilityContent)) {
            appendPromptSeparator(prompt);
            prompt.append("## 当前用户职责与偏好\n\n");
            prompt.append("以下信息描述的是当前用户的职责、业务关注点和偏好，不是你的身份。\n");
            prompt.append("回答时可以据此调整解释粒度、案例选择、表达重点和建议角度。\n\n");
            prompt.append(responsibilityContent.trim()).append("\n");
        }

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            appendPromptSeparator(prompt);
            prompt.append("## 当前用户").append(entry.getKey().trim()).append("\n\n");
            prompt.append("以下信息仍属于当前用户上下文，不是你的身份。\n\n");
            prompt.append(entry.getValue().trim()).append("\n");
        }
    }

    private void appendPromptSeparator(StringBuilder prompt) {
        if (prompt.length() > 0) {
            prompt.append("\n\n");
        }
    }

    private Map<String, String> parseMarkdownSections(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (!StringUtils.hasText(content)) {
            return sections;
        }
        String normalizedContent = content.replace("\r\n", "\n");
        String currentSection = "";
        StringBuilder currentBody = new StringBuilder();
        for (String line : normalizedContent.split("\n", -1)) {
            if (line.startsWith("## ")) {
                sections.put(currentSection, currentBody.toString().trim());
                currentSection = line.substring(3).trim();
                currentBody.setLength(0);
                continue;
            }
            if (currentBody.length() > 0) {
                currentBody.append("\n");
            }
            currentBody.append(line);
        }
        sections.put(currentSection, currentBody.toString().trim());
        return sections;
    }

    private String buildAvailableSkillsPrompt(List<RuntimeSkillDescriptor> availableSkills) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(GENERAL_SKILL_CATALOG_PROMPT);
        for (RuntimeSkillDescriptor descriptor : availableSkills) {
            if (descriptor == null || !StringUtils.hasText(descriptor.runtimeSkillName())) {
                continue;
            }
            builder.append("\n- `").append(descriptor.runtimeSkillName().trim()).append("`");
            if (StringUtils.hasText(descriptor.displayName())
                    && !descriptor
                            .displayName()
                            .trim()
                            .equals(descriptor.runtimeSkillName().trim())) {
                builder.append("（").append(descriptor.displayName().trim()).append("）");
            }
            if (StringUtils.hasText(descriptor.description())) {
                builder.append("：").append(descriptor.description().trim());
            }
        }
        return builder.toString().trim();
    }

    private List<RuntimeSkillDescriptor> resolveAvailableSkills(Long userId) {
        List<SkillSimpleDto> skills = userAgentConfigService.getUserSkills(userId);
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> skill != null && StringUtils.hasText(skill.getRuntimeSkillName()))
                .map(skill -> new RuntimeSkillDescriptor(
                        skill.getId(),
                        skill.getRuntimeSkillName().trim(),
                        skill.getDisplayName(),
                        skill.getDescription()))
                .toList();
    }

    private RuntimeSkillDescriptor resolveMentionedSkill(
            List<RuntimeSkillDescriptor> availableSkills, Long mentionedSkillId) {
        if (mentionedSkillId == null || availableSkills == null || availableSkills.isEmpty()) {
            return null;
        }
        return availableSkills.stream()
                .filter(skill -> skill != null && mentionedSkillId.equals(skill.skillId()))
                .findFirst()
                .orElse(null);
    }

    private String resolveExecutionModeHint(
            boolean directFastMatched,
            RuntimeSkillDescriptor mentionedSkill,
            List<FileParseResult> parsedFiles,
            String message) {
        if (mentionedSkill != null) {
            return EXECUTION_MODE_HINT_TOOL;
        }
        if (parsedFiles != null && !parsedFiles.isEmpty()) {
            return EXECUTION_MODE_HINT_TOOL;
        }
        if (directFastMatched) {
            return EXECUTION_MODE_HINT_DIRECT;
        }
        if (!StringUtils.hasText(message)) {
            return EXECUTION_MODE_HINT_DIRECT;
        }
        return EXECUTION_MODE_HINT_TOOL;
    }

    private boolean matchesDirectFast(
            String message,
            List<String> fileIds,
            List<FileParseResult> parsedFiles,
            RuntimeSkillDescriptor mentionedSkill) {
        if (mentionedSkill != null) {
            return false;
        }
        if (fileIds != null && !fileIds.isEmpty()) {
            return false;
        }
        if (parsedFiles != null && !parsedFiles.isEmpty()) {
            return false;
        }
        String normalized = normalizeMessage(message);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        String compact = normalizeDirectFastToken(normalized);
        if (compact.length() > 12) {
            return false;
        }
        return DIRECT_FAST_TOKENS.contains(compact);
    }

    private String normalizeDirectFastToken(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message.replaceAll("\\s+", "")
                .replaceAll("[!！?？,，。\\.~～、:：;；'\"`·]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String resolveExecutionWorld(String executionModeHint, ToolToCodeEscalationDecision toolToCodeDecision) {
        if (EXECUTION_MODE_HINT_DIRECT.equalsIgnoreCase(executionModeHint)) {
            return EXECUTION_WORLD_DIRECT;
        }
        if (toolToCodeDecision != null && toolToCodeDecision.codeEscalationCandidate()) {
            return EXECUTION_WORLD_TOOL_WITH_CODE_FALLBACK;
        }
        return EXECUTION_WORLD_TOOL;
    }

    private Map<String, Object> toSerializableToolToCodeDecision(ToolToCodeEscalationDecision decision) {
        if (decision == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recommendedPath", decision.recommendedPath());
        payload.put("codeEscalationCandidate", decision.codeEscalationCandidate());
        payload.put("allowCodeExecution", decision.allowCodeExecution());
        payload.put("reason", decision.reason());
        payload.put("signals", decision.signals() == null ? List.of() : List.copyOf(decision.signals()));
        payload.put("blockers", decision.blockers() == null ? List.of() : List.copyOf(decision.blockers()));
        return payload;
    }

    private String firstNonBlank(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left.trim();
        }
        return StringUtils.hasText(right) ? right.trim() : "";
    }
}
