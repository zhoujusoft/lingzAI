package lingzhou.agent.backend.business.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.attachment.FileParseService;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvDescriptorService;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.capability.agentruntime.prompt.PromptEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.contract.RuntimeSkillStateContractSupport;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.capability.tool.registry.ToolLibraryCallbackResolver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

class ChatRuntimePreparedRequestAssemblerTest {

    @Test
    void shouldKeepMentionedSkillAsHintWithoutPreloadingLegacySkillState() {
        ChatRuntimePreparedRequestAssembler assembler = new ChatRuntimePreparedRequestAssembler(
                new ChatFileService(null, null),
                null,
                new FileParseService(new ChatFileService(null, null), List.of(), null, null),
                null,
                null,
                new PromptEngineeringService(new lingzhou.agent.backend.app.ChatModelProperties(), null, null),
                new RequestScopedSkillRuntimeService(
                        null, new RuntimeSkillStateContractSupport(new ObjectMapper())),
                userAgentConfigService(List.of(skill(1L, "meeting-assistant", "会议助手", "会议查询"))),
                null,
                new GlobalToolRegistry(List.of()),
                new ToolLibraryCallbackResolver(null, new GlobalToolRegistry(List.of()), null, null, null, null, null, null, null) {
                    @Override
                    public List<ToolCallback> listEnabledGlobalCallbacks(Long userId) {
                        return List.of();
                    }
                },
                new ToolToCodeEscalationPolicy(),
                new PythonRuntimeEnvDescriptorService(new RuntimeExecutionProperties()));

        ChatRuntimePreparedRequest prepared = assembler.buildGeneral(
                ConversationSessionType.GENERAL_CHAT,
                new LingzRuntimeRequest(
                        "session-1",
                        "请用会议助手查询今天的会议",
                        List.of(),
                        null,
                        null,
                        null,
                        Map.of(),
                        LingzRuntimeScopeType.GENERAL,
                        null,
                        null,
                        1L,
                        null),
                7L);

        assertThat(prepared.runtimeSkillName()).isNull();
        assertThat(prepared.loadedSkills()).isEmpty();
        assertThat(prepared.paramsJson())
                .contains("\"runtimeSkillState\"")
                .contains("\"mentionedSkillId\":1")
                .contains("\"selectedSkillHintId\":1")
                .contains("\"selectedSkillHintRuntimeSkillName\":\"meeting-assistant\"");
        assertThat(prepared.systemPrompt())
                .contains("用户本轮显式指定优先使用技能：`meeting-assistant`")
                .contains("## 能力选择策略")
                .contains("## Skill 使用规则")
                .contains("调用 `loadSkillContent(skillName)`")
                .contains("第一步必须调用 `loadSkillContent(\"meeting-assistant\")`")
                .contains("## Tool 使用规则")
                .contains("需要工具时直接调用工具，不要只描述计划")
                .contains("## Code 执行规则")
                .contains("Code 是最后手段");
    }

    @Test
    void shouldWrapSoulAndProfileWithExplicitOwnershipBoundaries() {
        ChatRuntimePreparedRequestAssembler assembler = new ChatRuntimePreparedRequestAssembler(
                new ChatFileService(null, null),
                null,
                new FileParseService(new ChatFileService(null, null), List.of(), null, null),
                null,
                null,
                new PromptEngineeringService(new lingzhou.agent.backend.app.ChatModelProperties(), null, null),
                new RequestScopedSkillRuntimeService(
                        null, new RuntimeSkillStateContractSupport(new ObjectMapper())),
                userAgentConfigService(
                        List.of(),
                        agent("私人助理", "通用助手", "协助用户处理日常问题"),
                        List.of(
                                userAgentFile(
                                        "SOUL.md",
                                        """
                                        _你不是聊天机器人。你在成为某个人。_

                                        ## 核心准则

                                        真心帮忙，别演。
                                        """),
                                userAgentFile(
                                        "PROFILE.md",
                                        """
                                        ## 身份

                                        - 用户名：系统管理员
                                        - 部门：
                                        - 其他：

                                        ## 岗位职责

                                        部门经理，喜好偏向：生成分析报表和趋势洞察
                                        """))),
                null,
                new GlobalToolRegistry(List.of()),
                new ToolLibraryCallbackResolver(null, new GlobalToolRegistry(List.of()), null, null, null, null, null, null, null) {
                    @Override
                    public List<ToolCallback> listEnabledGlobalCallbacks(Long userId) {
                        return List.of();
                    }
                },
                new ToolToCodeEscalationPolicy(),
                new PythonRuntimeEnvDescriptorService(new RuntimeExecutionProperties()));

        ChatRuntimePreparedRequest prepared = assembler.buildGeneral(
                ConversationSessionType.GENERAL_CHAT,
                new LingzRuntimeRequest(
                        "session-1",
                        "帮我总结一下本周经营情况",
                        List.of(),
                        null,
                        null,
                        null,
                        Map.of(),
                        LingzRuntimeScopeType.GENERAL,
                        null,
                        null,
                        1L,
                        null),
                7L);

        assertThat(prepared.systemPrompt())
                .contains("## Agent 身份")
                .contains("Lingz Agent 是运行平台，不是你的身份。")
                .contains("- Agent 名称：私人助理")
                .contains("_你不是聊天机器人。你在成为某个人。_")
                .contains("## 当前用户档案")
                .contains("以下信息描述的是“正在与你对话的用户”，不是你的身份。")
                .contains("- 用户名：系统管理员")
                .contains("## 当前用户职责与偏好")
                .contains("以下信息描述的是当前用户的职责、业务关注点和偏好，不是你的身份。")
                .contains("部门经理，喜好偏向：生成分析报表和趋势洞察")
                .doesNotContain("你是 Lingz Agent。");
    }

