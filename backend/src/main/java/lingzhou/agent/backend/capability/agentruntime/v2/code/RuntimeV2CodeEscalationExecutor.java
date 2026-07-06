package lingzhou.agent.backend.capability.agentruntime.v2.code;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodePlanProtocol.CodeExecutionPlan;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService.CodeStagePreparation;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageService.CodeStageProgress;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol;
import lingzhou.agent.backend.capability.agentruntime.v2.observation.RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions;
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
public class RuntimeV2CodeEscalationExecutor {

    private static final int MAX_TOOL_RESULT_PROMPT_LENGTH = 4000;

    private final RuntimeV2CodeExecutionSupport codeExecutionSupport;
    private final RuntimeV2CodeStageService codeStageService;
    private final RuntimeV2ObservationSummaryProtocol observationSummaryProtocol;
    private final lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy recoveryPolicy;

    public RuntimeV2CodeEscalationExecutor(
            RuntimeV2CodeExecutionSupport codeExecutionSupport,
            RuntimeV2CodeStageService codeStageService,
            RuntimeV2ObservationSummaryProtocol observationSummaryProtocol,
            lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RecoveryPolicy recoveryPolicy) {
        this.codeExecutionSupport = codeExecutionSupport;
        this.codeStageService = codeStageService;
        this.observationSummaryProtocol = observationSummaryProtocol;
        this.recoveryPolicy = recoveryPolicy;
    }

