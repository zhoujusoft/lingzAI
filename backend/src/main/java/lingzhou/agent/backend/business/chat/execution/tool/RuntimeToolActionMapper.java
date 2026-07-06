package lingzhou.agent.backend.business.chat.execution.tool;

import java.util.Locale;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeToolActionMapper {

    public RuntimeExecutionAction map(String action) {
        if (!StringUtils.hasText(action)) {
            throw new IllegalArgumentException("runtime_tool.action 不能为空");
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "file_read", "read_file", "readfile" -> RuntimeExecutionAction.FILE_READ;
            case "file_write", "write_file", "writefile" -> RuntimeExecutionAction.FILE_WRITE;
            case "list_dir", "listdir" -> RuntimeExecutionAction.LIST_DIR;
            case "stat" -> RuntimeExecutionAction.STAT;
            case "run_python", "runpython" -> RuntimeExecutionAction.RUN_PYTHON;
            case "write_artifact", "writeartifact" -> RuntimeExecutionAction.WRITE_ARTIFACT;
            default -> throw new IllegalArgumentException("不支持的 runtime_tool.action: " + action);
        };
    }
}
