package lingzhou.agent.backend.capability.agentruntime.prompt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.app.ChatModelProperties;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.model.domain.ModelAdapterType;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TaskExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class PromptEngineeringService {

    private static final String SKILL_CORE_GUIDANCE_HEADER = "## 技能核心说明";
    private static final String SKILL_INSTRUCTION_HEADER = "## 技能使用说明";
    private static final String SKILL_RAW_CONTENT_MARKER =
            "Follow the skill instructions below. Use available tools only when needed.";
    private static final String VLLM_NO_THINK_PROMPT =
            """
            <nothink>
            你是快速回答 AI。直接输出最终答案，不要思考过程、不要 <think>、不要分析、不要解释、不要一步一步。保持简洁自然。
            """;
    private static final DateTimeFormatter CURRENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatModelProperties chatModelProperties;
    private final TaskExecutionCapabilityAdapter taskExecutionCapabilityAdapter;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final Clock clock;

    @Autowired
    public PromptEngineeringService(
            ChatModelProperties chatModelProperties,
            TaskExecutionCapabilityAdapter taskExecutionCapabilityAdapter,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this(chatModelProperties, taskExecutionCapabilityAdapter, requestScopedSkillRuntimeService, Clock.systemDefaultZone());
    }

    PromptEngineeringService(
            ChatModelProperties chatModelProperties,
            TaskExecutionCapabilityAdapter taskExecutionCapabilityAdapter,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            Clock clock) {
        this.chatModelProperties = chatModelProperties;
        this.taskExecutionCapabilityAdapter = taskExecutionCapabilityAdapter;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public RuntimePromptPack resolvePromptPack(
            AgentRuntimeExecutionContext executionContext,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig) {
        List<RuntimePromptBlock> blocks = new ArrayList<>();
        addBlock(
                blocks,
                RuntimePromptSourceType.MODEL,
                "model.chat.system",
                modelConfig == null ? null : modelConfig.systemPrompt());
        addBlock(blocks, RuntimePromptSourceType.MODEL, "model.vllm.no-think", resolveVllmNoThinkPrompt(modelConfig));
        addBlock(
                blocks,
                RuntimePromptSourceType.CONFIG,
                resolveBasePromptSource(executionContext),
                resolveBasePrompt(executionContext));
        addBlock(blocks, RuntimePromptSourceType.CONFIG, "runtime.current-time", resolveCurrentTimePrompt());
        ChatRuntimePreparedRequest prepared = executionContext == null ? null : executionContext.prepared();
        addBlock(
                blocks,
                RuntimePromptSourceType.SCENE,
                resolveScenePromptSource(prepared),
                prepared == null ? null : prepared.systemPrompt());
        addBlock(
                blocks,
                RuntimePromptSourceType.CONFIG,
                "skill.routing-state",
                resolveSkillRoutingStatePrompt(executionContext));
        // 注入已激活技能的内容
        addBlock(
                blocks,
                RuntimePromptSourceType.CONFIG,
                "skill.active-content",
                resolveActiveSkillContent(executionContext));
        addBlock(
                blocks,
                RuntimePromptSourceType.CONFIG,
                "personal-agent.execution-mode",
                resolvePersonalAgentExecutionModePrompt(executionContext));
        addBlock(
                blocks,
                RuntimePromptSourceType.REQUEST,
                "request.system-prompt-append",
                prepared == null ? null : prepared.systemPromptAppend());
        log.debug(
                "[提示词分流] PromptPack已组装：会话编码={}, 使用工具执行={}, 执行世界={}, 提示词块数={}",
                executionContext == null || executionContext.conversation() == null
                        ? null
                        : executionContext.conversation().sessionCode(),
                executionContext != null && executionContext.usesToolAwarePipeline(),
                resolveExecutionWorld(prepared),
                blocks.size());
        return RuntimePromptPack.of(blocks);
    }

    private String resolveCurrentTimePrompt() {
        ZoneId zoneId = clock.getZone();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        return """
                ## 当前运行状态

                - 当前日期：%s
                - 当前时间：%s
                - 当前时区：%s
                """.formatted(today, CURRENT_TIME_FORMATTER.format(now), zoneId.getId());
    }

    private String resolveVllmNoThinkPrompt(ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig) {
        if (modelConfig == null || !ModelAdapterType.VLLM.name().equalsIgnoreCase(modelConfig.adapterType())) {
            return null;
        }
        return VLLM_NO_THINK_PROMPT;
    }

    private String resolvePersonalAgentExecutionModePrompt(AgentRuntimeExecutionContext executionContext) {
        if (executionContext == null
                || !executionContext.hasActiveCapability(RuntimeCapabilitySlot.TASK_EXECUTION)
                || executionContext.prepared() == null
                || !executionContext.prepared().personalAgent()) {
            return null;
        }
        String prompt = taskExecutionCapabilityAdapter.buildExecutionModePrompt(executionContext.prepared());
        log.debug(
                "[提示词分流] 已选择执行提示词：会话编码={}, 个人模式={}, 提示词世界={}",
                executionContext.conversation() == null
                        ? null
                        : executionContext.conversation().sessionCode(),
                executionContext.prepared().personalAgentMode(),
                allowSkillInternals(executionContext.prepared()) ? "技能执行世界" : "通用执行世界");
        return prompt;
    }

    private String resolveSkillRoutingStatePrompt(AgentRuntimeExecutionContext executionContext) {
        if (executionContext == null || !executionContext.usesToolAwarePipeline()) {
            return null;
        }
        ChatRuntimePreparedRequest prepared = executionContext.prepared();
        if (prepared == null) {
            return null;
        }
        List<String> loadedSkillNames = resolveLoadedSkillNames(prepared);
        String currentRuntimeSkillName = resolveCurrentRuntimeSkillName(prepared);
        if (loadedSkillNames.isEmpty() && !StringUtils.hasText(currentRuntimeSkillName)) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 技能路由状态\n\n");
        if (!loadedSkillNames.isEmpty()) {
            builder.append("- 当前会话已加载技能：`")
                    .append(String.join("`、`", loadedSkillNames))
                    .append("`\n");
        }
        if (StringUtils.hasText(currentRuntimeSkillName)) {
            builder.append("- 当前最近一次使用的技能：`")
                    .append(currentRuntimeSkillName)
                    .append("`\n");
        }
        builder.append("- 这些状态只表示历史上加载或使用过 Skill，不表示本轮问题已经自动绑定到其中某个 Skill。\n");
        builder.append("- 每轮都应基于当前用户问题重新判断：直接回答、继续使用当前 Skill、切换 Skill，或回到 General 模式。\n");
        builder.append("- 如果本轮仍需使用某个 Skill 的专属工具、脚本、数据集或生成产物，先调用 `loadSkillContent(skillName)` 获取当前轮说明。\n");
        builder.append("- 如果查询目标、指标、维度、对象、时间范围或产物要求发生变化，本轮应重新查询或生成，不要直接复用上一轮结果。\n");
        return builder.toString();
    }

    /**
     * 解析已激活技能的内容，用于追问场景
     */
    private String resolveActiveSkillContent(AgentRuntimeExecutionContext executionContext) {
        if (executionContext == null) {
            return null;
        }
        ChatRuntimePreparedRequest prepared = executionContext.prepared();
        if (isGeneralSkillReloadSession(prepared)) {
            return null;
        }
        if (!executionContext.usesToolAwarePipeline()) {
            log.debug(
                    "[提示词分流] 跳过已激活技能内容注入：会话编码={}, 原因=当前不是工具执行管线",
                    executionContext.conversation() == null
                            ? null
                            : executionContext.conversation().sessionCode());
            return null;
        }
        if (!allowSkillInternals(prepared)) {
            log.debug(
                    "[提示词分流] 跳过已激活技能内容注入：会话编码={}, 原因=当前处于通用执行世界",
                    executionContext.conversation() == null
                            ? null
                            : executionContext.conversation().sessionCode());
            return null;
        }
        SkillKit skillKit = executionContext.requestSkillKit();
        if (skillKit == null) {
            return null;
        }
        Set<String> activeSkillNames = skillKit.getActivatedSkillNames();
        if (activeSkillNames == null || activeSkillNames.isEmpty()) {
            return null;
        }
        List<String> visibleSkillNames = resolveVisibleSkillNames(activeSkillNames, prepared);
        if (visibleSkillNames.isEmpty()) {
            return null;
        }
        log.debug(
                "[提示词分流] 注入已激活技能内容：会话编码={}, 当前技能列表={}",
                executionContext.conversation() == null
                        ? null
                        : executionContext.conversation().sessionCode(),
                visibleSkillNames);
        StringBuilder builder = new StringBuilder();
        builder.append("## 已激活技能\n\n");
        builder.append("以下为当前 runtime 优先沿用的已加载技能。\n");
        builder.append("这些技能只表示历史上已读取过，不表示本轮已经具备执行依据。\n");
        builder.append("只要本轮仍要使用该技能的专属工具、脚本、数据集或生成产物，必须先重新调用 `loadSkillContent(skillName)`，再继续执行。\n\n");
        for (String skillName : visibleSkillNames) {
            Skill skill = skillKit.getSkill(skillName);
            if (skill == null) {
                continue;
            }
            String content = sanitizeActiveSkillContent(skill.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            builder.append("---\n\n");
            builder.append("### 技能：").append(skillName).append("\n\n");
            builder.append(content).append("\n\n");
        }
        return builder.toString();
    }

    private String sanitizeActiveSkillContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n").trim();
        int coreGuidanceIndex = normalized.indexOf(SKILL_CORE_GUIDANCE_HEADER);
        if (coreGuidanceIndex >= 0) {
            normalized = normalized
                    .substring(coreGuidanceIndex + SKILL_CORE_GUIDANCE_HEADER.length())
                    .trim();
        } else {
            int instructionHeaderIndex = normalized.indexOf(SKILL_INSTRUCTION_HEADER);
            if (instructionHeaderIndex >= 0) {
                normalized = normalized
                        .substring(instructionHeaderIndex + SKILL_INSTRUCTION_HEADER.length())
                        .trim();
            }
            int rawContentMarkerIndex = normalized.indexOf(SKILL_RAW_CONTENT_MARKER);
            if (rawContentMarkerIndex >= 0) {
                normalized = normalized
                        .substring(rawContentMarkerIndex + SKILL_RAW_CONTENT_MARKER.length())
                        .trim();
            }
        }
        return normalized;
    }

    private List<String> resolveVisibleSkillNames(Set<String> activeSkillNames, ChatRuntimePreparedRequest prepared) {
        if (activeSkillNames == null || activeSkillNames.isEmpty()) {
            return List.of();
        }
        String currentRuntimeSkillName = resolveCurrentRuntimeSkillName(prepared);
        if (StringUtils.hasText(currentRuntimeSkillName) && activeSkillNames.contains(currentRuntimeSkillName)) {
            return List.of(currentRuntimeSkillName);
        }
        return new ArrayList<>(activeSkillNames);
    }

    private boolean allowSkillInternals(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return true;
        }
        return "true".equalsIgnoreCase(readExecutionPrecheckText(prepared.paramsJson(), "allowSkillInternals"));
    }

    private String resolveCurrentRuntimeSkillName(ChatRuntimePreparedRequest prepared) {
        String explicit = requestScopedSkillRuntimeService == null
                ? readRootText(prepared == null ? null : prepared.paramsJson(), "currentRuntimeSkillName")
                : requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(
                        prepared == null ? null : prepared.paramsJson(),
                        prepared == null ? List.<lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor>of()
                                : prepared.availableSkills(),
                        prepared == null ? null : prepared.runtimeSkillName());
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (prepared != null && StringUtils.hasText(prepared.runtimeSkillName())) {
            return prepared.runtimeSkillName().trim();
        }
        return "";
    }

    private List<String> resolveLoadedSkillNames(ChatRuntimePreparedRequest prepared) {
        if (prepared == null) {
            return List.of();
        }
        List<String> loadedSkillNames = requestScopedSkillRuntimeService == null
                ? List.of()
                : requestScopedSkillRuntimeService.resolveLoadedSkillNames(prepared.paramsJson());
        if (!loadedSkillNames.isEmpty()) {
            return loadedSkillNames;
        }
        List<String> fallback = new ArrayList<>();
        for (RuntimeLoadedSkill loadedSkill :
                prepared.loadedSkills() == null ? List.<RuntimeLoadedSkill>of() : prepared.loadedSkills()) {
            if (loadedSkill != null && StringUtils.hasText(loadedSkill.runtimeSkillName())) {
                String runtimeSkillName = loadedSkill.runtimeSkillName().trim();
                if (!fallback.contains(runtimeSkillName)) {
                    fallback.add(runtimeSkillName);
                }
            }
        }
        return fallback;
    }

    private boolean isGeneralSkillReloadSession(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.sessionType() == null) {
            return false;
        }
        return prepared.sessionType() == lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType.GENERAL_CHAT
                || prepared.sessionType() == lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType.GENERAL_CHAT_V2
                || prepared.sessionType() == lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType.CHANNEL_CHAT;
    }

    private String readRootText(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null) {
                return "";
            }
            Object value = payload.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readExecutionPrecheckText(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null) {
                return "";
            }
            Object rawPrecheck = payload.get("executionPrecheck");
            if (!(rawPrecheck instanceof Map<?, ?> precheckMap)) {
                return "";
            }
            Object value = precheckMap.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public String buildDatasetSystemPrompt(IntegrationDatasetService.DatasetDetail dataset, String sqlDialect) {
        return "你是数据集智能问数助手。你的任务是基于当前选中的数据集完成统计分析、指标计算和结果解释。"
                + "你可以按需多次调用工具，先理解数据集，再查询数据，最后给出结论。\n\n"
                + "工作规则：\n"
                + "1. 固定流程必须遵守：先调用 search_dataset_summary，先分析可能会用到哪些对象/表，再决定是否调用 get_dataset_schema，确认结构足够支撑 SQL 后，最后才调用 execute_dataset_sql。\n"
                + "2. 遇到业务口径不明确、对象不明确、字段不明确时，不要直接猜测，必须先看摘要，再按需要看结构。\n"
                + "3. search_dataset_summary、get_dataset_schema 返回结果中的 objectCode 才是 SQL 里可直接使用的真实表名；objectName 只是中文说明，绝对不能把中文对象名直接写进 SQL。\n"
                + "4. 当前数据集要求使用 MySQL 方言处理，不能混用其他数据库语法。\n"
                + "5. SQL 中出现的字段，必须来自 get_dataset_schema 返回的字段列表；不能凭经验、中文语义或历史习惯自行想象字段名。\n"
                + "6. 需要统计结果时，先确认对象、字段和关联关系，并确认现有信息已经足够写 SQL；如果还不够，就继续调用 get_dataset_schema，而不是硬写 SQL。\n"
                + "7. 只能基于工具返回的数据和当前数据集信息作答，禁止编造不存在的字段、表、结果或业务规则。\n"
                + "8. 如果 execute_dataset_sql 返回 success=false，要优先读取其中的 nextActionHint、suggestedSchemaRequest、schemaHints、fieldCandidates，再继续调用 get_dataset_schema 或重写 SQL；不要直接放弃，也不要忽略这些恢复信息。\n"
                + "9. execute_dataset_sql 始终返回 rowSchema 和最多 3 条 previewRows。结果少于 3 条时 previewRows 就是完整结果且不会生成 resultFile；达到 3 条时完整结果会由服务端写入 resultFile，previewRows 仅用于理解数据形状，不能视为完整结果。\n"
                + "10. execute_dataset_sql 返回 resultFile 后，必须记住 resultFile 的 JSON 顶层是对象，不是数组；固定结构包含 columns、rowSchema、rowCount、rows，Python 读取完整数据必须使用 json.load(f)[\"rows\"]。如需 Python 二次处理、生成 HTML 或其他文件，只允许生成读取 resultFile 的最小脚本，并通过 run_python args 传入 resultFile 和输出路径。禁止使用 file_write 重写查询数据，禁止把 previewRows、完整 rows、大段 JSON 或完整 HTML 内嵌进 Python 源码。\n"
                + "11. 如果用户请求超出当前数据集能力或数据不足，必须明确说明原因。\n"
                + "12. 回答风格要求（非常重要）：\n"
                + "   - 默认优先给出【简洁直接的核心结论】，用自然语言回答问题。\n"
                + "   - 非必要不要展开详细的SQL过程、字段说明或分析步骤。\n"
                + "   - 只有在以下情况才补充说明：\n"
                + "     a）用户明确追问计算方式或口径\n"
                + "     b）结果存在歧义或容易误解\n"
                + "     c）统计依赖重要过滤条件（如时间范围、状态）\n"
                + "   - 补充信息应简洁表达，不要写成长报告。\n"
                + "13. 输出结构建议：\n"
                + "   - 第一行：直接回答问题（核心结果）\n"
                + "   - 可选第二行：简要补充关键口径（如时间范围、筛选条件）\n"
                + "   - 除非用户要求，不要列出完整统计说明或SQL细节。\n"
                + "14. 除非问题非常复杂，否则不要先输出分析过程再给结论，应优先“结论先行”。\n\n"
                + buildDatasetPromptContext(dataset, sqlDialect);
    }

    public String buildConversationSummarySystemPrompt() {
        return """
                你是对话上下文压缩助手，负责把较早轮次的聊天记录压缩成可继续使用的结构化摘要。
                输出要求：
                1. 只输出最终摘要正文，不要加解释，不要加代码块。
                2. 摘要必须使用以下固定结构：
                【会话目标】
                ...
                【用户关键信息】
                ...
                【已确认事实】
                ...
                【已完成事项】
                ...
                【未完成事项】
                ...
                【工具与外部结果】
                ...
                【后续回答约束】
                ...
                3. 如果某一节没有内容，写“无”。
                4. 不要虚构信息，只能基于输入内容归纳。
                5. 摘要重点是让后续模型延续上下文、避免重复，而不是复述全部细节。
                """;
    }

    public String buildConversationSummaryUserPrompt(String previousSummary, String transcript) {
        return """
                请压缩以下历史会话消息。

                已有摘要：
                %s

                新增历史消息：
                %s
                """
                .formatted(
                        StringUtils.hasText(previousSummary) ? previousSummary.trim() : "无",
                        StringUtils.hasText(transcript) ? transcript.trim() : "无");
    }

    public String buildKnowledgeIntentClassifierSystemPrompt() {
        return """
                你是意图分类器。仅输出一个标签：SMALL_TALK 或 KB_QA。
                SMALL_TALK：问候、寒暄、自我介绍、泛聊天，不依赖知识库证据。
                KB_QA：需要基于知识库事实回答的问题。
                禁止输出任何解释或多余字符。
                """;
    }

    public String buildKnowledgeIntentClassifierUserPrompt(String query) {
        return "用户问题：%s".formatted(StringUtils.hasText(query) ? query.trim() : "");
    }

    public String buildKnowledgeAnswerSystemPrompt(String answerMode) {
        if ("KB_QA".equals(answerMode)) {
            return """
                    你是知识库问答助手。
                    只能基于当前提供的知识库内容进行回答，不得编造不存在的信息。

                    如果信息不足以回答问题，请明确说明：“当前知识库中没有找到相关内容”或“信息不足以支持回答”。

                    回答要求：
                    - 优先用简洁自然的语言直接回答问题，不要写成长篇说明
                    - 在不影响阅读的情况下，可以在关键信息后标注来源编号，如[1][2]
                    - 不要逐条罗列来源或做“分析报告式”输出
                    - 不要输出工具调用信息
                    """;
        }
        if ("LLM_FALLBACK".equals(answerMode)) {
            return """
                    你是通用问答助手。
                    当前问题未命中知识库，请基于通用知识进行回答。

                    回答要求：
                    - 保持简洁、清晰，优先直接回答问题
                    - 对不确定的信息要明确说明（如“可能”、“一般情况下”）
                    - 不要编造“来自知识库”的内容或引用
                    - 不要输出工具调用信息
                    """;
        }
        return """
                你是对话助手。
                当前是闲聊或简单问题，请用自然、友好的语气直接回答。

                回答要求：
                - 简洁自然，不要过度解释
                - 不要输出工具调用信息
                """;
    }

    public String buildKnowledgeAnswerUserPrompt(
            String answerMode, KnowledgeBase kb, String query, List<RecallChunkVo> recalls, String fallbackReason) {
        if ("KB_QA".equals(answerMode)) {
            return buildKnowledgeQaUserPrompt(kb, query, recalls);
        }
        if ("LLM_FALLBACK".equals(answerMode)) {
            return """
                    用户问题：
                    %s

                    未命中原因：%s
                    请直接给出尽可能有帮助的回答。
                    """
                    .formatted(
                            StringUtils.hasText(query) ? query.trim() : "",
                            StringUtils.hasText(fallbackReason) ? fallbackReason.trim() : "UNKNOWN");
        }
        return StringUtils.hasText(query) ? query.trim() : "";
    }

    private String resolveBasePromptSource(AgentRuntimeExecutionContext executionContext) {
        return executionContext != null && executionContext.usesToolAwarePipeline()
                ? "app.chat.system"
                : "app.chat.general";
    }

    private String resolveBasePrompt(AgentRuntimeExecutionContext executionContext) {
        if (executionContext != null && executionContext.usesToolAwarePipeline()) {
            return chatModelProperties.getSystemPrompt();
        }
        return chatModelProperties.getGeneralSystemPrompt();
    }

    private String resolveExecutionWorld(ChatRuntimePreparedRequest prepared) {
        if (prepared == null) {
            return "UNKNOWN";
        }
        String executionWorld = readTopLevelField(prepared.paramsJson(), "executionWorld");
        if (StringUtils.hasText(executionWorld)) {
            return executionWorld;
        }
        if (StringUtils.hasText(prepared.runtimeSkillName())) {
            return "SKILL";
        }
        return "GENERAL";
    }

    private String readTopLevelField(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            Object value = payload.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolveScenePromptSource(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.sessionType() == null) {
            return "scene.unknown";
        }
        return "scene." + prepared.sessionType().name().toLowerCase();
    }

    private void addBlock(
            List<RuntimePromptBlock> blocks, RuntimePromptSourceType sourceType, String source, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        blocks.add(RuntimePromptBlock.of(sourceType, source, content));
    }

    private String buildDatasetPromptContext(IntegrationDatasetService.DatasetDetail dataset, String sqlDialect) {
        if (dataset == null) {
            return "当前未提供数据集上下文。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前数据集信息：\n");
        builder.append("- 名称：").append(defaultText(dataset.name())).append("\n");
        builder.append("- 编码：").append(defaultText(dataset.datasetCode())).append("\n");
        builder.append("- 来源类型：").append(defaultText(dataset.sourceKind())).append("\n");
        builder.append("- SQL 方言：").append(defaultText(sqlDialect)).append("\n");
        builder.append("- 描述：").append(defaultText(dataset.description())).append("\n");
        builder.append("- 业务说明：").append(defaultText(dataset.businessLogic())).append("\n");
        builder.append("- 对象数量：").append(dataset.objectCount()).append("\n");
        builder.append("- 字段数量：").append(dataset.fieldCount()).append("\n");
        List<IntegrationDatasetService.ObjectBindingView> objects =
                dataset.objectBindings() == null ? List.of() : dataset.objectBindings();
        if (!objects.isEmpty()) {
            builder.append("对象列表：\n");
            builder.append("- 重要：SQL 中必须使用 objectCode 作为表名，不能使用中文 objectName。\n");
            objects.stream().limit(10).forEach(item -> builder.append("- ")
                    .append(defaultText(item.objectName()))
                    .append(" (")
                    .append(defaultText(item.objectCode()))
                    .append(")")
                    .append(StringUtils.hasText(item.objectSource()) ? " 来源=" + item.objectSource() : "")
                    .append("\n"));
        }
        List<IntegrationDatasetService.FieldBindingView> fields =
                dataset.fieldBindings() == null ? List.of() : dataset.fieldBindings();
        if (!fields.isEmpty()) {
            builder.append("字段列表：\n");
            builder.append("- 重要：SQL 中使用的字段必须来自这里或 get_dataset_schema 工具返回结果，不能自行想象字段。\n");
            fields.stream().limit(30).forEach(item -> builder.append("- ")
                    .append(defaultText(item.objectName()))
                    .append(".")
                    .append(defaultText(item.fieldName()))
                    .append(StringUtils.hasText(item.fieldAlias()) ? "（别名=" + item.fieldAlias() + "）" : "")
                    .append(StringUtils.hasText(item.fieldType()) ? " 类型=" + item.fieldType() : "")
                    .append(StringUtils.hasText(item.fieldScope()) ? " 范围=" + item.fieldScope() : "")
                    .append("\n"));
        }
        List<IntegrationDatasetService.RelationBindingView> relations =
                dataset.relationBindings() == null ? List.of() : dataset.relationBindings();
        if (!relations.isEmpty()) {
            builder.append("关系列表：\n");
            relations.stream().limit(20).forEach(item -> builder.append("- ")
                    .append(defaultText(item.leftObjectCode()))
                    .append(".")
                    .append(defaultText(item.leftFieldName()))
                    .append(" -> ")
                    .append(defaultText(item.rightObjectCode()))
                    .append(".")
                    .append(defaultText(item.rightFieldName()))
                    .append("\n"));
        }
        return builder.toString().trim();
    }

    private String buildKnowledgeQaUserPrompt(KnowledgeBase kb, String query, List<RecallChunkVo> recalls) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : "";
        String kbName = kb == null ? "" : StringUtils.trimWhitespace(kb.getKbName());
        if (recalls == null || recalls.isEmpty()) {
            return """
                    你将回答一个知识库问题，但当前没有可用证据。
                    问题：
                    %s

                    请明确说明未检索到足够证据，不要编造。
                    """
                    .formatted(normalizedQuery);
        }
        String evidence = buildEvidenceSection(recalls);
        return """
                知识库：%s
                用户问题：
                %s

                可用证据（按相关性排序）：
                %s

                请仅基于上述证据作答，回答尽量结构化，并在关键结论后附上证据编号，如[1][2]。
                如果证据无法支持结论，请明确说明不确定。
                """
                .formatted(StringUtils.hasText(kbName) ? kbName : "未命名知识库", normalizedQuery, evidence);
    }

    private String buildEvidenceSection(List<RecallChunkVo> recalls) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < recalls.size(); i++) {
            RecallChunkVo item = recalls.get(i);
            if (item == null) {
                continue;
            }
            String content = safeTrim(item.getContent(), 600);
            String fileName = buildEvidenceSourceName(item);
            String indexId = StringUtils.hasText(item.getId()) ? item.getId() : "N/A";
            builder.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append("file=")
                    .append(fileName)
                    .append(", indexId=")
                    .append(indexId)
                    .append(", score=")
                    .append(item.getScore() == null ? 0D : item.getScore())
                    .append("\n")
                    .append(content)
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String buildEvidenceSourceName(RecallChunkVo item) {
        String lawTitle = StringUtils.hasText(item.getLawTitle()) ? item.getLawTitle() : item.getFileName();
        String articleCn = item.getArticleCn();
        if (StringUtils.hasText(lawTitle) && StringUtils.hasText(articleCn)) {
            return lawTitle + " " + articleCn;
        }
        if (StringUtils.hasText(lawTitle)) {
            return lawTitle;
        }
        return "unknown";
    }

    private String safeTrim(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "无";
    }
}
