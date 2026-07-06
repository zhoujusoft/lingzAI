package lingzhou.agent.backend.skillstudio.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.app.SkillExecutionScope;
import lingzhou.agent.backend.capability.agentruntime.capabilities.EventPersistenceCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TokenUsageCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeTokenUsageAccumulator;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeErrorMessageResolver;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationIssue;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import lingzhou.agent.backend.skillstudio.validate.SkillStudioValidationService;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareAdvisor;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallbackResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;

@Service
public class SkillStudioCreatorSkillInvoker {

    private static final Logger log = LoggerFactory.getLogger(SkillStudioCreatorSkillInvoker.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final String FIRST_ROUND_STRICT_GUARD_MARKER =
            "##__SKILLSTUDIO_FIRST_ROUND_STRICT_RUNTIME_TOOL_GUARD__##";
    private static final String FIRST_ROUND_STRICT_GUARD_APPENDIX =
            """
            [首轮创建硬约束 - 必须遵守]
            A. 新 skill 的 runtime 能力说明只允许使用独立工具：file_read / file_write / run_python / write_artifact / list_dir / stat。
            B. 禁止在新 skill 的 SKILL.md 中生成 runtime_tool(...) 包装调用。
            C. 知识库、数据集、API、MCP 等绑定业务工具，必须写成“调用已绑定工具”，禁止写成 knowledge_search(...) 或 dataset_*/api_*/mcp_* 伪 action。
            D. 禁止在 SKILL.md 中输出 call_tool(...)、tool_call(...) 等伪调用语法；业务工具统一写成“调用当前已绑定工具”。
            E. 若业务工具存在 schema，参数名必须严格来自已绑定工具 schema；不要臆造字段。
            """;

    private final ObjectMapper objectMapper;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final EventPersistenceCapabilityAdapter eventPersistenceCapability;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;
    private final SkillStudioFilesystemSkillLoader skillLoader;
    private final SkillStudioValidationService validationService;

    public SkillStudioCreatorSkillInvoker(
            ObjectMapper objectMapper,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            EventPersistenceCapabilityAdapter eventPersistenceCapability,
            TokenUsageCapabilityAdapter tokenUsageCapability,
            SkillStudioFilesystemSkillLoader skillLoader,
            SkillStudioValidationService validationService) {
        this.objectMapper = objectMapper;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.eventPersistenceCapability = eventPersistenceCapability;
        this.tokenUsageCapability = tokenUsageCapability;
        this.skillLoader = skillLoader;
        this.validationService = validationService;
    }

    public SkillStudioCreatorSkillDebugResult invoke(SkillStudioContextInput input) {
        return invokeInternal(input, CreatorRunMode.PROPOSAL);
    }

    public SkillStudioCreatorSkillDebugResult execute(SkillStudioContextInput input) {
        return invokeInternal(input, CreatorRunMode.EXECUTE);
    }

    private SkillStudioCreatorSkillDebugResult invokeInternal(SkillStudioContextInput input, CreatorRunMode runMode) {
        List<String> executionLogs = new ArrayList<>();
        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        SkillStudioCreatorToolProvider toolProvider =
                new SkillStudioCreatorToolProvider(workspaceRoot, input.skillName());
        var loadedSkill = skillLoader.load(workspaceRoot, "zhuoju-skill-creator", toolProvider.toolCallbacks());
        SkillExecutionScope.activate(loadedSkill.skillMarkdownPath().getParent());
        executionLogs.add("loaded skill: " + loadedSkill.runtimeSkillName());
        executionLogs.add("skill path: " + loadedSkill.skillMarkdownPath());
        executionLogs.add("tool names: " + toolProvider.toolNames());

        ToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new DelegatingToolCallbackResolver(List.of(
                        new StaticToolCallbackResolver(toolProvider.toolCallbacks()),
                        SkillAwareToolCallbackResolver.builder()
                                .skillKit(loadedSkill.skillKit())
                                .build())))
                .build();
        ToolCallingManager toolCallingManager = SkillAwareToolCallingManager.builder()
                .skillKit(loadedSkill.skillKit())
                .delegate(delegate)
                .build();
        var chatBundle = modelRuntimeClientFactory.createChatBundle(toolCallingManager);
        executionLogs.add("chat model: " + chatBundle.config().model());
        executionLogs.add("run mode: " + runMode.name());

        String requestPrompt = buildRequestPrompt(input, workspaceRoot, runMode);
        RuntimeTokenUsageAccumulator tokenAccumulator =
                tokenUsageCapability.createAccumulator(chatBundle.config(), System.currentTimeMillis());
        CallSkillResult callResult =
                callSkill(chatBundle.chatClient(), loadedSkill.skillKit(), requestPrompt, tokenAccumulator);
        String rawOutput = callResult.rawOutput();
        RuntimeRunUsageSnapshot usageSnapshot = callResult.usageSnapshot();
        executionLogs.add("raw output chars: " + (rawOutput == null ? 0 : rawOutput.length()));

        if (runMode == CreatorRunMode.EXECUTE) {
            boolean wroteFiles = hasWriteSuccess(toolProvider.logs());
            SkillStudioValidationResult validation = wroteFiles ? toolProvider.validateWrittenBundle() : null;
            boolean applied = wroteFiles && (validation == null || validation.valid());
            String applyMessage = !wroteFiles
                    ? "Creator 未写入文件"
                    : applied ? "skill 文件已由 Creator 直接写入 draft" : "Creator 已写入文件，但未通过 skill 生成质量校验";
            return new SkillStudioCreatorSkillDebugResult(
                    loadedSkill.runtimeSkillName(),
                    loadedSkill.skillMarkdownPath().toString(),
                    toolProvider.toolNames(),
                    requestPrompt,
                    rawOutput,
                    null,
                    validation,
                    List.of(),
                    toolProvider.logs(),
                    List.copyOf(executionLogs),
                    usageSnapshot,
                    null,
                    applied,
                    applyMessage);
        }

        ParseResult parseResult = parseProposal(rawOutput);
        SkillStudioValidationResult validation = buildValidation(parseResult);
        if (parseResult.proposal() != null) {
            executionLogs.add("proposal parsed: true");
            executionLogs.add(
                    "proposal changes: " + parseResult.proposal().changes().size());
        } else {
            executionLogs.add("proposal parsed: false");
            executionLogs.add("parse error: " + parseResult.error());
        }
        log.info(
                "skillstudio creator skill 调用完成：requestedSkillName={}, runtimeSkillName={}, parsed={}, toolCalls={}",
                input.skillName(),
                loadedSkill.runtimeSkillName(),
                parseResult.proposal() != null,
                toolProvider.logs().size());
        return new SkillStudioCreatorSkillDebugResult(
                loadedSkill.runtimeSkillName(),
                loadedSkill.skillMarkdownPath().toString(),
                toolProvider.toolNames(),
                requestPrompt,
                rawOutput,
                parseResult.proposal(),
                validation,
                List.of(),
                toolProvider.logs(),
                List.copyOf(executionLogs),
                usageSnapshot,
                parseResult.error(),
                false,
                "未执行写入");
    }

