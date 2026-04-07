package lingzhou.agent.backend.business;

import lingzhou.agent.backend.common.enums.DeviceType;
import lingzhou.agent.backend.common.utils.ServletUtil;
import lingzhou.agent.backend.framework.authentication.JwtUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin
public class BaseController {

    @Autowired
    public JwtUtils jwtUtils;

    public String getBrowserKey() {
        String browserKey = ServletUtil.getHeader("BrowserKey");
        if (StringUtils.isEmpty(browserKey)) {
            browserKey = ServletUtil.getAttributes("BrowserKey");
        }
        return StringUtils.isEmpty(browserKey) ? "" : browserKey;
    }

    public String getUserId() {
        String userId = ServletUtil.getHeader("UserId");
        if (StringUtils.isEmpty(userId)) {
            userId = ServletUtil.getAttributes("UserId");
        }
        return StringUtils.isEmpty(userId) ? "" : userId;
    }

    //    /// <summary>
    //    /// 租户code
    //    /// </summary>
    //    public String getTenantCode() throws Exception {
    //        String tnCode = ServletUtil.getHeader("TnCode");
    //        return StringUtils.isEmpty(tnCode) ? "00000000" : tnCode;
    //    }

    public DeviceType getDeviceType() {
        String deviceType = ServletUtil.getHeader("AuthType");
        return !StringUtils.isEmpty(deviceType) ? DeviceType.forValue(Integer.parseInt(deviceType)) : DeviceType.Web;
    }

    public String getAcount() {
        // 获取用户凭证
        String token = ServletUtil.getHeader(jwtUtils.getHeader());
        if (StringUtils.isBlank(token)) {
            token = ServletUtil.getParameter(jwtUtils.getHeader());
        }

        if (StringUtils.isNotEmpty(token) && token.startsWith("Bearer")) {
            token = token.replaceAll("Bearer", "").trim();
        }

        if (StringUtils.isEmpty(token)) {
            return null;
        }
        token = token.replaceAll("Bearer", "").trim();
        return jwtUtils.getCodeByToken(token);
    }
}
