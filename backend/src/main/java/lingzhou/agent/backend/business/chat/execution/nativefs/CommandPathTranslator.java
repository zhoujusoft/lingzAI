package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommandPathTranslator {

    private static final Pattern LOGICAL_PATH_PATTERN = Pattern.compile(
            "(?:(?<=^)|(?<=[\\s\"']))(/(?:workspace|uploads|outputs|temp|logs|skill|profile)(?:/[^\\s\"']*)?)");

    private final LogicalPathResolver logicalPathResolver;

    public CommandPathTranslator(LogicalPathResolver logicalPathResolver) {
        this.logicalPathResolver = logicalPathResolver;
    }

    public String resolveWorkDir(RuntimeWorkspace workspace, String workDir) {
        String normalized = logicalPathResolver.normalizeLogicalPath(
                StringUtils.hasText(workDir) ? workDir : workspace.defaultLogicalWorkDir());
        Path hostPath = logicalPathResolver.resolve(workspace, normalized);
        return hostPath.toString();
    }

    public String translateCommand(RuntimeWorkspace workspace, String command) {
        if (!StringUtils.hasText(command)) {
            return "";
        }
        Matcher matcher = LOGICAL_PATH_PATTERN.matcher(command);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String logicalPath = matcher.group(1);
            Path hostPath = logicalPathResolver.resolve(workspace, logicalPath);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(hostPath.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
