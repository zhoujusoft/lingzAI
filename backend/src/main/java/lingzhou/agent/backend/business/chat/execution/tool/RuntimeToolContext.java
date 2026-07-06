package lingzhou.agent.backend.business.chat.execution.tool;

import java.util.function.Supplier;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import org.springframework.util.StringUtils;

public final class RuntimeToolContext {

    private final String sessionId;
    private final Long userId;
    private final Long runId;
    private final LingzRuntimeScopeType scopeType;
    private final Long scopeId;
    private final String fallbackRuntimeSkillName;
    private final Supplier<String> runtimeSkillNameSupplier;
    private final RuntimeExecutionMode runtimeMode;
    private final String fileListJson;
    private final String paramsJson;
    private final boolean personalAgent;
    private final String personalAgentMode;
    private final Long requestMessageId;
    private final Long assistantMessageId;

    public RuntimeToolContext(
            String sessionId,
            Long userId,
            Long runId,
            LingzRuntimeScopeType scopeType,
            Long scopeId,
            String fallbackRuntimeSkillName,
            Supplier<String> runtimeSkillNameSupplier,
            RuntimeExecutionMode runtimeMode,
            String fileListJson,
            String paramsJson,
            boolean personalAgent,
            String personalAgentMode,
            Long requestMessageId,
            Long assistantMessageId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.runId = runId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.fallbackRuntimeSkillName = normalize(fallbackRuntimeSkillName);
        this.runtimeSkillNameSupplier = runtimeSkillNameSupplier;
        this.runtimeMode = runtimeMode;
        this.fileListJson = fileListJson;
        this.paramsJson = paramsJson;
        this.personalAgent = personalAgent;
        this.personalAgentMode = normalize(personalAgentMode);
        this.requestMessageId = requestMessageId;
        this.assistantMessageId = assistantMessageId;
    }

    public String sessionId() {
        return sessionId;
    }

    public Long userId() {
        return userId;
    }

    public Long runId() {
        return runId;
    }

    public LingzRuntimeScopeType scopeType() {
        return scopeType;
    }

    public Long scopeId() {
        return scopeId;
    }

    public String fallbackRuntimeSkillName() {
        return fallbackRuntimeSkillName;
    }

    public String currentRuntimeSkillName() {
        String resolved = runtimeSkillNameSupplier == null ? "" : normalize(runtimeSkillNameSupplier.get());
        return StringUtils.hasText(resolved) ? resolved : fallbackRuntimeSkillName;
    }

    public RuntimeExecutionMode runtimeMode() {
        return runtimeMode;
    }

    public String fileListJson() {
        return fileListJson;
    }

    public String paramsJson() {
        return paramsJson;
    }

    public boolean personalAgent() {
        return personalAgent;
    }

    public String personalAgentMode() {
        return personalAgentMode;
    }

    public Long requestMessageId() {
        return requestMessageId;
    }

    public Long assistantMessageId() {
        return assistantMessageId;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
