package lingzhou.agent.backend.common.lzException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 前端消息类
 */
public enum MessageShowType {
    /**
     * 正常的弹出消息
     */
    Message(0),

    /**
     * 显示model样式的弹窗
     */
    ShowModel(10),

    /**
     * 显示model弹窗并且有回调函数
     */
    ShowModelAndCallback(11);

    public static final int SIZE = Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, MessageShowType> mappings;

    private static java.util.HashMap<Integer, MessageShowType> getMappings() {
        if (mappings == null) {
            synchronized (MessageShowType.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, MessageShowType>();
                }
            }
        }
        return mappings;
    }

    private MessageShowType(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    @JsonValue
    public int getValue() {
        return intValue;
    }

    @JsonCreator
    public static MessageShowType forValue(int value) {
        return getMappings().get(value);
    }
}
