package lingzhou.agent.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeviceType {
    /**
     * 未知
     */
    None(0),

    /**
     * PC
     */
    Web(1),

    /**
     * 移动端
     */
    App(2),

    /**
     * 微信
     */
    WeChat(3),

    /**
     * 钉钉
     */
    DingTalk(4),

    /**
     * 飞书
     */
    Feishu(5),

    All(999);

    public static final int SIZE = Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, DeviceType> mappings;

    private static java.util.HashMap<Integer, DeviceType> getMappings() {
        if (mappings == null) {
            synchronized (DeviceType.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, DeviceType>();
                }
            }
        }

        return mappings;
    }

    private DeviceType(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    @JsonValue
    public int getValue() {
        return intValue;
    }

    @JsonCreator
    public static DeviceType forValue(int value) {
        return getMappings().get(value);
    }
}
