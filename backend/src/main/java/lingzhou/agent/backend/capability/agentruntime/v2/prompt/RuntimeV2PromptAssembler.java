package lingzhou.agent.backend.capability.agentruntime.v2.prompt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lingzhou.agent.backend.business.chat.execution.nativefs.PythonScriptWritePolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionAssessment;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlocker;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ExecutionRequirement;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContract;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2PromptAssembler {

    private static final int MAX_TOOL_SUMMARY_PARAM_COUNT = 6;
    private static final int MAX_OBSERVATION_ITEMS = 3;
    private static final int MAX_OBSERVATION_CHARS = 280;
    private static final int MAX_LINE_LENGTH = 120;
    private static final int MAX_PROGRESS_ITEMS = 6;
    private static final int MAX_PROGRESS_DETAIL_CHARS = 200;
    private static final String SKILL_INSTRUCTION_HEADER = "## 技能使用说明";
    private static final String SKILL_RAW_CONTENT_MARKER =
            "Follow the skill instructions below. Use available tools only when needed.";

    private final PromptLoader promptLoader;

    public RuntimeV2PromptAssembler() {
        this(new PromptLoader());
    }

    public RuntimeV2PromptAssembler(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    public String buildDirectSystemPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_DIRECT_SYSTEM, Map.of("baseSystemPrompt", renderBaseSystemPrompt(state)));
    }

    public String buildReactSystemPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_REASONING_SYSTEM,
                Map.of(
                        "baseSystemPrompt", renderBaseSystemPrompt(state),
                        "taskContractSection", renderTaskContract(state),
                        "progressStatusSection", renderProgressStatusSection(state),
                        "codeCapabilitySection", renderCodeCapabilitySection(state),
                        "toolSummarySection", renderToolSummary(state == null ? List.of() : state.toolCallbacks())));
    }

    public String buildReactUserPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_REASONING_USER,
                Map.of(
                        "userRequest", state.prepared().userMessage(),
                        "observationTraceSection", renderReasoningObservationTrace(state),
                        "completionBlockersSection", renderReasoningCompletionBlockers(state)));
    }

    public String buildReactRepairUserPrompt(RuntimeV2State state, String rawOutput, String repairReason) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_REASONING_REPAIR_USER,
                Map.of(
                        "repairReason", StringUtils.hasText(repairReason) ? repairReason.trim() : "输出格式不合法",
                        "rawOutput", StringUtils.hasText(rawOutput) ? rawOutput.trim() : "(empty)",
                        "observationAwareRule",
                                state != null && !state.observationTrace().isEmpty()
                                        ? "5. 结合已有工具观察，不要重复输出无效工具调用。\n"
                                        : "",
                        "jsonEscapingRule", "6. 如果 arguments 里包含多行代码或大段文本，必须把它放在合法 JSON 字符串里，确保换行、引号、反斜杠已正确转义。"));
    }

    public String buildReactFinalAnswerSystemPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_FINAL_SYSTEM, Map.of("baseSystemPrompt", renderBaseSystemPrompt(state)));
    }

    public String buildReactFinalAnswerUserPrompt(RuntimeV2State state, String draftAnswer) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.REACT_FINAL_USER,
                Map.of(
                        "userRequest", state.prepared().userMessage(),
                        "observationTraceSection", renderFinalAnswerObservationTrace(state),
                        "draftAnswerSection",
                                StringUtils.hasText(draftAnswer) ? "\n可参考的回答草稿：\n" + draftAnswer.trim() + "\n" : ""));
    }

    public String buildCodePlanSystemPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.CODE_PLAN_SYSTEM, Map.of("baseSystemPrompt", renderBaseSystemPrompt(state)));
    }

    public String buildCodePlanUserPrompt(RuntimeV2State state, String attachmentSummary, String latestObservation) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.CODE_PLAN_USER,
                Map.of(
                        "userRequest",
                        state.prepared().userMessage(),
                        "attachmentSummarySection",
                        StringUtils.hasText(attachmentSummary) ? "\n当前可用附件：\n" + attachmentSummary.trim() + "\n" : "",
                        "latestObservationSection",
                        StringUtils.hasText(latestObservation) ? "\n最近一次关键观察：\n" + latestObservation.trim() + "\n" : "",
                        "observationTraceSection",
                        renderCodeObservationTrace(state)));
    }

    public String buildCodeScriptSystemPrompt(RuntimeV2State state) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.CODE_SCRIPT_SYSTEM,
                Map.of(
                        "baseSystemPrompt", renderBaseSystemPrompt(state),
                        "pythonWritePolicyContract", PythonScriptWritePolicy.buildCodeScriptPromptContract()));
    }

    public String buildCodeScriptUserPrompt(RuntimeV2State state, String planJson, String latestObservation) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.CODE_SCRIPT_USER,
                Map.of(
                        "userRequest",
                        state.prepared().userMessage(),
                        "latestObservationSection",
                        StringUtils.hasText(latestObservation) ? "\n最近一次关键观察：\n" + latestObservation.trim() + "\n" : "",
                        "observationTraceSection",
                        renderCodeObservationTrace(state),
                        "planJson",
                        planJson));
    }

    private String renderBaseSystemPrompt(RuntimeV2State state) {
        StringBuilder builder = new StringBuilder();
        appendBaseSystemPrompt(builder, state);
        return builder.toString().trim();
    }

    private String renderToolSummary(List<ToolCallback> tools) {
        StringBuilder builder = new StringBuilder();
        appendToolSummary(builder, tools);
        return builder.toString().trim();
    }

    private String renderObservationTrace(RuntimeV2State state) {
        StringBuilder builder = new StringBuilder();
        appendObservationTrace(builder, state);
        return builder.toString().trim();
    }

    private String renderReasoningObservationTrace(RuntimeV2State state) {
        if (state == null || !state.messages().isEmpty()) {
            return "";
        }
        return renderObservationTrace(state);
    }

    private String renderFinalAnswerObservationTrace(RuntimeV2State state) {
        return renderReasoningObservationTrace(state);
    }

    private String renderCodeObservationTrace(RuntimeV2State state) {
        if (state == null || !state.messages().isEmpty()) {
            return "";
        }
        return renderObservationTrace(state);
    }

    private String renderCodeCapabilitySection(RuntimeV2State state) {
        StringBuilder builder = new StringBuilder();
        appendCodeCapabilityPrompt(builder, state);
        return builder.toString().trim();
    }

    private String renderCompletionBlockers(RuntimeV2CompletionAssessment assessment) {
        StringBuilder builder = new StringBuilder();
        appendCompletionBlockers(builder, assessment);
        return builder.toString().trim();
    }

    private String renderReasoningCompletionBlockers(RuntimeV2State state) {
        if (state == null || !state.messages().isEmpty()) {
            return "";
        }
        return renderCompletionBlockers(state.completionAssessment());
    }

    private String renderTaskContract(RuntimeV2State state) {
        StringBuilder builder = new StringBuilder();
        appendTaskContract(builder, state);
        return builder.toString().trim();
    }

    private String renderProgressStatusSection(RuntimeV2State state) {
        StringBuilder builder = new StringBuilder();
        appendProgressStatus(builder, state);
        return builder.toString().trim();
    }

    private void appendBaseSystemPrompt(StringBuilder builder, RuntimeV2State state) {
        if (state == null || state.prepared() == null) {
            return;
        }
        String baseSystemPrompt = state.prepared().systemPrompt();
        if (StringUtils.hasText(baseSystemPrompt)) {
            builder.append(baseSystemPrompt.trim());
        }
        appendActiveSkillContent(builder, state);
        String appendPrompt = state.prepared().systemPromptAppend();
        if (StringUtils.hasText(appendPrompt)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(appendPrompt.trim());
        }
    }

    private void appendActiveSkillContent(StringBuilder builder, RuntimeV2State state) {
        if (builder == null || state == null) {
            return;
        }
        SkillKit skillKit = state.requestSkillKit();
        if (skillKit == null) {
            return;
        }
        Set<String> activeSkillNames = skillKit.getActivatedSkillNames();
        if (activeSkillNames == null || activeSkillNames.isEmpty()) {
            return;
        }
        String activeSkillContent = buildActiveSkillContent(skillKit, activeSkillNames);
        if (!StringUtils.hasText(activeSkillContent)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(activeSkillContent.trim());
    }

    private String buildActiveSkillContent(SkillKit skillKit, Set<String> activeSkillNames) {
        String skillItems = activeSkillNames.stream()
                .map(skillKit::getSkill)
                .filter(skill -> skill != null && StringUtils.hasText(skill.getContent()))
                .map(skill -> sanitizeActiveSkillContent(skill.getContent()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
        if (!StringUtils.hasText(skillItems)) {
            return "";
        }
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_ACTIVE_SKILL_SECTION, Map.of("skillItems", skillItems));
    }

    private void appendToolSummary(StringBuilder builder, List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            builder.append(promptLoader
                    .loadPrompt(RuntimeV2PromptCatalog.SHARED_TOOL_SUMMARY_EMPTY)
                    .trim());
            return;
        }
        String toolItems = tools.stream()
                .filter(tool -> tool != null && tool.getToolDefinition() != null)
                .map(tool -> promptLoader.renderPrompt(
                        RuntimeV2PromptCatalog.SHARED_TOOL_SUMMARY_ITEM,
                        Map.of(
                                "toolName", tool.getToolDefinition().name(),
                                "toolDescriptionLine",
                                        StringUtils.hasText(
                                                        tool.getToolDefinition().description())
                                                ? "："
                                                        + tool.getToolDefinition()
                                                                .description()
                                                                .trim()
                                                : "",
                                "inputSchemaLine",
                                        buildToolArgumentHint(
                                                tool.getToolDefinition().inputSchema()))))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (StringUtils.hasText(toolItems)) {
            builder.append(promptLoader.renderPrompt(
                    RuntimeV2PromptCatalog.SHARED_TOOL_SUMMARY_SECTION, Map.of("toolItems", toolItems)));
        }
    }

    private void appendObservationTrace(StringBuilder builder, RuntimeV2State state) {
        if (builder == null || state == null || state.observationTrace().isEmpty()) {
            return;
        }
        String observationItems = buildObservationItems(state.observationTrace());
        if (StringUtils.hasText(observationItems)) {
            builder.append(promptLoader.renderPrompt(
                    RuntimeV2PromptCatalog.SHARED_OBSERVATION_TRACE_SECTION,
                    Map.of("observationItems", observationItems)));
        }
    }

    private void appendCodeCapabilityPrompt(StringBuilder builder, RuntimeV2State state) {
        if (builder == null || state == null || state.prepared() == null) {
            return;
        }
        if (!readPreparedFlag(state, "allowCodeExecution")) {
            return;
        }
        builder.append(promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_CODE_CAPABILITY_SECTION,
                Map.of("pythonWritePolicyContract", PythonScriptWritePolicy.buildCodeScriptPromptContract())));
    }

    private boolean readPreparedFlag(RuntimeV2State state, String key) {
        if (state == null || state.prepared() == null) {
            return false;
        }
        Map<String, Object> payload = parseParamsJson(state.prepared().paramsJson());
        Object rawDecision = payload.get("toolToCodeDecision");
        if (rawDecision instanceof Map<?, ?> decisionMap) {
            Object value = decisionMap.get(key);
            if (value != null) {
                return "true".equalsIgnoreCase(String.valueOf(value).trim());
            }
        }
        Object directValue = payload.get(key);
        return directValue != null
                && "true".equalsIgnoreCase(String.valueOf(directValue).trim());
    }

    private Map<String, Object> parseParamsJson(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void appendCompletionBlockers(StringBuilder builder, RuntimeV2CompletionAssessment assessment) {
        if (builder == null || assessment == null || !assessment.blocked()) {
            return;
        }
        List<RuntimeV2CompletionBlocker> blockers = assessment.blockers();
        String blockerItems = buildCompletionBlockerItems(blockers);
        if (StringUtils.hasText(blockerItems)) {
            builder.append(promptLoader.renderPrompt(
                    RuntimeV2PromptCatalog.SHARED_COMPLETION_BLOCKERS_SECTION, Map.of("blockerItems", blockerItems)));
        }
    }

    private void appendTaskContract(StringBuilder builder, RuntimeV2State state) {
        if (builder == null || state == null || state.taskContract() == null) {
            return;
        }
        RuntimeV2TaskContract taskContract = state.taskContract();
        boolean messageFirstMode = !state.messages().isEmpty();
        String rendered = promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_SECTION,
                Map.of(
                        "intentsSection", messageFirstMode ? "" : renderTaskContractIntentsSection(taskContract),
                        "skillContractsSection", messageFirstMode ? "" : renderTaskContractSkillsSection(taskContract),
                        "requirementsSection", renderTaskContractRequirementsSection(taskContract)));
        if (StringUtils.hasText(rendered)) {
            builder.append(rendered);
        }
    }

    private void appendProgressStatus(StringBuilder builder, RuntimeV2State state) {
        if (builder == null || state == null) {
            return;
        }
        List<RuntimeV2ObligationEntry> obligations = state.obligationLedger();
        List<RuntimeV2EvidenceEntry> evidences = state.evidenceLedger();
        if (obligations.isEmpty()) {
            return;
        }
        String openObligationBlock = buildOpenObligationBlock(obligations);
        String priorityActionLine = buildPriorityActionLine(obligations, evidences);
        boolean messageFirstMode = !state.messages().isEmpty();
        if (!StringUtils.hasText(priorityActionLine) && !StringUtils.hasText(openObligationBlock)) {
            return;
        }
        String rendered = promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_PROGRESS_STATUS_SECTION,
                Map.of(
                        "priorityActionLine",
                        priorityActionLine,
                        "openObligationBlock",
                        messageFirstMode ? "" : openObligationBlock,
                        "satisfiedObligationBlock",
                        "",
                        "evidenceBlock",
                        ""));
        if (StringUtils.hasText(rendered)) {
            builder.append(rendered);
        }
    }

    private String buildObservationItems(List<Map<String, Object>> observationTrace) {
        List<Map<String, Object>> normalizedItems = IntStream.range(0, observationTrace.size())
                .mapToObj(index -> normalizeObservationItem(index, observationTrace.get(index)))
                .filter(item -> StringUtils.hasText(normalizeText(item.get("toolName")))
                        || StringUtils.hasText(normalizeText(item.get("observation"))))
                .toList();
        int start = Math.max(0, normalizedItems.size() - MAX_OBSERVATION_ITEMS);
        return IntStream.range(start, normalizedItems.size())
                .mapToObj(normalizedItems::get)
                .map(item -> promptLoader.renderPrompt(RuntimeV2PromptCatalog.SHARED_OBSERVATION_TRACE_ITEM, item))
                .collect(Collectors.joining("\n"));
    }

    private Map<String, Object> normalizeObservationItem(int index, Map<String, Object> item) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("index", index + 1);
        normalized.put("toolName", item == null ? "" : normalizeText(item.get("toolName")));
        normalized.put(
                "observation",
                item == null ? "" : truncateText(normalizeText(item.get("observation")), MAX_OBSERVATION_CHARS));
        return normalized;
    }

    private String buildToolArgumentHint(String inputSchema) {
        if (!StringUtils.hasText(inputSchema)) {
            return "";
        }
        try {
            Map<String, Object> schema = JSON.parseObject(inputSchema, new TypeReference<Map<String, Object>>() {});
            Object propertiesRaw = schema.get("properties");
            if (!(propertiesRaw instanceof Map<?, ?> propertiesMap) || propertiesMap.isEmpty()) {
                return "";
            }
            List<String> requiredFields;
            Object requiredRaw = schema.get("required");
            if (requiredRaw instanceof List<?> requiredList) {
                requiredFields = requiredList.stream()
                        .map(this::normalizeText)
                        .filter(StringUtils::hasText)
                        .toList();
            } else {
                requiredFields = List.of();
            }
            List<String> orderedFields = propertiesMap.keySet().stream()
                    .map(this::normalizeText)
                    .filter(StringUtils::hasText)
                    .sorted(Comparator.comparingInt(field -> requiredFields.contains(field) ? 0 : 1))
                    .limit(MAX_TOOL_SUMMARY_PARAM_COUNT)
                    .toList();
            if (orderedFields.isEmpty()) {
                return "";
            }
            String prefix = requiredFields.isEmpty() ? "关键参数" : "关键参数（前置必填优先）";
            return "\n  " + prefix + "：" + String.join(", ", orderedFields);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return normalizeText(text);
        }
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String buildCompletionBlockerItems(List<RuntimeV2CompletionBlocker> blockers) {
        return IntStream.range(0, blockers.size())
                .mapToObj(index -> {
                    RuntimeV2CompletionBlocker blocker = blockers.get(index);
                    return promptLoader.renderPrompt(
                            RuntimeV2PromptCatalog.SHARED_COMPLETION_BLOCKER_ITEM,
                            Map.of(
                                    "index", index + 1,
                                    "source", blocker.source(),
                                    "title", blocker.title(),
                                    "detail", blocker.detail(),
                                    "expectedActionLine",
                                            StringUtils.hasText(blocker.expectedAction())
                                                    ? "   期望动作："
                                                            + blocker.expectedAction()
                                                                    .trim()
                                                    : ""));
                })
                .collect(Collectors.joining("\n"));
    }

    private String renderTaskContractIntentsSection(RuntimeV2TaskContract taskContract) {
        if (taskContract == null || taskContract.intents().isEmpty()) {
            return "";
        }
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_INTENTS_SECTION, Map.of("intents", taskContract.intents()));
    }

    private String renderTaskContractSkillsSection(RuntimeV2TaskContract taskContract) {
        if (taskContract == null || taskContract.activeSkillContracts().isEmpty()) {
            return "";
        }
        String skillItems = taskContract.activeSkillContracts().stream()
                .map(contract -> promptLoader.renderPrompt(
                        RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_SKILL_ITEM,
                        Map.of(
                                "displayName", normalizeText(contract.displayName()),
                                "skillName", normalizeText(contract.skillName()),
                                "capabilitiesLine",
                                        contract.capabilities().isEmpty() ? "" : "，能力：" + contract.capabilities())))
                .collect(Collectors.joining("\n"));
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_SKILLS_SECTION,
                Map.of("skillItems", skillItems, "skillSwitchGuidanceLine", ""));
    }

    private String renderTaskContractRequirementsSection(RuntimeV2TaskContract taskContract) {
        if (taskContract == null || taskContract.activeRequirements().isEmpty()) {
            return "";
        }
        String requirementItems = taskContract.activeRequirements().stream()
                .map(requirement -> renderTaskContractRequirementItem(requirement))
                .collect(Collectors.joining("\n"));
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_REQUIREMENTS_SECTION,
                Map.of("requirementItems", requirementItems));
    }

    private String renderTaskContractRequirementItem(RuntimeV2ExecutionRequirement requirement) {
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_TASK_CONTRACT_REQUIREMENT_ITEM,
                Map.of(
                        "title", normalizeText(requirement.title()),
                        "detail", normalizeText(requirement.detail()),
                        "preferredToolSequenceLine",
                                requirement.preferredToolSequence().isEmpty()
                                        ? ""
                                        : "  推荐顺序：" + requirement.preferredToolSequence()));
    }

    private String buildPriorityActionLine(
            List<RuntimeV2ObligationEntry> obligations, List<RuntimeV2EvidenceEntry> evidences) {
        if (obligations == null || obligations.isEmpty()) {
            return "";
        }
        List<RuntimeV2ObligationEntry> pendingObligations = obligations.stream()
                .filter(this::isPendingObligation)
                .sorted(Comparator.comparingInt(this::progressPriority))
                .toList();
        if (pendingObligations.isEmpty()) {
            return "- 当前优先下一步：所有 requirement 已满足；如果答案已完整，直接输出 final，不要再重复工具调用。\n";
        }
        RuntimeV2ObligationEntry firstPending = pendingObligations.get(0);
        RuntimeV2EvidenceEntry fileOutputEvidence = findEvidence(evidences, "file.output.present");
        if (matchesObligation(firstPending, "artifact.publish.required")) {
            if (isSatisfiedEvidenceWithSource(fileOutputEvidence, "run_python")) {
                return "- 当前优先下一步：交付物闭环仍未完成，但最终文件已经生成；下一步优先调用 `write_artifact` 发布产物，发布完成前不要切到别的子任务。\n";
            }
            if (isSatisfiedEvidenceWithSource(fileOutputEvidence, "file_write")) {
                return "- 当前优先下一步：交付物闭环仍未完成，当前只有中间文件；下一步优先继续执行 `run_python` 生成最终文件，不要回头重做已完成的读取或翻译步骤。\n";
            }
        }
        if (matchesObligation(firstPending, "dataset.result.required")
                && isSatisfiedEvidence(findEvidence(evidences, "dataset.summary.known"))
                && isSatisfiedEvidence(findEvidence(evidences, "dataset.schema.known"))
                && !isSatisfiedEvidence(findEvidence(evidences, "dataset.query.success"))) {
            return "- 当前优先下一步：数据集摘要和 schema 已具备；下一步优先执行 SQL 查询拿到结果，不要回头重做 summary/schema。\n";
        }
        String nextAction = StringUtils.hasText(firstPending.expectedAction())
                ? firstPending.expectedAction().trim()
                : firstPending.detail();
        if (!StringUtils.hasText(nextAction)) {
            return "";
        }
        return "- 当前优先下一步：" + nextAction + "\n";
    }

    private String buildOpenObligationBlock(List<RuntimeV2ObligationEntry> obligations) {
        if (obligations == null || obligations.isEmpty()) {
            return "";
        }
        List<RuntimeV2ObligationEntry> pendingObligations = obligations.stream()
                .filter(this::isPendingObligation)
                .sorted(Comparator.comparingInt(this::progressPriority))
                .limit(MAX_PROGRESS_ITEMS)
                .toList();
        if (pendingObligations.isEmpty()) {
            return "";
        }
        String items = pendingObligations.stream()
                .map(this::renderProgressObligationItem)
                .collect(Collectors.joining("\n"));
        return "\n当前未完成/失败闭环：\n" + items + "\n";
    }

    private String buildSatisfiedObligationBlock(List<RuntimeV2ObligationEntry> obligations) {
        if (obligations == null || obligations.isEmpty()) {
            return "";
        }
        List<RuntimeV2ObligationEntry> satisfiedObligations = obligations.stream()
                .filter(entry -> entry != null && entry.status() == RuntimeV2ObligationStatus.SATISFIED)
                .limit(MAX_PROGRESS_ITEMS)
                .toList();
        if (satisfiedObligations.isEmpty()) {
            return "";
        }
        String items = satisfiedObligations.stream()
                .map(entry -> "- [SATISFIED] " + normalizeText(entry.title()) + "：该闭环已满足，无需重做。")
                .collect(Collectors.joining("\n"));
        return "\n已满足闭环：\n" + items + "\n";
    }

    private String buildSatisfiedEvidenceBlock(List<RuntimeV2EvidenceEntry> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "";
        }
        List<RuntimeV2EvidenceEntry> satisfiedEvidences = evidences.stream()
                .filter(entry -> entry != null && entry.status() == RuntimeV2EvidenceStatus.SATISFIED)
                .limit(MAX_PROGRESS_ITEMS)
                .toList();
        if (satisfiedEvidences.isEmpty()) {
            return "";
        }
        String items = satisfiedEvidences.stream()
                .map(this::renderProgressEvidenceItem)
                .collect(Collectors.joining("\n"));
        return "\n已获得关键证据：\n" + items + "\n";
    }

    private String renderProgressObligationItem(RuntimeV2ObligationEntry obligation) {
        String expectedActionLine = StringUtils.hasText(obligation.expectedAction())
                ? "  期望动作：" + obligation.expectedAction().trim()
                : "";
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_PROGRESS_STATUS_OBLIGATION_ITEM,
                Map.of(
                        "status", normalizeObligationStatus(obligation.status()),
                        "title", normalizeText(obligation.title()),
                        "detail", truncateText(normalizeText(obligation.detail()), MAX_PROGRESS_DETAIL_CHARS),
                        "expectedActionLine", expectedActionLine));
    }

    private String renderProgressEvidenceItem(RuntimeV2EvidenceEntry evidence) {
        String sourceRefLine = StringUtils.hasText(evidence.sourceRef())
                ? "（来源：" + evidence.sourceRef().trim() + "）"
                : "";
        return promptLoader.renderPrompt(
                RuntimeV2PromptCatalog.SHARED_PROGRESS_STATUS_EVIDENCE_ITEM,
                Map.of(
                        "status", normalizeEvidenceStatus(evidence.status()),
                        "title", normalizeText(evidence.title()),
                        "detail", truncateText(normalizeText(evidence.detail()), MAX_PROGRESS_DETAIL_CHARS),
                        "sourceRefLine", sourceRefLine));
    }

    private String sanitizeActiveSkillContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n").trim();
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
        return normalized;
    }

    private boolean isPendingObligation(RuntimeV2ObligationEntry entry) {
        return entry != null
                && entry.status() != null
                && entry.status() != RuntimeV2ObligationStatus.SATISFIED
                && entry.status() != RuntimeV2ObligationStatus.WAIVED;
    }

    private int progressPriority(RuntimeV2ObligationEntry entry) {
        if (entry == null || entry.status() == null) {
            return Integer.MAX_VALUE;
        }
        return switch (entry.status()) {
            case FAILED -> 0;
            case OPEN -> 1;
            case IN_PROGRESS -> 2;
            case SATISFIED -> 3;
            case WAIVED -> 4;
        };
    }

    private RuntimeV2EvidenceEntry findEvidence(List<RuntimeV2EvidenceEntry> evidences, String code) {
        if (evidences == null || !StringUtils.hasText(code)) {
            return null;
        }
        return evidences.stream()
                .filter(entry -> entry != null && code.equals(entry.code()))
                .findFirst()
                .orElse(null);
    }

    private boolean isSatisfiedEvidence(RuntimeV2EvidenceEntry evidence) {
        return evidence != null && evidence.status() == RuntimeV2EvidenceStatus.SATISFIED;
    }

    private boolean isSatisfiedEvidenceWithSource(RuntimeV2EvidenceEntry evidence, String sourceRef) {
        return isSatisfiedEvidence(evidence)
                && StringUtils.hasText(sourceRef)
                && sourceRef.equalsIgnoreCase(normalizeText(evidence.sourceRef()));
    }

    private boolean matchesObligation(RuntimeV2ObligationEntry obligation, String code) {
        return obligation != null && StringUtils.hasText(code) && code.equals(obligation.code());
    }

    private String normalizeObligationStatus(RuntimeV2ObligationStatus status) {
        return status == null ? "" : status.name();
    }

    private String normalizeEvidenceStatus(RuntimeV2EvidenceStatus status) {
        return status == null ? "" : status.name();
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