    public Flux<ServerSentEvent<String>> streamInvoke(
            SkillStudioContextInput input,
            Consumer<SkillStudioCreatorSkillDebugResult> completionConsumer,
            Consumer<Throwable> errorConsumer) {
        return streamInternal(input, completionConsumer, errorConsumer, CreatorRunMode.PROPOSAL);
    }

    public Flux<ServerSentEvent<String>> streamExecute(
            SkillStudioContextInput input,
            Consumer<SkillStudioCreatorSkillDebugResult> completionConsumer,
            Consumer<Throwable> errorConsumer) {
        return streamInternal(input, completionConsumer, errorConsumer, CreatorRunMode.EXECUTE);
    }

    private Flux<ServerSentEvent<String>> streamInternal(
            SkillStudioContextInput input,
            Consumer<SkillStudioCreatorSkillDebugResult> completionConsumer,
            Consumer<Throwable> errorConsumer,
            CreatorRunMode runMode) {
        return Flux.defer(() -> {
            List<String> executionLogs = new ArrayList<>();
            Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
            SkillStudioCreatorToolProvider toolProvider =
                    new SkillStudioCreatorToolProvider(workspaceRoot, input.skillName());
            var loadedSkill = skillLoader.load(workspaceRoot, "zhuoju-skill-creator", toolProvider.toolCallbacks());
            SkillExecutionScope.activate(loadedSkill.skillMarkdownPath().getParent());
            executionLogs.add("loaded skill: " + loadedSkill.runtimeSkillName());
            executionLogs.add("skill path: " + loadedSkill.skillMarkdownPath());
            executionLogs.add("tool names: " + toolProvider.toolNames());

            ToolCallingManager delegate = DefaultToolCallingManager.builder()
                    .toolCallbackResolver(new DelegatingToolCallbackResolver(List.of(
                            new StaticToolCallbackResolver(toolProvider.toolCallbacks()),
                            SkillAwareToolCallbackResolver.builder()
                                    .skillKit(loadedSkill.skillKit())
                                    .build())))
                    .build();
            ToolCallingManager toolCallingManager = SkillAwareToolCallingManager.builder()
                    .skillKit(loadedSkill.skillKit())
                    .delegate(delegate)
                    .build();
            var chatBundle = modelRuntimeClientFactory.createChatBundle(toolCallingManager);
            executionLogs.add("chat model: " + chatBundle.config().model());
            executionLogs.add("run mode: " + runMode.name());

            String requestPrompt = buildRequestPrompt(input, workspaceRoot, runMode);
            RuntimeTokenUsageAccumulator tokenAccumulator =
                    tokenUsageCapability.createAccumulator(chatBundle.config(), System.currentTimeMillis());
            AtomicReference<String> last = new AtomicReference<>("");
            AtomicReference<String> previousResponseText = new AtomicReference<>("");
            AtomicBoolean finalized = new AtomicBoolean(false);
            List<Map<String, Object>> toolEvents = Collections.synchronizedList(new ArrayList<>());
            Map<String, Long> toolStartedAtByTrace = Collections.synchronizedMap(new LinkedHashMap<>());
            Sinks.Many<ServerSentEvent<String>> toolSink =
                    Sinks.many().unicast().onBackpressureBuffer();

            var publisher = (java.util.function.BiConsumer<String, String>) (eventType, payload) -> {
                String normalizedPayload =
                        eventPersistenceCapability.enrichToolEventPayload(eventType, payload, toolStartedAtByTrace);
                recordToolEvent(eventType, normalizedPayload, toolEvents);
                tokenUsageCapability.recordToolEvent(tokenAccumulator, eventType, normalizedPayload);
                toolSink.tryEmitNext(ServerSentEvent.builder(normalizedPayload)
                        .event(eventType)
                        .build());
            };

            Flux<ServerSentEvent<String>> stream = chatBundle
                    .chatClient()
                    .prompt()
                    .advisors(SkillAwareAdvisor.builder()
                            .skillKit(loadedSkill.skillKit())
                            .cleanupAfterCall(false)
                            .build())
                    .user(requestPrompt)
                    .stream()
                    .chatResponse()
                    .flatMap(chatResponse -> {
                        if (chatResponse == null
                                || chatResponse.getResult() == null
                                || chatResponse.getResult().getOutput() == null) {
                            return Flux.empty();
                        }
                        AssistantMessage output = chatResponse.getResult().getOutput();
                        String current = output.getText();
                        if (current == null) {
                            current = "";
                        }
                        String previous = previousResponseText.get();
                        String delta = current.startsWith(previous) ? current.substring(previous.length()) : current;
                        previousResponseText.set(current);
                        boolean usageOnlyChunk = isUsageOnlyChunk(chatResponse, delta);
                        if (!usageOnlyChunk || tokenAccumulator.hasCurrentCall()) {
                            tokenAccumulator.ensureCurrentCall(System.currentTimeMillis());
                        }
                        tokenUsageCapability.recordResponse(tokenAccumulator, chatResponse);
                        if (usageOnlyChunk) {
                            tokenUsageCapability.completeCurrentCall(
                                    tokenAccumulator, "COMPLETED", System.currentTimeMillis(), safeLength(last.get()));
                        }
                        if (!StringUtils.hasText(delta)) {
                            return Flux.empty();
                        }
                        last.updateAndGet(existing -> existing + delta);
                        return Flux.just(messageEvent(delta));
                    })
                    .onErrorResume(error -> {
                        String friendlyMessage = ModelRuntimeErrorMessageResolver.resolve(error);
                        long completedAt = System.currentTimeMillis();
                        tokenUsageCapability.completeCurrentCall(
                                tokenAccumulator, "FAILED", completedAt, safeLength(last.get()));
                        RuntimeRunUsageSnapshot usageSnapshot =
                                tokenUsageCapability.snapshot(tokenAccumulator, "FAILED", completedAt);
                        errorConsumer.accept(
                                new SkillStudioCreatorExecutionException(friendlyMessage, usageSnapshot, error));
                        return Flux.just(errorEvent(friendlyMessage));
                    })
                    .doOnComplete(() -> {
                        if (!finalized.compareAndSet(false, true)) {
                            return;
                        }
                        executionLogs.add("raw output chars: " + last.get().length());
                        long completedAt = System.currentTimeMillis();
                        tokenUsageCapability.completeCurrentCall(
                                tokenAccumulator, "COMPLETED", completedAt, safeLength(last.get()));
                        RuntimeRunUsageSnapshot usageSnapshot =
                                tokenUsageCapability.snapshot(tokenAccumulator, "COMPLETED", completedAt);
                        if (runMode == CreatorRunMode.EXECUTE) {
                            boolean wroteFiles = hasWriteSuccess(toolProvider.logs());
                            SkillStudioValidationResult validation =
                                    wroteFiles ? toolProvider.validateWrittenBundle() : null;
                            boolean applied = wroteFiles && (validation == null || validation.valid());
                            completionConsumer.accept(new SkillStudioCreatorSkillDebugResult(
                                    loadedSkill.runtimeSkillName(),
                                    loadedSkill.skillMarkdownPath().toString(),
                                    toolProvider.toolNames(),
                                    requestPrompt,
                                    last.get(),
                                    null,
                                    validation,
                                    List.copyOf(toolEvents),
                                    toolProvider.logs(),
                                    List.copyOf(executionLogs),
                                    usageSnapshot,
                                    null,
                                    applied,
                                    !wroteFiles
                                            ? "Creator 未写入文件"
                                            : applied
                                                    ? "skill 文件已由 Creator 直接写入 draft"
                                                    : "Creator 已写入文件，但未通过 skill 生成质量校验"));
                            return;
                        }
                        ParseResult parseResult = parseProposal(last.get());
                        SkillStudioValidationResult validation = buildValidation(parseResult);
                        if (parseResult.proposal() != null) {
                            executionLogs.add("proposal parsed: true");
                            executionLogs.add("proposal changes: "
                                    + parseResult.proposal().changes().size());
                        } else {
                            executionLogs.add("proposal parsed: false");
                            executionLogs.add("parse error: " + parseResult.error());
                        }
                        completionConsumer.accept(new SkillStudioCreatorSkillDebugResult(
                                loadedSkill.runtimeSkillName(),
                                loadedSkill.skillMarkdownPath().toString(),
                                toolProvider.toolNames(),
                                requestPrompt,
                                last.get(),
                                parseResult.proposal(),
                                validation,
                                List.copyOf(toolEvents),
                                toolProvider.logs(),
                                List.copyOf(executionLogs),
                                usageSnapshot,
                                parseResult.error(),
                                false,
                                "未执行写入"));
                    })
                    .doFinally(signalType -> {
                        toolSink.tryEmitComplete();
                        SkillExecutionScope.clear();
                    })
                    .contextWrite(Context.of("toolEventPublisher", publisher));

            return Flux.merge(stream, toolSink.asFlux());
        });
    }