    public boolean execute(
            RuntimeV2State state,
            ChatClient chatClient,
            Sinks.Many<ServerSentEvent<String>> sink,
            Map<String, ToolCallback> toolIndex,
            String requestedToolName,
            Host host) {
        String targetToolName = normalizeText(requestedToolName);
        CodeExecutionPlan plan = codeExecutionSupport.readPlan(
                state == null ? null : state.codeState(),
                state == null || state.prepared() == null
                        ? ""
                        : state.prepared().userMessage());
        String scriptContent = codeExecutionSupport.readScriptContent(state == null ? null : state.codeState());
        String codeStatus = resolveCodeStatus(state);

        int writeRepairAttempt = 0;
        while (shouldPrepareCodeScript(targetToolName, codeStatus, plan, scriptContent)) {
            ToolCallback fileWriteTool = toolIndex.get("file_write");
            if (fileWriteTool == null) {
                return false;
            }
            CodeStagePreparation preparation = codeStageService.prepare(
                    state, chatClient, progress -> host.emitCodeStageProgress(state, sink, progress));
            if (!preparation.valid()) {
                state.setCodeState(Map.of());
                return false;
            }
            plan = preparation.plan();
            scriptContent = preparation.scriptContent();

            host.emitCodeStageProgress(
                    state,
                    sink,
                    new CodeStageProgress("CODE_FILE_WRITE_START", "写入脚本文件", "Python 脚本正文已生成，正在写入工作区文件。", true));
            host.emitPhase(state, RuntimeV2Phase.ACTION, sink);
            Map<String, Object> toolArguments = buildFileWriteDisplayArguments(plan.scriptPath(), scriptContent);
            String toolResult = host.executeToolCall(state, sink, "file_write", fileWriteTool, toolArguments);

            host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
            String toolArgumentsJson = JSON.toJSONString(toolArguments);
            boolean writeSuccess = codeExecutionSupport.isToolExecutionSuccess(toolResult);
            String fileWriteToolObservation = observationSummaryProtocol.summarize(
                    "file_write",
                    toolResult,
                    new ObservationSummaryOptions(true, MAX_TOOL_RESULT_PROMPT_LENGTH, userMessage(state)));
            String observationSummary = codeExecutionSupport.buildCodeFileWriteObservation(
                    plan, fileWriteToolObservation, writeSuccess, MAX_TOOL_RESULT_PROMPT_LENGTH);
            state.setCodeState(buildCodeStateSnapshot(
                    state,
                    plan,
                    scriptContent,
                    writeSuccess ? RuntimeV2CodeState.CODE_SCRIPT_READY : RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED));
            host.recordObservation(state, "file_write", toolArgumentsJson, observationSummary);
            if (!writeSuccess) {
                log.warn(
                        "Runtime V2 CODE 写脚本失败，回退 REACT：sessionId={}, iteration={}, scriptPath={}, resultPreview={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        plan.scriptPath(),
                        summarizeForLog(toolResult));
                if (recoveryPolicy.shouldRetryCodeScriptWrite(toolResult, writeRepairAttempt)) {
                    writeRepairAttempt += 1;
                    codeStatus = RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED;
                    host.emitCodeStageProgress(
                            state,
                            sink,
                            new CodeStageProgress(
                                    "CODE_FILE_WRITE_RETRY", "重写脚本", "脚本写入被规则拦截，正在根据错误提示重写 Python 脚本。", true));
                    continue;
                }
                return false;
            }
            codeStatus = RuntimeV2CodeState.CODE_SCRIPT_READY;
            break;
        }
        if (RuntimeV2CodeState.CODE_SCRIPT_READY.equalsIgnoreCase(codeStatus)
                && !requiresRunPythonStep(targetToolName)) {
            log.info(
                    "Runtime V2 CODE 阶段完成：sessionId={}, iteration={}, status={}, scriptPath={}, outputPath={}",
                    safeSessionId(state),
                    state.iterationCount(),
                    codeStatus,
                    plan.scriptPath(),
                    plan.outputPath());
            return true;
        }

        if (shouldExecuteRunPythonStep(targetToolName, codeStatus, plan)) {
            ToolCallback runPythonTool = toolIndex.get("run_python");
            if (runPythonTool == null) {
                return false;
            }
            host.emitCodeStageProgress(
                    state,
                    sink,
                    new CodeStageProgress("CODE_RUN_START", "执行 Python 脚本", "脚本已就绪，正在执行 Python 处理流程。", true));
            host.emitPhase(state, RuntimeV2Phase.ACTION, sink);
            Map<String, Object> toolArguments = codeExecutionSupport.buildRunPythonArguments(plan);
            String toolResult = host.executeToolCall(state, sink, "run_python", runPythonTool, toolArguments);

            host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
            String toolArgumentsJson = JSON.toJSONString(toolArguments);
            boolean runSuccess = codeExecutionSupport.isToolExecutionSuccess(toolResult);
            String runPythonToolObservation = observationSummaryProtocol.summarize(
                    "run_python",
                    toolResult,
                    new ObservationSummaryOptions(true, MAX_TOOL_RESULT_PROMPT_LENGTH, userMessage(state)));
            String observationSummary = codeExecutionSupport.buildCodeRunObservation(
                    plan, runPythonToolObservation, runSuccess, MAX_TOOL_RESULT_PROMPT_LENGTH);
            state.setCodeState(buildCodeStateSnapshot(
                    state,
                    plan,
                    scriptContent,
                    runSuccess ? RuntimeV2CodeState.CODE_OUTPUT_READY : RuntimeV2CodeState.CODE_RUN_FAILED));
            host.recordObservation(state, "run_python", toolArgumentsJson, observationSummary);
            if (!runSuccess) {
                log.warn(
                        "Runtime V2 CODE 执行脚本失败：sessionId={}, iteration={}, scriptPath={}, resultPreview={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        plan.scriptPath(),
                        summarizeForLog(toolResult));
                if (recoveryPolicy.shouldRetryCodeRun(toolResult, state.observationTrace())) {
                    host.emitCodeStageProgress(
                            state,
                            sink,
                            new CodeStageProgress("CODE_RUN_RETRY", "重写脚本", "脚本已执行但结果不符合预期，正在基于失败信息重写并重试。", true));
                    return execute(state, chatClient, sink, toolIndex, requestedToolName, host);
                }
                return false;
            }
            codeStatus = RuntimeV2CodeState.CODE_OUTPUT_READY;
            if (!requiresWriteArtifactStep(targetToolName)) {
                log.info(
                        "Runtime V2 CODE 阶段完成：sessionId={}, iteration={}, status={}, scriptPath={}, outputPath={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        codeStatus,
                        plan.scriptPath(),
                        plan.outputPath());
                return true;
            }
        }

        if (shouldExecuteWriteArtifactStep(targetToolName, codeStatus, plan)) {
            ToolCallback writeArtifactTool = toolIndex.get("write_artifact");
            if (writeArtifactTool == null) {
                return false;
            }
            host.emitCodeStageProgress(
                    state, sink, new CodeStageProgress("CODE_ARTIFACT_START", "发布产物", "脚本执行已完成，正在发布最终产物。", true));
            host.emitPhase(state, RuntimeV2Phase.ACTION, sink);
            Map<String, Object> toolArguments = codeExecutionSupport.buildWriteArtifactArguments(plan);
            String toolResult = host.executeToolCall(state, sink, "write_artifact", writeArtifactTool, toolArguments);

            host.emitPhase(state, RuntimeV2Phase.OBSERVATION, sink);
            String toolArgumentsJson = JSON.toJSONString(toolArguments);
            boolean publishSuccess = codeExecutionSupport.isToolExecutionSuccess(toolResult);
            String artifactToolObservation = observationSummaryProtocol.summarize(
                    "write_artifact",
                    toolResult,
                    new ObservationSummaryOptions(true, MAX_TOOL_RESULT_PROMPT_LENGTH, userMessage(state)));
            String observationSummary = codeExecutionSupport.buildCodeArtifactObservation(
                    plan, artifactToolObservation, publishSuccess, MAX_TOOL_RESULT_PROMPT_LENGTH);
            state.setCodeState(buildCodeStateSnapshot(
                    state,
                    plan,
                    scriptContent,
                    publishSuccess
                            ? RuntimeV2CodeState.CODE_ARTIFACT_READY
                            : RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED));
            host.recordObservation(state, "write_artifact", toolArgumentsJson, observationSummary);
            if (!publishSuccess) {
                log.warn(
                        "Runtime V2 CODE 发布产物失败：sessionId={}, iteration={}, outputPath={}, resultPreview={}",
                        safeSessionId(state),
                        state.iterationCount(),
                        plan.outputPath(),
                        summarizeForLog(toolResult));
                return false;
            }
            codeStatus = RuntimeV2CodeState.CODE_ARTIFACT_READY;
        }

        if (!StringUtils.hasText(codeStatus)) {
            return false;
        }
        log.info(
                "Runtime V2 CODE 阶段完成：sessionId={}, iteration={}, status={}, scriptPath={}, outputPath={}",
                safeSessionId(state),
                state.iterationCount(),
                codeStatus,
                plan == null ? "" : plan.scriptPath(),
                plan == null ? "" : plan.outputPath());
        return true;
    }

