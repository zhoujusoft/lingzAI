package lingzhou.agent.backend.skillstudio.template;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClasspathSkillStudioTemplateResolver implements SkillStudioTemplateResolver {

    @Override
    public String resolveBaseTemplate(
            SkillStudioContextInput input, SkillStudioIntent intent, SkillStudioIntentMap intentMap) {
        if (input != null
                && input.hints() != null
                && StringUtils.hasText(input.hints().preferredTemplate())) {
            return input.hints().preferredTemplate().trim();
        }
        if (intent != null && StringUtils.hasText(intent.baseTemplate())) {
            return intent.baseTemplate().trim();
        }
        return intentMap == null || !StringUtils.hasText(intentMap.defaultBaseTemplate())
                ? "minimal"
                : intentMap.defaultBaseTemplate().trim();
    }

    @Override
    public String loadTemplateContent(String templateName) {
        String normalized = normalizeTemplateName(templateName);
        String path = "skillstudio/templates/" + normalized + "/template.md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("加载 skill studio 模板失败: " + normalized, ex);
        }
    }

    private String normalizeTemplateName(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            return "basic";
        }
        String normalized = templateName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "minimal" -> "basic";
            case "reference" -> "reference-driven";
            case "scripted-workflow" -> "document-workflow";
            default -> normalized;
        };
    }
}