    private CallSkillResult callSkill(
            ChatClient chatClient,
            lingzhou.agent.spring.ai.skill.core.SkillKit skillKit,
            String requestPrompt,
            RuntimeTokenUsageAccumulator tokenAccumulator) {
        long startedAt = System.currentTimeMillis();
        tokenAccumulator.ensureCurrentCall(startedAt);
        try {
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .advisors(SkillAwareAdvisor.builder()
                            .skillKit(skillKit)
                            .cleanupAfterCall(false)
                            .build())
                    .user(requestPrompt)
                    .call()
                    .chatResponse();
            String rawOutput = extractResponseText(chatResponse);
            tokenUsageCapability.recordResponse(tokenAccumulator, chatResponse);
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(tokenAccumulator, "COMPLETED", completedAt, safeLength(rawOutput));
            return new CallSkillResult(
                    rawOutput, tokenUsageCapability.snapshot(tokenAccumulator, "COMPLETED", completedAt));
        } catch (Exception error) {
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(tokenAccumulator, "FAILED", completedAt, 0);
            RuntimeRunUsageSnapshot usageSnapshot =
                    tokenUsageCapability.snapshot(tokenAccumulator, "FAILED", completedAt);
            throw new SkillStudioCreatorExecutionException(
                    ModelRuntimeErrorMessageResolver.resolve(error), usageSnapshot, error);
        } finally {
            SkillExecutionScope.clear();
        }
    }

