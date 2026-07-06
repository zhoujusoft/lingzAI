package lingzhou.agent.backend.business.system.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import lingzhou.agent.backend.business.system.model.LoginCaptchaDto;
import lingzhou.agent.backend.common.constants.Constants;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class LoginCaptchaService {

    private static final int CAPTCHA_LENGTH = 4;
    private static final int IMAGE_WIDTH = 132;
    private static final int IMAGE_HEIGHT = 44;
    private static final int NOISE_LINE_COUNT = 6;
    private static final char[] CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final LoginCaptchaStore loginCaptchaStore;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginCaptchaService(LoginCaptchaStore loginCaptchaStore) {
        this.loginCaptchaStore = loginCaptchaStore;
    }

    public LoginCaptchaDto createCaptcha() {
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String captchaCode = generateCaptchaCode();
        Duration ttl = Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION);
        loginCaptchaStore.save(captchaKey, captchaCode, ttl);
        return LoginCaptchaDto.builder()
                .captchaKey(captchaKey)
                .imageData(renderCaptchaImageData(captchaCode))
                .expiresInSeconds(ttl.toSeconds())
                .build();
    }

    public String validateCaptcha(String captchaKey, String captchaCode) {
        if (StringUtils.isBlank(captchaCode)) {
            return "请输入验证码";
        }
        if (StringUtils.isBlank(captchaKey)) {
            return "验证码已过期，请刷新后重试";
        }
        String storedCode = loginCaptchaStore.get(captchaKey);
        if (StringUtils.isBlank(storedCode)) {
            return "验证码已过期，请刷新后重试";
        }
        loginCaptchaStore.delete(captchaKey);
        if (!StringUtils.equalsIgnoreCase(StringUtils.trim(captchaCode), storedCode)) {
            return "验证码错误，请重试";
        }
        return null;
    }

    private String generateCaptchaCode() {
        StringBuilder builder = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            builder.append(CAPTCHA_CHARS[secureRandom.nextInt(CAPTCHA_CHARS.length)]);
        }
        return builder.toString();
    }

    private String renderCaptchaImageData(String captchaCode) {
        StringBuilder svgBuilder = new StringBuilder();
        svgBuilder
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(IMAGE_WIDTH)
                .append("\" height=\"")
                .append(IMAGE_HEIGHT)
                .append("\" viewBox=\"0 0 ")
                .append(IMAGE_WIDTH)
                .append(" ")
                .append(IMAGE_HEIGHT)
                .append("\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#f8fafc\" rx=\"8\" ry=\"8\"/>")
                .append("<rect x=\"0.5\" y=\"0.5\" width=\"")
                .append(IMAGE_WIDTH - 1)
                .append("\" height=\"")
                .append(IMAGE_HEIGHT - 1)
                .append("\" fill=\"none\" stroke=\"#e2e8f0\" rx=\"8\" ry=\"8\"/>");

        for (int i = 0; i < NOISE_LINE_COUNT; i++) {
            svgBuilder
                    .append("<line x1=\"")
                    .append(secureRandom.nextInt(IMAGE_WIDTH))
                    .append("\" y1=\"")
                    .append(secureRandom.nextInt(IMAGE_HEIGHT))
                    .append("\" x2=\"")
                    .append(secureRandom.nextInt(IMAGE_WIDTH))
                    .append("\" y2=\"")
                    .append(secureRandom.nextInt(IMAGE_HEIGHT))
                    .append("\" stroke=\"")
                    .append(randomPastelColor())
                    .append("\" stroke-width=\"1.5\" stroke-linecap=\"round\" opacity=\"0.75\"/>");
        }

        for (int i = 0; i < captchaCode.length(); i++) {
            int x = 18 + i * 24;
            int y = 30 + secureRandom.nextInt(4);
            double rotate = (secureRandom.nextDouble() - 0.5D) * 24D;
            svgBuilder
                    .append("<text x=\"")
                    .append(x)
                    .append("\" y=\"")
                    .append(y)
                    .append("\" fill=\"")
                    .append(randomTextColor())
                    .append("\" font-size=\"26\" font-family=\"Arial, Helvetica, sans-serif\" font-weight=\"700\"")
                    .append(" transform=\"rotate(")
                    .append(formatDouble(rotate))
                    .append(" ")
                    .append(x)
                    .append(" ")
                    .append(y)
                    .append(")\">")
                    .append(captchaCode.charAt(i))
                    .append("</text>");
        }

        svgBuilder.append("</svg>");
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svgBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String randomPastelColor() {
        return toHexColor(
                160 + secureRandom.nextInt(60), 180 + secureRandom.nextInt(60), 200 + secureRandom.nextInt(55));
    }

    private String randomTextColor() {
        return toHexColor(30 + secureRandom.nextInt(80), 50 + secureRandom.nextInt(90), 80 + secureRandom.nextInt(90));
    }

    private String toHexColor(int red, int green, int blue) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", red, green, blue);
    }
}
