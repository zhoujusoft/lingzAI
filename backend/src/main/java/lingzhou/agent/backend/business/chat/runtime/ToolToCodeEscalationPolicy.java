package lingzhou.agent.backend.business.chat.runtime;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ToolToCodeEscalationPolicy {

    public ToolToCodeEscalationDecision evaluate(
            String message, List<String> fileIds, List<?> parsedFiles, RuntimeSkillDescriptor mentionedSkill) {
        boolean hasAttachments =
                (fileIds != null && !fileIds.isEmpty()) || (parsedFiles != null && !parsedFiles.isEmpty());
        String normalizedMessage = normalize(message);
        boolean archiveExtractionIntent = AttachmentIntentHeuristics.shouldZipEscalateToCode(message);
        boolean explicitFileProcessingIntent =
                matchesAny(normalizedMessage, "excel", "xlsx", "csv", "表格", "文档", "pdf", "word", "docx", "ppt", "pptx");
        boolean explicitProcessingIntent = matchesAny(
                normalizedMessage,
                "汇总",
                "统计",
                "分析",
                "清洗",
                "批量",
                "转换",
                "生成图",
                "图表",
                "趋势",
                "合并",
                "拆分",
                "导出",
                "报告",
                "画图",
                "透视",
                "对照文档");
        boolean knowledgeLookupIntent =
                matchesAny(normalizedMessage, "标准", "制度", "政策", "规定", "是什么", "怎么报销", "报销标准", "查询", "多少");

        List<String> signals = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        if (hasAttachments) {
            signals.add("检测到附件或解析文件");
        }
        if (explicitFileProcessingIntent) {
            signals.add("命中文件处理语境");
        }
        if (archiveExtractionIntent) {
            signals.add("命中显式 ZIP 提取/筛选/重打包意图");
        }
        if (explicitProcessingIntent) {
            signals.add("命中显式加工/分析意图");
        }

        if (mentionedSkill != null && StringUtils.hasText(mentionedSkill.runtimeSkillName())) {
            signals.add("当前请求已显式命中技能，升级到 CODE 前应先检查该技能是否足够");
        }
        if (knowledgeLookupIntent && !hasAttachments && !explicitProcessingIntent) {
            blockers.add("当前更像知识查询或制度问答，缺的是业务依据，不是代码能力");
        }
        if (!hasAttachments && !explicitProcessingIntent && !explicitFileProcessingIntent) {
            blockers.add("当前未出现复杂文件处理、批量加工或重型产物生成信号");
        }

        boolean allowCodeExecution = blockers.isEmpty()
                && (hasAttachments
                        || explicitProcessingIntent
                        || explicitFileProcessingIntent
                        || archiveExtractionIntent);
        String reason = allowCodeExecution
                ? "当前请求允许在 TOOL 不足时使用 CODE 作为兜底能力，是否升级由模型自行判断"
                : firstNonBlank(blockers, "当前请求应优先使用已有工具、技能、知识库或数据集完成");

        ToolToCodeEscalationDecision decision = new ToolToCodeEscalationDecision(
                null, false, allowCodeExecution, reason, List.copyOf(signals), List.copyOf(blockers));
        log.debug(
                "[执行策略] TOOL->CODE 判定：allowCodeExecution={}, reason={}, signals={}, blockers={}",
                decision.allowCodeExecution(),
                decision.reason(),
                decision.signals(),
                decision.blockers());
        return decision;
    }

    private boolean matchesAny(String source, String... keywords) {
        if (!StringUtils.hasText(source) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && source.contains(keyword.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private String firstNonBlank(List<String> values, String fallback) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return fallback;
    }
}