    @Test
    void shouldExposeInjectedResourceToolsInSystemPrompt() {
        ToolCallback datasetTool = FunctionToolCallback.builder(
                        "dataset.jingyingzhibiao.execute_dataset_sql",
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                Map.of("success", true))
                .description("经营指标 / SQL 执行")
                .inputType(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema("{\"type\":\"object\"}")
                .build();
        ChatRuntimePreparedRequestAssembler assembler = new ChatRuntimePreparedRequestAssembler(
                new ChatFileService(null, null),
                null,
                new FileParseService(new ChatFileService(null, null), List.of(), null, null),
                null,
                null,
                new PromptEngineeringService(new lingzhou.agent.backend.app.ChatModelProperties(), null, null),
                new RequestScopedSkillRuntimeService(
                        null, new RuntimeSkillStateContractSupport(new ObjectMapper())),
                userAgentConfigService(List.of()),
                null,
                new GlobalToolRegistry(List.of()),
                new ToolLibraryCallbackResolver(null, new GlobalToolRegistry(List.of()), null, null, null, null, null, null, null) {
                    @Override
                    public List<ToolCallback> listEnabledGlobalCallbacks(Long userId) {
                        return List.of(datasetTool);
                    }
                },
                new ToolToCodeEscalationPolicy(),
                new PythonRuntimeEnvDescriptorService(new RuntimeExecutionProperties()));

        ChatRuntimePreparedRequest prepared = assembler.buildGeneral(
                ConversationSessionType.GENERAL_CHAT,
                new LingzRuntimeRequest(
                        "session-1",
                        "查询经营指标数据集",
                        List.of(),
                        null,
                        null,
                        null,
                        Map.of(),
                        LingzRuntimeScopeType.GENERAL,
                        null,
                        null,
                        1L,
                        null),
                7L);

        assertThat(prepared.toolCallbacks()).hasSize(1);
        assertThat(prepared.systemPrompt())
                .contains("## 当前可用工具")
                .contains("已经按当前用户权限注入本轮 ToolCallbacks")
                .contains("`dataset.jingyingzhibiao.execute_dataset_sql`")
                .contains("经营指标 / SQL 执行");
    }

    private static SkillSimpleDto skill(Long id, String runtimeSkillName, String displayName, String description) {
        SkillSimpleDto dto = new SkillSimpleDto();
        dto.setId(id);
        dto.setRuntimeSkillName(runtimeSkillName);
        dto.setDisplayName(displayName);
        dto.setDescription(description);
        return dto;
    }

    private static UserAgentConfigService userAgentConfigService(List<SkillSimpleDto> skills) {
        return userAgentConfigService(skills, null, List.of());
    }

    private static UserAgentConfigService userAgentConfigService(
            List<SkillSimpleDto> skills, AgentDetailDto agent, List<UserAgentFile> files) {
        return (UserAgentConfigService) Proxy.newProxyInstance(
                UserAgentConfigService.class.getClassLoader(),
                new Class<?>[] {UserAgentConfigService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUserAgentFiles" -> files;
                    case "getUserAgentTemplate" -> agent;
                    case "getUserSkills" -> skills;
                    case "getUserSkillIds" -> skills.stream().map(SkillSimpleDto::getId).toList();
                    case "getUserAgentCode" -> "";
                    default -> null;
                });
    }

    private static AgentDetailDto agent(String displayName, String agentName, String description) {
        AgentDetailDto dto = new AgentDetailDto();
        dto.setDisplayName(displayName);
        dto.setAgentName(agentName);
        dto.setDescription(description);
        return dto;
    }

    private static UserAgentFile userAgentFile(String filename, String content) {
        UserAgentFile file = new UserAgentFile();
        file.setId(System.nanoTime());
        file.setUserId(7L);
        file.setAgentCode("general-assistant");
        file.setFilename(filename);
        file.setContent(content);
        file.setEnabled(1);
        file.setCreatedAt(new Date());
        file.setUpdatedAt(new Date());
        return file;
    }
}
