package lingzhou.agent.backend.capability.dataset.runtime;

import java.util.Locale;
import java.util.regex.Pattern;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LowcodeDatasetSqlValidator {

    private static final Pattern FORBIDDEN_WRITE_KEYWORD =
            Pattern.compile("(^|[^a-z0-9_])(update|insert|delete)([^a-z0-9_]|$)");

    public void validate(String sql) throws TaskException {
        String normalized = StringUtils.hasText(sql) ? sql.trim().toLowerCase(Locale.ROOT) : "";
        if (!normalized.startsWith("select")) {
            throw compatibilityError("仅支持以 SELECT 开头的查询，不支持 WITH 等其他查询形式");
        }
        if (FORBIDDEN_WRITE_KEYWORD.matcher(normalized).find()) {
            throw compatibilityError("SQL 中包含独立的 update、insert 或 delete 写操作关键字");
        }
    }

    private TaskException compatibilityError(String reason) {
        return new TaskException("低代码 sqlSelect 兼容性校验失败：" + reason, TaskException.Code.UNKNOWN);
    }
}
