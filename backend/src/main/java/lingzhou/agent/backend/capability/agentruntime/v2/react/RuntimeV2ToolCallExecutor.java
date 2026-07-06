package lingzhou.agent.backend.capability.agentruntime.v2.react;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class RuntimeV2ToolCallExecutor {

    public String execute(String toolName, ToolCallback tool, Map<String, Object> arguments) {
        if (tool == null) {
            throw new IllegalStateException("工具不存在：" + toolName);
        }
        String actualArgumentsJson = JSON.toJSONString(resolveInvocationArguments(toolName, arguments));
        return tool.call(actualArgumentsJson);
    }

    private Map<String, Object> resolveInvocationArguments(String toolName, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
        return switch (toolName == null ? "" : toolName.trim().toLowerCase()) {
            case "file_read", "list_dir", "stat" -> mapSingleArgument(safeArguments, "path");
            case "parse_file" -> mapArguments(safeArguments, "path", "mode");
            case "file_write" -> mapArguments(safeArguments, "path", "content");
            case "run_python" -> mapArguments(
                    normalizeRunPythonArguments(safeArguments), "scriptPath", "args", "workDir", "timeoutSeconds");
            case "write_artifact" -> mapArguments(
                    safeArguments, "folder", "fileName", "content", "sourcePath", "contentType");
            default -> safeArguments;
        };
    }

    private Map<String, Object> normalizeRunPythonArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return arguments;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(arguments);
        Object scriptPath = firstPresent(normalized, "scriptPath", "arg0");
        if (scriptPath != null) {
            normalized.put("scriptPath", scriptPath);
        }
        Object rawArgs = firstPresent(normalized, "args", "arg1");
        Object positionalThird = normalized.get("arg2");
        if (shouldTreatArg2AsSecondScriptArgument(normalized, positionalThird)) {
            List<String> mergedArgs = new ArrayList<>((List<String>) normalizeRunPythonArgsValue(rawArgs));
            String thirdValue = String.valueOf(positionalThird).trim();
            if (StringUtils.hasText(thirdValue)) {
                mergedArgs.add(thirdValue);
            }
            normalized.put("args", List.copyOf(mergedArgs));
            normalized.remove("arg2");
        } else if (rawArgs != null || normalized.containsKey("arg1")) {
            normalized.put("args", normalizeRunPythonArgsValue(rawArgs));
        }
        Object workDir = firstPresent(normalized, "workDir", "arg2");
        if (workDir != null && !shouldTreatArg2AsSecondScriptArgument(normalized, workDir)) {
            normalized.put("workDir", workDir);
        }
        Object timeoutSeconds = firstPresent(normalized, "timeoutSeconds", "arg3");
        if (timeoutSeconds != null) {
            normalized.put("timeoutSeconds", timeoutSeconds);
        }
        normalized.remove("arg0");
        normalized.remove("arg1");
        normalized.remove("arg2");
        normalized.remove("arg3");
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Object firstPresent(Map<String, Object> arguments, String preferredKey, String fallbackKey) {
        if (arguments.containsKey(preferredKey)) {
            return arguments.get(preferredKey);
        }
        return arguments.get(fallbackKey);
    }

    private Object normalizeRunPythonArgsValue(Object rawArgs) {
        if (rawArgs == null) {
            return List.of();
        }
        if (rawArgs instanceof List<?> rawList) {
            List<String> normalized = new ArrayList<>();
            for (Object item : rawList) {
                if (item != null) {
                    normalized.add(String.valueOf(item));
                }
            }
            return List.copyOf(normalized);
        }
        String text = String.valueOf(rawArgs).trim();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text);
    }

    private boolean shouldTreatArg2AsSecondScriptArgument(Map<String, Object> arguments, Object arg2Value) {
        if (!arguments.containsKey("arg2") || arg2Value == null) {
            return false;
        }
        if (arguments.containsKey("workDir")) {
            return false;
        }
        String text = String.valueOf(arg2Value).trim();
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.equals("/workspace")
                || text.equals("/outputs")
                || text.equals("/temp")
                || text.equals("/logs")
                || text.equals("/profile")
                || text.endsWith("/")) {
            return false;
        }
        return text.startsWith("/uploads/")
                || text.startsWith("/outputs/")
                || text.startsWith("/temp/")
                || looksLikeFilePath(text);
    }

    private boolean looksLikeFilePath(String value) {
        int slashIndex = value.lastIndexOf('/');
        int dotIndex = value.lastIndexOf('.');
        return dotIndex > slashIndex && dotIndex < value.length() - 1;
    }

    private Map<String, Object> mapSingleArgument(Map<String, Object> arguments, String key) {
        if (arguments.containsKey("arg0") || !arguments.containsKey(key)) {
            return arguments;
        }
        Map<String, Object> positionalArguments = new LinkedHashMap<>();
        positionalArguments.put("arg0", arguments.get(key));
        return positionalArguments;
    }

    private Map<String, Object> mapArguments(Map<String, Object> arguments, String... keys) {
        if (arguments == null || arguments.isEmpty() || keys == null || keys.length == 0) {
            return arguments;
        }
        boolean alreadyPositional = arguments.keySet().stream().anyMatch(this::isPositionalArgumentKey);
        if (alreadyPositional) {
            return arguments;
        }
        Map<String, Object> positionalArguments = new LinkedHashMap<>();
        for (int index = 0; index < keys.length; index += 1) {
            String key = keys[index];
            if (key != null && arguments.containsKey(key)) {
                positionalArguments.put("arg" + index, arguments.get(key));
            }
        }
        return positionalArguments.isEmpty() ? arguments : positionalArguments;
    }

    private boolean isPositionalArgumentKey(String key) {
        if (key == null || key.length() < 4 || !key.startsWith("arg")) {
            return false;
        }
        for (int index = 3; index < key.length(); index += 1) {
            if (!Character.isDigit(key.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
