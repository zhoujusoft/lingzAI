package lingzhou.agent.backend.capability.agentruntime.context;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationMessage;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationContextWindowService;
import lingzhou.agent.backend.business.chat.service.ConversationEventService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.model.RuntimeModelRequest;
import lingzhou.agent.backend.capability.agentruntime.prompt.RuntimePromptPack;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ContextEngineeringService {

    private final ConversationEventService conversationEventService;
    private final ConversationContextWindowService conversationContextWindowService;
    private final ConversationHistoryService conversationHistoryService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;

    public ContextEngineeringService(
            ConversationEventService conversationEventService,
            ConversationContextWindowService conversationContextWindowService) {
        this(conversationEventService, conversationContextWindowService, null, null);
    }

    @Autowired
    public ContextEngineeringService(
            ConversationEventService conversationEventService,
            ConversationContextWindowService conversationContextWindowService,
            ConversationHistoryService conversationHistoryService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this.conversationEventService = conversationEventService;
        this.conversationContextWindowService = conversationContextWindowService;
        this.conversationHistoryService = conversationHistoryService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
    }

    public RuntimeContextAssembly assembleContext(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimePromptPack promptPack) {
        String executionStateSummary = context == null
                ? ""
                : conversationEventService.buildExecutionStateSummary(context.sessionCode());
        String systemPrompt = appendExecutionStateSummary(
                promptPack == null ? null : promptPack.systemPrompt(), executionStateSummary);
        return new RuntimeContextAssembly(
                buildHistoryMessages(context),
                systemPrompt,
                prepared == null ? null : prepared.userMessage(),
                prepared == null ? List.of() : prepared.toolCallbacks());
    }

    public RuntimeModelRequest buildModelRequest(
            ModelRuntimeClientFactory.ChatRuntimeBundle chatRuntimeBundle, RuntimeContextAssembly contextAssembly) {
        ChatClient.ChatClientRequestSpec requestSpec =
                buildRequestSpec(chatRuntimeBundle.chatClient(), contextAssembly);
        return new RuntimeModelRequest(chatRuntimeBundle, requestSpec, contextAssembly == null ? RuntimeContextAssembly.empty() : contextAssembly);
    }

    private ChatClient.ChatClientRequestSpec buildRequestSpec(
            ChatClient chatClient, RuntimeContextAssembly contextAssembly) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (contextAssembly == null) {
            return spec;
        }
        if (!contextAssembly.historyMessages().isEmpty()) {
            spec = spec.messages(contextAssembly.historyMessages());
        }
        if (StringUtils.hasText(contextAssembly.systemPrompt())) {
            spec.system(contextAssembly.systemPrompt());
        }
        if (StringUtils.hasText(contextAssembly.userMessage())) {
            spec.user(contextAssembly.userMessage());
        }
        if (!contextAssembly.toolCallbacks().isEmpty()) {
            spec.toolCallbacks(contextAssembly.toolCallbacks());
        }
        return spec;
    }

    public List<Message> buildHistoryMessages(ConversationHistoryService.ConversationContext context) {
        if (context == null) {
            return List.of();
        }
        List<Message> historyMessages =
                conversationEventService.buildSpringHistoryMessages(context.sessionCode(), context.userMessageId());
        List<Message> skillReadFactMessages = buildSkillReadFactMessages(context);
        if (skillReadFactMessages.isEmpty()) {
            return historyMessages;
        }
        java.util.ArrayList<Message> merged = new java.util.ArrayList<>(historyMessages);
        merged.addAll(skillReadFactMessages);
        return List.copyOf(merged);
    }

    public void compactIfNeeded(ConversationHistoryService.ConversationContext context) {
        if (context == null) {
            return;
        }
        conversationContextWindowService.compactIfNeeded(context);
    }

    private String appendExecutionStateSummary(String systemPrompt, String executionStateSummary) {
        if (!StringUtils.hasText(executionStateSummary)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return executionStateSummary;
        }
        return systemPrompt + "\n\n" + executionStateSummary;
    }

    private List<Message> buildSkillReadFactMessages(ConversationHistoryService.ConversationContext context) {
        if (context == null || conversationHistoryService == null || requestScopedSkillRuntimeService == null) {
            return List.of();
        }
        ConversationMessage previousAssistant = conversationHistoryService.findPreviousAssistantMessage(context);
        if (previousAssistant == null || !StringUtils.hasText(previousAssistant.getParamsJson())) {
            return List.of();
        }
        return requestScopedSkillRuntimeService.resolveSkillReadFactsFromParams(previousAssistant.getParamsJson()).stream()
                .map(RequestScopedSkillRuntimeService.SkillReadFact::message)
                .filter(StringUtils::hasText)
                .map(org.springframework.ai.chat.messages.SystemMessage::new)
                .map(Message.class::cast)
                .toList();
    }
}
