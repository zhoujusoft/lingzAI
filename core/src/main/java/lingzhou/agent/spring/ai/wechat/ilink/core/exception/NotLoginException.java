package lingzhou.agent.spring.ai.wechat.ilink.core.exception;

public class NotLoginException extends ILinkException {
    public NotLoginException(String m) {
        super(m);
    }

    public NotLoginException(String m, Throwable c) {
        super(m, c);
    }
}