    private String extractResponseText(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null
                || chatResponse.getResult().getOutput().getText() == null) {
            return "";
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private boolean isUsageOnlyChunk(ChatResponse chatResponse, String delta) {
        return hasUsage(chatResponse) && !StringUtils.hasText(delta);
    }

    private boolean hasUsage(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getMetadata() == null
                || chatResponse.getMetadata().getUsage() == null) {
            return false;
        }
        return chatResponse.getMetadata().getUsage().getPromptTokens() != null
                || chatResponse.getMetadata().getUsage().getCompletionTokens() != null
                || chatResponse.getMetadata().getUsage().getTotalTokens() != null;
    }

    private String buildRequestPrompt(SkillStudioContextInput input, Path workspaceRoot, CreatorRunMode runMode) {
        PromptInput promptInput = normalizeFirstRoundStrictInput(input);
        String inputJson = toJson(promptInput.input());
        boolean firstRoundStrictGuard = promptInput.firstRoundStrictGuard();
        if (runMode == CreatorRunMode.EXECUTE) {
            String prompt =
                    """
                    你现在正在执行 `zhuoju-skill-creator`。

                    你的任务是：基于下面的上下文，直接完善当前 skill 的 draft 目录内容。

                    强制要求：
                    1. 先识别本轮意图：如果只是问候、闲聊、解释当前项目或询问能力，直接自然回答，不要写任何文件。
                    2. 只有当用户明确要新建、编辑、完善、修复或补充 skill 时，才进入文件写入流程。
                    3. 进入文件写入流程后，按本地 `skill-creator` 方法论设计：先定义触发场景、能力边界、最小文件集合，再决定是否需要 references 或 Python scripts。
                    4. 你必须把文件直接写入当前 skill 的 draft 目录，使用 `writeFile` 完成，不要返回 JSON 提案让别人代写。
                    5. 如果需要脚本，只允许生成 Python 脚本，路径必须在 `scripts/**/*.py`；禁止生成 shell、JavaScript、TypeScript 或其他脚本语言。
                    6. 基础运行环境是 Python 3.11；脚本语法、标准库用法和第三方依赖版本都必须兼容 Python 3.11。
                    7. 只要新增或修改 Python 脚本，就必须同步生成或更新 skill 根目录 `requirements.txt`；无第三方依赖时也写入一个空内容或仅注释说明的 `requirements.txt`。
                    8. `requirements.txt` 中声明的第三方包必须选择支持 Python 3.11 的版本范围，避免引用仅支持其他 Python 版本的依赖。
                    9. 当前 skill 的 draft 目录已经初始化完成，至少包含：
                       - `SKILL.md`
                       - `references/`
                       - `scripts/`
                       - `requirements.txt` 可按需创建
                    10. 只允许写当前 skill 的 draft 目录；如果需要补充说明、Python 脚本、参考资料或依赖声明，都写到这个目录下面。
                    11. 优先产出可工作的最小技能目录；不要为了结构完整生成无用文件。
                    12. 不要默认读取或套用 `basic`、`reference-driven`、`document-workflow` 通用结构模板；这些只用于理解旧草稿。
                    12.1 禁止把其他已发布 skill、其他 draft skill 或同类现有技能当成可复制模板；最多只能借鉴能力边界和文件职责，不能整段改写后直接搬运。
                    13. 不要 mock 核心业务能力。例如文档翻译不要生成 `mock_translate()` 当作真实翻译；如果没有真实 API，就把 Python 脚本限定为抽取、组装、校验和产物写出，并在 SKILL.md 中说明语义能力由模型或绑定工具完成。
                    14. 只要生成或修改 Python 脚本，最终 `SKILL.md` 必须写出完整、真实的独立工具调用方式：`run_python(scriptPath=..., args=..., workDir=..., timeoutSeconds=...)`。必须包含真实 `scriptPath`、`args`、`workDir`、输入输出路径和脚本 stdout JSON 预期；不要用 `...` 代替关键参数。
                    15. 脚本必须实现真实确定性处理，禁止写“模拟处理”“实际应替换”“TODO 实现核心逻辑”等核心占位；如果处理 `.docx` 等二进制格式，必须使用真实解析库并在 `requirements.txt` 声明 Python 3.11 兼容依赖。
                    16. 完成写入后，请直接输出一段自然中文总结。
                    17. 总结不要生硬套模板，但应尽量覆盖：
                       - 实现概览：本次创建或更新了什么
                       - 关键决策：为什么采用当前文件结构、Python 脚本或工具方式
                       - 技术架构：核心文件分别承担什么职责
                       - 产出结果：最终有哪些文件、是否已写入完成
                    18. 对最终生成出来的 skill，文件、脚本和最终产物这类宿主 runtime 能力只允许写成独立工具调用：
                       - 文件读取：`file_read(path="...")`
                       - 中间文件写入：`file_write(path="...", content="...")`
                       - Python 脚本执行：`run_python(scriptPath="/skill/scripts/name.py", args=[...], workDir="...", timeoutSeconds=...)`
                       - 最终下载产物：`write_artifact(folder="...", fileName="...", sourcePath="..." | content="...")`
                         若脚本已生成最终文件，`write_artifact` 必须使用 `sourcePath` 指向 `/temp/...` 或 `/outputs/...` 的已有文件，不要把文档内容放进 `content` 再重写。
                       数据集、知识库、API、MCP 这类运行时绑定的业务工具也不要伪造为 runtime 工具调用：
                       - `dataset_summary(...)`
                       - `dataset_schema(...)`
                       - `dataset_query(...)`
                       - `knowledge_search(...)`
                       这类能力应描述为“调用当前绑定的某类工具”，实际工具名和参数口径以运行时注入说明及 `toolProfiles` 为准。
                       不要把工坊内部的 `readFile`、`writeFile`、`bash`、`writeArtifact` 直接写进最终 skill 内容里；但你在当前工坊执行过程中，仍然使用 `readFile` 读模板、使用 `writeFile` 写 draft 文件。
                    19. 不要输出 JSON，不要输出代码块围栏，不要把完整文件内容再重复贴回聊天里。
                    20. 如果上下文中提供了 `toolResolution`、`toolProfiles`、`projectHints`、`projectConstraints`，必须优先把它们当作当前项目的真实事实源：
                        - `toolResolution` 用于判断本轮优先使用哪些已绑定工具
                        - `toolProfiles` 用于理解工具能力边界、参数要求与 schema 摘要
                        - `projectHints` / `projectConstraints` 用于约束技能口径
                        不要虚构未绑定工具，不要擅自扩展 schema 中不存在的关键字段。
                    21. 即使你识别出“这是某类常见 skill”，也必须围绕当前用户目标重新组织内容，不能通过读取其他 skill 后直接复制其 SKILL.md、references 或脚本。

                    可读取能力模板和 runtime tool 说明：
                    - %s/%s/zhuoju-skill-creator/templates/capability/file-operations.template.md
                    - %s/%s/zhuoju-skill-creator/templates/capability/knowledge-base.template.md
                    - %s/%s/zhuoju-skill-creator/templates/capability/dataset.template.md
                    - %s/%s/zhuoju-skill-creator/templates/capability/api-library.template.md
                    - %s/%s/zhuoju-skill-creator/templates/capability/mcp-library.template.md

                    上下文输入 JSON：
                    %s
                    """
                            .formatted(
                                    workspaceRoot,
                                    SkillStudioWorkspacePaths.SKILLS_ROOT,
                                    workspaceRoot,
                                    SkillStudioWorkspacePaths.SKILLS_ROOT,
                                    workspaceRoot,
                                    SkillStudioWorkspacePaths.SKILLS_ROOT,
                                    workspaceRoot,
                                    SkillStudioWorkspacePaths.SKILLS_ROOT,
                                    workspaceRoot,
                                    SkillStudioWorkspacePaths.SKILLS_ROOT,
                                    inputJson);
            return appendFirstRoundStrictGuard(prompt, firstRoundStrictGuard);
        }
        String prompt =
                """
                你现在正在执行 `zhuoju-skill-creator`。

                请基于下面的上下文，为目标 skill 生成完整的 draft 提案。

                强制要求：
                1. 只输出一个 JSON 对象，不要输出 Markdown、解释或代码块围栏。
                2. 按本地 `skill-creator` 方法论生成提案：先确定触发场景、能力边界、最小文件集合，再决定是否需要 references 或 Python scripts。
                3. 不要默认读取或套用 `basic`、`reference-driven`、`document-workflow` 通用结构模板；这些只用于理解旧草稿。
                3.1 禁止把其他已发布 skill、其他 draft skill 或同类现有技能当成可复制模板；最多只能借鉴能力边界和文件职责，不能整段改写后直接搬运。
                4. 能力模板位于：
                   - %s/%s/zhuoju-skill-creator/templates/capability/knowledge-base.template.md
                   - %s/%s/zhuoju-skill-creator/templates/capability/dataset.template.md
                   - %s/%s/zhuoju-skill-creator/templates/capability/api-library.template.md
                   - %s/%s/zhuoju-skill-creator/templates/capability/mcp-library.template.md
                5. Runtime tool 说明位于：
                   - %s/%s/zhuoju-skill-creator/templates/capability/file-operations.template.md
                6. 能力模板是可选层，只有当用户明确依赖本项目已有能力库时才命中。
                7. 普通文档翻译、摘要、抽取、重组等场景，即使可能会调用模型或外部服务，也不要自动命中 `api-library`。
                8. 如果当前需求不涉及能力模板，`capabilityTemplate` 必须返回空字符串。
                9. 文件处理与产物输出不是能力模板，统一作为独立 runtime 工具能力说明处理，不要把它返回为 `capabilityTemplate`。
                10. 所有 `changes[].path` 必须落在 `%s/<skill-name>/` 下。
                11. 生成的是提案，不要实际调用 `writeFile` 落盘。
                12. 如果需要脚本，只允许生成 `scripts/**/*.py`，且必须同步生成或更新根目录 `requirements.txt`。
                13. 基础运行环境是 Python 3.11；脚本语法、标准库用法和 `requirements.txt` 中第三方依赖版本都必须兼容 Python 3.11。
                14. 不要 mock 核心业务能力。例如文档翻译不要生成 `mock_translate()` 当作真实翻译；如果没有真实 API，就把 Python 脚本限定为抽取、组装、校验和产物写出，并在 SKILL.md 中说明语义能力由模型或绑定工具完成。
                15. 只要生成或修改 Python 脚本，提案中的 `SKILL.md` 必须写出完整、真实的独立工具调用方式：`run_python(...)`。必须包含真实 `scriptPath`、`args`、`workDir`、输入输出路径和脚本 stdout JSON 预期；不要用 `...` 代替关键参数。
                16. 脚本必须实现真实确定性处理，禁止写“模拟处理”“实际应替换”“TODO 实现核心逻辑”等核心占位；如果处理 `.docx` 等二进制格式，必须使用真实解析库并在 `requirements.txt` 声明 Python 3.11 兼容依赖。
                17. `baseTemplate` 为历史兼容字段，只表达结构倾向，不代表要读取通用结构模板；可返回 `minimal`、`reference`、`scripted-workflow` 或空字符串。
                18. 提案中的最终 skill 内容，文件、脚本和最终产物这类宿主 runtime 能力只允许写成独立工具风格：
                   - 文件读取：`file_read(path="...")`
                   - 中间文件写入：`file_write(path="...", content="...")`
                   - Python 脚本执行：`run_python(scriptPath="/skill/scripts/name.py", args=[...], workDir="...", timeoutSeconds=...)`
                   - 最终下载产物：`write_artifact(folder="...", fileName="...", sourcePath="..." | content="...")`
                     若脚本已生成最终文件，`write_artifact` 必须使用 `sourcePath` 指向 `/temp/...` 或 `/outputs/...` 的已有文件，不要把文档内容放进 `content` 再重写。
                   数据集、知识库、API、MCP 这类运行时绑定的业务工具也不要伪造为 runtime 工具调用：
                   - `dataset_summary(...)`
                   - `dataset_schema(...)`
                   - `dataset_query(...)`
                   - `knowledge_search(...)`
                   这些能力应写成“调用当前绑定的某类工具”，实际工具名和参数口径以运行时注入说明及 `toolProfiles` 为准。
                   不要把工坊内部的 `readFile`、`writeFile`、`bash`、`writeArtifact` 直接写进最终 skill 内容里。
                19. 如果上下文中提供了 `toolResolution`、`toolProfiles`、`projectHints`、`projectConstraints`，必须优先基于这些项目事实组织提案，不要虚构未绑定工具，也不要擅自扩展 schema 中不存在的关键字段。
                20. 即使你识别出“这是某类常见 skill”，也必须围绕当前用户目标重新组织内容，不能通过读取其他 skill 后直接复制其 SKILL.md、references 或脚本。

                输出 JSON schema：
                {
                  "skillName": "string",
                  "mode": "CREATE|EDIT",
                  "intent": {
                    "baseTemplate": "minimal|reference|scripted-workflow|\"\"",
                    "capabilityTemplate": "knowledge-base|dataset|api-library|mcp-library|\"\"",
                    "confidence": 0.0,
                    "reasons": ["string"]
                  },
                  "summary": "string",
                  "changes": [
                    {
                      "path": "string",
                      "changeType": "CREATE|UPDATE",
                      "fileType": "SKILL|REFERENCE|SCRIPT|REQUIREMENTS",
                      "content": "string"
                    }
                  ],
                  "validation": {
                    "valid": true,
                    "errors": [],
                    "warnings": []
                  },
                  "notes": ["string"]
                }

                上下文输入 JSON：
                %s
                """
                        .formatted(
                                workspaceRoot,
                                SkillStudioWorkspacePaths.SKILLS_ROOT,
                                workspaceRoot,
                                SkillStudioWorkspacePaths.SKILLS_ROOT,
                                workspaceRoot,
                                SkillStudioWorkspacePaths.SKILLS_ROOT,
                                workspaceRoot,
                                SkillStudioWorkspacePaths.SKILLS_ROOT,
                                workspaceRoot,
                                SkillStudioWorkspacePaths.SKILLS_ROOT,
                                SkillStudioWorkspacePaths.DRAFT_ROOT,
                                inputJson);
        return appendFirstRoundStrictGuard(prompt, firstRoundStrictGuard);
    }

