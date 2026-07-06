package lingzhou.agent.backend.skillstudio.context;

import java.util.List;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioToolProfileService;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioToolResolutionService;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode;
import org.springframework.stereotype.Service;

@Service
public class DefaultSkillStudioContextAssembler implements SkillStudioContextAssembler {

    private final SkillStudioContextSummaryService contextSummaryService;
    private final SkillStudioProjectSettingsService projectSettingsService;
    private final SkillStudioToolResolutionService toolResolutionService;
    private final SkillStudioToolProfileService toolProfileService;

    public DefaultSkillStudioContextAssembler(
            SkillStudioContextSummaryService contextSummaryService,
            SkillStudioProjectSettingsService projectSettingsService,
            SkillStudioToolResolutionService toolResolutionService,
            SkillStudioToolProfileService toolProfileService) {
        this.contextSummaryService = contextSummaryService;
        this.projectSettingsService = projectSettingsService;
        this.toolResolutionService = toolResolutionService;
        this.toolProfileService = toolProfileService;
    }

    @Override
    public SkillStudioContextInput assemble(
            Long userId,
            Long projectId,
            String skillName,
            SkillStudioMode mode,
            String userGoal,
            String preferredTemplate,
            boolean preferMinimalChange,
            boolean allowCreateReferences)
            throws TaskException {
        SkillStudioProjectSettingsService.ProjectSettingsState settingsState = userId == null || projectId == null
                ? new SkillStudioProjectSettingsService.ProjectSettingsState(
                        null, List.of(), List.of(), List.of(), "", "", false)
                : projectSettingsService.loadState(userId, projectId);
        SkillStudioContextInput.ToolResolution toolResolution = toolResolutionService.resolve(settingsState, userGoal);
        List<SkillStudioContextInput.ToolProfile> toolProfiles = toolProfileService.buildProfiles(settingsState);
        return new SkillStudioContextInput(
                skillName,
                mode,
                userGoal,
                contextSummaryService.buildDraftSummary(skillName),
                contextSummaryService.buildMemorySummary(skillName),
                new SkillStudioContextInput.CreatorHints(preferredTemplate, preferMinimalChange, allowCreateReferences),
                toolResolution,
                toolProfiles,
                settingsState.projectHints(),
                settingsState.projectConstraints());
    }
}
