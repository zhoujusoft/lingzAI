package lingzhou.agent.backend.skillstudio.application;

import java.util.function.Consumer;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.context.SkillStudioContextAssembler;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode;
import lingzhou.agent.backend.skillstudio.runtime.SkillStudioCreatorSkillDebugResult;
import lingzhou.agent.backend.skillstudio.runtime.SkillStudioCreatorSkillInvoker;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SkillStudioCreatorDebugService {

    private final SkillStudioContextAssembler contextAssembler;
    private final SkillStudioCreatorSkillInvoker skillInvoker;

    public SkillStudioCreatorDebugService(
            SkillStudioContextAssembler contextAssembler, SkillStudioCreatorSkillInvoker skillInvoker) {
        this.contextAssembler = contextAssembler;
        this.skillInvoker = skillInvoker;
    }

    public SkillStudioCreatorSkillDebugResult applyBySkill(PreviewCommand command) throws TaskException {
        return skillInvoker.execute(buildInput(command));
    }

    public Flux<ServerSentEvent<String>> streamBySkill(
            PreviewCommand command,
            Consumer<SkillStudioCreatorSkillDebugResult> completionConsumer,
            Consumer<Throwable> errorConsumer)
            throws TaskException {
        return skillInvoker.streamExecute(buildInput(command), completionConsumer, errorConsumer);
    }

    private SkillStudioContextInput buildInput(PreviewCommand command) throws TaskException {
        SkillStudioContextInput input = contextAssembler.assemble(
                command.userId(),
                command.projectId(),
                command.skillName(),
                command.mode(),
                command.userGoal(),
                command.preferredTemplate(),
                command.preferMinimalChange(),
                command.allowCreateReferences());
        return input;
    }

    public record PreviewCommand(
            Long userId,
            Long projectId,
            String skillName,
            SkillStudioMode mode,
            String userGoal,
            String preferredTemplate,
            boolean preferMinimalChange,
            boolean allowCreateReferences) {}
}