    private PromptInput normalizeFirstRoundStrictInput(SkillStudioContextInput input) {
        if (input == null || !StringUtils.hasText(input.userGoal())) {
            return new PromptInput(input, false);
        }
        String userGoal = input.userGoal();
        if (!userGoal.contains(FIRST_ROUND_STRICT_GUARD_MARKER)) {
            return new PromptInput(input, false);
        }
        String normalizedUserGoal =
                userGoal.replace(FIRST_ROUND_STRICT_GUARD_MARKER, "").trim();
        SkillStudioContextInput normalizedInput = new SkillStudioContextInput(
                input.skillName(),
                input.mode(),
                normalizedUserGoal,
                input.draftSummary(),
                input.memorySummary(),
                input.hints(),
                input.toolResolution(),
                input.toolProfiles(),
                input.projectHints(),
                input.projectConstraints());
        return new PromptInput(normalizedInput, true);
    }

    private String appendFirstRoundStrictGuard(String prompt, boolean firstRoundStrictGuard) {
        if (!firstRoundStrictGuard) {
            return prompt;
        }
        return prompt + "\n\n" + FIRST_ROUND_STRICT_GUARD_APPENDIX;
    }

    private boolean hasWriteSuccess(List<String> logs) {
        return logs != null && logs.stream().anyMatch(item -> item != null && item.startsWith("writeFile ok:"));
    }

