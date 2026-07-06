package lingzhou.agent.backend.capability.agentruntime.approval;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.ConversationRun;
import lingzhou.agent.backend.business.chat.service.ConversationRunConstants;
import lingzhou.agent.backend.business.chat.service.ConversationRunService;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.RuntimeV2GraphRuntimeRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ToolCallExecutor;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RuntimeApprovalService {

    private final RuntimeApprovalMapper runtimeApprovalMapper;
    private final RuntimeToolApprovalPolicy approvalPolicy;
    private final RuntimeToolApprovalAnalyzer approvalAnalyzer;
    private final ConversationRunService conversationRunService;
    private final RuntimeV2GraphRuntimeRegistry runtimeRegistry;
    private final RuntimeV2ToolCallExecutor toolCallExecutor;

    public RuntimeApprovalService(
            RuntimeApprovalMapper runtimeApprovalMapper,
            RuntimeToolApprovalPolicy approvalPolicy,
            RuntimeToolApprovalAnalyzer approvalAnalyzer,
            ConversationRunService conversationRunService,
            RuntimeV2GraphRuntimeRegistry runtimeRegistry,
            RuntimeV2ToolCallExecutor toolCallExecutor) {
        this.runtimeApprovalMapper = runtimeApprovalMapper;
        this.approvalPolicy = approvalPolicy;
        this.approvalAnalyzer = approvalAnalyzer;
        this.conversationRunService = conversationRunService;
        this.runtimeRegistry = runtimeRegistry;
        this.toolCallExecutor = toolCallExecutor;
    }

    public boolean requiresApproval(String toolName) {
        return approvalPolicy.requiresApproval(toolName);
    }

    @Transactional(rollbackFor = Exception.class)
    public RuntimeApproval createPendingApproval(
            RuntimeV2State runtimeState,
            String toolCallId,
            String toolName,
            String toolDisplayName,
            Map<String, Object> arguments) {
        RuntimeToolApprovalAnalysis analysis = approvalAnalyzer.analyze(toolName, arguments);
        RuntimeApproval approval = new RuntimeApproval();
        approval.setApprovalCode(UlidGenerator.next());
        approval.setRunId(runtimeState == null ? null : runtimeState.runId());
        approval.setRunCode(runtimeState == null ? "" : normalizeText(runtimeState.runCode()));
        approval.setSessionId(
                runtimeState == null || runtimeState.conversation() == null
                        ? null
                        : runtimeState.conversation().sessionId());
        approval.setAssistantMessageId(
                runtimeState == null || runtimeState.conversation() == null
                        ? null
                        : runtimeState.conversation().assistantMessageId());
        approval.setToolCallId(normalizeText(toolCallId));
        approval.setToolName(normalizeText(toolName));
        approval.setToolDisplayName(
                StringUtils.hasText(toolDisplayName) ? toolDisplayName.trim() : normalizeText(toolName));
        approval.setToolArgumentsJson(JSON.toJSONString(arguments == null ? Map.of() : arguments));
        approval.setApprovalStatus(RuntimeApprovalConstants.APPROVAL_PENDING);
        approval.setExecutionStatus(RuntimeApprovalConstants.EXECUTION_NOT_STARTED);
        approval.setRiskLevel(analysis == null ? RuntimeApprovalConstants.RISK_MEDIUM : analysis.riskLevel());
        approval.setTriggerReason(approvalPolicy.triggerReason(toolName));
        approval.setAnalysisJson(JSON.toJSONString(analysis));
        approval.setRequestedBy(runtimeState == null ? null : runtimeState.userId());
        runtimeApprovalMapper.insert(approval);
        return approval;
    }

    public Map<String, Object> buildApprovalPayload(RuntimeApproval approval) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (approval == null) {
            return payload;
        }
        payload.put("approvalCode", approval.getApprovalCode());
        payload.put("runId", approval.getRunId());
        payload.put("runCode", approval.getRunCode());
        payload.put("sessionId", approval.getSessionId());
        payload.put("assistantMessageId", approval.getAssistantMessageId());
        payload.put("toolCallId", approval.getToolCallId());
        payload.put("toolName", approval.getToolName());
        payload.put("toolDisplayName", approval.getToolDisplayName());
        payload.put("toolArguments", parseJsonObject(approval.getToolArgumentsJson()));
        payload.put("approvalStatus", approval.getApprovalStatus());
        payload.put("executionStatus", approval.getExecutionStatus());
        payload.put("riskLevel", approval.getRiskLevel());
        payload.put("triggerReason", approval.getTriggerReason());
        payload.put("analysis", parseJsonObject(approval.getAnalysisJson()));
        payload.put("requestedBy", approval.getRequestedBy());
        return payload;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalDecisionResponse approve(String runCode, String approvalCode, Long userId, String comment)
            throws TaskException {
        RuntimeApproval approval = resolveApproval(runCode, approvalCode, userId);
        ensurePending(approval);
        RuntimeV2GraphExecutionContext executionContext =
                runtimeRegistry.resolveExecutionContext(approval.getRunCode());
        if (executionContext == null) {
            throw new TaskException("执行上下文已失效，请重新发起任务", TaskException.Code.UNKNOWN);
        }
        Map<String, ToolCallback> toolCallbackIndex = runtimeRegistry.resolveToolCallbackIndex(approval.getRunCode());
        ToolCallback callback = toolCallbackIndex.get(approval.getToolName());
        if (callback == null) {
            throw new TaskException("审批工具已不可用：" + approval.getToolName(), TaskException.Code.UNKNOWN);
        }
        markExecutionRunning(approval, userId, comment);
        String result;
        try {
            result = toolCallExecutor.execute(
                    approval.getToolName(), callback, parseArguments(approval.getToolArgumentsJson()));
        } catch (RuntimeException ex) {
            markExecutionFailed(approval, ex.getMessage());
            ConversationRun run = conversationRunService.findByRunCode(approval.getRunCode());
            if (run != null) {
                conversationRunService.failRun(
                        run.getId(),
                        approval.getAssistantMessageId(),
                        "APPROVAL_TOOL_FAILED",
                        ex.getMessage(),
                        run.getContextJson());
            }
            runtimeRegistry.unregister(approval.getRunCode());
            throw ex;
        }
        markExecutionSucceeded(approval, result);
        ConversationRun run = conversationRunService.findByRunCode(approval.getRunCode());
        if (run != null) {
            conversationRunService.succeedRun(run.getId(), approval.getAssistantMessageId(), run.getContextJson());
        }
        runtimeRegistry.unregister(approval.getRunCode());
        return new ApprovalDecisionResponse(
                true, approval.getApprovalCode(), approval.getApprovalStatus(), approval.getExecutionStatus(), result);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalDecisionResponse reject(String runCode, String approvalCode, Long userId, String comment)
            throws TaskException {
        RuntimeApproval approval = resolveApproval(runCode, approvalCode, userId);
        ensurePending(approval);
        approval.setApprovalStatus(RuntimeApprovalConstants.APPROVAL_REJECTED);
        approval.setExecutionStatus(RuntimeApprovalConstants.EXECUTION_SKIPPED);
        approval.setDecidedBy(userId);
        approval.setDecisionComment(normalizeText(comment));
        approval.setDecidedAt(new Date());
        runtimeApprovalMapper.updateById(approval);
        ConversationRun run = conversationRunService.findByRunCode(approval.getRunCode());
        if (run != null) {
            conversationRunService.cancelRun(run.getId(), approval.getAssistantMessageId(), run.getContextJson());
        }
        runtimeRegistry.unregister(approval.getRunCode());
        return new ApprovalDecisionResponse(
                true, approval.getApprovalCode(), approval.getApprovalStatus(), approval.getExecutionStatus(), "");
    }

    public RuntimeApproval findByApprovalCode(String approvalCode) {
        return runtimeApprovalMapper.selectByApprovalCode(approvalCode);
    }

    private RuntimeApproval resolveApproval(String runCode, String approvalCode, Long userId) throws TaskException {
        String normalizedRunCode = normalizeRequired(runCode, "runCode 不能为空");
        String normalizedApprovalCode = normalizeRequired(approvalCode, "approvalCode 不能为空");
        RuntimeApproval approval = runtimeApprovalMapper.selectByApprovalCode(normalizedApprovalCode);
        if (approval == null || !normalizedRunCode.equals(approval.getRunCode())) {
            throw new TaskException("审批记录不存在", TaskException.Code.UNKNOWN);
        }
        ConversationRun run = conversationRunService.findByRunCode(normalizedRunCode);
        if (run == null) {
            throw new TaskException("执行记录不存在", TaskException.Code.UNKNOWN);
        }
        if (userId == null || !userId.equals(run.getCreateUserId())) {
            throw new TaskException("无权限审批该执行", TaskException.Code.UNKNOWN);
        }
        return approval;
    }

    private void ensurePending(RuntimeApproval approval) throws TaskException {
        if (approval == null || !RuntimeApprovalConstants.APPROVAL_PENDING.equals(approval.getApprovalStatus())) {
            throw new TaskException("审批已处理，不能重复操作", TaskException.Code.UNKNOWN);
        }
    }

    private void markExecutionRunning(RuntimeApproval approval, Long userId, String comment) {
        approval.setApprovalStatus(RuntimeApprovalConstants.APPROVAL_APPROVED);
        approval.setExecutionStatus(RuntimeApprovalConstants.EXECUTION_RUNNING);
        approval.setDecidedBy(userId);
        approval.setDecisionComment(normalizeText(comment));
        approval.setDecidedAt(new Date());
        runtimeApprovalMapper.updateById(approval);
    }

    private void markExecutionSucceeded(RuntimeApproval approval, String result) {
        approval.setToolResult(result);
        approval.setExecutionStatus(RuntimeApprovalConstants.EXECUTION_SUCCEEDED);
        runtimeApprovalMapper.updateById(approval);
    }

    private void markExecutionFailed(RuntimeApproval approval, String errorMessage) {
        approval.setToolResult(StringUtils.hasText(errorMessage) ? "工具执行失败：" + errorMessage.trim() : "工具执行失败");
        approval.setExecutionStatus(RuntimeApprovalConstants.EXECUTION_FAILED);
        runtimeApprovalMapper.updateById(approval);
    }

    public void markRunWaitingApproval(RuntimeV2State runtimeState, RuntimeApproval approval) {
        if (runtimeState == null || runtimeState.runId() == null || runtimeState.runId() <= 0) {
            return;
        }
        runtimeState.setPhase(RuntimeV2Phase.ACTION);
        conversationRunService.updateRunningState(
                runtimeState.runId(),
                ConversationRunConstants.STATUS_WAITING_APPROVAL,
                RuntimeV2Phase.ACTION.name(),
                "APPROVAL_REQUIRED",
                "等待人工审批：" + normalizeText(approval == null ? "" : approval.getToolName()),
                resolveRuntimeSkillName(runtimeState),
                JSON.toJSONString(Map.of(
                        "runId",
                        runtimeState.runId(),
                        "runCode",
                        runtimeState.runCode(),
                        "runtimeVersion",
                        "v2",
                        "runtimeV2Engine",
                        "graph",
                        "graphRuntime",
                        Boolean.TRUE,
                        "phase",
                        RuntimeV2Phase.ACTION.name(),
                        "finishReason",
                        "WAITING_APPROVAL",
                        "approval",
                        buildApprovalPayload(approval))));
    }

    private String resolveRuntimeSkillName(RuntimeV2State runtimeState) {
        return runtimeState == null || runtimeState.prepared() == null
                ? null
                : normalizeText(runtimeState.prepared().runtimeSkillName());
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private Map<String, Object> parseArguments(String json) {
        return parseJsonObject(json);
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of("raw", json);
        }
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ApprovalDecisionResponse(
            boolean accepted, String approvalCode, String approvalStatus, String executionStatus, String toolResult) {}
}
