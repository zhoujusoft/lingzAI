package lingzhou.agent.backend.capability.agentruntime.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultRuntimeToolApprovalAnalyzer implements RuntimeToolApprovalAnalyzer {

    private static final int EXCERPT_LIMIT = 1200;

    @Override
    public RuntimeToolApprovalAnalysis analyze(String toolName, Map<String, Object> arguments) {
        String normalizedToolName = normalizeText(toolName).toLowerCase();
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return switch (normalizedToolName) {
            case "file_write" -> analyzeFileWrite(safeArguments);
            case "run_python" -> analyzeRunPython(safeArguments);
            default -> new RuntimeToolApprovalAnalysis(
                    "即将调用重点工具 " + normalizeText(toolName),
                    RuntimeApprovalConstants.RISK_MEDIUM,
                    List.of(),
                    Map.of("arguments", safeArguments),
                    "请确认工具调用参数后再批准。");
        };
    }

    private RuntimeToolApprovalAnalysis analyzeFileWrite(Map<String, Object> arguments) {
        String path = firstText(arguments, "path", "filePath", "filename", "targetPath", "arg0");
        String content = firstText(arguments, "content", "text", "data", "arg1");
        String language = resolveLanguage(path, content);
        List<RuntimeToolApprovalAnalysis.RiskItem> risks = scanContentRisks(content);
        if (isSensitivePath(path)) {
            risks.add(new RuntimeToolApprovalAnalysis.RiskItem(
                    "SENSITIVE_PATH", RuntimeApprovalConstants.RISK_HIGH, "目标路径位于敏感目录或配置目录。"));
        }
        String riskLevel = highestRiskLevel(risks);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("path", path);
        preview.put("language", language);
        preview.put("contentLength", content.length());
        preview.put("contentExcerpt", excerpt(content));
        return new RuntimeToolApprovalAnalysis(
                StringUtils.hasText(path) ? "即将写入文件 " + path : "即将写入文件",
                riskLevel,
                risks,
                preview,
                recommendedAction(riskLevel));
    }

    private RuntimeToolApprovalAnalysis analyzeRunPython(Map<String, Object> arguments) {
        String scriptPath = firstText(arguments, "scriptPath", "path", "filePath", "arg0");
        String args = firstText(arguments, "args", "arg1");
        String workDir = firstText(arguments, "workDir", "cwd", "arg2");
        String timeoutSeconds = firstText(arguments, "timeoutSeconds", "timeout", "arg3");
        List<RuntimeToolApprovalAnalysis.RiskItem> risks = new ArrayList<>();
        risks.add(new RuntimeToolApprovalAnalysis.RiskItem(
                "EXECUTE_CODE", RuntimeApprovalConstants.RISK_HIGH, "批准后会执行 Python 脚本。"));
        if (StringUtils.hasText(args)) {
            risks.add(new RuntimeToolApprovalAnalysis.RiskItem(
                    "EXECUTION_ARGS", RuntimeApprovalConstants.RISK_MEDIUM, "执行请求包含额外命令参数。"));
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("scriptPath", scriptPath);
        preview.put("args", args);
        preview.put("workDir", workDir);
        preview.put("timeoutSeconds", timeoutSeconds);
        return new RuntimeToolApprovalAnalysis(
                StringUtils.hasText(scriptPath) ? "即将执行 Python 脚本 " + scriptPath : "即将执行 Python 脚本",
                RuntimeApprovalConstants.RISK_HIGH,
                risks,
                preview,
                "请确认脚本来源、执行参数和输出影响后再批准。");
    }

    private List<RuntimeToolApprovalAnalysis.RiskItem> scanContentRisks(String content) {
        List<RuntimeToolApprovalAnalysis.RiskItem> risks = new ArrayList<>();
        String text = content == null ? "" : content;
        String lower = text.toLowerCase();
        addIfContains(
                risks,
                lower,
                "subprocess",
                "SUBPROCESS",
                RuntimeApprovalConstants.RISK_HIGH,
                "脚本包含 subprocess 调用，可能执行系统命令。");
        addIfContains(
                risks,
                lower,
                "os.system",
                "OS_SYSTEM",
                RuntimeApprovalConstants.RISK_HIGH,
                "脚本包含 os.system 调用，可能执行系统命令。");
        addIfContains(
                risks, lower, "os.popen", "OS_POPEN", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 os.popen 调用，可能执行系统命令。");
        addIfContains(risks, lower, "eval(", "EVAL", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 eval 调用。");
        addIfContains(risks, lower, "exec(", "EXEC", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 exec 调用。");
        addIfContains(risks, lower, "shutil.rmtree", "RMTREE", RuntimeApprovalConstants.RISK_HIGH, "脚本包含目录递归删除调用。");
        addIfContains(risks, lower, "os.remove", "REMOVE_FILE", RuntimeApprovalConstants.RISK_HIGH, "脚本包含文件删除调用。");
        addIfContains(risks, lower, "requests.", "HTTP_REQUEST", RuntimeApprovalConstants.RISK_HIGH, "脚本包含外部 HTTP 访问。");
        addIfContains(risks, lower, "httpx.", "HTTP_REQUEST", RuntimeApprovalConstants.RISK_HIGH, "脚本包含外部 HTTP 访问。");
        addIfContains(
                risks, lower, "urllib.request", "HTTP_REQUEST", RuntimeApprovalConstants.RISK_HIGH, "脚本包含外部 HTTP 访问。");
        addIfContains(risks, lower, "socket.", "SOCKET", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 Socket 网络访问。");
        addIfContains(
                risks, lower, "drop table", "SQL_DROP", RuntimeApprovalConstants.RISK_HIGH, "脚本包含高危 SQL：DROP TABLE。");
        addIfContains(
                risks, lower, "truncate", "SQL_TRUNCATE", RuntimeApprovalConstants.RISK_HIGH, "脚本包含高危 SQL：TRUNCATE。");
        addIfContains(
                risks,
                lower,
                "delete from",
                "SQL_DELETE",
                RuntimeApprovalConstants.RISK_HIGH,
                "脚本包含高危 SQL：DELETE FROM。");
        addIfContains(
                risks,
                lower,
                "alter table",
                "SQL_ALTER",
                RuntimeApprovalConstants.RISK_HIGH,
                "脚本包含高危 SQL：ALTER TABLE。");
        addIfContains(risks, lower, "update ", "SQL_UPDATE", RuntimeApprovalConstants.RISK_MEDIUM, "脚本包含 SQL UPDATE。");
        addIfContains(risks, lower, "open(", "FILE_OPEN", RuntimeApprovalConstants.RISK_MEDIUM, "脚本包含文件打开操作。");
        addIfContains(risks, lower, "path(", "PATH_ACCESS", RuntimeApprovalConstants.RISK_MEDIUM, "脚本包含路径访问操作。");
        addIfContains(risks, lower, "os.environ", "ENV_ACCESS", RuntimeApprovalConstants.RISK_MEDIUM, "脚本访问环境变量。");
        addIfContains(risks, lower, "while true", "INFINITE_LOOP", RuntimeApprovalConstants.RISK_MEDIUM, "脚本可能包含无限循环。");
        addIfContains(risks, lower, "input(", "INTERACTIVE_INPUT", RuntimeApprovalConstants.RISK_MEDIUM, "脚本可能等待交互输入。");
        addIfContains(
                risks, lower, "pickle.load", "PICKLE_LOAD", RuntimeApprovalConstants.RISK_MEDIUM, "脚本包含 pickle 反序列化。");
        addIfContains(risks, lower, "yaml.load", "YAML_LOAD", RuntimeApprovalConstants.RISK_MEDIUM, "脚本包含 yaml.load。");
        addIfContains(risks, lower, "rm -rf", "SHELL_RM_RF", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 rm -rf 命令。");
        addIfContains(risks, lower, "curl ", "SHELL_CURL", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 curl 命令。");
        addIfContains(risks, lower, "wget ", "SHELL_WGET", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 wget 命令。");
        addIfContains(risks, lower, "chmod ", "SHELL_CHMOD", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 chmod 命令。");
        addIfContains(risks, lower, "sudo ", "SHELL_SUDO", RuntimeApprovalConstants.RISK_HIGH, "脚本包含 sudo 命令。");
        return risks;
    }

    private void addIfContains(
            List<RuntimeToolApprovalAnalysis.RiskItem> risks,
            String content,
            String marker,
            String code,
            String level,
            String message) {
        if (content != null && content.contains(marker)) {
            risks.add(new RuntimeToolApprovalAnalysis.RiskItem(code, level, message));
        }
    }

    private String highestRiskLevel(List<RuntimeToolApprovalAnalysis.RiskItem> risks) {
        if (risks == null || risks.isEmpty()) {
            return RuntimeApprovalConstants.RISK_LOW;
        }
        boolean high = risks.stream().anyMatch(item -> RuntimeApprovalConstants.RISK_HIGH.equals(item.level()));
        if (high) {
            return RuntimeApprovalConstants.RISK_HIGH;
        }
        boolean medium = risks.stream().anyMatch(item -> RuntimeApprovalConstants.RISK_MEDIUM.equals(item.level()));
        return medium ? RuntimeApprovalConstants.RISK_MEDIUM : RuntimeApprovalConstants.RISK_LOW;
    }

    private String recommendedAction(String riskLevel) {
        if (RuntimeApprovalConstants.RISK_HIGH.equals(riskLevel)) {
            return "请重点确认脚本内容、目标路径、外部访问和删除/命令执行行为后再批准。";
        }
        if (RuntimeApprovalConstants.RISK_MEDIUM.equals(riskLevel)) {
            return "请确认文件内容和执行影响后再批准。";
        }
        return "风险较低，仍建议确认目标路径和内容符合预期。";
    }

    private boolean isSensitivePath(String path) {
        String normalized = normalizeText(path).toLowerCase();
        return normalized.startsWith("/etc/")
                || normalized.startsWith("/usr/")
                || normalized.startsWith("/bin/")
                || normalized.startsWith("/sbin/")
                || normalized.contains("/.ssh/")
                || normalized.endsWith(".env")
                || normalized.contains("/.env");
    }

    private String resolveLanguage(String path, String content) {
        String normalizedPath = normalizeText(path).toLowerCase();
        if (normalizedPath.endsWith(".py")) {
            return "python";
        }
        if (normalizedPath.endsWith(".sql")) {
            return "sql";
        }
        if (normalizeText(content).startsWith("#!/bin/")) {
            return "shell";
        }
        return "";
    }

    private String firstText(Map<String, Object> arguments, String... keys) {
        if (arguments == null || arguments.isEmpty() || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && arguments.containsKey(key)) {
                return normalizeText(arguments.get(key));
            }
        }
        return "";
    }

    private String excerpt(String content) {
        String text = content == null ? "" : content;
        if (text.length() <= EXCERPT_LIMIT) {
            return text;
        }
        return text.substring(0, EXCERPT_LIMIT);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
