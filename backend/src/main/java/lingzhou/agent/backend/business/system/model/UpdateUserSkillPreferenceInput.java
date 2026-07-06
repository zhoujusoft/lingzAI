package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class UpdateUserSkillPreferenceInput {

    private List<Long> enabledSkillIds;

    public List<Long> getEnabledSkillIds() {
        return enabledSkillIds;
    }

    public void setEnabledSkillIds(List<Long> enabledSkillIds) {
        this.enabledSkillIds = enabledSkillIds;
    }
}
