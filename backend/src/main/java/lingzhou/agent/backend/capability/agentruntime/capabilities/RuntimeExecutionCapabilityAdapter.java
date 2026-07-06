package lingzhou.agent.backend.capability.agentruntime.capabilities;

import java.util.List;
import java.util.function.Supplier;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.agentruntime.execution.RuntimeToolExecutionService;
import lingzhou.agent.backend.capability.agentruntime.execution.SandboxExecutionService;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class RuntimeExecutionCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private final RuntimeToolExecutionService runtimeToolExecutionService;
    private final SandboxExecutionService sandboxExecutionService;

    public RuntimeExecutionCapabilityAdapter(
            RuntimeToolExecutionService runtimeToolExecutionService, SandboxExecutionService sandboxExecutionService) {
        super(RuntimeCapabilitySlot.RUNTIME_EXECUTION, "runtime-execution", RuntimeCapabilityStatus.ACTIVE);
        this.runtimeToolExecutionService = runtimeToolExecutionService;
        this.sandboxExecutionService = sandboxExecutionService;
    }

    public void prepareWorkspaceIfNeeded(
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        runtimeToolExecutionService.prepareWorkspaceIfNeeded(prepared, context, requestSkillKit);
    }

    public ChatRuntimePreparedRequest bindRuntimeToolCallbacks(
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        return runtimeToolExecutionService.bindRuntimeToolCallbacks(prepared, context, requestSkillKit);
    }

    public Flux<ServerSentEvent<String>> withRuntimeToolContext(
            Flux<ServerSentEvent<String>> stream,
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        return runtimeToolExecutionService.withRuntimeToolContext(stream, prepared, context, requestSkillKit);
    }

    public List<ToolCallback> bindToolCallbacks(
            List<ToolCallback> callbacks,
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        return runtimeToolExecutionService.bindToolCallbacks(callbacks, prepared, context, requestSkillKit);
    }

    public Flux<ServerSentEvent<String>> withSkillExecutionScope(
            Flux<ServerSentEvent<String>> stream, ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit) {
        return sandboxExecutionService.withSkillExecutionScope(stream, prepared, requestSkillKit);
    }

    public <T> T callWithSkillExecutionScope(
            ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit, Supplier<T> supplier) {
        return sandboxExecutionService.callWithSkillExecutionScope(prepared, requestSkillKit, supplier);
    }
}
