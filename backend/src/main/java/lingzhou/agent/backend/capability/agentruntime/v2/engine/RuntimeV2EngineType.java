package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum RuntimeV2EngineType {
    GRAPH("graph");

    private final String configValue;

    RuntimeV2EngineType(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static RuntimeV2EngineType fromConfigValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return GRAPH;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if ("graph-preview".equals(normalized) || "classic".equals(normalized)) {
            return GRAPH;
        }
        for (RuntimeV2EngineType value : values()) {
            if (value.configValue.equals(normalized)) {
                return value;
            }
        }
        return GRAPH;
    }
}
