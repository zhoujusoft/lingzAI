package lingzhou.agent.backend.skillstudio.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TokenUsageCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeTokenUsageAccumulator;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.common.lzException.TaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class SkillStudioProjectAutoToolBindingService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}", Pattern.DOTALL);
    private static final int MODEL_CANDIDATE_LIMIT = 120;

    private final SkillStudioProjectSettingsService projectSettingsService;
    private final SkillCatalogService skillCatalogService;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;
    private final ObjectMapper objectMapper;

    public SkillStudioProjectAutoToolBindingService(
            SkillStudioProjectSettingsService projectSettingsService,
            SkillCatalogService skillCatalogService,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            TokenUsageCapabilityAdapter tokenUsageCapability,
            ObjectMapper objectMapper) {
        this.projectSettingsService = projectSettingsService;
        this.skillCatalogService = skillCatalogService;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.tokenUsageCapability = tokenUsageCapability;
        this.objectMapper = objectMapper;
    }

    public AutoBindingResult tryAutoBindFromMessage(Long userId, Long projectId, String message) throws TaskException {
        String normalizedMessage = normalize(message);
        if (!StringUtils.hasText(normalizedMessage)) {
            log.info("skillstudio auto-bind skipped: empty message, projectId={}", projectId);
            return AutoBindingResult.none();
        }
        SkillStudioProjectSettingsService.ProjectSettingsState state =
                projectSettingsService.loadState(userId, projectId);
        if (state == null) {
            log.info("skillstudio auto-bind skipped: settings missing, projectId={}", projectId);
            return AutoBindingResult.none();
        }

        boolean hasExistingBinding = state.bindings() != null
                && state.bindings().stream()
                        .anyMatch(binding -> binding != null && StringUtils.hasText(binding.toolName()));
        if (hasExistingBinding) {
            log.info("skillstudio auto-bind skipped: bindings already exist, projectId={}", projectId);
            return AutoBindingResult.none();
        }

        List<SkillCatalogService.ToolLibraryItem> candidates = loadBindableCandidates(userId);
        if (candidates.isEmpty()) {
            log.info("skillstudio auto-bind skipped: bindable tool library empty, projectId={}", projectId);
            return AutoBindingResult.none();
        }

        ModelToolMatchResult matchResult = matchTopToolByModel(normalizedMessage, candidates);
        log.info(
                "skillstudio auto-bind queried tools: projectId={}, candidateTotal={}, modelTop1={}, confidence={}, reason={}, raw={}",
                projectId,
                matchResult.candidateTotal(),
                matchResult.toolName(),
                matchResult.confidence(),
                abbreviate(matchResult.reason(), 120),
                abbreviate(matchResult.rawResponse(), 240));
        if (!StringUtils.hasText(matchResult.toolName())) {
            return AutoBindingResult.none(matchResult.usageSnapshot());
        }

        SkillCatalogService.ToolLibraryItem selectedTool = findCandidateByName(candidates, matchResult.toolName());
        if (selectedTool == null) {
            log.warn(
                    "skillstudio auto-bind model returned unknown tool: projectId={}, toolName={}",
                    projectId,
                    matchResult.toolName());
            return AutoBindingResult.none(matchResult.usageSnapshot());
        }

        List<SkillStudioProjectSettingsService.ProjectToolBindingInput> nextBindings =
                List.of(new SkillStudioProjectSettingsService.ProjectToolBindingInput(
                        selectedTool.name(), true, "", List.of(), 100));

        projectSettingsService.updateSettings(
                userId,
                projectId,
                new SkillStudioProjectSettingsService.UpdateProjectSettingsRequest(
                        state.projectHints(), state.projectConstraints(), nextBindings));
        log.info(
                "skillstudio auto-bind applied: projectId={}, toolName={}, displayName={}",
                projectId,
                selectedTool.name(),
                selectedTool.displayName());
        return new AutoBindingResult(List.of(selectedTool.name()), matchResult.usageSnapshot());
    }

    private List<SkillCatalogService.ToolLibraryItem> loadBindableCandidates(Long userId) {
        List<SkillCatalogService.ToolLibraryItem> library = skillCatalogService.listToolLibrary(userId);
        if (library == null || library.isEmpty()) {
            return List.of();
        }
        Map<String, SkillCatalogService.ToolLibraryItem> dedup = new LinkedHashMap<>();
        for (SkillCatalogService.ToolLibraryItem item : library) {
            if (item == null || !item.bindable() || !StringUtils.hasText(item.name())) {
                continue;
            }
            dedup.putIfAbsent(item.name().trim(), item);
        }
        return List.copyOf(dedup.values());
    }

    private SkillCatalogService.ToolLibraryItem findCandidateByName(
            List<SkillCatalogService.ToolLibraryItem> candidates, String toolName) {
        if (!StringUtils.hasText(toolName) || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String normalized = toolName.trim();
        for (SkillCatalogService.ToolLibraryItem item : candidates) {
            if (item != null && normalized.equals(item.name())) {
                return item;
            }
        }
        return null;
    }

    private ModelToolMatchResult matchTopToolByModel(
            String userPrompt, List<SkillCatalogService.ToolLibraryItem> allCandidates) {
        if (!StringUtils.hasText(userPrompt) || allCandidates == null || allCandidates.isEmpty()) {
            return ModelToolMatchResult.none(allCandidates == null ? 0 : allCandidates.size(), "", null);
        }
        List<ModelToolCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < allCandidates.size() && i < MODEL_CANDIDATE_LIMIT; i++) {
            SkillCatalogService.ToolLibraryItem item = allCandidates.get(i);
            candidates.add(new ModelToolCandidate(item.name(), normalize(item.displayName())));
        }
        String prompt = buildModelMatchPrompt(userPrompt, candidates);
        var chatBundle = modelRuntimeClientFactory.createChatBundle();
        long startedAt = System.currentTimeMillis();
        RuntimeTokenUsageAccumulator accumulator =
                tokenUsageCapability.createAccumulator(chatBundle.config(), startedAt);
        accumulator.ensureCurrentCall(startedAt);
        String rawResponse;
        RuntimeRunUsageSnapshot usageSnapshot;
        try {
            ChatResponse chatResponse =
                    chatBundle.chatClient().prompt().user(prompt).call().chatResponse();
            rawResponse = extractResponseText(chatResponse);
            tokenUsageCapability.recordResponse(accumulator, chatResponse);
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(accumulator, "COMPLETED", completedAt, safeLength(rawResponse));
            usageSnapshot = tokenUsageCapability.snapshot(accumulator, "COMPLETED", completedAt);
        } catch (Exception ex) {
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(accumulator, "FAILED", completedAt, 0);
            usageSnapshot = tokenUsageCapability.snapshot(accumulator, "FAILED", completedAt);
            log.warn("skillstudio auto-bind model match failed: error={}", ex.getMessage(), ex);
            return ModelToolMatchResult.none(allCandidates.size(), "", usageSnapshot);
        }
        ParsedModelResult parsed = parseModelResult(rawResponse);
        if (!StringUtils.hasText(parsed.toolName())) {
            return ModelToolMatchResult.none(allCandidates.size(), rawResponse, usageSnapshot);
        }
        boolean exists = candidates.stream().anyMatch(item -> parsed.toolName().equals(item.toolName()));
        if (!exists) {
            return ModelToolMatchResult.none(allCandidates.size(), rawResponse, usageSnapshot);
        }
        return new ModelToolMatchResult(
                parsed.toolName(),
                parsed.confidence(),
                parsed.reason(),
                allCandidates.size(),
                rawResponse == null ? "" : rawResponse.trim(),
                usageSnapshot);
    }

    private String extractResponseText(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null
                || chatResponse.getResult().getOutput().getText() == null) {
            return "";
        }
        return chatResponse.getResult().getOutput().getText().trim();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String buildModelMatchPrompt(String userPrompt, List<ModelToolCandidate> candidates) {
        String candidateJson = toJson(candidates);
        return """
                你是技能工坊的工具匹配器。
                目标：
                根据用户提示词，从候选工具中选出最可能用到的一个 toolName（top1）。

                规则：
                1. 只能从候选列表中选择 toolName；如果无法判断，toolName 返回空字符串。
                2. 优先参考 displayName 与用户需求语义的贴合程度。
                3. 不要编造候选列表中不存在的 toolName。
                4. 只输出一个 JSON 对象，不要输出解释性文本或 Markdown。

                输出 JSON schema：
                {
                  "toolName": "string",
                  "confidence": 0.0,
                  "reason": "string"
                }

                用户提示词：
                %s

                候选工具（JSON）：
                %s
                """
                .formatted(userPrompt, candidateJson);
    }

    private ParsedModelResult parseModelResult(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return ParsedModelResult.empty();
        }
        String trimmed = rawResponse.trim();
        try {
            ModelToolSelectionPayload payload = objectMapper.readValue(trimmed, ModelToolSelectionPayload.class);
            return ParsedModelResult.from(payload);
        } catch (Exception ignored) {
            // Continue with extracted JSON or plain-text fallback.
        }
        String json = extractJsonBlock(trimmed);
        if (StringUtils.hasText(json)) {
            try {
                ModelToolSelectionPayload payload = objectMapper.readValue(json, ModelToolSelectionPayload.class);
                return ParsedModelResult.from(payload);
            } catch (Exception ignored) {
                // Fallback to plain text below.
            }
        }
        return new ParsedModelResult(trimmed, 0d, "plain-text-fallback");
    }

    private String extractJsonBlock(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return "";
        }
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(rawResponse.trim());
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || maxLength <= 0) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private record ModelToolCandidate(String toolName, String displayName) {}

    private record ModelToolSelectionPayload(String toolName, Double confidence, String reason) {}

    private record ParsedModelResult(String toolName, double confidence, String reason) {
        private static ParsedModelResult empty() {
            return new ParsedModelResult("", 0d, "");
        }

        private static ParsedModelResult from(ModelToolSelectionPayload payload) {
            if (payload == null || !StringUtils.hasText(payload.toolName())) {
                return empty();
            }
            double confidence = payload.confidence() == null ? 0d : Math.max(0d, Math.min(1d, payload.confidence()));
            return new ParsedModelResult(payload.toolName().trim(), confidence, normalizeReason(payload.reason()));
        }

        private static String normalizeReason(String value) {
            return StringUtils.hasText(value) ? value.trim() : "";
        }
    }

    private record ModelToolMatchResult(
            String toolName,
            double confidence,
            String reason,
            int candidateTotal,
            String rawResponse,
            RuntimeRunUsageSnapshot usageSnapshot) {
        private static ModelToolMatchResult none(
                int candidateTotal, String rawResponse, RuntimeRunUsageSnapshot usageSnapshot) {
            return new ModelToolMatchResult(
                    "", 0d, "", candidateTotal, rawResponse == null ? "" : rawResponse.trim(), usageSnapshot);
        }
    }

    public record AutoBindingResult(List<String> matchedToolNames, RuntimeRunUsageSnapshot usageSnapshot) {
        public static AutoBindingResult none() {
            return none(null);
        }

        public static AutoBindingResult none(RuntimeRunUsageSnapshot usageSnapshot) {
            return new AutoBindingResult(List.of(), usageSnapshot);
        }

        public boolean applied() {
            return matchedToolNames != null && !matchedToolNames.isEmpty();
        }
    }
}
