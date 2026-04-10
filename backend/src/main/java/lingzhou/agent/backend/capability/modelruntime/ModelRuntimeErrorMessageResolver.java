package lingzhou.agent.backend.capability.modelruntime;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class ModelRuntimeErrorMessageResolver {

    private ModelRuntimeErrorMessageResolver() {}

    public static String resolve(Throwable error) {
        if (error == null) {
            return "模型调用失败，请稍后重试";
        }
        if (error instanceof WebClientResponseException webClientResponseException) {
            return resolveHttpError(
                    webClientResponseException.getStatusCode().value(),
                    webClientResponseException.getResponseBodyAsString());
        }
        if (error instanceof RestClientResponseException restClientResponseException) {
            return resolveHttpError(
                    restClientResponseException.getStatusCode().value(),
                    restClientResponseException.getResponseBodyAsString());
        }

        Throwable root = mostSpecificCause(error);
        if (root instanceof UnknownHostException) {
            return "模型服务地址不可达，请检查 Base URL 是否填写正确。";
        }
        if (root instanceof ConnectException) {
            return "无法连接模型服务，请检查 Base URL、端口和服务状态。";
        }
        if (root instanceof SocketTimeoutException) {
            return "模型请求超时，请检查模型服务响应时间或超时配置。";
        }
        if (root instanceof SSLException) {
            return "模型服务 SSL 连接失败，请检查证书或 https 配置。";
        }

        String message = trimToEmpty(error.getMessage());
        if (message.contains("配置不完整")) {
            return "模型配置不完整，请检查 Base URL、模型名以及 API Key 配置。";
        }
        if (message.contains("Connection refused")) {
            return "无法连接模型服务，请检查 Base URL、端口和服务状态。";
        }
        if (message.toLowerCase().contains("timeout")) {
            return "模型请求超时，请检查模型服务响应时间或超时配置。";
        }
        return StringUtils.hasText(message) ? message : "模型调用失败，请稍后重试";
    }

    private static String resolveHttpError(int status, String responseBody) {
        String detail = shorten(trimToEmpty(responseBody), 160);
        if (status == 401) {
            return StringUtils.hasText(detail)
                    ? "模型服务认证失败（401），请检查 API Key 是否正确。详情：" + detail
                    : "模型服务认证失败（401），请检查 API Key 是否正确。";
        }
        if (status == 403) {
            return StringUtils.hasText(detail)
                    ? "模型服务拒绝访问（403），请检查鉴权配置或访问权限。详情：" + detail
                    : "模型服务拒绝访问（403），请检查鉴权配置或访问权限。";
        }
        if (status == 404) {
            return StringUtils.hasText(detail)
                    ? "模型接口不存在（404），请检查 Base URL 或请求路径配置。详情：" + detail
                    : "模型接口不存在（404），请检查 Base URL 或请求路径配置。";
        }
        if (status == 429) {
            return StringUtils.hasText(detail)
                    ? "模型服务限流（429），请稍后重试。详情：" + detail
                    : "模型服务限流（429），请稍后重试。";
        }
        if (status >= 500) {
            return StringUtils.hasText(detail)
                    ? "模型服务异常（HTTP " + status + "）。详情：" + detail
                    : "模型服务异常（HTTP " + status + "），请稍后重试。";
        }
        return StringUtils.hasText(detail)
                ? "模型请求失败（HTTP " + status + "）。详情：" + detail
                : "模型请求失败（HTTP " + status + "）。";
    }

    private static Throwable mostSpecificCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text) || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
