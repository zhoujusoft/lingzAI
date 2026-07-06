package lingzhou.agent.backend.capability.agentruntime.execution;

import java.nio.file.Path;
import java.util.function.Supplier;
import lingzhou.agent.backend.app.SkillExecutionScope;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
public class SandboxExecutionService {

    private final SkillRuntimeRegistry skillRuntimeRegistry;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;

    public SandboxExecutionService(
            SkillRuntimeRegistry skillRuntimeRegistry,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this.skillRuntimeRegistry = skillRuntimeRegistry;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
    }

    private void activateSkillExecutionScope(ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit) {
        SkillExecutionScope.activate(resolveSkillDirectory(prepared, requestSkillKit));
    }

    private void clearSkillExecutionScope() {
        SkillExecutionScope.clear();
    }

    public Flux<ServerSentEvent<String>> withSkillExecutionScope(
            Flux<ServerSentEvent<String>> stream, ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit) {
        if (stream == null) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            activateSkillExecutionScope(prepared, requestSkillKit);
            return stream.doFinally(signalType -> clearSkillExecutionScope());
        });
    }

    public <T> T callWithSkillExecutionScope(
            ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit, Supplier<T> supplier) {
        activateSkillExecutionScope(prepared, requestSkillKit);
        try {
            return supplier == null ? null : supplier.get();
        } finally {
            clearSkillExecutionScope();
        }
    }

    private Path resolveSkillDirectory(ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit) {
        if (prepared == null) {
            return null;
        }
        String runtimeSkillName =
                requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(requestSkillKit, prepared);
        if (!StringUtils.hasText(runtimeSkillName)) {
            return null;
        }
        if (prepared.scopeType() == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT_PREVIEW) {
            return Path.of(SkillStudioWorkspacePaths.DRAFT_ROOT)
                    .toAbsolutePath()
                    .normalize()
                    .resolve(runtimeSkillName.trim())
                    .normalize();
        }
        SkillRuntimeRegistry.FilesystemSkillDescriptor descriptor =
                skillRuntimeRegistry.findFilesystemSkill(runtimeSkillName);
        return descriptor == null ? null : descriptor.directoryPath();
    }
}
