package lingzhou.agent.backend.capability.agentruntime.personal;

import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationRunConstants;
import lingzhou.agent.backend.business.chat.service.ConversationRunContext;
import lingzhou.agent.backend.business.chat.service.ConversationRunService;
import org.springframework.stereotype.Service;

@Service
public class PersonalAgentPreflightService {

    private final PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService;
    private final ConversationHistoryService conversationHistoryService;
    private final ConversationRunService conversationRunService;

    public PersonalAgentPreflightService(
            PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService,
            ConversationHistoryService conversationHistoryService,
            ConversationRunService conversationRunService) {
        this.personalAgentExecutionSnapshotService = personalAgentExecutionSnapshotService;
        this.conversationHistoryService = conversationHistoryService;
        this.conversationRunService = conversationRunService;
    }

    public PreflightResult prepare(
            ConversationHistoryService.ConversationContext context,
            ConversationRunContext runContext,
            ChatRuntimePreparedRequest prepared) {
        personalAgentExecutionSnapshotService.recordPlanningEvents(context, prepared);
        PersonalAgentExecutionSnapshotService.PrecheckDecision decision =
                personalAgentExecutionSnapshotService.resolvePrecheckDecision(prepared);
        if (!decision.terminal()) {
            return PreflightResult.continueWith(prepared);
        }
        ChatRuntimePreparedRequest terminalPrepared = "CONFIRMATION_REQUIRED".equals(decision.status())
                ? personalAgentExecutionSnapshotService.prepareForConfirmationResponse(context, prepared)
                : personalAgentExecutionSnapshotService.prepareForBlockedResponse(context, prepared);
        persistTerminalResponse(context, runContext, terminalPrepared, decision);
        return PreflightResult.terminal(terminalPrepared, decision.status(), decision.responseMessage());
    }

    private void persistTerminalResponse(
            ConversationHistoryService.ConversationContext context,
            ConversationRunContext runContext,
            ChatRuntimePreparedRequest prepared,
            PersonalAgentExecutionSnapshotService.PrecheckDecision decision) {
        if (context == null || prepared == null || decision == null) {
            return;
        }
        conversationHistoryService.completeMessage(
                context, decision.responseMessage(), null, prepared.fileListJson(), prepared.paramsJson(), 0L, null);
        if (runContext == null || runContext.runId() == null || runContext.runId() <= 0) {
            return;
        }
        if ("CONFIRMATION_REQUIRED".equals(decision.status())) {
            conversationRunService.updateRunningState(
                    runContext.runId(),
                    ConversationRunConstants.STATUS_WAITING_INPUT,
                    ConversationRunConstants.PHASE_TRIAGE,
                    "PERSONAL_AGENT_CONFIRMATION_REQUIRED",
                    decision.responseMessage(),
                    prepared.runtimeSkillName(),
                    prepared.paramsJson());
            return;
        }
        conversationRunService.failRun(
                runContext.runId(),
                context.assistantMessageId(),
                "PERSONAL_AGENT_PRECHECK_BLOCKED",
                decision.responseMessage(),
                prepared.paramsJson());
    }

    public record PreflightResult(
            ChatRuntimePreparedRequest prepared, boolean terminal, String status, String responseMessage) {

        public static PreflightResult continueWith(ChatRuntimePreparedRequest prepared) {
            return new PreflightResult(prepared, false, "", "");
        }

        public static PreflightResult terminal(
                ChatRuntimePreparedRequest prepared, String status, String responseMessage) {
            return new PreflightResult(
                    prepared, true, status == null ? "" : status, responseMessage == null ? "" : responseMessage);
        }
    }
}
