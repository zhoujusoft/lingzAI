package lingzhou.agent.backend.skillstudio.project.service;

import java.util.function.Predicate;
import org.springframework.util.StringUtils;

public final class SkillStudioProjectIdentityResolver {

    private SkillStudioProjectIdentityResolver() {}

    public static String resolveUniqueName(String candidate, int maxLength, Predicate<String> existsPredicate) {
        String normalized = normalize(candidate);
        if (!StringUtils.hasText(normalized) || existsPredicate == null || !existsPredicate.test(normalized)) {
            return normalized;
        }
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String resolved = appendSuffix(normalized, suffix, maxLength);
            if (!existsPredicate.test(resolved)) {
                return resolved;
            }
        }
        throw new IllegalStateException("无法生成唯一标识: " + normalized);
    }

    static String appendSuffix(String candidate, int suffix, int maxLength) {
        String normalized = normalize(candidate);
        String suffixText = "-" + suffix;
        int safeMaxLength = Math.max(suffixText.length() + 1, maxLength);
        String base = normalized;
        if (base.length() + suffixText.length() > safeMaxLength) {
            base = base.substring(0, safeMaxLength - suffixText.length());
        }
        base = base.replaceAll("-+$", "");
        if (!StringUtils.hasText(base)) {
            base = "skill";
        }
        return base + suffixText;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
