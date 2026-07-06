package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2SkillContractBuilder {

    public RuntimeV2SkillContract build(String skillName, String displayName, List<String> toolNames) {
        List<String> safeToolNames = toolNames == null
                ? List.of()
                : toolNames.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        Set<RuntimeV2ContractCapability> capabilities = new LinkedHashSet<>();
        List<RuntimeV2ExecutionRequirement> requirements = new ArrayList<>();

        List<String> knowledgeBaseTools = safeToolNames.stream()
                .filter(toolName -> toolName.startsWith("knowledge_base."))
                .toList();
        if (!knowledgeBaseTools.isEmpty()) {
            capabilities.add(RuntimeV2ContractCapability.KNOWLEDGE_BASE);
            requirements.add(new RuntimeV2ExecutionRequirement(
                    "knowledge.base.required",
                    "制度依据闭环",
                    RuntimeV2CompletionBlockerSource.EVIDENCE,
                    "当前技能命中知识库检索能力时，最终答复需要有知识库依据支撑。",
                    "先查询知识库，再给出最终答复。",
                    List.of("knowledge.base.hit"),
                    RuntimeV2EvidenceMatchMode.ANY_OF,
                    knowledgeBaseTools,
                    List.of(RuntimeV2TaskIntent.POLICY_QA)));
        }

        List<String> datasetTools = safeToolNames.stream()
                .filter(toolName -> toolName.contains(".search_dataset_summary")
                        || toolName.contains(".get_dataset_schema")
                        || toolName.contains(".execute_dataset_sql"))
                .toList();
        boolean hasDatasetSummary =
                datasetTools.stream().anyMatch(toolName -> toolName.contains(".search_dataset_summary"));
        boolean hasDatasetSchema = datasetTools.stream().anyMatch(toolName -> toolName.contains(".get_dataset_schema"));
        boolean hasDatasetSql = datasetTools.stream().anyMatch(toolName -> toolName.contains(".execute_dataset_sql"));
        if (!datasetTools.isEmpty()) {
            capabilities.add(RuntimeV2ContractCapability.DATASET_QUERY);
        }
        if (hasDatasetSummary) {
            requirements.add(new RuntimeV2ExecutionRequirement(
                    "dataset.summary.required",
                    "数据集摘要闭环",
                    RuntimeV2CompletionBlockerSource.SKILL,
                    "当前技能包含数据集查询路径，写 SQL 前应先查看数据集摘要，确认候选表和对象编码。",
                    "先查看数据集 summary，再继续 schema 和 SQL。",
                    List.of("dataset.summary.known"),
                    RuntimeV2EvidenceMatchMode.ALL_OF,
                    datasetTools,
                    List.of(RuntimeV2TaskIntent.DATA_QUERY)));
        }
        if (hasDatasetSchema) {
            requirements.add(new RuntimeV2ExecutionRequirement(
                    "dataset.schema.required",
                    "结构确认闭环",
                    RuntimeV2CompletionBlockerSource.SKILL,
                    "当前技能包含数据集查询路径，执行 SQL 前应先确认数据集 schema 和字段。",
                    "先查看数据集 summary/schema，再执行 SQL。",
                    List.of("dataset.schema.known"),
                    RuntimeV2EvidenceMatchMode.ALL_OF,
                    datasetTools,
                    List.of(RuntimeV2TaskIntent.DATA_QUERY)));
        }
        if (hasDatasetSql) {
            requirements.add(new RuntimeV2ExecutionRequirement(
                    "dataset.result.required",
                    "数据结果闭环",
                    RuntimeV2CompletionBlockerSource.EVIDENCE,
                    "当前技能命中数据集查询路径时，最终答复需要成功的数据查询结果支撑。",
                    "继续执行数据集查询，拿到有效结果后再结束。",
                    List.of("dataset.query.success"),
                    RuntimeV2EvidenceMatchMode.ALL_OF,
                    datasetTools,
                    List.of(RuntimeV2TaskIntent.DATA_QUERY)));
        }

        return new RuntimeV2SkillContract(
                StringUtils.hasText(skillName) ? skillName.trim() : "",
                StringUtils.hasText(displayName) ? displayName.trim() : StringUtils.trimWhitespace(skillName),
                safeToolNames,
                new ArrayList<>(capabilities),
                requirements);
    }
}
