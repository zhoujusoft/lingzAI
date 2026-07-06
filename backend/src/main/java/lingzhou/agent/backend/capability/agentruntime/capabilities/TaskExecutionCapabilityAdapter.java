package lingzhou.agent.backend.capability.agentruntime.capabilities;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvDescriptorService;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentMode;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentModeResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TaskExecutionCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private final PersonalAgentModeResolver personalAgentModeResolver;
    private final PythonRuntimeEnvDescriptorService pythonRuntimeEnvDescriptorService;

    public TaskExecutionCapabilityAdapter(
            PersonalAgentModeResolver personalAgentModeResolver,
            PythonRuntimeEnvDescriptorService pythonRuntimeEnvDescriptorService) {
        super(RuntimeCapabilitySlot.TASK_EXECUTION, "task-execution", RuntimeCapabilityStatus.ACTIVE);
        this.personalAgentModeResolver = personalAgentModeResolver;
        this.pythonRuntimeEnvDescriptorService = pythonRuntimeEnvDescriptorService;
    }

    public PersonalAgentMode resolveMode(ChatRuntimePreparedRequest prepared) {
        return personalAgentModeResolver.resolve(prepared);
    }

    public String buildExecutionModePrompt(ChatRuntimePreparedRequest prepared) {
        PersonalAgentMode mode = resolveMode(prepared);
        if (mode == PersonalAgentMode.EXECUTION_TASK) {
            return buildExecutionTaskPrompt(prepared);
        }
        if (mode == PersonalAgentMode.CONTENT_ASSIST) {
            return """
                    ## Personal Agent Content Assist Mode
                    当前请求以内容理解为主，可使用 `parse_file` 辅助读取附件内容。
                    工作规则：
                    - 目标是理解内容，不要默认进入文件修改、脚本执行或导出流程。
                    - 若信息不足，明确说明限制，不要编造文件内容。
                    """;
        }
        if (prepared != null && prepared.personalAgent() && StringUtils.hasText(prepared.message())) {
            return """
                    ## Personal Agent Chat Mode
                    当前请求属于个人 Agent 普通对话，优先直接回答。
                    仅在用户明确要求执行任务、产生产物或操作文件时，再切换到执行任务模式。
                    """;
        }
        return null;
    }

    private String buildExecutionTaskPrompt(ChatRuntimePreparedRequest prepared) {
        boolean allowSkillInternals = readExecutionPrecheckBoolean(prepared, "allowSkillInternals");
        String relevantSkill = readExecutionPrecheckText(prepared, "relevantSkill");
        String stepExecutor = readExecutionPrecheckText(prepared, "stepExecutor");
        boolean allowCodeExecution = readExecutionPrecheckBoolean(prepared, "allowCodeExecution");
        if (allowSkillInternals && StringUtils.hasText(relevantSkill)) {
            return """
                    ## Personal Agent Skill Execution Mode
                    当前执行步已命中技能：`%s`。
                    本步允许使用该技能的内部能力与既有执行方式。
                    工作规则：
                    - 严格按当前已激活 skill 的内容、专属工具和执行约定完成任务。
                    - 仅在本步确实属于该 skill 处理域时，才使用 skill 内部脚本、/skill 路径和专属流程。
                    - 若发现当前问题已不属于该 skill 处理域，应停止沿用 skill 内部实现，回到通用执行编排。
                    - 宣称“已完成”前，必须先验证产物或结果真实存在。
                    """
                    .formatted(relevantSkill);
        }
        String envPrompt = allowCodeExecution ? "\n" + pythonRuntimeEnvDescriptorService.buildGeneralCodePrompt() : "";
        return """
                ## Personal Agent General Execution Mode
                当前请求属于执行任务，本步执行器：`%s`。
                当前处于 general world，不应感知任何 skill 内部实现。
                工作规则：
                - 按以下顺序决策：先看能否直接回答；不能直接回答再判断 skill / tool 是否足够；足够就继续用 skill / tool；明显不足时才升级到 CODE。
                - 先按本步执行器完成任务，不要自行脑补 skill 内部脚本、/skill 路径或 skill 专属流程。
                - 若需要 skill，先明确命中 skill，再加载对应 skill 后进入 skill world。
                - `parse_file` 仅用于理解附件或 runtime 产物内容，不是默认执行入口；可用于 `/uploads/...`、`/temp/...`、`/outputs/...`、`/workspace/...` 等受控逻辑路径。
                - 优先使用已有 TOOL 能力、知识库、数据集、MCP、独立 runtime 工具与已激活 skill。
                - 若考虑升级到 CODE，必须先完成分析：先检查已加载技能，必要时调用 `listActiveSkills` 查看当前用户全部可用技能及其加载状态，再判断当前 skill/tool 是否足够。
                - 如果当前轮次调用过 `listActiveSkills`，且目的只是回答“有哪些技能/可用技能”，同一轮最多只允许这一调用一次；拿到列表后必须直接回答用户，不要再次调用 `listActiveSkills`。
                - 若目标是二进制附件或 runtime 生成文件，优先先试 `parse_file`；如果 `parse_file` 仍拿不到有效内容或直接无法解析，再进入 CODE，不要围绕同一个文件反复空转试探。
                - 仅当 TOOL 明显不足且系统明确允许升级到 CODE 时，才允许脚本化执行。
                - CODE 只是 general world 里的兜底能力，不是独立链路；一旦进入 CODE，仍然只需继续使用普通工具调用，通常顺序是：先分析 -> 再 `file_write` 生成真实 Python 脚本到 `/workspace` -> 再 `run_python` 执行。
                - 禁止直接猜测脚本路径并运行；禁止把示例路径、默认路径当成真实脚本。
                - 当前是否允许 CODE 兜底：`%s`。
                - 宣称“已完成”前，必须先验证产物或结果真实存在。
                """
                        .formatted(StringUtils.hasText(stepExecutor) ? stepExecutor : "TOOL", allowCodeExecution)
                + envPrompt;
    }

    private boolean readExecutionPrecheckBoolean(ChatRuntimePreparedRequest prepared, String key) {
        return "true".equalsIgnoreCase(readExecutionPrecheckText(prepared, key));
    }

    private String readExecutionPrecheckText(ChatRuntimePreparedRequest prepared, String key) {
        if (prepared == null || !StringUtils.hasText(prepared.paramsJson()) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null) {
                return "";
            }
            Object rawPrecheck = payload.get("executionPrecheck");
            if (!(rawPrecheck instanceof Map<?, ?> precheckMap)) {
                return "";
            }
            Object value = precheckMap.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