    public interface Host {
        void emitPhase(RuntimeV2State state, RuntimeV2Phase phase, Sinks.Many<ServerSentEvent<String>> sink);

        void emitCodeStageProgress(
                RuntimeV2State state, Sinks.Many<ServerSentEvent<String>> sink, CodeStageProgress progress);

        String executeToolCall(
                RuntimeV2State state,
                Sinks.Many<ServerSentEvent<String>> sink,
                String toolName,
                ToolCallback tool,
                Map<String, Object> arguments);

        void recordObservation(
                RuntimeV2State state, String toolName, String toolArgumentsJson, String observationSummary);
    }

    private boolean shouldPrepareCodeScript(
            String requestedToolName, String codeStatus, CodeExecutionPlan plan, String scriptContent) {
        if (!StringUtils.hasText(codeStatus)
                || RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED.equalsIgnoreCase(codeStatus)
                || RuntimeV2CodeState.CODE_RUN_FAILED.equalsIgnoreCase(codeStatus)
                || RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED.equalsIgnoreCase(codeStatus)) {
            return true;
        }
        if (plan == null || !StringUtils.hasText(scriptContent)) {
            return true;
        }
        return "file_write".equalsIgnoreCase(requestedToolName)
                && !RuntimeV2CodeState.CODE_ARTIFACT_READY.equalsIgnoreCase(codeStatus);
    }

    private boolean shouldExecuteRunPythonStep(String requestedToolName, String codeStatus, CodeExecutionPlan plan) {
        if (!requiresRunPythonStep(requestedToolName) || plan == null) {
            return false;
        }
        return RuntimeV2CodeState.CODE_SCRIPT_READY.equalsIgnoreCase(codeStatus);
    }

    private boolean shouldExecuteWriteArtifactStep(
            String requestedToolName, String codeStatus, CodeExecutionPlan plan) {
        if (!requiresWriteArtifactStep(requestedToolName) || plan == null) {
            return false;
        }
        return RuntimeV2CodeState.CODE_OUTPUT_READY.equalsIgnoreCase(codeStatus);
    }

    private boolean requiresRunPythonStep(String requestedToolName) {
        return "run_python".equalsIgnoreCase(requestedToolName) || "write_artifact".equalsIgnoreCase(requestedToolName);
    }

    private boolean requiresWriteArtifactStep(String requestedToolName) {
        return "write_artifact".equalsIgnoreCase(requestedToolName);
    }

    private String resolveCodeStatus(RuntimeV2State state) {
        return codeExecutionSupport.readCodeStatus(state == null ? null : state.codeState());
    }

    private Map<String, Object> buildCodeStateSnapshot(
            RuntimeV2State state, CodeExecutionPlan plan, String scriptContent, String status) {
        String attachmentSummary = codeExecutionSupport.buildAttachmentSummary(
                state == null || state.prepared() == null
                        ? null
                        : state.prepared().fileListJson());
        return codeExecutionSupport.buildCodeState(plan, scriptContent, attachmentSummary, status);
    }

    private Map<String, Object> buildFileWriteDisplayArguments(String path, String content) {
        Map<String, Object> toolArguments = new LinkedHashMap<>();
        toolArguments.put("path", path);
        toolArguments.put("content", content);
        return toolArguments;
    }

    private String userMessage(RuntimeV2State state) {
        return state == null || state.prepared() == null ? "" : state.prepared().userMessage();
    }

    private String safeSessionId(RuntimeV2State state) {
        return state == null || state.prepared() == null
                ? ""
                : normalizeText(state.prepared().sessionId());
    }

    private String summarizeForLog(String text) {
        String normalized = normalizeText(text);
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
