package lingzhou.agent.backend.capability.agentruntime.v2.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionAssessment;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContract;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public class RuntimeV2State {

    private final ChatRuntimePreparedRequest prepared;
    private final Long userId;
    private final ConversationHistoryService.ConversationContext conversation;
    private final List<Message> messages = new ArrayList<>();
    private final List<Map<String, Object>> toolEvents = new ArrayList<>();
    private final List<Map<String, Object>> promptToolEvents = new ArrayList<>();
    private final List<Map<String, Object>> timelineSegments = new ArrayList<>();
    private final List<Map<String, Object>> phaseTrace = new ArrayList<>();
    private final List<Map<String, Object>> observationTrace = new ArrayList<>();
    private final List<RuntimeV2EvidenceEntry> evidenceLedger = new ArrayList<>();
    private final List<RuntimeV2ObligationEntry> obligationLedger = new ArrayList<>();
    private final List<RuntimeV2SkillContract> activeSkillContracts = new ArrayList<>();
    private final SkillKit requestSkillKit;
    private final ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig;

    private List<ToolCallback> toolCallbacks;
    private RuntimeV2Mode mode;
    private RuntimeV2Phase phase;
    private RuntimeV2FinishReason finishReason;
    private Long runId;
    private String runCode = "";
    private String runType = "";
    private String finalAnswer = "";
    private String phaseSubStage = "";
    private String phaseSubStageLabel = "";
    private String phaseProgressMessage = "";
    private boolean terminalAnswerStreamed;
    private final StringBuilder terminalAnswerStreamBuffer = new StringBuilder();
    private int iterationCount;
    private int llmCallCount;
    private int toolCallCount;
    private int decisionRepairCount;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private RuntimeV2CompletionAssessment completionAssessment;
    private RuntimeV2TaskContract taskContract;
    private Map<String, Object> codeState = Map.of();
    private volatile boolean cancellationRequested;
    private String cancellationReason = "";

    public RuntimeV2State(
            ChatRuntimePreparedRequest prepared,
            Long userId,
            ConversationHistoryService.ConversationContext conversation,
            List<ToolCallback> toolCallbacks,
            SkillKit requestSkillKit,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig) {
        this.prepared = prepared;
        this.userId = userId;
        this.conversation = conversation;
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
        this.requestSkillKit = requestSkillKit;
        this.modelConfig = modelConfig;
    }

    public ChatRuntimePreparedRequest prepared() {
        return prepared;
    }

    public Long userId() {
        return userId;
    }

    public ConversationHistoryService.ConversationContext conversation() {
        return conversation;
    }

    public List<ToolCallback> toolCallbacks() {
        return toolCallbacks;
    }

    public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
    }

    public List<Message> messages() {
        return messages;
    }

    public void replaceMessages(List<Message> nextMessages) {
        messages.clear();
        if (nextMessages != null && !nextMessages.isEmpty()) {
            messages.addAll(nextMessages);
        }
    }

    public List<Map<String, Object>> toolEvents() {
        return toolEvents;
    }

    public List<Map<String, Object>> promptToolEvents() {
        return promptToolEvents;
    }

    public List<Map<String, Object>> timelineSegments() {
        return timelineSegments;
    }

    public List<Map<String, Object>> phaseTrace() {
        return phaseTrace;
    }

    public List<Map<String, Object>> observationTrace() {
        return observationTrace;
    }

    public List<RuntimeV2EvidenceEntry> evidenceLedger() {
        return List.copyOf(evidenceLedger);
    }

    public void replaceEvidenceLedger(List<RuntimeV2EvidenceEntry> nextEntries) {
        evidenceLedger.clear();
        if (nextEntries != null && !nextEntries.isEmpty()) {
            evidenceLedger.addAll(nextEntries);
        }
    }

    public List<RuntimeV2ObligationEntry> obligationLedger() {
        return List.copyOf(obligationLedger);
    }

    public void replaceObligationLedger(List<RuntimeV2ObligationEntry> nextEntries) {
        obligationLedger.clear();
        if (nextEntries != null && !nextEntries.isEmpty()) {
            obligationLedger.addAll(nextEntries);
        }
    }

    public List<RuntimeV2SkillContract> activeSkillContracts() {
        return List.copyOf(activeSkillContracts);
    }

    public void replaceActiveSkillContracts(List<RuntimeV2SkillContract> nextContracts) {
        activeSkillContracts.clear();
        if (nextContracts != null && !nextContracts.isEmpty()) {
            activeSkillContracts.addAll(nextContracts);
        }
    }

    public ModelRuntimeConfigResolver.ResolvedChatModelConfig modelConfig() {
        return modelConfig;
    }

    public SkillKit requestSkillKit() {
        return requestSkillKit;
    }

    public RuntimeV2Mode mode() {
        return mode;
    }

    public void setMode(RuntimeV2Mode mode) {
        this.mode = mode;
    }

    public RuntimeV2Phase phase() {
        return phase;
    }

    public void setPhase(RuntimeV2Phase phase) {
        this.phase = phase;
    }

    public RuntimeV2FinishReason finishReason() {
        return finishReason;
    }

    public void setFinishReason(RuntimeV2FinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public Long runId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String runCode() {
        return runCode;
    }

    public void setRunCode(String runCode) {
        this.runCode = runCode == null ? "" : runCode;
    }

    public String runType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType == null ? "" : runType;
    }

    public String finalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer == null ? "" : finalAnswer;
    }

    public String phaseSubStage() {
        return phaseSubStage;
    }

    public void setPhaseSubStage(String phaseSubStage) {
        this.phaseSubStage = phaseSubStage == null ? "" : phaseSubStage;
    }

    public String phaseSubStageLabel() {
        return phaseSubStageLabel;
    }

    public void setPhaseSubStageLabel(String phaseSubStageLabel) {
        this.phaseSubStageLabel = phaseSubStageLabel == null ? "" : phaseSubStageLabel;
    }

    public String phaseProgressMessage() {
        return phaseProgressMessage;
    }

    public void setPhaseProgressMessage(String phaseProgressMessage) {
        this.phaseProgressMessage = phaseProgressMessage == null ? "" : phaseProgressMessage;
    }

    public boolean terminalAnswerStreamed() {
        return terminalAnswerStreamed;
    }

    public String terminalAnswerStreamText() {
        return terminalAnswerStreamBuffer.toString();
    }

    public void appendTerminalAnswerDelta(String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        this.terminalAnswerStreamed = true;
        this.terminalAnswerStreamBuffer.append(delta);
    }

    public int iterationCount() {
        return iterationCount;
    }

    public void incrementIterationCount() {
        this.iterationCount += 1;
    }

    public void setIterationCount(int iterationCount) {
        this.iterationCount = Math.max(0, iterationCount);
    }

    public int llmCallCount() {
        return llmCallCount;
    }

    public void incrementLlmCallCount() {
        this.llmCallCount += 1;
    }

    public void setLlmCallCount(int llmCallCount) {
        this.llmCallCount = Math.max(0, llmCallCount);
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public void incrementToolCallCount() {
        this.toolCallCount += 1;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = Math.max(0, toolCallCount);
    }

    public int decisionRepairCount() {
        return decisionRepairCount;
    }

    public void incrementDecisionRepairCount() {
        this.decisionRepairCount += 1;
    }

    public void setDecisionRepairCount(int decisionRepairCount) {
        this.decisionRepairCount = Math.max(0, decisionRepairCount);
    }

    public Integer promptTokens() {
        return promptTokens;
    }

    public Integer completionTokens() {
        return completionTokens;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    public void addUsage(Integer prompt, Integer completion, Integer total) {
        this.promptTokens = sumNullable(this.promptTokens, prompt);
        this.completionTokens = sumNullable(this.completionTokens, completion);
        this.totalTokens = sumNullable(this.totalTokens, total);
    }

    public RuntimeV2CompletionAssessment completionAssessment() {
        return completionAssessment;
    }

    public void setCompletionAssessment(RuntimeV2CompletionAssessment completionAssessment) {
        this.completionAssessment = completionAssessment;
    }

    public RuntimeV2TaskContract taskContract() {
        return taskContract;
    }

    public void setTaskContract(RuntimeV2TaskContract taskContract) {
        this.taskContract = taskContract;
    }

    public Map<String, Object> codeState() {
        return codeState;
    }

    public void setCodeState(Map<String, Object> codeState) {
        if (codeState == null || codeState.isEmpty()) {
            this.codeState = Map.of();
            return;
        }
        this.codeState = Map.copyOf(new LinkedHashMap<>(codeState));
    }

    public void replaceObservationTrace(List<Map<String, Object>> nextTrace) {
        observationTrace.clear();
        if (nextTrace != null && !nextTrace.isEmpty()) {
            observationTrace.addAll(nextTrace);
        }
    }

    public boolean cancellationRequested() {
        return cancellationRequested;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public void requestCancellation(String reason) {
        this.cancellationRequested = true;
        this.cancellationReason = reason == null ? "" : reason.trim();
    }

    private Integer sumNullable(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }
}
