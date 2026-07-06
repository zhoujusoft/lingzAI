package lingzhou.agent.backend.capability.agentruntime.personal;

import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PersonalAgentModeResolver {

    public PersonalAgentMode resolve(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !prepared.personalAgent()) {
            return PersonalAgentMode.CHAT_ONLY;
        }
        PersonalAgentMode explicitMode = PersonalAgentMode.fromPreparedMode(prepared.personalAgentMode());
        if (explicitMode != PersonalAgentMode.CHAT_ONLY) {
            return explicitMode;
        }
        if (!StringUtils.hasText(prepared.message()) && prepared.fileListJson() != null) {
            return PersonalAgentMode.CONTENT_ASSIST;
        }
        if (looksLikeExecutionTask(prepared.message())) {
            return PersonalAgentMode.EXECUTION_TASK;
        }
        if (prepared.fileListJson() != null && prepared.fileListJson().contains("\"id\"")) {
            return PersonalAgentMode.CONTENT_ASSIST;
        }
        return PersonalAgentMode.CHAT_ONLY;
    }

    private boolean looksLikeExecutionTask(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.trim();
        return text.contains("导出")
                || text.contains("生成文件")
                || text.contains("保存为")
                || text.contains("筛选")
                || text.contains("整理成")
                || text.contains("执行")
                || text.contains("运行")
                || text.contains("修改文件")
                || text.contains("处理这个");
    }
}
