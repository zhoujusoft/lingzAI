package lingzhou.agent.backend.capability.tool.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class GlobalToolRegistry {

    private final List<ToolRegistration> registrations;
    private final List<ToolCallback> toolCallbacks;
    private final List<ToolCallback> systemRuntimeToolCallbacks;
    private final List<ToolCallback> bindableToolCallbacks;
    private final Map<String, ToolCallback> callbacksByName;
    private final Map<String, ToolRegistration> registrationsByName;

    public GlobalToolRegistry(List<ToolRegistration> registrations) {
        this.registrations = new ArrayList<>();
        this.toolCallbacks = new ArrayList<>();
        this.systemRuntimeToolCallbacks = new ArrayList<>();
        this.bindableToolCallbacks = new ArrayList<>();
        this.callbacksByName = new LinkedHashMap<>();
        this.registrationsByName = new LinkedHashMap<>();
        if (registrations != null) {
            for (ToolRegistration registration : registrations) {
                addRegistration(registration);
            }
        }
    }

    private synchronized void addRegistration(ToolRegistration registration) {
        ToolCallback callback = registration == null ? null : registration.callback();
        if (callback == null || callback.getToolDefinition() == null) {
            return;
        }
        String name = callback.getToolDefinition().name();
        if (name == null || name.isBlank() || callbacksByName.containsKey(name)) {
            return;
        }
        registrationsByName.put(name, registration);
        callbacksByName.put(name, callback);
        this.registrations.add(registration);
        this.toolCallbacks.add(callback);
        if (registration.systemRuntime()) {
            this.systemRuntimeToolCallbacks.add(callback);
        }
        if (registration.bindable()) {
            this.bindableToolCallbacks.add(callback);
        }
    }

    public synchronized void registerTool(ToolCallback callback, boolean bindable, boolean systemRuntime) {
        if (callback == null || callback.getToolDefinition() == null) {
            return;
        }
        String name = callback.getToolDefinition().name();
        if (name == null || name.isBlank()) {
            return;
        }
        if (callbacksByName.containsKey(name)) {
            return;
        }
        ToolRegistration registration = new ToolRegistration(callback, bindable, systemRuntime);
        addRegistration(registration);
    }

    public synchronized void registerTools(List<ToolCallback> callbacks, boolean bindable, boolean systemRuntime) {
        if (callbacks == null) return;
        for (ToolCallback callback : callbacks) {
            registerTool(callback, bindable, systemRuntime);
        }
    }

    public synchronized void unregisterTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        ToolCallback removed = callbacksByName.remove(toolName);
        if (removed == null) {
            return;
        }
        ToolRegistration registration = registrationsByName.remove(toolName);
        if (registration == null) {
            return;
        }
        registrations.remove(registration);
        toolCallbacks.remove(removed);
        systemRuntimeToolCallbacks.remove(removed);
        bindableToolCallbacks.remove(removed);
    }

    public synchronized void unregisterToolsBySource(String sourcePrefix) {
        if (sourcePrefix == null || sourcePrefix.isBlank()) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (String name : callbacksByName.keySet()) {
            if (name.startsWith(sourcePrefix)) {
                toRemove.add(name);
            }
        }
        for (String name : toRemove) {
            unregisterTool(name);
        }
    }

    public List<ToolCallback> getToolCallbacks() {
        return List.copyOf(toolCallbacks);
    }

    public List<ToolCallback> getSystemRuntimeToolCallbacks() {
        return List.copyOf(systemRuntimeToolCallbacks);
    }

    public List<ToolCallback> getBindableToolCallbacks() {
        return List.copyOf(bindableToolCallbacks);
    }

    public ToolCallback findByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        return callbacksByName.get(toolName);
    }

    public ToolCallback findBindableByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        ToolRegistration registration = registrationsByName.get(toolName);
        return registration != null && registration.bindable() ? registration.callback() : null;
    }

    public boolean contains(String toolName) {
        return findByName(toolName) != null;
    }

    public boolean containsBindable(String toolName) {
        return findBindableByName(toolName) != null;
    }

    public boolean containsSystemRuntime(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        ToolRegistration registration = registrationsByName.get(toolName);
        return registration != null && registration.systemRuntime();
    }

    public List<ToolDescriptor> getDescriptors() {
        List<ToolDescriptor> descriptors = new ArrayList<>(toolCallbacks.size());
        for (ToolRegistration registration : registrations) {
            ToolCallback callback = registration.callback();
            ToolDefinition definition = callback.getToolDefinition();
            descriptors.add(new ToolDescriptor(
                    definition.name(),
                    definition.description(),
                    registration.bindable(),
                    registration.systemRuntime()));
        }
        return descriptors;
    }

    public record ToolDescriptor(String name, String description, boolean bindable, boolean systemRuntime) {}

    public record ToolRegistration(ToolCallback callback, boolean bindable, boolean systemRuntime) {}
}
