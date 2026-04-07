package lingzhou.agent.backend.framework.authentication.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.framework.authentication.JwtUtils;
import lingzhou.agent.backend.framework.authentication.TokenBlacklistService;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthTokenInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public AuthTokenInterceptor(
            JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            if (handlerMethod.hasMethodAnnotation(NotLogin.class)) {
                return true;
            }
        }

        String token = request.getHeader(jwtUtils.getHeader());
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(jwtUtils.getHeader());
        }

        token = normalizeToken(token);
        if (StringUtils.isBlank(token)) {
            writeUnauthorized(response, "token is required");
            return false;
        }
        if (tokenBlacklistService.isBlacklisted(token)) {
            writeUnauthorized(response, "token is invalidated");
            return false;
        }

        try {
            String userCode = jwtUtils.getCodeByToken(token);
            String userId = jwtUtils.getUserIdByToken(token);
            if (StringUtils.isBlank(userCode) || StringUtils.isBlank(userId)) {
                writeUnauthorized(response, "invalid token");
                return false;
            }
            if (!jwtUtils.isAccessToken(token)) {
                writeUnauthorized(response, "access token is required");
                return false;
            }
            request.setAttribute("UserId", userId);
            request.setAttribute("UserCode", userCode);
            return true;
        } catch (Exception ex) {
            writeUnauthorized(response, "token validate failed");
            return false;
        }
    }

    private static String normalizeToken(String token) {
        if (StringUtils.isBlank(token)) {
            return token;
        }
        String value = token.trim();
        if (value.startsWith("Bearer ")) {
            value = value.substring("Bearer ".length()).trim();
        }
        return value;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.resetBuffer();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(401, message)));
        response.flushBuffer();
    }
}
