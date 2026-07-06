package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ContractSupport {

    public static final String EXTENSION_KEY = "runtimeContract";

    private final ObjectMapper objectMapper;

    public RuntimeV2ContractSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeV2SkillContract readSkillContract(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            if (payload instanceof RuntimeV2SkillContract contract) {
                return contract;
            }
            if (payload instanceof Map<?, ?> map) {
                return objectMapper.convertValue(map, RuntimeV2SkillContract.class);
            }
            if (payload instanceof String text && StringUtils.hasText(text)) {
                return objectMapper.readValue(text, RuntimeV2SkillContract.class);
            }
            return objectMapper.convertValue(payload, RuntimeV2SkillContract.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public Map<String, Object> toPayload(RuntimeV2SkillContract contract) {
        if (contract == null) {
            return Map.of();
        }
        return objectMapper.convertValue(contract, new TypeReference<Map<String, Object>>() {});
    }

    public RuntimeV2SkillContract normalize(RuntimeV2SkillContract contract) {
        if (contract == null) {
            return null;
        }
        List<String> toolNames = contract.toolNames() == null ? List.of() : List.copyOf(contract.toolNames());
        List<RuntimeV2ContractCapability> capabilities =
                contract.capabilities() == null ? List.of() : List.copyOf(contract.capabilities());
        List<RuntimeV2ExecutionRequirement> requirements =
                contract.requirements() == null ? List.of() : List.copyOf(contract.requirements());
        return new RuntimeV2SkillContract(
                contract.skillName(), contract.displayName(), toolNames, capabilities, requirements);
    }
}
