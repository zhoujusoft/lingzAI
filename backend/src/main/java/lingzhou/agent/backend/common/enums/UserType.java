package lingzhou.agent.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserType {
    admin(0), // 管理员
    user(1); // 普通用户

    public static final int SIZE = Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, UserType> mappings;

    private static java.util.HashMap<Integer, UserType> getMappings() {
        if (mappings == null) {
            synchronized (UserType.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, UserType>();
                }
            }
        }
        return mappings;
    }

    private UserType(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    @JsonValue
    public int getValue() {
        return intValue;
    }

    @JsonCreator
    public static UserType forValue(int value) {
        return getMappings().get(value);
    }
}
