package lingzhou.agent.backend.capability.agentruntime.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RuntimeV2ActiveToolRegistry {

    private final RuntimeExecutionCapabilityAdapter runtimeExecutionCapability;

    public RuntimeV2ActiveToolRegistry(RuntimeExecutionCapabilityAdapter runtimeExecutionCapability) {
        this.runtimeExecutionCapability = runtimeExecutionCapability;
    }

    public Map<String, ToolCallback> refresh(RuntimeV2State state) {
        if (state == null || state.prepared() == null) {
            return Map.of();
        }
        Map<String, ToolCallback> merged = new LinkedHashMap<>();
        for (ToolCallback callback : state.prepared().toolCallbacks()) {
            registerToolCallback(merged, callback);
        }
        SkillKit requestSkillKit = state.requestSkillKit();
        if (requestSkillKit != null) {
            List<ToolCallback> dynamicCallbacks = new ArrayList<>();
            dynamicCallbacks.addAll(requestSkillKit.getSkillLoaderTools());
            dynamicCallbacks.addAll(requestSkillKit.getAllActiveTools());
            List<ToolCallback> wrappedDynamicCallbacks = runtimeExecutionCapability.bindToolCallbacks(
                    dynamicCallbacks, state.prepared(), state.conversation(), requestSkillKit);
            for (ToolCallback callback : wrappedDynamicCallbacks) {
                registerToolCallback(merged, callback);
            }
        }
        state.setToolCallbacks(List.copyOf(merged.values()));
        log.info(
                "Runtime V2 工具集刷新：sessionId={}, toolCount={}, tools={}",
                safeSessionId(state),
                state.toolCallbacks().size(),
                merged.keySet());
        return Map.copyOf(merged);
    }

    private void registerToolCallback(Map<String, ToolCallback> target, ToolCallback callback) {
        if (target == null || callback == null || callback.getToolDefinition() == null) {
            return;
        }
        String toolName = callback.getToolDefinition().name();
        if (!StringUtils.hasText(toolName)) {
            return;
        }
        target.putIfAbsent(toolName.trim(), callback);
    }

    private String safeSessionId(RuntimeV2State state) {
        if (state == null || state.conversation() == null) {
            return "";
        }
        return StringUtils.trimWhitespace(state.conversation().sessionCode());
    }
}
