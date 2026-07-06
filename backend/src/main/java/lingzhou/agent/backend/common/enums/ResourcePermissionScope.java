package lingzhou.agent.backend.common.enums;

public enum ResourcePermissionScope {
    OWNER_ONLY(1),
    PUBLIC_VISIBLE_OWNER_OPERATE(2),
    PUBLIC_FULL_ACCESS(3);

    private final int code;

    ResourcePermissionScope(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static int normalizeCode(Integer value) {
        if (value == null) {
            return PUBLIC_FULL_ACCESS.code;
        }
        for (ResourcePermissionScope scope : values()) {
            if (scope.code == value) {
                return scope.code;
            }
        }
        return PUBLIC_FULL_ACCESS.code;
    }
}
