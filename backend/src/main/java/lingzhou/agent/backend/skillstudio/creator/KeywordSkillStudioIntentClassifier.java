package lingzhou.agent.backend.skillstudio.creator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;
import lingzhou.agent.backend.skillstudio.template.SkillStudioIntentMap;
import lingzhou.agent.backend.skillstudio.template.SkillStudioIntentMapLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KeywordSkillStudioIntentClassifier implements SkillStudioIntentClassifier {

    private final SkillStudioIntentMapLoader intentMapLoader;

    public KeywordSkillStudioIntentClassifier(SkillStudioIntentMapLoader intentMapLoader) {
        this.intentMapLoader = intentMapLoader;
    }

    @Override
    public SkillStudioIntent classify(SkillStudioContextInput input) {
        SkillStudioIntentMap map = intentMapLoader.load();
        String corpus = buildCorpus(input);
        List<String> reasons = new ArrayList<>();
        String baseTemplate = "";
        String capabilityTemplate = "";
        for (SkillStudioIntentMap.BusinessIntentRule rule : map.businessIntentRules()) {
            if (matchesAny(corpus, rule.keywords())) {
                capabilityTemplate = rule.tag();
                if (StringUtils.hasText(rule.preferredBaseTemplate())) {
                    baseTemplate = rule.preferredBaseTemplate();
                }
                reasons.add("命中能力模板: " + rule.tag());
                break;
            }
        }
        if (!StringUtils.hasText(baseTemplate)) {
            for (SkillStudioIntentMap.BaseTemplateRule rule : map.baseTemplateRules()) {
                if (matchesAny(corpus, rule.signals()) && !matchesAny(corpus, rule.excludeSignals())) {
                    baseTemplate = rule.template();
                    reasons.add("命中结构倾向: " + rule.template());
                    break;
                }
            }
        }
        if (!StringUtils.hasText(baseTemplate)) {
            baseTemplate = map.defaultBaseTemplate();
            reasons.add("回退默认结构倾向: " + baseTemplate);
        }
        double confidence = reasons.isEmpty() ? 0.5d : Math.min(0.99d, 0.6d + reasons.size() * 0.1d);
        return new SkillStudioIntent(baseTemplate, capabilityTemplate, confidence, List.copyOf(reasons));
    }

    private String buildCorpus(SkillStudioContextInput input) {
        StringBuilder builder = new StringBuilder();
        if (input == null) {
            return "";
        }
        append(builder, input.userGoal());
        if (input.memorySummary() != null) {
            append(builder, input.memorySummary().baseTemplate());
            append(builder, input.memorySummary().capabilityTemplate());
            input.memorySummary().referencePlan().forEach(plan -> append(builder, plan));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private boolean matchesAny(String corpus, List<String> keywords) {
        if (!StringUtils.hasText(corpus) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void append(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim()).append('\n');
        }
    }
}
