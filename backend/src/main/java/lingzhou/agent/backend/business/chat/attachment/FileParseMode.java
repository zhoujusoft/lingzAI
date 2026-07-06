package lingzhou.agent.backend.business.chat.attachment;

public enum FileParseMode {
    TEXT,
    MARKDOWN,
    STRUCTURED;

    public static FileParseMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return STRUCTURED;
        }
        String normalized = value.trim().toUpperCase();
        for (FileParseMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return STRUCTURED;
    }
}
