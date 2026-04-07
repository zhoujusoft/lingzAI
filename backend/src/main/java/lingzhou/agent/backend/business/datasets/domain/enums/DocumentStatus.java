package lingzhou.agent.backend.business.datasets.domain.enums;

public enum DocumentStatus {
    PENDING("0", "待处理"),
    PROCESSED("1", "已处理"),
    FAILED("2", "处理失败");

    private final String value;
    private final String displayName;

    DocumentStatus(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    // 获取数据库存储的原始值（如 "0"）
    public String getValue() {
        return value;
    }

    // 获取前端展示的友好名称（如 "待处理"）
    public String getDisplayName() {
        return displayName;
    }

    // 根据数据库值反推枚举类型
    public static DocumentStatus fromValue(String value) {
        for (DocumentStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的文档状态: " + value);
    }
}
