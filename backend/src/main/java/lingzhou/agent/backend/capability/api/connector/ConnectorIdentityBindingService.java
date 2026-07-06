package lingzhou.agent.backend.capability.api.connector;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ConnectorIdentityBindingService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public ConnectorIdentityBindingService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    public Long resolveCurrentUserId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute("UserId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? Long.valueOf(text) : null;
    }

    public ConnectorIdentityContext resolveCurrentIdentity() {
        HttpServletRequest request = currentRequest();
        String requestUserCode = request == null ? "" : textValue(request.getAttribute("UserCode"));
        Long requestUserId = resolveCurrentUserId();
        SysUserModel user = requestUserId == null ? null : sysUserMapper.selectById(requestUserId);
        if (user == null && StringUtils.hasText(requestUserCode)) {
            user = sysUserMapper.selectByCode(requestUserCode.trim());
        }
        SysRole role = user == null || user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());

        Map<String, Object> userVariables = new LinkedHashMap<>();
        userVariables.put("id", user != null ? user.getId() : requestUserId);
        userVariables.put("code", user != null ? user.getCode() : requestUserCode);
        userVariables.put("name", user == null ? "" : emptyIfNull(user.getName()));
        userVariables.put("mobile", user == null ? "" : emptyIfNull(user.getMobile()));
        userVariables.put("phone", user == null ? "" : emptyIfNull(user.getMobile()));
        userVariables.put("email", user == null ? "" : emptyIfNull(user.getEmail()));
        userVariables.put("gender", "");
        userVariables.put("userType", user == null ? null : user.getUserType());
        userVariables.put("state", user == null ? null : user.getState());
        userVariables.put("roleId", user == null ? null : user.getRoleId());

        Map<String, Object> roleVariables = new LinkedHashMap<>();
        roleVariables.put("id", role == null ? null : role.getId());
        roleVariables.put("code", role == null ? "" : emptyIfNull(role.getRoleCode()));
        roleVariables.put("name", role == null ? "" : emptyIfNull(role.getRoleName()));

        return new ConnectorIdentityContext(requestUserId, requestUserCode, user, role, userVariables, roleVariables);
    }

    public Map<String, Object> buildTemplateVariables(Map<String, Object> input) {
        ConnectorIdentityContext identity = resolveCurrentIdentity();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("input", input == null ? Map.of() : input);
        variables.put("user", identity.userVariables());
        variables.put("role", identity.roleVariables());
        return variables;
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String textValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    public record ConnectorIdentityContext(
            Long requestUserId,
            String requestUserCode,
            SysUserModel user,
            SysRole role,
            Map<String, Object> userVariables,
            Map<String, Object> roleVariables) {}
}
