package lingzhou.agent.backend.business.system.model;

public class SkillSimpleDto {

    private Long id;
    private String runtimeSkillName;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String iconColor;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuntimeSkillName() {
        return runtimeSkillName;
    }

    public void setRuntimeSkillName(String runtimeSkillName) {
        this.runtimeSkillName = runtimeSkillName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getIconColor() {
        return iconColor;
    }

    public void setIconColor(String iconColor) {
        this.iconColor = iconColor;
    }
}
