package lingzhou.agent.backend.common.login;

import lombok.Data;

@Data
public class GetOrganizationListInput {
    /// <summary>
    /// 账号
    /// </summary>
    private String code;

    /// <summary>
    /// 密码
    /// </summary>
    private String password;

    /// <summary>
    /// 是否记住我（影响 refreshToken 过期时间）
    /// </summary>
    private boolean rememberMe;
}
