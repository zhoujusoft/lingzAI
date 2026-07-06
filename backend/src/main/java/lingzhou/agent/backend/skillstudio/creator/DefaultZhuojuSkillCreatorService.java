package lingzhou.agent.backend.skillstudio.creator;

import java.util.List;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import lingzhou.agent.backend.skillstudio.template.SkillStudioIntentMap;
import lingzhou.agent.backend.skillstudio.template.SkillStudioIntentMapLoader;
import lingzhou.agent.backend.skillstudio.template.SkillStudioTemplateResolver;
import lingzhou.agent.backend.skillstudio.validate.SkillStudioValidationService;
import org.springframework.stereotype.Service;

@Service
public class DefaultZhuojuSkillCreatorService implements ZhuojuSkillCreatorService {

    private final SkillStudioIntentClassifier intentClassifier;
    private final SkillStudioIntentMapLoader intentMapLoader;
    private final SkillStudioTemplateResolver templateResolver;
    private final SkillStudioProposalService proposalService;
    private final SkillStudioValidationService validationService;

    public DefaultZhuojuSkillCreatorService(
            SkillStudioIntentClassifier intentClassifier,
            SkillStudioIntentMapLoader intentMapLoader,
            SkillStudioTemplateResolver templateResolver,
            SkillStudioProposalService proposalService,
            SkillStudioValidationService validationService) {
        this.intentClassifier = intentClassifier;
        this.intentMapLoader = intentMapLoader;
        this.templateResolver = templateResolver;
        this.proposalService = proposalService;
        this.validationService = validationService;
    }

    @Override
    public SkillStudioChangeProposal createProposal(SkillStudioContextInput input) {
        var intent = intentClassifier.classify(input);
        SkillStudioIntentMap intentMap = intentMapLoader.load();
        String templateName = templateResolver.resolveBaseTemplate(input, intent, intentMap);
        SkillStudioChangeProposal draftProposal = proposalService.propose(input, intent, templateName);
        SkillStudioValidationResult validationResult = validationService.validateProposal(draftProposal);
        return new SkillStudioChangeProposal(
                draftProposal.skillName(),
                draftProposal.mode(),
                draftProposal.intent(),
                draftProposal.summary(),
                draftProposal.changes(),
                validationResult,
                List.copyOf(draftProposal.notes()));
    }
}
