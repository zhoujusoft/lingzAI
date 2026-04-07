package lingzhou.agent.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LoginInfoState {
    /**
     * 正常
     */
    Normal(0),

    /**
     * 密码不正确
     */
    NoPass(1),

    /**
     * 用户被禁用
     */
    UserDisable(2),

    /**
     * 审核中
     */
    Audit(3),

    /**
     * 审核拒绝
     */
    Reject(4),

    /**
     * 租户被禁用
     */
    TenantDisable(5),

    /**
     * 租户被删除
     */
    TenantDeleted(6),

    /**
     * 加载中
     */
    Loading(7),

    /**
     * 非企业成员授权
     */
    UnAuthorized(8),

    /**
     * 账号不存在
     */
    UserNotExist(9),

    /**
     * 待激活
     */
    WaitActive(10),

    /**
     * 未指定
     */
    Unspecified(11),

    /**
     * 无法获取手机号
     */
    UnableGetMobile(12),
    /// <summary>
    /// 第三方登录校验失败
    /// </summary>
    ThirdPartyLogin(13),
    /// <summary>
    /// AD 登录失败
    /// </summary>
    ADAuthError(14),
    /// <summary>
    /// 账号已登录
    /// </summary>
    AccountLoggedIn(15);

    public static final int SIZE = Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, LoginInfoState> mappings;

    private static java.util.HashMap<Integer, LoginInfoState> getMappings() {
        if (mappings == null) {
            synchronized (LoginInfoState.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, LoginInfoState>();
                }
            }
        }

        return mappings;
    }

    private LoginInfoState(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    @JsonValue
    public int getValue() {
        return intValue;
    }

    @JsonCreator
    public static LoginInfoState forValue(int value) {
        return getMappings().get(value);
    }
}
