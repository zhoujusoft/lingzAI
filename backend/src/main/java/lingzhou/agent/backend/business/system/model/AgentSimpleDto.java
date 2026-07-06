package lingzhou.agent.backend.business.system.model;

public class AgentSimpleDto {

    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String openingMessage;
    private String icon;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

}
