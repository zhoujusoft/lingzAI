package lingzhou.agent.backend.framework.authentication.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lingzhou.agent.backend.business.license.service.LicenseException;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.framework.authentication.annotation.BypassLicense;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LicenseAccessInterceptor implements HandlerInterceptor {

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    public LicenseAccessInterceptor(LicenseService licenseService, ObjectMapper objectMapper) {
        this.licenseService = licenseService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !licenseService.isEnabled()) {
            return true;
        }
        if (handler instanceof HandlerMethod handlerMethod
                && (handlerMethod.hasMethodAnnotation(NotLogin.class)
                        || handlerMethod.hasMethodAnnotation(BypassLicense.class)
                        || handlerMethod.getBeanType().isAnnotationPresent(BypassLicense.class))) {
            return true;
        }
        try {
            licenseService.assertSystemAccessible();
            return true;
        } catch (LicenseException ex) {
            writeForbidden(response, ex.getCode(), ex.getMessage());
            return false;
        }
    }

    private void writeForbidden(HttpServletResponse response, int code, String message) throws Exception {
        response.resetBuffer();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
        response.flushBuffer();
    }
}
