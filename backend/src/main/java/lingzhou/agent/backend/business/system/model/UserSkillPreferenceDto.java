package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class UserSkillPreferenceDto {

    private List<SkillSimpleDto> permittedSkills;
    private List<Long> enabledSkillIds;
    private Boolean configured;

    public List<SkillSimpleDto> getPermittedSkills() {
        return permittedSkills;
    }

    public void setPermittedSkills(List<SkillSimpleDto> permittedSkills) {
        this.permittedSkills = permittedSkills;
    }

    public List<Long> getEnabledSkillIds() {
        return enabledSkillIds;
    }

    public void setEnabledSkillIds(List<Long> enabledSkillIds) {
        this.enabledSkillIds = enabledSkillIds;
    }

    public Boolean getConfigured() {
        return configured;
    }

    public void setConfigured(Boolean configured) {
        this.configured = configured;
    }
}
