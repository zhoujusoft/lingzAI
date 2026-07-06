package lingzhou.agent.spring.ai.wechat.ilink.core.exception;

public class ConnectFailedException extends ILinkException {
    public ConnectFailedException(String m) {
        super(m);
    }

    public ConnectFailedException(String m, Throwable c) {
        super(m, c);
    }
}
