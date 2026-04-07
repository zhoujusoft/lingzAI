package lingzhou.agent.backend.common.lzException;

public class LZException extends RuntimeException {
    private ExceptionCode _errorCode = ExceptionCode.values()[0];
    /**
     * 错误码
     */
    public final ExceptionCode getErrorCode() {
        return _errorCode;
    }

    private MessageShowType MessageType = MessageShowType.values()[0];

    public final MessageShowType getMessageType() {
        return MessageType;
    }

    public final void setMessageType(MessageShowType value) {
        MessageType = value;
    }

    public LZException(ExceptionCode code, String message) {
        this(code, message, MessageShowType.Message);
    }
    // C# TO JAVA CONVERTER NOTE: Java does not support optional parameters. Overloaded method(s) are created above:
    // ORIGINAL LINE: public S2Exception(S2ExceptionCode code, string message, MessageShowType messageType =
    // MessageShowType.Message)
    public LZException(ExceptionCode code, String message, MessageShowType messageType) {
        super(message);
        this._errorCode = code;
        setMessageType(messageType);
    }
    /**
     * 用于在catch中再throw
     *
     * @param message 需要记录的异常信息
     * @param innerException 反馈给前端的友好的提示信息
     */
    public LZException(ExceptionCode code, String message, RuntimeException innerException) {
        this(code, message, innerException, MessageShowType.Message);
    }

    // C# TO JAVA CONVERTER NOTE: Java does not support optional parameters. Overloaded method(s) are created above:
    // ORIGINAL LINE: public S2Exception(S2ExceptionCode code, string message, Exception innerException, MessageShowType
    // messageType = MessageShowType.Message)
    public LZException(
            ExceptionCode code, String message, RuntimeException innerException, MessageShowType messageType) {
        super(message, innerException);
        this._errorCode = code;
        setMessageType(messageType);
    }
}
