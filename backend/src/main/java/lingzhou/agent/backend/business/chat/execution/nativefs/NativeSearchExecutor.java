package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NativeSearchExecutor {

    private final LogicalPathResolver logicalPathResolver;

    public NativeSearchExecutor(LogicalPathResolver logicalPathResolver) {
        this.logicalPathResolver = logicalPathResolver;
    }

    public RuntimeExecutionResult search(PathJail jail, RuntimeExecutionRequest request) {
        String searchPath = stringValue(request.payload(), "path");
        String pattern = stringValue(request.payload(), "pattern");
        int maxResults = intValue(request.payload(), "maxResults", 50, 200);
        if (!StringUtils.hasText(pattern)) {
            return RuntimeExecutionResult.failure(request.action(), "SEARCH_PATTERN_EMPTY", "搜索关键词不能为空");
        }
        try {
            String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(searchPath);
            Path hostPath =
                    jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath));
            if (!Files.exists(hostPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "SEARCH_PATH_NOT_FOUND", "搜索路径不存在: " + normalizedLogicalPath);
            }
            SearchResult result = runRipgrepOrFallback(hostPath, normalizedLogicalPath, pattern, maxResults);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedLogicalPath);
            data.put("pattern", pattern);
            data.put("count", result.matches().size());
            data.put("matches", result.matches());
            return RuntimeExecutionResult.success(request.action(), result.textOutput(), data);
        } catch (Exception ex) {
            return RuntimeExecutionResult.failure(request.action(), "SEARCH_FAILED", ex.getMessage());
        }
    }

    private SearchResult runRipgrepOrFallback(Path hostPath, String logicalPath, String pattern, int maxResults)
            throws IOException, InterruptedException {
        try {
            return runRipgrep(hostPath, logicalPath, pattern, maxResults);
        } catch (IOException ex) {
            return fallbackSearch(hostPath, logicalPath, pattern, maxResults);
        }
    }

    private SearchResult runRipgrep(Path hostPath, String logicalPath, String pattern, int maxResults)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "rg",
                "-n",
                "--no-heading",
                "--color",
                "never",
                "--max-count",
                String.valueOf(maxResults),
                pattern,
                hostPath.toString());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        List<Map<String, Object>> matches = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 1) {
            throw new IOException("rg 执行失败，退出码=" + exitCode);
        }
        for (String line : lines) {
            Map<String, Object> parsed = parseRipgrepLine(hostPath, logicalPath, line);
            if (!parsed.isEmpty()) {
                matches.add(parsed);
            }
        }
        return new SearchResult(String.join("\n", lines), matches);
    }

    private SearchResult fallbackSearch(Path hostPath, String logicalPath, String pattern, int maxResults)
            throws IOException {
        List<Map<String, Object>> matches = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        String lowered = pattern.toLowerCase();
        try (var stream = Files.walk(hostPath)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                List<String> fileLines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int index = 0; index < fileLines.size(); index++) {
                    String text = fileLines.get(index);
                    if (text != null && text.toLowerCase().contains(lowered)) {
                        String logicalFilePath = toLogicalChildPath(hostPath, logicalPath, path);
                        matches.add(Map.of("path", logicalFilePath, "line", index + 1, "text", text));
                        lines.add(logicalFilePath + ":" + (index + 1) + ":" + text);
                        if (matches.size() >= maxResults) {
                            return new SearchResult(String.join("\n", lines), matches);
                        }
                    }
                }
            }
        }
        return new SearchResult(String.join("\n", lines), matches);
    }

    private Map<String, Object> parseRipgrepLine(Path hostPath, String logicalPath, String line) {
        int first = line.indexOf(':');
        if (first <= 0) {
            return Map.of();
        }
        int second = line.indexOf(':', first + 1);
        if (second <= first) {
            return Map.of();
        }
        String filePath = line.substring(0, first);
        String lineNumber = line.substring(first + 1, second);
        String text = line.substring(second + 1);
        Path path = Path.of(filePath);
        String logicalFilePath = toLogicalChildPath(hostPath, logicalPath, path);
        return Map.of(
                "path", logicalFilePath,
                "line", parseInt(lineNumber),
                "text", text);
    }

    private String toLogicalChildPath(Path hostBasePath, String logicalBasePath, Path filePath) {
        Path relative = hostBasePath
                .toAbsolutePath()
                .normalize()
                .relativize(filePath.toAbsolutePath().normalize());
        String suffix = relative.toString().replace('\\', '/');
        if (!StringUtils.hasText(suffix)) {
            return logicalBasePath;
        }
        return logicalBasePath.endsWith("/") ? logicalBasePath + suffix : logicalBasePath + "/" + suffix;
    }

    private int intValue(Map<String, Object> payload, String key, int defaultValue, int maxValue) {
        if (payload == null) {
            return defaultValue;
        }
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private record SearchResult(String textOutput, List<Map<String, Object>> matches) {}
}
