package lingzhou.agent.backend.business.chat.execution.nativefs;

public class SandboxViolationException extends RuntimeException {

    public SandboxViolationException(String message) {
        super(message);
    }
}
