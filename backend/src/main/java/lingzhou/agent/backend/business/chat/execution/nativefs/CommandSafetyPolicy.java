package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommandSafetyPolicy {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\bln\\s+-s\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmklink\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmount\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bkill(all)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpkill\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btaskkill\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bstop-process\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(curl|wget)[^\\n\\r]*\\|[^\\n\\r]*(bash|sh|python|node)", Pattern.CASE_INSENSITIVE));

    private static final Pattern PARENT_ESCAPE = Pattern.compile("(?:^|[/\\\\\\s])\\.\\.(?:[/\\\\]|$)");
    private static final Pattern HOST_ABSOLUTE_UNIX =
            Pattern.compile("(?<![\\w./])/(Users|home|opt|etc|var|private|tmp)(?:/[^\\s\"']*)?");
    private static final Pattern HOST_ABSOLUTE_WIN = Pattern.compile("(?i)(?<![a-zA-Z])[A-Z]:[/\\\\][^\\s\"']*");

    public void assertSafe(String command) {
        if (!StringUtils.hasText(command)) {
            throw new SandboxViolationException("命令不能为空");
        }
        if (PARENT_ESCAPE.matcher(command).find()) {
            throw new SandboxViolationException("命令中包含 .. 路径逃逸");
        }
        if (HOST_ABSOLUTE_UNIX.matcher(command).find()
                || HOST_ABSOLUTE_WIN.matcher(command).find()) {
            throw new SandboxViolationException("命令中不允许直接使用宿主机绝对路径");
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                throw new SandboxViolationException("命令包含受限操作: " + pattern.pattern());
            }
        }
    }
}
