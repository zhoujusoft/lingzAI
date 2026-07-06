package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import java.util.List;

public record RuntimeV2SkillContract(
        String skillName,
        String displayName,
        List<String> toolNames,
        List<RuntimeV2ContractCapability> capabilities,
        List<RuntimeV2ExecutionRequirement> requirements) {

    public RuntimeV2SkillContract {
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }
}
