package lingzhou.agent.backend.capability.agentruntime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StaticAgentRuntimeProfileRegistry {

    private final Map<AgentRuntimeProfile, AgentRuntimeProfileDefinition> definitions;

    public StaticAgentRuntimeProfileRegistry() {
        Map<AgentRuntimeProfile, AgentRuntimeProfileDefinition> values = new LinkedHashMap<>();
        register(
                values,
                AgentRuntimeProfile.GENERAL_CHAT,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE);
        register(
                values,
                AgentRuntimeProfile.SKILL_CHAT,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.RUNTIME_EXECUTION,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE);
        register(
                values,
                AgentRuntimeProfile.SKILL_STUDIO,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.RUNTIME_EXECUTION,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE);
        register(
                values,
                AgentRuntimeProfile.DATASET_CHAT,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE);
        register(
                values,
                AgentRuntimeProfile.PERSONAL_ASSISTANT,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE,
                RuntimeCapabilitySlot.TASK_EXECUTION);
        register(
                values,
                AgentRuntimeProfile.SUB_AGENT,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE,
                RuntimeCapabilitySlot.SUB_AGENT);
        register(
                values,
                AgentRuntimeProfile.TASK_EXECUTE,
                RuntimeCapabilitySlot.TOOL_CALLING,
                RuntimeCapabilitySlot.EVENT_PERSISTENCE,
                RuntimeCapabilitySlot.TOKEN_USAGE,
                RuntimeCapabilitySlot.OBSERVABILITY,
                RuntimeCapabilitySlot.SAFETY_GUARD,
                RuntimeCapabilitySlot.LONG_TERM_MEMORY,
                RuntimeCapabilitySlot.QUALITY_GATE,
                RuntimeCapabilitySlot.SUB_AGENT,
                RuntimeCapabilitySlot.TASK_EXECUTION);
        this.definitions = Map.copyOf(values);
    }

    public AgentRuntimeProfileDefinition require(AgentRuntimeProfile profile) {
        AgentRuntimeProfile resolvedProfile = profile == null ? AgentRuntimeProfile.GENERAL_CHAT : profile;
        AgentRuntimeProfileDefinition definition = definitions.get(resolvedProfile);
        if (definition == null) {
            throw new IllegalStateException("Missing agent runtime profile: " + resolvedProfile);
        }
        return definition;
    }

    private void register(
            Map<AgentRuntimeProfile, AgentRuntimeProfileDefinition> values,
            AgentRuntimeProfile profile,
            RuntimeCapabilitySlot... slots) {
        values.put(profile, new AgentRuntimeProfileDefinition(profile, slots == null ? List.of() : List.of(slots)));
    }
}
