package lingzhou.agent.backend.business.license.service;

import java.security.SecureRandom;
import lingzhou.agent.backend.app.LicenseProperties;
import lingzhou.agent.backend.business.system.dao.SystemConfigMapper;
import lingzhou.agent.backend.business.system.model.SystemConfigModel;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class LicenseInstanceService {

    private static final String CONFIG_KEY = "service_license_instance";
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final SystemConfigMapper systemConfigMapper;
    private final LicenseProperties licenseProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public LicenseInstanceService(SystemConfigMapper systemConfigMapper, LicenseProperties licenseProperties) {
        this.systemConfigMapper = systemConfigMapper;
        this.licenseProperties = licenseProperties;
    }

    public synchronized String resolveInstanceCode() {
        if (StringUtils.isNotBlank(licenseProperties.getInstanceCode())) {
            return licenseProperties.getInstanceCode().trim();
        }
        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CONFIG_KEY);
        if (config != null && StringUtils.isNotBlank(config.getConfigValue())) {
            return config.getConfigValue().trim();
        }
        String generated = generateInstanceCode();
        SystemConfigModel entity = new SystemConfigModel();
        entity.setConfigKey(CONFIG_KEY);
        entity.setConfigValue(generated);
        entity.setStatus(1);
        systemConfigMapper.insert(entity);
        return generated;
    }

    private String generateInstanceCode() {
        StringBuilder builder = new StringBuilder("lz-");
        for (int i = 0; i < 10; i++) {
            builder.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
}
