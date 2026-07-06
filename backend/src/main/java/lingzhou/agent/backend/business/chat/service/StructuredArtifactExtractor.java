package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class StructuredArtifactExtractor {

    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private StructuredArtifactExtractor() {}

    public static Map<String, Object> extract(String answer) {
        if (!StringUtils.hasText(answer)) {
            return Map.of();
        }
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(answer);
        while (matcher.find()) {
            Map<String, Object> candidate = parseObject(matcher.group(1));
            if (isArtifact(candidate)) {
                return candidate;
            }
        }
        Map<String, Object> directCandidate = parseObject(answer);
        return isArtifact(directCandidate) ? directCandidate : Map.of();
    }

    private static boolean isArtifact(Map<String, Object> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (candidate.containsKey("app") || candidate.containsKey("meta") || candidate.containsKey("artifact")) {
            return true;
        }
        return candidate.containsKey("objectName")
                || candidate.containsKey("downloadUrl")
                || candidate.containsKey("path")
                || candidate.containsKey("file");
    }

    private static Map<String, Object> parseObject(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(text, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
