package lingzhou.agent.backend.common.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public GlobalResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (selectedContentType != null && MediaType.TEXT_EVENT_STREAM.includes(selectedContentType)) {
            return body;
        }
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        if (body instanceof Resource || body instanceof byte[]) {
            return body;
        }
        ApiResponse<Object> wrapped;
        int statusCode = 200;
        if (response instanceof ServletServerHttpResponse servletResponse) {
            statusCode = servletResponse.getServletResponse().getStatus();
        }
        if (statusCode >= 400) {
            if (body == null) {
                wrapped = ApiResponse.fail(statusCode, defaultMessage(statusCode));
            } else if (body instanceof String) {
                wrapped = ApiResponse.fail(statusCode, body.toString());
            } else {
                wrapped = ApiResponse.fail(statusCode, defaultMessage(statusCode), body);
            }
        } else {
            wrapped = ApiResponse.success(body);
        }
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (JsonProcessingException e) {
                return "{\"code\":500,\"message\":\"serialization error\",\"data\":null,\"requestId\":\""
                        + RequestIdContext.getOrEmpty()
                        + "\",\"timestamp\":0}";
            }
        }
        return wrapped;
    }

    private static String defaultMessage(int statusCode) {
        if (statusCode == 400) {
            return "Bad Request";
        }
        if (statusCode == 401) {
            return "Unauthorized";
        }
        if (statusCode == 403) {
            return "Forbidden";
        }
        if (statusCode == 404) {
            return "Not Found";
        }
        if (statusCode >= 500) {
            return "Internal Server Error";
        }
        return "Request Failed";
    }
}
