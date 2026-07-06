package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeV2TaskContract(
        List<RuntimeV2TaskIntent> intents,
        List<RuntimeV2SkillContract> activeSkillContracts,
        List<RuntimeV2ExecutionRequirement> activeRequirements) {

    public RuntimeV2TaskContract {
        intents = intents == null ? List.of() : List.copyOf(intents);
        activeSkillContracts = activeSkillContracts == null ? List.of() : List.copyOf(activeSkillContracts);
        activeRequirements = activeRequirements == null ? List.of() : List.copyOf(activeRequirements);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intents", intents);
        payload.put("activeSkillContracts", activeSkillContracts);
        payload.put("activeRequirements", activeRequirements);
        payload.put("intentCount", intents.size());
        payload.put("activeSkillContractCount", activeSkillContracts.size());
        payload.put("activeRequirementCount", activeRequirements.size());
        return payload;
    }

    public String toJson() {
        return JSON.toJSONString(toPayload());
    }
}
