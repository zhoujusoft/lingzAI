package lingzhou.agent.backend.business.license.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LicenseBootstrapService {

    private final LicenseService licenseService;

    public LicenseBootstrapService(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultLicense() {
        try {
            boolean initialized = licenseService.initializeDefaultLicenseIfAbsent();
            if (initialized) {
                log.info("默认 license 初始化完成");
            }
        } catch (Exception ex) {
            log.error("license 启动初始化失败: {}", ex.getMessage(), ex);
        }
    }
}
