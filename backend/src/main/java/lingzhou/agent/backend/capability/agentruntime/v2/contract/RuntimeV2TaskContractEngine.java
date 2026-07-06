package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RequestHints;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2TaskContractEngine {

    private final RuntimeV2SkillContractResolver skillContractResolver;

    public RuntimeV2TaskContractEngine(RuntimeV2SkillContractResolver skillContractResolver) {
        this.skillContractResolver = skillContractResolver;
    }

    public RuntimeV2TaskContract resolve(RuntimeV2State state) {
        List<RuntimeV2SkillContract> activeSkillContracts = skillContractResolver.resolveActiveContracts(state);
        List<RuntimeV2TaskIntent> intents = inferIntents(state);
        List<RuntimeV2ExecutionRequirement> activeRequirements = new ArrayList<>();
        for (RuntimeV2SkillContract skillContract : activeSkillContracts) {
            if (skillContract == null || skillContract.requirements().isEmpty()) {
                continue;
            }
            for (RuntimeV2ExecutionRequirement requirement : skillContract.requirements()) {
                if (requirement != null && requirement.isActivatedBy(intents)) {
                    activeRequirements.add(requirement);
                }
            }
        }
        for (RuntimeV2TaskIntent uncoveredIntent : resolveUncoveredIntents(intents, activeSkillContracts)) {
            activeRequirements.add(buildSkillCoverageRequirement(uncoveredIntent));
        }
        if (shouldRequireArtifactDelivery(state, intents)) {
            activeRequirements.add(new RuntimeV2ExecutionRequirement(
                    "artifact.publish.required",
                    "交付物闭环",
                    RuntimeV2CompletionBlockerSource.USER,
                    "当前任务属于报告、页面或产物交付，最终结果需要以 artifact 形式发布。",
                    "继续生成并发布最终产物。",
                    List.of("artifact.published"),
                    RuntimeV2EvidenceMatchMode.ALL_OF,
                    List.of("file_write", "run_python", "write_artifact"),
                    List.of(RuntimeV2TaskIntent.ARTIFACT_DELIVERY)));
        }
        return new RuntimeV2TaskContract(intents, activeSkillContracts, activeRequirements);
    }

    private List<RuntimeV2TaskIntent> resolveUncoveredIntents(
            List<RuntimeV2TaskIntent> intents, List<RuntimeV2SkillContract> activeSkillContracts) {
        if (intents == null || intents.isEmpty()) {
            return List.of();
        }
        Set<RuntimeV2ContractCapability> coveredCapabilities = new LinkedHashSet<>();
        for (RuntimeV2SkillContract contract :
                activeSkillContracts == null ? List.<RuntimeV2SkillContract>of() : activeSkillContracts) {
            if (contract == null || contract.capabilities() == null) {
                continue;
            }
            coveredCapabilities.addAll(contract.capabilities());
        }
        List<RuntimeV2TaskIntent> uncovered = new ArrayList<>();
        for (RuntimeV2TaskIntent intent : intents) {
            if (intent == null || isIntentCoveredByCapabilities(intent, coveredCapabilities)) {
                continue;
            }
            if (!uncovered.contains(intent)) {
                uncovered.add(intent);
            }
        }
        return List.copyOf(uncovered);
    }

    private RuntimeV2ExecutionRequirement buildSkillCoverageRequirement(RuntimeV2TaskIntent intent) {
        String normalizedIntent = intent == null ? "unknown" : intent.name().toLowerCase(Locale.ROOT);
        return new RuntimeV2ExecutionRequirement(
                "skill.intent.covered." + normalizedIntent,
                "技能覆盖闭环",
                RuntimeV2CompletionBlockerSource.SKILL,
                "当前请求仍包含未被已加载技能覆盖的 " + (intent == null ? "未知" : intent.name()) + " 意图。",
                "先调用 listActiveSkills 确认可用技能，再调用 loadSkillContent(skillName) 追加或切换到对应 skill。",
                List.of(intentCoverageEvidenceCode(intent)),
                RuntimeV2EvidenceMatchMode.ALL_OF,
                List.of("listActiveSkills", "loadSkillContent"),
                intent == null ? List.of() : List.of(intent));
    }

    private String intentCoverageEvidenceCode(RuntimeV2TaskIntent intent) {
        return "skill.intent.covered."
                + (intent == null ? "unknown" : intent.name().toLowerCase(Locale.ROOT));
    }

    private boolean isIntentCoveredByCapabilities(
            RuntimeV2TaskIntent intent, Set<RuntimeV2ContractCapability> coveredCapabilities) {
        if (intent == null) {
            return true;
        }
        if (intent == RuntimeV2TaskIntent.ARTIFACT_DELIVERY) {
            return true;
        }
        if (coveredCapabilities == null || coveredCapabilities.isEmpty()) {
            return false;
        }
        return switch (intent) {
            case POLICY_QA -> coveredCapabilities.contains(RuntimeV2ContractCapability.KNOWLEDGE_BASE);
            case DATA_QUERY -> coveredCapabilities.contains(RuntimeV2ContractCapability.DATASET_QUERY);
            case ARTIFACT_DELIVERY -> true;
        };
    }

    private List<RuntimeV2TaskIntent> inferIntents(RuntimeV2State state) {
        Set<RuntimeV2TaskIntent> intents = new LinkedHashSet<>();
        String userMessage = state == null || state.prepared() == null
                ? ""
                : normalize(state.prepared().userMessage());
        if (containsAny(userMessage, "标准", "规定", "制度", "政策", "流程", "要求", "报销标准")) {
            intents.add(RuntimeV2TaskIntent.POLICY_QA);
        }
        if (containsAny(userMessage, "总额", "金额", "多少", "统计", "汇总", "占比", "排名", "top", "趋势")) {
            intents.add(RuntimeV2TaskIntent.DATA_QUERY);
        }
        if (containsAny(userMessage, "报告", "html", "页面", "图表", "可视化", "导出", "产物", "原型")) {
            intents.add(RuntimeV2TaskIntent.ARTIFACT_DELIVERY);
        }
        if (state != null && state.mode() == RuntimeV2Mode.CODE) {
            intents.add(RuntimeV2TaskIntent.ARTIFACT_DELIVERY);
        }
        for (String toolName : extractObservedToolNames(state)) {
            if (toolName.startsWith("knowledge_base.")) {
                intents.add(RuntimeV2TaskIntent.POLICY_QA);
            }
            if (toolName.contains(".get_dataset_schema")
                    || toolName.contains(".execute_dataset_sql")
                    || toolName.contains(".search_dataset_summary")) {
                intents.add(RuntimeV2TaskIntent.DATA_QUERY);
            }
            if ("file_write".equals(toolName) || "run_python".equals(toolName) || "write_artifact".equals(toolName)) {
                intents.add(RuntimeV2TaskIntent.ARTIFACT_DELIVERY);
            }
        }
        return List.copyOf(intents);
    }

    private boolean shouldRequireArtifactDelivery(RuntimeV2State state, List<RuntimeV2TaskIntent> intents) {
        if (state != null
                && RuntimeV2RequestHints.readBooleanFlag(
                        state.prepared() == null ? null : state.prepared().paramsJson(), "artifactRequired")) {
            return true;
        }
        if (intents != null && intents.contains(RuntimeV2TaskIntent.ARTIFACT_DELIVERY)) {
            return true;
        }
        if (state == null) {
            return false;
        }
        return extractObservedToolNames(state).stream()
                .anyMatch(toolName -> "file_write".equals(toolName)
                        || "run_python".equals(toolName)
                        || "write_artifact".equals(toolName));
    }

    private List<String> extractObservedToolNames(RuntimeV2State state) {
        if (state == null || state.observationTrace().isEmpty()) {
            return List.of();
        }
        List<String> toolNames = new ArrayList<>();
        for (var item : state.observationTrace()) {
            Object value = item.get("toolName");
            if (value == null) {
                continue;
            }
            String toolName = normalize(String.valueOf(value));
            if (StringUtils.hasText(toolName)) {
                toolNames.add(toolName);
            }
        }
        return List.copyOf(toolNames);
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
