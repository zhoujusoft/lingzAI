package lingzhou.agent.backend.common.api;

public final class RequestIdContext {

    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();

    private RequestIdContext() {}

    public static void set(String requestId) {
        REQUEST_ID_HOLDER.set(requestId);
    }

    public static String get() {
        return REQUEST_ID_HOLDER.get();
    }

    public static String getOrEmpty() {
        String value = REQUEST_ID_HOLDER.get();
        return value == null ? "" : value;
    }

    public static void clear() {
        REQUEST_ID_HOLDER.remove();
    }
}
