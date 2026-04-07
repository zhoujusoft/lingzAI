package lingzhou.agent.backend.business.skill.service;

import java.util.List;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.business.system.service.SystemConfigService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodePlatformConfigService {

    private final SystemConfigService systemConfigService;

    public LowcodePlatformConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public List<PlatformEndpointItem> listEnabledPlatforms() {
        return systemConfigService.getEnabledPlatformItems();
    }

    public PlatformEndpointItem requirePlatform(String platformKey) throws TaskException {
        if (!StringUtils.hasText(platformKey)) {
            throw new TaskException("平台 key 不能为空", TaskException.Code.UNKNOWN);
        }
        return systemConfigService.getEnabledPlatformByKey(platformKey.trim());
    }
}
