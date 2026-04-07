package lingzhou.agent.backend.capability.tool.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class GlobalToolRegistry {

    private final List<ToolCallback> toolCallbacks;
    private final Map<String, ToolCallback> callbacksByName;

    public GlobalToolRegistry(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = new ArrayList<>();
        this.callbacksByName = new LinkedHashMap<>();
        if (toolCallbacks == null) {
            return;
        }
        for (ToolCallback callback : toolCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (name == null || name.isBlank() || callbacksByName.containsKey(name)) {
                continue;
            }
            callbacksByName.put(name, callback);
            this.toolCallbacks.add(callback);
        }
    }

    public List<ToolCallback> getToolCallbacks() {
        return List.copyOf(toolCallbacks);
    }

    public ToolCallback findByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        return callbacksByName.get(toolName);
    }

    public boolean contains(String toolName) {
        return findByName(toolName) != null;
    }

    public List<ToolDescriptor> getDescriptors() {
        List<ToolDescriptor> descriptors = new ArrayList<>(toolCallbacks.size());
        for (ToolCallback callback : toolCallbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            descriptors.add(new ToolDescriptor(definition.name(), definition.description()));
        }
        return descriptors;
    }

    public record ToolDescriptor(String name, String description) {}
}
