package lingzhou.agent.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Pattern UUID_V7_LOWER_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        String resolved = resolveRequestId(inbound);

        RequestIdContext.set(resolved);
        request.setAttribute("RequestId", resolved);
        response.setHeader(REQUEST_ID_HEADER, resolved);

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdContext.clear();
        }
    }

    private String resolveRequestId(String inbound) {
        if (!StringUtils.hasText(inbound)) {
            return UuidV7Generator.next();
        }

        String normalized = inbound.strip();
        if (isValidUuidV7(normalized)) {
            return normalized;
        }

        String regenerated = UuidV7Generator.next();
        log.warn(
                "Invalid gateway requestId regenerated: masked_request_id={}, regenerated_request_id={}",
                maskRequestId(normalized),
                regenerated);
        return regenerated;
    }

    private boolean isValidUuidV7(String requestId) {
        return StringUtils.hasText(requestId) && UUID_V7_LOWER_PATTERN.matcher(requestId).matches();
    }

    private String maskRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return "***";
        }
        String value = requestId.strip();
        if (value.length() < 8) {
            return "***";
        }
        int middle = value.length() - 8;
        return value.substring(0, 4) + "*".repeat(middle) + value.substring(value.length() - 4);
    }
}
