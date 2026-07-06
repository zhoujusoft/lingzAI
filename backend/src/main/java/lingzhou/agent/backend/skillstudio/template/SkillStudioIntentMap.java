package lingzhou.agent.backend.skillstudio.template;

import java.util.List;

public record SkillStudioIntentMap(
        String defaultBaseTemplate,
        List<BusinessIntentRule> businessIntentRules,
        List<BaseTemplateRule> baseTemplateRules) {

    public record BusinessIntentRule(
            String tag, List<String> keywords, String preferredBaseTemplate, List<String> upgradeSignals) {}

    public record BaseTemplateRule(
            String template, List<String> signals, List<String> excludeSignals, boolean fallback) {}
}
