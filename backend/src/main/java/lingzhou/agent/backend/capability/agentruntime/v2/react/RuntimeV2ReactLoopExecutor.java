package lingzhou.agent.backend.capability.agentruntime.v2.react;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2ActiveToolRegistry;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeExecutionSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeState;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ReadOnlyToolGuard;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2SkillScriptGuardPolicy;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol.ReactDecision;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class RuntimeV2ReactLoopExecutor {

    private static final int MAX_REACT_ITERATIONS = 20;
    private static final int MAX_TOOL_RESULT_PROMPT_LENGTH = 4000;

    private final RuntimeV2ActiveToolRegistry activeToolRegistry;
    private final RuntimeV2ReadOnlyToolGuard readOnlyToolGuard;
    private final RuntimeV2SkillScriptGuardPolicy skillScriptGuardPolicy;
    private final RuntimeV2RecoveryPolicy recoveryPolicy;
    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;

    public RuntimeV2ReactLoopExecutor(
            RuntimeV2ActiveToolRegistry activeToolRegistry,
            RuntimeV2ReadOnlyToolGuard readOnlyToolGuard,
            RuntimeV2SkillScriptGuardPolicy skillScriptGuardPolicy,
            RuntimeV2RecoveryPolicy recoveryPolicy,
            RuntimeV2CodeExecutionSupport codeExecutionSupport) {
        this.activeToolRegistry = activeToolRegistry;
        this.readOnlyToolGuard = readOnlyToolGuard;
        this.skillScriptGuardPolicy = skillScriptGuardPolicy;
        this.recoveryPolicy = recoveryPolicy;
        this.codeExecutionSupport = codeExecutionSupport;
    }

    public void execute(
            RuntimeV2State state, ChatClient decisionChatClient, Sinks.Many<ServerSentEvent<String>> sink, Host host) {
        Map<String, ToolCallback> toolIndex = indexTools(state.toolCallbacks());
        if (toolIndex.isEmpty()) {
            host.runDirectFallbackInReact(state, decisionChatClient, sink);
            return;
        }

        for (int iteration = 0; iteration < MAX_REACT_ITERATIONS; iteration += 1) {
            state.incrementIterationCount();
            activeToolRegistry.refresh(state);
            toolIndex = indexTools(state.toolCallbacks());
            log.info(
                    "Runtime V2 REACT 新一轮开始：sessionId={}, iteration={}, llmCalls={}, toolCalls={}, observations={}",
                    safeSessionId(state),
                    state.iterationCount(),
                    state.llmCallCount(),
                    state.toolCallCount(),
                    state.observationTrace().size());
            host.emitPhase(state, RuntimeV2Phase.REASONING, sink);
            ReactDecision decision = host.resolveReactDecision(state, decisionChatClient, toolIndex, sink);
            String type = decision.type();
            log.info(
                    "Runtime V2 REACT 决策完成：sessionId={}, iteration={}, type={}, toolName={}, answerPreview={}",
                    safeSessionId(state),
                    state.iterationCount(),
                    type,
                    decision.toolName(),
                    summarizeForLog(decision.answer()));
            if (!"tool".equals(type)) {
                host.emitFinalStreamingContext(state);
                host.emitPhase(state, RuntimeV2Phase.FINAL_STREAMING, sink);
                String answer = host.emitTerminalAnswerIfNeeded(state, sink, decision.answer());
                state.setFinalAnswer(answer);
                state.setFinishReason(RuntimeV2FinishReason.COMPLETED);
                host.emitPhase(state, RuntimeV2Phase.COMPLETED, sink);
                return;
            }

            host.emitPhase(state, RuntimeV2Phase.ACTION, sink);
            String toolName = decision.toolName();
            ToolCallback tool = toolIndex.get(toolName);
            if (tool == null) {
                state.setFinishReason(RuntimeV2FinishReason.TOOL_ERROR);
                throw new IllegalStateException("模型请求了不存在的工具：" + toolName);
            }
            if (shouldForceCodeModeForToolDecision(state, decision, toolIndex)) {
                log.info(
                        "Runtime V2 REACT 将工具决策接管到 CODE：sessionId={}, iteration={}, requestedTool={}, codeStatus={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        toolName,
                        resolveCodeStatus(state));
                if (host.tryExecuteCodeEscalation(state, decisionChatClient, sink, toolIndex, toolName)) {
                    continue;
                }
            }
            Map<String, Object> arguments = decision.arguments();
            String toolArgumentsJson = JSON.toJSONString(arguments);
            String duplicateObservation = readOnlyToolGuard.buildDuplicateObservation(
                    state, toolName, arguments, MAX_TOOL_RESULT_PROMPT_LENGTH);
            if (StringUtils.hasText(duplicateObservation)) {
                log.warn(
                        "Runtime V2 REACT 拦截重复只读工具调用：sessionId={}, iteration={}, toolName={}, argumentsPreview={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        toolName,
                        summarizeForLog(toolArgumentsJson));
                host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
                host.recordObservation(state, toolName, toolArgumentsJson, duplicateObservation);
                continue;
            }
            String fixedSkillScriptObservation = skillScriptGuardPolicy.buildBlockedRewriteObservation(
                    state, toolName, arguments, MAX_TOOL_RESULT_PROMPT_LENGTH);
            if (StringUtils.hasText(fixedSkillScriptObservation)) {
                log.warn(
                        "Runtime V2 REACT 拦截 skill 固定脚本漂移：sessionId={}, iteration={}, toolName={}, argumentsPreview={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        toolName,
                        summarizeForLog(toolArgumentsJson));
                host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
                host.recordObservation(state, toolName, toolArgumentsJson, fixedSkillScriptObservation);
                continue;
            }

            String toolResult = host.executeToolCall(state, sink, toolName, tool, arguments);

            host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
            String observationSummary = host.summarizeObservationForPrompt(state, toolName, toolResult);
            host.recordObservation(state, toolName, toolArgumentsJson, observationSummary);
            String recoverableFailureMessage =
                    recoveryPolicy.resolveRecoverableFailureProgressMessage(toolName, toolResult);
            if (StringUtils.hasText(recoverableFailureMessage)) {
                host.pushVisibleProgress(state, sink, recoverableFailureMessage);
            }
        }

        state.setFinishReason(RuntimeV2FinishReason.LIMIT_EXCEEDED);
        state.setFinalAnswer("已达到本轮 V2 最大工具迭代次数，请收敛问题后重试。");
        log.warn(
                "Runtime V2 REACT 达到最大迭代：sessionId={}, iterations={}, llmCalls={}, toolCalls={}",
                safeSessionId(state),
                state.iterationCount(),
                state.llmCallCount(),
                state.toolCallCount());
        host.emitPhase(state, RuntimeV2Phase.FINALIZING, sink);
        host.appendMessageEvents(sink, state.finalAnswer());
        host.emitPhase(state, RuntimeV2Phase.COMPLETED, sink);
    }

    public interface Host {
        void runDirectFallbackInReact(
                RuntimeV2State state, ChatClient decisionChatClient, Sinks.Many<ServerSentEvent<String>> sink);

        ReactDecision resolveReactDecision(
                RuntimeV2State state,
                ChatClient decisionChatClient,
                Map<String, ToolCallback> toolIndex,
                Sinks.Many<ServerSentEvent<String>> sink);

        void emitFinalStreamingContext(RuntimeV2State state);

        void emitPhase(RuntimeV2State state, RuntimeV2Phase phase, Sinks.Many<ServerSentEvent<String>> sink);

        String emitTerminalAnswerIfNeeded(
                RuntimeV2State state, Sinks.Many<ServerSentEvent<String>> sink, String draftAnswer);

        boolean tryExecuteCodeEscalation(
                RuntimeV2State state,
                ChatClient decisionChatClient,
                Sinks.Many<ServerSentEvent<String>> sink,
                Map<String, ToolCallback> toolIndex,
                String requestedToolName);

        String executeToolCall(
                RuntimeV2State state,
                Sinks.Many<ServerSentEvent<String>> sink,
                String toolName,
                ToolCallback tool,
                Map<String, Object> arguments);

        void recordObservation(
                RuntimeV2State state, String toolName, String toolArgumentsJson, String observationSummary);

        String summarizeObservationForPrompt(RuntimeV2State state, String toolName, String toolResult);

        void pushVisibleProgress(RuntimeV2State state, Sinks.Many<ServerSentEvent<String>> sink, String message);

        void appendMessageEvents(Sinks.Many<ServerSentEvent<String>> sink, String answer);
    }

    private Map<String, ToolCallback> indexTools(List<ToolCallback> callbacks) {
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        if (callbacks == null || callbacks.isEmpty()) {
            return index;
        }
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (StringUtils.hasText(name)) {
                index.putIfAbsent(name.trim(), callback);
            }
        }
        return index;
    }

    private boolean shouldForceCodeModeForToolDecision(
            RuntimeV2State state, ReactDecision decision, Map<String, ToolCallback> toolIndex) {
        if (state == null || decision == null || toolIndex == null) {
            return false;
        }
        if (!readToolToCodeDecisionFlag(
                state.prepared() == null ? null : state.prepared().paramsJson(), "allowCodeExecution")) {
            return false;
        }
        String toolName = normalizeText(decision.toolName());
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        if (!toolIndex.containsKey("file_write")) {
            return false;
        }
        String codeStatus = resolveCodeStatus(state);
        if ("file_write".equals(toolName)) {
            String targetPath = resolveToolPathArgument(decision.arguments());
            if (!targetPath.startsWith("/workspace/") || !targetPath.endsWith(".py")) {
                return false;
            }
            return !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        if ("run_python".equals(toolName)) {
            return !RuntimeV2CodeState.CODE_OUTPUT_READY.equalsIgnoreCase(codeStatus)
                    && !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        if ("write_artifact".equals(toolName)) {
            return !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
        }
        return false;
    }

    private String resolveToolPathArgument(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        String path = normalizeText(arguments.get("path"));
        if (!StringUtils.hasText(path)) {
            path = normalizeText(arguments.get("arg0"));
        }
        return path.replace('\\', '/');
    }

    private String resolveCodeStatus(RuntimeV2State state) {
        return codeExecutionSupport.readCodeStatus(state == null ? null : state.codeState());
    }

    @SuppressWarnings("unchecked")
    private boolean readToolToCodeDecisionFlag(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return false;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, Map.class);
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            Object rawDecision = payload.get("toolToCodeDecision");
            if (!(rawDecision instanceof Map<?, ?> decisionMap)) {
                return false;
            }
            Object value = decisionMap.get(key);
            return value != null
                    && "true".equalsIgnoreCase(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safeSessionId(RuntimeV2State state) {
        if (state == null || state.conversation() == null) {
            return "";
        }
        return StringUtils.trimWhitespace(state.conversation().sessionCode());
    }

    private String summarizeForLog(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
