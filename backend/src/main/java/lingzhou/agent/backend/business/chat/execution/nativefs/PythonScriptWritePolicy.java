package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class PythonScriptWritePolicy {

    public static final String FILE_WRITE_TOOL_DESCRIPTION_SUFFIX =
            " When writing /workspace/*.py scripts, stay within the runtime safe subset: pass paths through run_python args + sys.argv, do not hardcode logical roots or host absolute paths, and do not use subprocess/os.system/os.popen/eval/exec/__import__/pty process APIs.";

    public static final String CODE_SCRIPT_MODEL_GUIDANCE =
            """
            - 生成 `/workspace/*.py` 时，必须同时遵守 `file_write` 的 Python 安全约束。
            - 允许的脚本范围仅限受控的数据读取、处理和产物生成。
            - 禁止 `subprocess`、`os.system(`、`os.popen(`、`eval(`、`exec(`、`__import__(`、`pty.spawn(`、`pty.openpty(`、`pty.fork(`。
            - 路径只能通过 `run_python args` + `sys.argv` 传入；不要在源码里硬编码 `/workspace`、`/uploads`、`/outputs`、`/temp`、`/skill`、`/profile`。
            - 不要写宿主机绝对路径或搜索路径，例如 `/tmp`、`/var/tmp`、`/Users`、`/private`、`/etc`、`/var`、`/root`、Windows 盘符、`sys.path.append('/...')`。
            - 不要通过 `os.environ` / `getenv` 读取 `LINGZ_*` 运行时路径。
            - `tempfile.TemporaryDirectory()`、`shutil.rmtree(temp_dir, ignore_errors=True)`、`os.remove()`、`os.unlink()` 可用于清理脚本自己创建的临时文件/目录，但不要删除输入附件、最终产物或运行时根目录。
            """;

    private static final List<String> HIGH_RISK_PATTERNS = List.of(
            "import subprocess",
            "from subprocess import",
            "os.system(",
            "os.popen(",
            "eval(",
            "exec(",
            "__import__(",
            "import pty",
            "pty.spawn(",
            "pty.openpty(",
            "pty.fork(");

    private static final List<String> HOST_PATH_PATTERNS = List.of(
            "/tmp/",
            "\"/tmp\"",
            "'/tmp'",
            "/var/tmp/",
            "\"/var/tmp\"",
            "'/var/tmp'",
            "/users/",
            "/private/",
            "/etc/",
            "/var/",
            "/root/",
            "c:\\\\",
            "sys.path.append('/",
            "sys.path.append(\"/");

    private static final List<String> ENV_PATTERNS = List.of(
            "os.environ[\"lingz_",
            "os.environ['lingz_",
            "os.environ.get(\"lingz_",
            "os.environ.get('lingz_",
            "os.getenv(\"lingz_",
            "os.getenv('lingz_",
            "environ[\"lingz_",
            "environ['lingz_",
            "environ.get(\"lingz_",
            "environ.get('lingz_",
            "getenv(\"lingz_",
            "getenv('lingz_");

    private static final List<String> LOGICAL_ROOTS =
            List.of("/workspace", "/uploads", "/outputs", "/temp", "/skill", "/profile");

    private PythonScriptWritePolicy() {}

    public static String buildCodeScriptPromptContract() {
        StringBuilder builder = new StringBuilder();
        builder.append(CODE_SCRIPT_MODEL_GUIDANCE.trim());
        builder.append("\n");
        builder.append("- 下面这些规则会在 `file_write` 写入 `/workspace/*.py` 时被执行器按字面校验；命中后会直接拦截写入，所以生成脚本时必须提前规避。\n");
        builder.append("- 高风险调用黑名单：")
                .append(joinPatternsForPrompt(HIGH_RISK_PATTERNS))
                .append("。\n");
        builder.append("- 宿主机路径/搜索路径黑名单：")
                .append(joinPatternsForPrompt(HOST_PATH_PATTERNS))
                .append("。\n");
        builder.append("- 运行时环境变量黑名单：")
                .append(joinPatternsForPrompt(ENV_PATTERNS))
                .append("。\n");
        builder.append("- 逻辑根路径字面量黑名单：不要在源码字符串里直接出现 ")
                .append(joinPatternsForPrompt(LOGICAL_ROOTS))
                .append("；这些路径必须通过 `run_python args` 传入，再由 `sys.argv` 读取。\n");
        builder.append("- 如果确实需要临时目录，只能使用 `tempfile` 动态创建，不要写死 `/tmp`、`/var/tmp` 等字面量。\n");
        builder.append("- 如果需要删除临时文件，只删除脚本自己创建的临时文件；不要删除输入附件、最终产物或运行时根目录。\n");
        return builder.toString();
    }

    public static String validateWorkspacePythonScript(String logicalPath, String content) {
        if (!isWorkspacePythonScript(logicalPath)) {
            return "";
        }
        String script = content == null ? "" : content;
        String normalizedScript = script.toLowerCase(Locale.ROOT);

        String matchedHighRiskPattern = findFirstMatch(normalizedScript, HIGH_RISK_PATTERNS);
        if (StringUtils.hasText(matchedHighRiskPattern)) {
            return "写入的 Python 脚本包含高风险调用 `" + matchedHighRiskPattern + "`，已被拦截。请仅保留受控的数据读取、处理和产物生成逻辑。";
        }

        String matchedHostPathPattern = findFirstMatch(normalizedScript, HOST_PATH_PATTERNS);
        if (StringUtils.hasText(matchedHostPathPattern)) {
            return "写入的 Python 脚本包含宿主机绝对路径或搜索路径修改 `"
                    + matchedHostPathPattern
                    + "`，已被拦截。请改用 run_python args + sys.argv 传递路径。";
        }

        String matchedEnvPattern = findFirstMatch(normalizedScript, ENV_PATTERNS);
        if (StringUtils.hasText(matchedEnvPattern)) {
            return "写入的 Python 脚本试图通过环境变量读取运行时路径 `"
                    + matchedEnvPattern
                    + "`，已被拦截。当前版本只允许通过 run_python args + sys.argv 传递路径。";
        }

        String logicalRootLiteral = detectLogicalRootLiteral(script);
        if (StringUtils.hasText(logicalRootLiteral)) {
            return "写入的 Python 脚本直接硬编码了逻辑路径 "
                    + logicalRootLiteral
                    + "。请不要在脚本正文里直接写 /workspace、/uploads、/outputs、/temp、/skill、/profile；应通过 run_python args 传入，再由脚本用 sys.argv 读取。";
        }
        return "";
    }

    private static boolean isWorkspacePythonScript(String logicalPath) {
        if (!StringUtils.hasText(logicalPath)) {
            return false;
        }
        String normalizedPath = logicalPath.trim().toLowerCase(Locale.ROOT);
        return normalizedPath.startsWith("/workspace/") && normalizedPath.endsWith(".py");
    }

    private static String findFirstMatch(String source, List<String> patterns) {
        if (!StringUtils.hasText(source) || patterns == null || patterns.isEmpty()) {
            return "";
        }
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern) && source.contains(pattern)) {
                return pattern;
            }
        }
        return "";
    }

    private static String detectLogicalRootLiteral(String script) {
        if (!StringUtils.hasText(script)) {
            return "";
        }
        for (String logicalRoot : LOGICAL_ROOTS) {
            if (containsQuotedLiteral(script, logicalRoot)) {
                return logicalRoot;
            }
        }
        return "";
    }

    private static boolean containsQuotedLiteral(String script, String literal) {
        if (!StringUtils.hasText(script) || !StringUtils.hasText(literal)) {
            return false;
        }
        return script.contains("'" + literal + "'")
                || script.contains("\"" + literal + "\"")
                || script.contains("'" + literal + "/")
                || script.contains("\"" + literal + "/");
    }

    private static String joinPatternsForPrompt(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "";
        }
        return patterns.stream()
                .filter(StringUtils::hasText)
                .map(pattern -> "`" + pattern + "`")
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }
}
