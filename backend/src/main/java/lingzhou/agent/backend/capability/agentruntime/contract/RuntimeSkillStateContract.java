package lingzhou.agent.backend.capability.agentruntime.contract;

import java.util.List;

public record RuntimeSkillStateContract(
        List<String> loadedSkillNames,
        String currentRuntimeSkillName,
        Long selectedSkillHintId,
        String selectedSkillHintRuntimeSkillName,
        Long mentionedSkillId,
        List<RuntimeSkillReadFactContract> skillReadFacts) {

    public RuntimeSkillStateContract {
        loadedSkillNames = loadedSkillNames == null ? List.of() : List.copyOf(loadedSkillNames);
        skillReadFacts = skillReadFacts == null ? List.of() : List.copyOf(skillReadFacts);
    }
}
