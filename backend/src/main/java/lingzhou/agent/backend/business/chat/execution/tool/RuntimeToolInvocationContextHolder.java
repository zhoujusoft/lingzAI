package lingzhou.agent.backend.business.chat.execution.tool;

public final class RuntimeToolInvocationContextHolder {

    private static final InheritableThreadLocal<RuntimeToolContext> HOLDER = new InheritableThreadLocal<>();

    private RuntimeToolInvocationContextHolder() {}

    public static void set(RuntimeToolContext context) {
        HOLDER.set(context);
    }

    public static RuntimeToolContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
