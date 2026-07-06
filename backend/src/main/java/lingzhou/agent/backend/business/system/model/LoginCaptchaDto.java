package lingzhou.agent.backend.business.system.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginCaptchaDto {

    private String captchaKey;

    private String imageData;

    private long expiresInSeconds;
}
