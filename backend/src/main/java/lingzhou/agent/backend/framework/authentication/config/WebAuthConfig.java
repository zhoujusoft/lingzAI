package lingzhou.agent.backend.framework.authentication.config;

import lingzhou.agent.backend.framework.authentication.interceptor.AuthTokenInterceptor;
import lingzhou.agent.backend.framework.authentication.interceptor.LicenseAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebAuthConfig implements WebMvcConfigurer {

    private final AuthTokenInterceptor authTokenInterceptor;
    private final LicenseAccessInterceptor licenseAccessInterceptor;

    public WebAuthConfig(
            AuthTokenInterceptor authTokenInterceptor, LicenseAccessInterceptor licenseAccessInterceptor) {
        this.authTokenInterceptor = authTokenInterceptor;
        this.licenseAccessInterceptor = licenseAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authTokenInterceptor).addPathPatterns("/**");
        registry.addInterceptor(licenseAccessInterceptor).addPathPatterns("/**");
    }
}
