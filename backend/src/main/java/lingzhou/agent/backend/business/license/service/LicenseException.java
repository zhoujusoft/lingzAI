package lingzhou.agent.backend.business.license.service;

public class LicenseException extends RuntimeException {

    private final int code;

    public LicenseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
