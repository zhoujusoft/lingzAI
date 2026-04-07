package lingzhou.agent.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;

public enum State {
    Inactive(0),
    Active(1),
    Unspecified(-1),
    WaitActive(-2),
    Quit(-3);

    private int intValue;

    private static Map<Integer, State> mappings;

    private static Map<Integer, State> getMappings() {
        if (mappings == null) mappings = new HashMap<>();
        return mappings;
    }

    State(int value) {
        this.intValue = value;
        getMappings().put(Integer.valueOf(value), this);
    }

    @JsonValue
    public int getValue() {
        return this.intValue;
    }

    @JsonCreator
    public static State forValue(int value) {
        return getMappings().get(Integer.valueOf(value));
    }
}
