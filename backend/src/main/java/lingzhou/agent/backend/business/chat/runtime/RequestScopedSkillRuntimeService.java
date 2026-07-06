package lingzhou.agent.backend.business.chat.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillReadFactContract;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContract;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContractSupport;
import lingzhou.agent.spring.ai.skill.capability.ReferencesLoader;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class RequestScopedSkillRuntimeService {

    private static final String SOURCE = "request-bound";
    private static final String PARAM_LOADED_SKILLS = "loadedSkills";
    private static final String PARAM_AVAILABLE_SKILLS = "availableSkills";
    private static final String PARAM_CURRENT_RUNTIME_SKILL = "currentRuntimeSkillName";
    private static final String LOAD_SKILL_CONTENT_TOOL_NAME = "loadSkillContent";

    private final SkillCatalogService skillCatalogService;
    private final RuntimeSkillStateContractSupport runtimeSkillStateContractSupport;

    public RequestScopedSkillRuntimeService(SkillCatalogService skillCatalogService) {
        this(skillCatalogService, null);
    }

    @Autowired
    public RequestScopedSkillRuntimeService(
            SkillCatalogService skillCatalogService, RuntimeSkillStateContractSupport runtimeSkillStateContractSupport) {
        this.skillCatalogService = skillCatalogService;
        this.runtimeSkillStateContractSupport = runtimeSkillStateContractSupport;
    }

    public SkillKit buildSkillKit(ChatRuntimePreparedRequest prepared) {
        SimpleSkillBox skillBox = new SimpleSkillBox();
        SkillKit delegate = DefaultSkillKit.builder()
                .skillBox(skillBox)
                .poolManager(new DefaultSkillPoolManager())
                .build();
        TrackingSkillKit skillKit = new TrackingSkillKit(
                delegate,
                prepared == null ? List.<RuntimeSkillDescriptor>of() : prepared.availableSkills(),
                resolveCurrentRuntimeSkillName(
                        prepared == null ? null : prepared.paramsJson(),
                        prepared == null ? List.<RuntimeSkillDescriptor>of() : prepared.availableSkills(),
                        prepared == null ? null : prepared.runtimeSkillName()));
        for (RuntimeSkillDescriptor descriptor :
                prepared == null ? List.<RuntimeSkillDescriptor>of() : prepared.availableSkills()) {
            if (descriptor == null || !StringUtils.hasText(descriptor.runtimeSkillName())) {
                continue;
            }
            RequestScopedSkill skill = resolveRequestScopedSkill(descriptor);
            if (skill == null) {
                continue;
            }
            SkillMetadata metadata = SkillMetadata.builder(
                            descriptor.runtimeSkillName(),
                            StringUtils.hasText(descriptor.description()) ? descriptor.description().trim() : "",
                            SOURCE)
                    .extension("displayName", descriptor.displayName())
                    .extension("skillId", descriptor.skillId())
                    .build();
            skillKit.register(metadata, () -> skill);
        }

        Set<String> loadedSkillNames = new LinkedHashSet<>();
        for (RuntimeLoadedSkill loadedSkill :
                prepared == null ? List.<RuntimeLoadedSkill>of() : prepared.loadedSkills()) {
            if (loadedSkill != null && StringUtils.hasText(loadedSkill.runtimeSkillName())) {
                loadedSkillNames.add(loadedSkill.runtimeSkillName().trim());
            }
        }
        for (String runtimeSkillName : loadedSkillNames) {
            if (!skillKit.exists(runtimeSkillName)) {
                log.warn(
                        "[运行时画像] 已恢复技能未注册到当前请求上下文：sessionId={}, runtimeSkillName={}",
                        prepared == null ? null : prepared.sessionId(),
                        runtimeSkillName);
                continue;
            }
        }
        String currentRuntimeSkillName = resolveCurrentRuntimeSkillName(skillKit, prepared);
        if (StringUtils.hasText(currentRuntimeSkillName)) {
            skillKit.setCurrentRuntimeSkillName(currentRuntimeSkillName);
        }
        log.debug(
                "[运行时画像] SkillKit已构建：sessionId={}, executionModeHint={}, loadedSkills={}, currentRuntimeSkillName={}, activatedSkills={}",
                prepared == null ? null : prepared.sessionId(),
                resolveExecutionModeHint(prepared == null ? null : prepared.paramsJson()),
                loadedSkillNames,
                currentRuntimeSkillName,
                skillKit.getActivatedSkillNames());
        return skillKit;
    }

    private RequestScopedSkill resolveRequestScopedSkill(RuntimeSkillDescriptor descriptor) {
        if (descriptor == null
                || !StringUtils.hasText(descriptor.runtimeSkillName())
                || descriptor.skillId() == null
                || skillCatalogService == null) {
            return null;
        }
        try {
            SkillCatalogService.SkillChatContext context =
                    skillCatalogService.resolveSkillChatContextForPublished(descriptor.skillId(), null, null);
            Skill sourceSkill = skillCatalogService.getRuntimeSkill(descriptor.skillId());
            return new RequestScopedSkill(
                    descriptor.runtimeSkillName(),
                    StringUtils.hasText(descriptor.description()) ? descriptor.description().trim() : "",
                    buildLoadableSkillContent(descriptor, sourceSkill == null ? null : sourceSkill.getContent()),
                    context.toolCallbacks(),
                    loadReferences(sourceSkill));
        } catch (Exception ex) {
            log.warn(
                    "[运行时画像] 请求级技能上下文构建失败，降级为 runtime skill 直连：skillId={}, runtimeSkillName={}, error={}",
                    descriptor.skillId(),
                    descriptor.runtimeSkillName(),
                    ex.getMessage());
            try {
                Skill sourceSkill = skillCatalogService.getRuntimeSkill(descriptor.skillId());
                List<ToolCallback> toolCallbacks = sourceSkill.getTools() == null ? List.of() : List.copyOf(sourceSkill.getTools());
                SkillCatalogService.SkillChatContext fallbackContext = new SkillCatalogService.SkillChatContext(
                        descriptor.skillId(),
                        descriptor.runtimeSkillName(),
                        firstNonBlank(descriptor.displayName(), descriptor.runtimeSkillName()),
                        StringUtils.hasText(descriptor.description()) ? descriptor.description().trim() : "",
                        sourceSkill.getContent(),
                        toolCallbacks,
                        false,
                        null);
                return new RequestScopedSkill(
                        descriptor.runtimeSkillName(),
                        StringUtils.hasText(descriptor.description()) ? descriptor.description().trim() : "",
                        buildLoadableSkillContent(descriptor, sourceSkill.getContent()),
                        toolCallbacks,
                        loadReferences(sourceSkill));
            } catch (Exception fallbackEx) {
                log.error(
                        "[运行时画像] 请求级技能注册失败：skillId={}, runtimeSkillName={}, error={}",
                        descriptor.skillId(),
                        descriptor.runtimeSkillName(),
                        fallbackEx.getMessage(),
                        fallbackEx);
                return null;
            }
        }
    }

    public List<RuntimeLoadedSkill> resolveLoadedSkillsFromParams(
            String paramsJson, List<RuntimeSkillDescriptor> availableSkills) {
        List<String> loadedSkillNames = resolveLoadedSkillNames(paramsJson);
        if (loadedSkillNames.isEmpty()) {
            return List.of();
        }
        Map<String, RuntimeSkillDescriptor> descriptorsByName = new LinkedHashMap<>();
        for (RuntimeSkillDescriptor descriptor :
                availableSkills == null ? List.<RuntimeSkillDescriptor>of() : availableSkills) {
            if (descriptor != null && StringUtils.hasText(descriptor.runtimeSkillName())) {
                descriptorsByName.put(descriptor.runtimeSkillName().trim(), descriptor);
            }
        }
        List<RuntimeLoadedSkill> result = new ArrayList<>();
        for (String runtimeSkillName : loadedSkillNames) {
            RuntimeSkillDescriptor descriptor = descriptorsByName.get(runtimeSkillName);
            if (descriptor != null) {
                result.add(new RuntimeLoadedSkill(
                        descriptor.skillId(),
                        descriptor.runtimeSkillName(),
                        descriptor.displayName(),
                        descriptor.description()));
            }
        }
        return List.copyOf(result);
    }

    public String resolveCurrentRuntimeSkillName(SkillKit skillKit, ChatRuntimePreparedRequest prepared) {
        if (skillKit instanceof TrackingSkillKit trackingSkillKit) {
            String tracked = trackingSkillKit.currentRuntimeSkillName();
            if (StringUtils.hasText(tracked)) {
                return tracked.trim();
            }
        }
        return resolveCurrentRuntimeSkillName(
                prepared == null ? null : prepared.paramsJson(),
                prepared == null ? List.<RuntimeSkillDescriptor>of() : prepared.availableSkills(),
                prepared == null ? null : prepared.runtimeSkillName());
    }

    public String resolveCurrentRuntimeSkillName(
            String paramsJson, List<RuntimeSkillDescriptor> availableSkills, String fallbackRuntimeSkillName) {
        Set<String> knownSkillNames = resolveKnownSkillNames(availableSkills);
        if (runtimeSkillStateContractSupport != null && StringUtils.hasText(paramsJson)) {
            String explicit = validateCurrentRuntimeSkillName(
                    runtimeSkillStateContractSupport.readCurrentRuntimeSkillName(paramsJson), knownSkillNames);
            if (StringUtils.hasText(explicit)) {
                return explicit;
            }
            List<String> loadedSkillNames = runtimeSkillStateContractSupport.readLoadedSkillNames(paramsJson);
            for (int i = loadedSkillNames.size() - 1; i >= 0; i--) {
                String restored = validateCurrentRuntimeSkillName(loadedSkillNames.get(i), knownSkillNames);
                if (StringUtils.hasText(restored)) {
                    return restored;
                }
            }
        }
        return validateCurrentRuntimeSkillName(fallbackRuntimeSkillName, knownSkillNames);
    }

    public String mergeSkillStateParams(
            String paramsJson,
            List<RuntimeSkillDescriptor> availableSkills,
            List<RuntimeLoadedSkill> loadedSkills,
            String currentRuntimeSkillName) {
        List<String> loadedSkillNames = new ArrayList<>();
        for (RuntimeLoadedSkill loadedSkill : loadedSkills == null ? List.<RuntimeLoadedSkill>of() : loadedSkills) {
            if (loadedSkill != null && StringUtils.hasText(loadedSkill.runtimeSkillName())) {
                loadedSkillNames.add(loadedSkill.runtimeSkillName().trim());
            }
        }
        String normalizedCurrent =
                validateCurrentRuntimeSkillName(currentRuntimeSkillName, resolveKnownSkillNames(availableSkills));
        if (runtimeSkillStateContractSupport != null) {
            RuntimeSkillStateContract existing = runtimeSkillStateContractSupport.readContractFromParams(paramsJson);
            String merged = runtimeSkillStateContractSupport.mergeContractIntoParams(
                    paramsJson,
                    runtimeSkillStateContractSupport.withRoutingState(
                            existing,
                            loadedSkillNames,
                            normalizedCurrent,
                            existing.selectedSkillHintId(),
                            existing.selectedSkillHintRuntimeSkillName(),
                            existing.mentionedSkillId()));
            Map<String, Object> payload = parseParamsPayload(merged);
            payload.put(PARAM_AVAILABLE_SKILLS, availableSkills == null ? List.of() : List.copyOf(availableSkills));
            return JSON.toJSONString(payload);
        }
        Map<String, Object> payload = parseParamsPayload(paramsJson);
        payload.put(PARAM_AVAILABLE_SKILLS, availableSkills == null ? List.of() : List.copyOf(availableSkills));
        payload.put(PARAM_LOADED_SKILLS, loadedSkillNames);
        if (StringUtils.hasText(normalizedCurrent)) {
            payload.put(PARAM_CURRENT_RUNTIME_SKILL, normalizedCurrent);
        } else {
            payload.remove(PARAM_CURRENT_RUNTIME_SKILL);
        }
        return JSON.toJSONString(payload);
    }

    public String mergeSkillStateParams(
            String paramsJson, List<RuntimeSkillDescriptor> availableSkills, List<RuntimeLoadedSkill> loadedSkills) {
        return mergeSkillStateParams(paramsJson, availableSkills, loadedSkills, null);
    }

    private String resolveExecutionModeHint(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return "";
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (parsed == null || parsed.isEmpty()) {
                return "";
            }
            Object value = parsed.get("executionModeHint");
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildLoadableSkillContent(RuntimeSkillDescriptor descriptor, String rawSkillContent) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 技能：").append(firstNonBlank(descriptor.displayName(), descriptor.runtimeSkillName()));
        builder.append("\n\n");
        builder.append("- 运行时技能名：`").append(descriptor.runtimeSkillName()).append("`\n");
        if (StringUtils.hasText(descriptor.description())) {
            builder.append("- 描述：").append(descriptor.description().trim()).append("\n");
        }
        builder.append("- 说明：该技能已按需加载，但这只表示当前轮已读取技能正文，不表示任务已经执行完成。\n");
        builder.append("- 提示：只要本轮仍需使用该技能的专属工具、脚本、数据集或生成产物，就必须严格按技能定义的顺序继续执行，不要把历史加载状态或中间查询结果当成当前轮已完成结果。\n");
        builder.append("- 完成条件：若技能定义了查询后生成右侧预览、HTML artifact、报表或其他结构化产物，则必须执行到这些最终步骤成功后，才能回复完成；中间查询成功不等于任务完成。\n");
        builder.append("- 约束：实际可调用工具、入参与固定步骤以当前 runtime 暴露的 tool definitions 和下方技能核心说明为准。\n");
        String normalizedSkillContent = sanitizeLoadedSkillContent(rawSkillContent);
        if (StringUtils.hasText(normalizedSkillContent)) {
            builder.append("\n## 技能核心说明\n\n").append(normalizedSkillContent);
        }
        return builder.toString().trim();
    }

    private String sanitizeLoadedSkillContent(String rawSkillContent) {
        if (!StringUtils.hasText(rawSkillContent)) {
            return "";
        }
        String normalized = rawSkillContent.replace("\r\n", "\n").trim();
        String rawMarker = "Follow the skill instructions below. Use available tools only when needed.";
        int rawMarkerIndex = normalized.indexOf(rawMarker);
        if (rawMarkerIndex >= 0) {
            normalized = normalized.substring(rawMarkerIndex + rawMarker.length()).trim();
        }
        String instructionHeader = "## 技能使用说明";
        int instructionHeaderIndex = normalized.indexOf(instructionHeader);
        if (instructionHeaderIndex >= 0) {
            normalized = normalized
                    .substring(instructionHeaderIndex + instructionHeader.length())
                    .trim();
        }
        return normalized;
    }

    private Map<String, String> loadReferences(Skill skill) {
        if (skill == null || !skill.supports(ReferencesLoader.class)) {
            return Map.of();
        }
        try {
            Map<String, String> references = skill.as(ReferencesLoader.class).getReferences();
            if (references == null || references.isEmpty()) {
                return Map.of();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(references));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public List<RuntimeLoadedSkill> extractLoadedSkills(
            SkillKit skillKit, List<RuntimeSkillDescriptor> availableSkills) {
        if (skillKit == null || availableSkills == null || availableSkills.isEmpty()) {
            return List.of();
        }
        Set<String> activated = skillKit.getActivatedSkillNames();
        if (activated == null || activated.isEmpty()) {
            return List.of();
        }
        List<RuntimeLoadedSkill> result = new ArrayList<>();
        for (RuntimeSkillDescriptor descriptor : availableSkills) {
            if (descriptor == null || !StringUtils.hasText(descriptor.runtimeSkillName())) {
                continue;
            }
            if (!activated.contains(descriptor.runtimeSkillName().trim())) {
                continue;
            }
            result.add(new RuntimeLoadedSkill(
                    descriptor.skillId(),
                    descriptor.runtimeSkillName(),
                    descriptor.displayName(),
                    descriptor.description()));
        }
        return List.copyOf(result);
    }

    public List<SkillReadFact> resolveSkillReadFactsFromParams(String paramsJson) {
        if (runtimeSkillStateContractSupport == null) {
            return List.of();
        }
        return runtimeSkillStateContractSupport.readSkillReadFactsFromParams(paramsJson).stream()
                .map(fact -> new SkillReadFact(
                        fact.skillName(), fact.displayName(), fact.message(), fact.toolCallId()))
                .toList();
    }

    public List<SkillReadFact> extractSkillReadFacts(
            List<Map<String, Object>> toolEvents, List<RuntimeSkillDescriptor> availableSkills) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayNamesBySkill = new LinkedHashMap<>();
        for (RuntimeSkillDescriptor descriptor :
                availableSkills == null ? List.<RuntimeSkillDescriptor>of() : availableSkills) {
            if (descriptor == null || !StringUtils.hasText(descriptor.runtimeSkillName())) {
                continue;
            }
            displayNamesBySkill.put(
                    descriptor.runtimeSkillName().trim(),
                    firstNonBlank(descriptor.displayName(), descriptor.runtimeSkillName()));
        }
        Map<String, PendingSkillReadFact> pendingByToolCallId = new LinkedHashMap<>();
        Map<String, SkillReadFact> factsBySkillName = new LinkedHashMap<>();
        for (Map<String, Object> toolEvent : toolEvents) {
            if (toolEvent == null) {
                continue;
            }
            String eventType = normalizeSkillText(toolEvent.get("type"));
            Object rawContent = toolEvent.get("content");
            if (!(rawContent instanceof Map<?, ?> contentMap)) {
                continue;
            }
            String toolName = normalizeSkillText(contentMap.get("name"));
            if (!LOAD_SKILL_CONTENT_TOOL_NAME.equalsIgnoreCase(toolName)) {
                continue;
            }
            String toolCallId = normalizeSkillText(contentMap.get("id"));
            String skillName = extractSkillName(contentMap.get("arguments"));
            if ("tool".equalsIgnoreCase(eventType)) {
                if (!StringUtils.hasText(skillName)) {
                    continue;
                }
                pendingByToolCallId.put(toolCallId, new PendingSkillReadFact(skillName));
                continue;
            }
            if (!"result".equalsIgnoreCase(eventType)) {
                continue;
            }
            if (!isSuccessfulSkillReadResponse(contentMap.get("response"))) {
                continue;
            }
            PendingSkillReadFact pending = pendingByToolCallId.get(toolCallId);
            String resolvedSkillName =
                    StringUtils.hasText(skillName) ? skillName : pending == null ? "" : pending.skillName();
            if (!StringUtils.hasText(resolvedSkillName)) {
                continue;
            }
            String displayName = firstNonBlank(displayNamesBySkill.get(resolvedSkillName), resolvedSkillName);
            factsBySkillName.put(
                    resolvedSkillName,
                    new SkillReadFact(
                            resolvedSkillName,
                            displayName,
                            buildSkillReadFactMessage(resolvedSkillName, displayName),
                            toolCallId));
        }
        return List.copyOf(factsBySkillName.values());
    }

    private String firstNonBlank(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left.trim();
        }
        return StringUtils.hasText(right) ? right.trim() : "";
    }

    private String normalizeSkillName(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }

    private String normalizeSkillText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }

    private String extractSkillName(Object rawArguments) {
        String arguments = normalizeSkillText(rawArguments);
        if (!StringUtils.hasText(arguments)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(arguments, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            return normalizeSkillName(payload.get("skillName"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isSuccessfulSkillReadResponse(Object rawResponse) {
        String response = normalizeSkillText(rawResponse);
        if (!StringUtils.hasText(response)) {
            return false;
        }
        if (response.startsWith("Error:")) {
            return false;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(response, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return true;
            }
            if (payload.containsKey("error")) {
                return false;
            }
            Object message = payload.get("message");
            if (message != null
                    && String.valueOf(message).toLowerCase(Locale.ROOT).contains("not found")) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    private String buildSkillReadFactMessage(String skillName, String displayName) {
        StringBuilder builder = new StringBuilder();
        builder.append("[技能读取事实] 上一轮已读取技能 `").append(skillName).append("`");
        if (StringUtils.hasText(displayName) && !displayName.trim().equals(skillName.trim())) {
            builder.append("（").append(displayName.trim()).append("）");
        }
        builder.append("。若本轮仍使用该技能，请重新调用 `loadSkillContent(")
                .append(skillName)
                .append(")` 获取当前轮权威说明。");
        return builder.toString();
    }

    private Set<String> resolveKnownSkillNames(List<RuntimeSkillDescriptor> availableSkills) {
        Set<String> knownSkillNames = new LinkedHashSet<>();
        for (RuntimeSkillDescriptor descriptor :
                availableSkills == null ? List.<RuntimeSkillDescriptor>of() : availableSkills) {
            if (descriptor != null && StringUtils.hasText(descriptor.runtimeSkillName())) {
                knownSkillNames.add(descriptor.runtimeSkillName().trim());
            }
        }
        return knownSkillNames;
    }

    private String validateCurrentRuntimeSkillName(Object value, Set<String> knownSkillNames) {
        String normalized = normalizeSkillName(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (knownSkillNames == null || knownSkillNames.isEmpty() || knownSkillNames.contains(normalized)) {
            return normalized;
        }
        return "";
    }

    private static final class TrackingSkillKit implements SkillKit {

        private final SkillKit delegate;
        private final List<RuntimeSkillDescriptor> availableSkills;
        private final AtomicReference<String> currentRuntimeSkillName;
        private final List<ToolCallback> skillLoaderTools;

        private TrackingSkillKit(
                SkillKit delegate,
                List<RuntimeSkillDescriptor> availableSkills,
                String initialCurrentRuntimeSkillName) {
            this.delegate = delegate;
            this.availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
            this.currentRuntimeSkillName = new AtomicReference<>(normalize(initialCurrentRuntimeSkillName));
            this.skillLoaderTools = List.of(ToolCallbacks.from(new TrackingSkillLoaderTools(this)));
        }

        @Override
        public void register(SkillMetadata metadata, java.util.function.Supplier<Skill> loader) {
            delegate.register(metadata, loader);
        }

        @Override
        public void register(Object instance) {
            delegate.register(instance);
        }

        @Override
        public void register(Class<?> skillClass) {
            delegate.register(skillClass);
        }

        @Override
        public boolean exists(String name) {
            return delegate.exists(name);
        }

        @Override
        public Skill getSkill(String name) {
            return delegate.getSkill(name);
        }

        @Override
        public SkillMetadata getMetadata(String name) {
            return delegate.getMetadata(name);
        }

        @Override
        public void activateSkill(String name) {
            delegate.activateSkill(name);
            setCurrentRuntimeSkillName(name);
        }

        @Override
        public void deactivateSkill(String name) {
            delegate.deactivateSkill(name);
            if (normalize(name).equals(currentRuntimeSkillName())) {
                setCurrentRuntimeSkillName("");
            }
        }

        @Override
        public void deactivateAllSkills() {
            delegate.deactivateAllSkills();
            setCurrentRuntimeSkillName("");
        }

        @Override
        public boolean isActivated(String name) {
            return delegate.isActivated(name);
        }

        @Override
        public List<ToolCallback> getSkillLoaderTools() {
            return skillLoaderTools;
        }

        @Override
        public List<ToolCallback> getAllActiveTools() {
            String currentSkillName = currentRuntimeSkillName();
            if (!StringUtils.hasText(currentSkillName) || !delegate.isActivated(currentSkillName)) {
                return delegate.getAllActiveTools();
            }
            Skill currentSkill = delegate.getSkill(currentSkillName);
            if (currentSkill == null) {
                return delegate.getAllActiveTools();
            }
            List<ToolCallback> currentTools = currentSkill.getTools();
            return currentTools == null ? List.of() : List.copyOf(currentTools);
        }

        @Override
        public String getSkillSystemPrompt() {
            return delegate.getSkillSystemPrompt();
        }

        @Override
        public Set<String> getActivatedSkillNames() {
            return delegate.getActivatedSkillNames();
        }

        private List<RuntimeSkillDescriptor> availableSkills() {
            return availableSkills;
        }

        private String currentRuntimeSkillName() {
            return currentRuntimeSkillName.get();
        }

        private void setCurrentRuntimeSkillName(String runtimeSkillName) {
            currentRuntimeSkillName.set(normalize(runtimeSkillName));
        }

        private static String normalize(String value) {
            return StringUtils.hasText(value) ? value.trim() : "";
        }
    }

    private record PendingSkillReadFact(String skillName) {}

    public record SkillReadFact(String skillName, String displayName, String message, String toolCallId) {}

    public RuntimeSkillReadFactContract toContract(SkillReadFact fact) {
        if (fact == null) {
            return null;
        }
        return new RuntimeSkillReadFactContract(fact.skillName(), fact.displayName(), fact.message(), fact.toolCallId());
    }

    public String mergeSkillHintParams(
            String paramsJson, Long mentionedSkillId, Long selectedSkillHintId, String selectedSkillHintRuntimeSkillName) {
        if (runtimeSkillStateContractSupport == null) {
            return paramsJson;
        }
        RuntimeSkillStateContract existing = runtimeSkillStateContractSupport.readContractFromParams(paramsJson);
        return runtimeSkillStateContractSupport.mergeContractIntoParams(
                paramsJson,
                runtimeSkillStateContractSupport.withRoutingState(
                        existing,
                        existing.loadedSkillNames(),
                        existing.currentRuntimeSkillName(),
                        selectedSkillHintId,
                        selectedSkillHintRuntimeSkillName,
                        mentionedSkillId));
    }

    public String resolveSelectedSkillHintRuntimeSkillName(String paramsJson) {
        if (runtimeSkillStateContractSupport == null) {
            return "";
        }
        return runtimeSkillStateContractSupport.readSelectedSkillHintRuntimeSkillName(paramsJson);
    }

    public Long resolveSelectedSkillHintId(String paramsJson) {
        if (runtimeSkillStateContractSupport == null) {
            return null;
        }
        return runtimeSkillStateContractSupport.readSelectedSkillHintId(paramsJson);
    }

    public Long resolveMentionedSkillId(String paramsJson) {
        if (runtimeSkillStateContractSupport == null) {
            return null;
        }
        return runtimeSkillStateContractSupport.readMentionedSkillId(paramsJson);
    }

    public List<String> resolveLoadedSkillNames(String paramsJson) {
        if (runtimeSkillStateContractSupport == null) {
            return List.of();
        }
        return runtimeSkillStateContractSupport.readLoadedSkillNames(paramsJson);
    }

    private Map<String, Object> parseParamsPayload(String paramsJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!StringUtils.hasText(paramsJson)) {
            return payload;
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (parsed != null) {
                payload.putAll(parsed);
            }
        } catch (Exception ignored) {
            // rebuild payload
        }
        return payload;
    }

    private static final class TrackingSkillLoaderTools {

        private final TrackingSkillKit skillKit;

        private TrackingSkillLoaderTools(TrackingSkillKit skillKit) {
            this.skillKit = skillKit;
        }

        @Tool(
                description =
                        "Load the content of a skill by its runtime skill name. "
                                + "This activates the skill for the current runtime and returns its documentation. "
                                + "Use a skill name returned by listActiveSkills() when you need to understand what the skill does and how to use it. "
                                + "If the user explicitly names a skill, prefer loading that exact runtime skill name directly. "
                                + "If this tool reports skill not found, do not keep guessing similar names repeatedly: "
                                + "first explain to the user that the skill is unavailable, then either confirm the name or choose another valid skill from listActiveSkills().")
        public String loadSkillContent(@ToolParam(description = "The name of the skill to load") String skillName) {
            if (!StringUtils.hasText(skillName)) {
                return "Error: Missing or empty skill name";
            }
            String normalizedSkillName = skillName.trim();
            if (!skillKit.exists(normalizedSkillName)) {
                return buildSkillNotFoundPayload(normalizedSkillName);
            }
            skillKit.activateSkill(normalizedSkillName);
            Skill skill = skillKit.getSkill(normalizedSkillName);
            if (skill == null) {
                return "Error: Skill not found: " + normalizedSkillName;
            }
            return skill.getContent();
        }

        @Tool(
                description =
                        "ONLY use this tool when a skill's content explicitly mentions it has reference materials. "
                                + "Load a specific reference from a skill using the reference key mentioned in the skill's content. "
                                + "Returns reference content (URL, file path, or text string). "
                                + "Do NOT use this for regular skill operations - use skill's own tools instead.")
        public String loadSkillReference(
                @ToolParam(description = "The skill name that has references") String skillName,
                @ToolParam(description = "The exact reference key mentioned in the skill's content")
                        String referenceKey) {
            if (!StringUtils.hasText(skillName)) {
                return "Error: Missing or empty skill name";
            }
            if (!StringUtils.hasText(referenceKey)) {
                return "Error: Missing or empty reference key";
            }
            String normalizedSkillName = skillName.trim();
            if (!skillKit.exists(normalizedSkillName)) {
                return buildSkillNotFoundPayload(normalizedSkillName);
            }
            Skill skill = skillKit.getSkill(normalizedSkillName);
            if (skill == null) {
                return "Error: Skill not found: " + normalizedSkillName;
            }
            if (!skill.supports(ReferencesLoader.class)) {
                return "Error: Skill '" + normalizedSkillName + "' does not have references. ";
            }
            ReferencesLoader loader = skill.as(ReferencesLoader.class);
            Map<String, String> references = loader.getReferences();
            if (references == null || !references.containsKey(referenceKey.trim())) {
                return "Error: Reference key '" + referenceKey.trim() + "' not found in skill '"
                        + normalizedSkillName
                        + "'. Available keys: "
                        + (references == null ? List.of() : references.keySet());
            }
            return references.get(referenceKey.trim());
        }

        @Tool(
                description =
                        "List all skills currently available to the user for this conversation, "
                                + "and indicate which ones are already loaded in the current runtime. "
                                + "Use this to discover valid runtime skill names before calling loadSkillContent(skillName). "
                                + "If the user mentions a skill that is not in this list, treat it as unavailable for this conversation: "
                                + "first tell the user it was not found, then ask them to confirm the exact name or choose another listed skill instead of guessing aliases repeatedly.")
        public String listActiveSkills() {
            List<RuntimeSkillDescriptor> available = skillKit.availableSkills();
            Set<String> loaded = skillKit.getActivatedSkillNames();
            Set<String> loadedSet = loaded == null ? Set.of() : loaded;
            List<Map<String, Object>> availableSkills = new ArrayList<>();
            for (RuntimeSkillDescriptor descriptor : available) {
                if (descriptor == null || !StringUtils.hasText(descriptor.runtimeSkillName())) {
                    continue;
                }
                String runtimeSkillName = descriptor.runtimeSkillName().trim();
                SkillMetadata metadata = skillKit.getMetadata(runtimeSkillName);
                boolean isLoaded = loadedSet.contains(runtimeSkillName);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skillId", descriptor.skillId());
                item.put("name", runtimeSkillName);
                item.put("displayName", descriptor.displayName());
                item.put(
                        "description",
                        StringUtils.hasText(descriptor.description())
                                ? descriptor.description().trim()
                                : metadata == null ? "" : metadata.getDescription());
                item.put("source", metadata == null ? "" : metadata.getSource());
                item.put("loaded", isLoaded);
                item.put("current", runtimeSkillName.equals(skillKit.currentRuntimeSkillName()));
                availableSkills.add(item);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("availableSkills", availableSkills);
            payload.put(
                    "loadedSkills",
                    availableSkills.stream()
                            .filter(item -> Boolean.TRUE.equals(item.get("loaded")))
                            .map(item -> item.get("name"))
                            .toList());
            payload.put("currentRuntimeSkillName", skillKit.currentRuntimeSkillName());
            payload.put(
                    "note",
                    "这里列出的是当前用户在本次会话可用的全部技能；`loaded=true` 仅表示该技能已经在当前 runtime 中加载，后续每轮仍需重新做路由判定，确认仍命中该技能后才可以继续沿用其专属工具。");
            payload.put(
                    "selectionGuide",
                    "优先使用这里返回的精确 runtime skill name。若用户点名的 skill 不在列表中，应先明确告知未找到，再让用户确认名称或改选列表中的 skill，不要连续猜测近似名字；若本轮追问仍属于已加载 skill，先按本轮问题重新判定，再决定是否直接沿用，不要把历史加载状态当成自动绑定。");
            return JSON.toJSONString(payload);
        }

        private String buildSkillNotFoundPayload(String skillName) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "SKILL_NOT_FOUND");
            payload.put("skillName", skillName);
            payload.put("message", "当前用户在本次会话可用技能中不存在该 runtime skill name。");
            payload.put(
                    "nextAction",
                    "Do not keep retrying guessed aliases. If the user explicitly named this skill, first tell the user the skill is unavailable, then ask them to confirm the exact name or choose another valid skill from listActiveSkills(). If the user did not specify an exact skill, call listActiveSkills() and select the closest matching listed skill before retrying.");
            payload.put("userFacingHint", "我先确认了一下当前可用技能，你提到的这个 skill 目前不在可用列表里。");
            return JSON.toJSONString(payload);
        }
    }

    private static final class RequestScopedSkill implements Skill, ReferencesLoader {

        private final SkillMetadata metadata;
        private final String content;
        private final List<ToolCallback> tools;
        private final Map<String, String> references;

        private RequestScopedSkill(
                String runtimeSkillName,
                String description,
                String content,
                List<ToolCallback> tools,
                Map<String, String> references) {
            this.metadata = SkillMetadata.builder(
                            runtimeSkillName, StringUtils.hasText(description) ? description : runtimeSkillName, SOURCE)
                    .build();
            this.content = content;
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            this.references = references == null ? Map.of() : Map.copyOf(references);
        }

        @Override
        public SkillMetadata getMetadata() {
            return metadata;
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public List<ToolCallback> getTools() {
            return tools;
        }

        @Override
        public Map<String, String> getReferences() {
            return references;
        }
    }
}
