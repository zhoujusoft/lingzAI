package lingzhou.agent.backend.capability.agentruntime.v2.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

@Component
public class PromptLoader {

    private static final String PROMPT_PATH_PREFIX = "prompts/";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final ConcurrentHashMap<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();

    public String loadPrompt(String promptName) {
        if (!StringUtils.hasText(promptName)) {
            throw new IllegalArgumentException("promptName 不能为空");
        }
        return PROMPT_CACHE.computeIfAbsent(promptName.trim(), this::readPromptFile);
    }

    public String renderPrompt(String promptName, Map<String, ?> variables) {
        String template = loadPrompt(promptName);
        Map<String, ?> safeVariables = variables == null ? Map.of() : variables;
        List<String> missingVariables = new ArrayList<>();
        String rendered = replacePlaceholders(template, safeVariables, missingVariables);
        assertNoMissingVariables(promptName, missingVariables);
        assertNoUnresolvedPlaceholders(promptName, rendered, safeVariables);
        return rendered.trim();
    }

    public static void clearCache() {
        PROMPT_CACHE.clear();
    }

    public static int getCacheSize() {
        return PROMPT_CACHE.size();
    }

    private String readPromptFile(String promptName) {
        String path = PROMPT_PATH_PREFIX + promptName + ".txt";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt 文件不存在: " + path);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("加载 Prompt 失败: " + path, ex);
        }
    }

    private String replacePlaceholders(String template, Map<String, ?> variables, List<String> missingVariables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!variables.containsKey(key)) {
                if (missingVariables != null && !missingVariables.contains(key)) {
                    missingVariables.add(key);
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            Object value = variables.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void assertNoMissingVariables(String promptName, List<String> missingVariables) {
        if (missingVariables == null || missingVariables.isEmpty()) {
            return;
        }
        throw new IllegalStateException("Prompt 缺少变量: " + promptName + " -> " + missingVariables);
    }

    private void assertNoUnresolvedPlaceholders(String promptName, String rendered, Map<String, ?> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rendered);
        if (!matcher.find()) {
            return;
        }
        Map<String, Object> unresolved = new LinkedHashMap<>();
        do {
            String key = matcher.group(1).trim();
            unresolved.put(key, variables.get(key));
        } while (matcher.find());
        throw new IllegalStateException("Prompt 存在未解析变量: " + promptName + " -> " + unresolved.keySet());
    }
}
