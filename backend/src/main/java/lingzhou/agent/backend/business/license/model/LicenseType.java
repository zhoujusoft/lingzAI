package lingzhou.agent.backend.business.license.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang.StringUtils;

public enum LicenseType {
    TRIAL(0, "试用版"),
    STANDARD(1, "正式版");

    private final int code;
    private final String label;

    LicenseType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFormal() {
        return this == STANDARD;
    }

    @JsonCreator
    public static LicenseType fromValue(Object rawValue) {
        if (rawValue instanceof Number number) {
            return fromCode(number.intValue());
        }

        String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
        if (StringUtils.isBlank(value)) {
            return TRIAL;
        }
        if (StringUtils.equalsIgnoreCase(value, "1")
                || StringUtils.equalsIgnoreCase(value, "STANDARD")
                || StringUtils.equalsIgnoreCase(value, "FORMAL")
                || StringUtils.equalsIgnoreCase(value, "OFFICIAL")
                || StringUtils.equalsIgnoreCase(value, "正式版")
                || StringUtils.equalsIgnoreCase(value, "正式")) {
            return STANDARD;
        }
        if (StringUtils.equalsIgnoreCase(value, "0")
                || StringUtils.equalsIgnoreCase(value, "TRIAL")
                || StringUtils.equalsIgnoreCase(value, "试用版")
                || StringUtils.equalsIgnoreCase(value, "试用")) {
            return TRIAL;
        }
        return TRIAL;
    }

    public static LicenseType fromCode(Integer code) {
        return code != null && code == 1 ? STANDARD : TRIAL;
    }
}
