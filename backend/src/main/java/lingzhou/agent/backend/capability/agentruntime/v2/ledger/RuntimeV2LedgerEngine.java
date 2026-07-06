package lingzhou.agent.backend.capability.agentruntime.v2.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionEvidenceSource;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractCapability;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ExecutionRequirement;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContractEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskIntent;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2LedgerEngine {

    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final RuntimeV2TaskContractEngine taskContractEngine;

    public RuntimeV2LedgerEngine(
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            RuntimeV2TaskContractEngine taskContractEngine) {
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.taskContractEngine = taskContractEngine;
    }

    public void recordToolSuccess(RuntimeV2State state, String toolName, String toolResult) {
        if (state == null) {
            return;
        }
        String normalizedToolName = normalize(toolName);
        if (normalizedToolName.startsWith("knowledge_base.")) {
            upsertEvidence(
                    state,
                    "knowledge.base.hit",
                    "知识库依据",
                    RuntimeV2CompletionEvidenceSource.KNOWLEDGE_BASE,
                    RuntimeV2EvidenceStatus.SATISFIED,
                    "已获取知识库查询结果。",
                    toolName);
        }
        if (normalizedToolName.contains(".search_dataset_summary")) {
            upsertEvidence(
                    state,
                    "dataset.summary.known",
                    "数据集摘要",
                    RuntimeV2CompletionEvidenceSource.TOOL_OBSERVATION,
                    RuntimeV2EvidenceStatus.SATISFIED,
                    "已获取数据集摘要信息。",
                    toolName);
        }
        if (normalizedToolName.contains(".get_dataset_schema")) {
            upsertEvidence(
                    state,
                    "dataset.schema.known",
                    "数据集结构",
                    RuntimeV2CompletionEvidenceSource.DATASET_SCHEMA,
                    RuntimeV2EvidenceStatus.SATISFIED,
                    "已确认数据集 schema。",
                    toolName);
        }
        if (normalizedToolName.contains(".execute_dataset_sql")) {
            RuntimeV2EvidenceStatus status = isSuccessfulDatasetResult(toolResult)
                    ? RuntimeV2EvidenceStatus.SATISFIED
                    : RuntimeV2EvidenceStatus.FAILED;
            upsertEvidence(
                    state,
                    "dataset.query.success",
                    "数据集查询结果",
                    RuntimeV2CompletionEvidenceSource.DATASET_QUERY,
                    status,
                    status == RuntimeV2EvidenceStatus.SATISFIED ? "已获取成功的数据集查询结果。" : "数据集查询已执行，但结果未成功。",
                    toolName);
        }
        if ("file_write".equals(normalizedToolName) || "run_python".equals(normalizedToolName)) {
            upsertEvidence(
                    state,
                    "file.output.present",
                    "文件输出",
                    RuntimeV2CompletionEvidenceSource.FILE_OUTPUT,
                    RuntimeV2EvidenceStatus.SATISFIED,
                    "已生成脚本或输出文件。",
                    toolName);
        }
        if ("write_artifact".equals(normalizedToolName)) {
            upsertEvidence(
                    state,
                    "artifact.published",
                    "产物发布",
                    RuntimeV2CompletionEvidenceSource.ARTIFACT,
                    RuntimeV2EvidenceStatus.SATISFIED,
                    "已发布 artifact。",
                    toolName);
        }
        refresh(state);
    }

    public void recordToolFailure(RuntimeV2State state, String toolName, String errorMessage) {
        if (state == null) {
            return;
        }
        String normalizedToolName = normalize(toolName);
        if (normalizedToolName.contains(".execute_dataset_sql")) {
            upsertEvidence(
                    state,
                    "dataset.query.success",
                    "数据集查询结果",
                    RuntimeV2CompletionEvidenceSource.DATASET_QUERY,
                    RuntimeV2EvidenceStatus.FAILED,
                    normalizeError(errorMessage, "数据集查询失败。"),
                    toolName);
        } else if ("file_write".equals(normalizedToolName) || "run_python".equals(normalizedToolName)) {
            upsertEvidence(
                    state,
                    "file.output.present",
                    "文件输出",
                    RuntimeV2CompletionEvidenceSource.FILE_OUTPUT,
                    RuntimeV2EvidenceStatus.FAILED,
                    normalizeError(errorMessage, "文件输出失败。"),
                    toolName);
        } else if ("write_artifact".equals(normalizedToolName)) {
            upsertEvidence(
                    state,
                    "artifact.published",
                    "产物发布",
                    RuntimeV2CompletionEvidenceSource.ARTIFACT,
                    RuntimeV2EvidenceStatus.FAILED,
                    normalizeError(errorMessage, "产物发布失败。"),
                    toolName);
        }
        refresh(state);
    }

    public void refresh(RuntimeV2State state) {
        if (state == null) {
            return;
        }
        List<RuntimeLoadedSkill> loadedSkills = resolveLoadedSkills(state);
        boolean hasLoadedSkills = !loadedSkills.isEmpty();
        upsertEvidence(
                state,
                "skill.loaded.present",
                "已加载技能",
                RuntimeV2CompletionEvidenceSource.SKILL_STATE,
                hasLoadedSkills ? RuntimeV2EvidenceStatus.SATISFIED : RuntimeV2EvidenceStatus.OPEN,
                hasLoadedSkills
                        ? "当前 run 已加载技能："
                                + loadedSkills.stream()
                                        .map(RuntimeLoadedSkill::runtimeSkillName)
                                        .toList()
                        : "当前 run 未加载技能。",
                loadedSkills.stream()
                        .map(RuntimeLoadedSkill::runtimeSkillName)
                        .findFirst()
                        .orElse(""));

        RuntimeV2TaskContract taskContract = taskContractEngine.resolve(state);
        state.setTaskContract(taskContract);
        state.replaceActiveSkillContracts(taskContract.activeSkillContracts());
        upsertIntentCoverageEvidence(state, taskContract);
        state.replaceObligationLedger(buildObligations(state, taskContract));
    }

    private void upsertIntentCoverageEvidence(RuntimeV2State state, RuntimeV2TaskContract taskContract) {
        if (state == null || taskContract == null || taskContract.intents().isEmpty()) {
            return;
        }
        List<RuntimeV2SkillContract> activeSkillContracts = taskContract.activeSkillContracts();
        for (RuntimeV2TaskIntent intent : taskContract.intents()) {
            String evidenceCode = intentCoverageEvidenceCode(intent);
            boolean covered = isIntentCoveredByActiveSkills(intent, activeSkillContracts);
            String coveredSkills = resolveCoveredSkillNames(intent, activeSkillContracts);
            upsertEvidence(
                    state,
                    evidenceCode,
                    intent == null ? "技能覆盖" : intent.name(),
                    RuntimeV2CompletionEvidenceSource.SKILL_STATE,
                    covered ? RuntimeV2EvidenceStatus.SATISFIED : RuntimeV2EvidenceStatus.OPEN,
                    covered
                            ? "当前请求的 " + (intent == null ? "未知" : intent.name()) + " 意图已被已加载技能覆盖。"
                            : "当前请求的 " + (intent == null ? "未知" : intent.name()) + " 意图尚未被已加载技能覆盖。",
                    coveredSkills);
        }
    }

    private String resolveCoveredSkillNames(
            RuntimeV2TaskIntent intent, List<RuntimeV2SkillContract> activeSkillContracts) {
        if (intent == null || activeSkillContracts == null || activeSkillContracts.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (RuntimeV2SkillContract contract : activeSkillContracts) {
            if (contract == null || contract.capabilities() == null) {
                continue;
            }
            if (isIntentCoveredByCapabilities(intent, contract.capabilities())) {
                names.add(contract.skillName());
            }
        }
        return String.join(",", names);
    }

    private String intentCoverageEvidenceCode(RuntimeV2TaskIntent intent) {
        return "skill.intent.covered."
                + (intent == null ? "unknown" : intent.name().toLowerCase(Locale.ROOT));
    }

    private boolean isIntentCoveredByActiveSkills(
            RuntimeV2TaskIntent intent, List<RuntimeV2SkillContract> activeSkillContracts) {
        if (intent == null) {
            return true;
        }
        if (activeSkillContracts == null || activeSkillContracts.isEmpty()) {
            return false;
        }
        for (RuntimeV2SkillContract contract : activeSkillContracts) {
            if (contract == null || contract.capabilities() == null) {
                continue;
            }
            if (isIntentCoveredByCapabilities(intent, contract.capabilities())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIntentCoveredByCapabilities(
            RuntimeV2TaskIntent intent, List<RuntimeV2ContractCapability> coveredCapabilities) {
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

    private List<RuntimeLoadedSkill> resolveLoadedSkills(RuntimeV2State state) {
        if (state == null || state.prepared() == null) {
            return List.of();
        }
        return requestScopedSkillRuntimeService.extractLoadedSkills(
                state.requestSkillKit(), state.prepared().availableSkills());
    }

    private List<RuntimeV2ObligationEntry> buildObligations(RuntimeV2State state, RuntimeV2TaskContract taskContract) {
        if (taskContract == null || taskContract.activeRequirements().isEmpty()) {
            return List.of();
        }
        List<RuntimeV2ObligationEntry> obligations = new ArrayList<>();
        for (RuntimeV2ExecutionRequirement requirement : taskContract.activeRequirements()) {
            if (requirement == null || !StringUtils.hasText(requirement.code())) {
                continue;
            }
            RuntimeV2ObligationStatus status = resolveRequirementStatus(state, requirement);
            String detail =
                    switch (status) {
                        case SATISFIED -> requirement.title() + "已满足。";
                        case FAILED -> resolveFailureDetail(state, requirement);
                        case WAIVED -> "当前 requirement 已豁免。";
                        default -> requirement.detail();
                    };
            obligations.add(new RuntimeV2ObligationEntry(
                    requirement.code(),
                    requirement.title(),
                    requirement.source(),
                    status,
                    detail,
                    requirement.expectedAction()));
        }
        return List.copyOf(obligations);
    }

    private RuntimeV2ObligationStatus resolveRequirementStatus(
            RuntimeV2State state, RuntimeV2ExecutionRequirement requirement) {
        List<String> evidenceCodes = requirement.requiredEvidenceCodes();
        if (evidenceCodes.isEmpty()) {
            return RuntimeV2ObligationStatus.OPEN;
        }
        boolean satisfied =
                switch (requirement.evidenceMatchMode()) {
                    case ANY_OF -> evidenceCodes.stream()
                            .anyMatch(code -> hasEvidence(state, code, RuntimeV2EvidenceStatus.SATISFIED));
                    case ALL_OF -> evidenceCodes.stream()
                            .allMatch(code -> hasEvidence(state, code, RuntimeV2EvidenceStatus.SATISFIED));
                };
        if (satisfied) {
            return RuntimeV2ObligationStatus.SATISFIED;
        }
        boolean failed =
                switch (requirement.evidenceMatchMode()) {
                    case ANY_OF -> evidenceCodes.size() == 1
                            && hasEvidence(state, evidenceCodes.get(0), RuntimeV2EvidenceStatus.FAILED);
                    case ALL_OF -> evidenceCodes.stream()
                            .anyMatch(code -> hasEvidence(state, code, RuntimeV2EvidenceStatus.FAILED));
                };
        return failed ? RuntimeV2ObligationStatus.FAILED : RuntimeV2ObligationStatus.OPEN;
    }

    private boolean hasEvidence(RuntimeV2State state, String code, RuntimeV2EvidenceStatus status) {
        if (state == null || !StringUtils.hasText(code) || status == null) {
            return false;
        }
        String normalizedCode = code.trim();
        return state.evidenceLedger().stream()
                .anyMatch(entry -> normalizedCode.equals(entry.code()) && entry.status() == status);
    }

    private String resolveFailureDetail(RuntimeV2State state, RuntimeV2ExecutionRequirement requirement) {
        if (state == null || requirement == null) {
            return "";
        }
        for (String evidenceCode : requirement.requiredEvidenceCodes()) {
            for (RuntimeV2EvidenceEntry entry : state.evidenceLedger()) {
                if (evidenceCode.equals(entry.code()) && entry.status() == RuntimeV2EvidenceStatus.FAILED) {
                    return normalizeError(entry.detail(), requirement.detail());
                }
            }
        }
        return requirement.detail();
    }

    private boolean isSuccessfulDatasetResult(String toolResult) {
        String normalized = normalize(toolResult);
        return StringUtils.hasText(normalized)
                && !normalized.contains("失败")
                && !normalized.contains("unknown column")
                && !normalized.contains("error")
                && !normalized.contains("异常");
    }

    private void upsertEvidence(
            RuntimeV2State state,
            String code,
            String title,
            RuntimeV2CompletionEvidenceSource source,
            RuntimeV2EvidenceStatus status,
            String detail,
            String sourceRef) {
        if (state == null || !StringUtils.hasText(code)) {
            return;
        }
        RuntimeV2EvidenceEntry next = new RuntimeV2EvidenceEntry(code, title, source, status, detail, sourceRef);
        List<RuntimeV2EvidenceEntry> existing = new ArrayList<>(state.evidenceLedger());
        int index = indexOfEvidence(existing, code);
        if (index >= 0) {
            existing.set(index, next);
        } else {
            existing.add(next);
        }
        state.replaceEvidenceLedger(existing);
    }

    private int indexOfEvidence(List<RuntimeV2EvidenceEntry> entries, String code) {
        for (int index = 0; index < entries.size(); index += 1) {
            if (code.equals(entries.get(index).code())) {
                return index;
            }
        }
        return -1;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeError(String message, String fallback) {
        return StringUtils.hasText(message) ? message.trim() : fallback;
    }
}
