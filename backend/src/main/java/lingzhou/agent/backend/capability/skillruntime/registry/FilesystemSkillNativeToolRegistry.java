package lingzhou.agent.backend.capability.skillruntime.registry;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import lingzhou.agent.backend.business.system.service.RegisterUserSkillService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FilesystemSkillNativeToolRegistry {

    private static final String REGISTER_USER_SKILL = "register-user";

    private final RegisterUserSkillService registerUserSkillService;

    public FilesystemSkillNativeToolRegistry(RegisterUserSkillService registerUserSkillService) {
        this.registerUserSkillService = registerUserSkillService;
    }

    public List<ToolCallback> resolveNativeTools(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return List.of();
        }
        if (REGISTER_USER_SKILL.equals(runtimeSkillName.trim())) {
            return List.of(buildRegisterUserPreviewTool(), buildRegisterUserConfirmTool());
        }
        return List.of();
    }

    private ToolCallback buildRegisterUserPreviewTool() {
        return FunctionToolCallback.builder(
                        "register_user_preview",
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) -> {
                            Map<String, Object> payload = arguments == null ? Map.of() : arguments;
                            try {
                                RegisterUserSkillService.PreviewResult result =
                                        registerUserSkillService.preview(new RegisterUserSkillService.PreviewCommand(
                                                asText(payload.get("username")),
                                                asText(payload.get("name")),
                                                asText(payload.get("password")),
                                                asText(payload.get("mobile")),
                                                asText(payload.get("email"))));
                                return JSON.toJSONString(toPreviewView(result));
                            } catch (TaskException ex) {
                                return JSON.toJSONString(error(ex.getMessage()));
                            }
                        })
                .description("预校验并整理待创建用户信息，自动生成唯一用户名、补默认密码，并返回确认摘要。不真正创建用户。")
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(
                        """
                        {
                          "type": "object",
                          "properties": {
                            "username": {"type": "string", "description": "可选。管理员主动提供的用户名。"},
                            "name": {"type": "string", "description": "可选。姓名；若未提供用户名，可用于自动生成用户名。"},
                            "password": {"type": "string", "description": "可选。明文密码；不提供则默认 zhouju123.123。"},
                            "mobile": {"type": "string", "description": "可选。手机号。"},
                            "email": {"type": "string", "description": "可选。邮箱。"}
                          }
                        }
                        """)
                .build();
    }

    private ToolCallback buildRegisterUserConfirmTool() {
        return FunctionToolCallback.builder(
                        "register_user_confirm",
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) -> {
                            Map<String, Object> payload = arguments == null ? Map.of() : arguments;
                            RuntimeToolContext runtimeContext = RuntimeToolInvocationContextHolder.get();
                            Long operatorUserId = runtimeContext == null ? null : runtimeContext.userId();
                            try {
                                RegisterUserSkillService.ConfirmResult result = registerUserSkillService.confirm(
                                        operatorUserId,
                                        new RegisterUserSkillService.ConfirmCommand(
                                                asText(payload.get("username")),
                                                asText(payload.get("name")),
                                                asText(payload.get("password")),
                                                asText(payload.get("mobile")),
                                                asText(payload.get("email")),
                                                asBoolean(payload.get("confirm"))));
                                return JSON.toJSONString(toConfirmView(result));
                            } catch (TaskException ex) {
                                return JSON.toJSONString(error(ex.getMessage()));
                            }
                        })
                .description("在管理员明确确认后真正创建用户。必须传 confirm=true。")
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(
                        """
                        {
                          "type": "object",
                          "required": ["username", "name", "password", "confirm"],
                          "properties": {
                            "username": {"type": "string", "description": "最终确认后的用户名。"},
                            "name": {"type": "string", "description": "最终确认后的姓名。"},
                            "password": {"type": "string", "description": "最终确认后的明文密码。"},
                            "mobile": {"type": "string", "description": "可选。手机号。"},
                            "email": {"type": "string", "description": "可选。邮箱。"},
                            "confirm": {"type": "boolean", "description": "必须为 true，表示管理员已明确确认创建。"}
                          }
                        }
                        """)
                .build();
    }

    private Map<String, Object> toPreviewView(RegisterUserSkillService.PreviewResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("username", result.username());
        data.put("name", result.name());
        data.put("password", result.password());
        data.put("defaultPassword", result.defaultPassword());
        data.put("mobile", result.mobile());
        data.put("email", result.email());
        data.put("generatedUsername", result.generatedUsername());
        data.put("notices", result.notices());
        return data;
    }

    private Map<String, Object> toConfirmView(RegisterUserSkillService.ConfirmResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("userId", result.userId());
        data.put("username", result.username());
        data.put("name", result.name());
        data.put("roleCode", result.roleCode());
        data.put("defaultPassword", result.defaultPassword());
        data.put("mobile", result.mobile());
        data.put("email", result.email());
        data.put("platformUrl", result.platformUrl());
        data.put("message", result.message());
        return data;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", false);
        data.put("message", StringUtils.hasText(message) ? message.trim() : "操作失败");
        return data;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
