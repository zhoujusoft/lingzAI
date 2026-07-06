package lingzhou.agent.backend.business.system.model;

import java.util.List;

/**
 * 创建 Agent 输入
 */
public class CreateAgentInput {

    private String agentCode;
    private String agentName;
    private String description;
    private String openingMessage;
    private String icon;
    private String soulTemplate;
    private String profileTemplate;
    private Integer enabled = 1;
    private List<Long> skillIds;
    private List<Long> toolIds;

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOpeningMessage() {
        return openingMessage;
    }

    public void setOpeningMessage(String openingMessage) {
        this.openingMessage = openingMessage;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getSoulTemplate() {
        return soulTemplate;
    }

    public void setSoulTemplate(String soulTemplate) {
        this.soulTemplate = soulTemplate;
    }

    public String getProfileTemplate() {
        return profileTemplate;
    }

    public void setProfileTemplate(String profileTemplate) {
        this.profileTemplate = profileTemplate;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public List<Long> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<Long> skillIds) {
        this.skillIds = skillIds;
    }

    public List<Long> getToolIds() {
        return toolIds;
    }

    public void setToolIds(List<Long> toolIds) {
        this.toolIds = toolIds;
    }
}