    private void recordToolEvent(String eventType, String payload, List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || (!"tool".equals(eventType) && !"result".equals(eventType))) {
            return;
        }
        try {
            Map<String, Object> wrapper = objectMapper.readValue(payload, Map.class);
            Object content = wrapper == null ? null : wrapper.get("content");
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", eventType);
            if (content instanceof Map<?, ?> contentMap) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                event.put("content", normalized);
            } else if (content != null) {
                event.put("content", content);
            }
            toolEvents.add(event);
        } catch (Exception ex) {
            log.warn("记录 skillstudio 工具事件失败：eventType={}, error={}", eventType, ex.getMessage());
        }
    }

    private ServerSentEvent<String> messageEvent(String content) {
        return typedEvent("message", "message", content);
    }

    private ServerSentEvent<String> errorEvent(String error) {
        return typedEvent("error", "error", error);
    }

    private ServerSentEvent<String> typedEvent(String eventName, String type, Object content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return ServerSentEvent.builder(toJson(payload)).event(eventName).build();
    }

    private SkillStudioValidationResult buildValidation(ParseResult parseResult) {
        if (parseResult.proposal() == null) {
            return new SkillStudioValidationResult(
                    false,
                    List.of(new SkillStudioValidationIssue(
                            "model-output",
                            "PARSE_FAILED",
                            parseResult.error() == null ? "模型输出未能解析为 proposal" : parseResult.error())),
                    List.of());
        }
        return validationService.validateProposal(parseResult.proposal());
    }

    private ParseResult parseProposal(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new ParseResult(null, "模型未返回内容");
        }
        String json = extractJson(rawOutput);
        try {
            return new ParseResult(objectMapper.readValue(json, SkillStudioChangeProposal.class), null);
        } catch (JsonProcessingException ex) {
            log.warn("解析 creator skill 输出失败：error={}", ex.getOriginalMessage());
            return new ParseResult(null, ex.getOriginalMessage());
        }
    }

    private String extractJson(String rawOutput) {
        String trimmed = rawOutput.trim();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 skillstudio 上下文失败", ex);
        }
    }

    private record CallSkillResult(String rawOutput, RuntimeRunUsageSnapshot usageSnapshot) {}

    private record PromptInput(SkillStudioContextInput input, boolean firstRoundStrictGuard) {}

    private record ParseResult(SkillStudioChangeProposal proposal, String error) {}

    private enum CreatorRunMode {
        PROPOSAL,
        EXECUTE
    }
}
