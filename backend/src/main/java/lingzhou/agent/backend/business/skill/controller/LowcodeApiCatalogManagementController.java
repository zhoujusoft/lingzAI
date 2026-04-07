package lingzhou.agent.backend.business.skill.controller;

import lingzhou.agent.backend.business.skill.service.LowcodeApiCatalogService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/lowcode/catalog")
public class LowcodeApiCatalogManagementController {

    private final LowcodeApiCatalogService lowcodeApiCatalogService;

    public LowcodeApiCatalogManagementController(LowcodeApiCatalogService lowcodeApiCatalogService) {
        this.lowcodeApiCatalogService = lowcodeApiCatalogService;
    }

    @PostMapping("/register")
    public LowcodeApiCatalogService.RegistrationView register(@RequestBody RegisterRequest request) throws TaskException {
        return lowcodeApiCatalogService.register(new LowcodeApiCatalogService.RegisterCommand(
                request.platformKey(),
                request.appId(),
                request.appName(),
                request.apiId(),
                request.apiCode(),
                request.apiName(),
                request.description(),
                request.toolName(),
                request.remoteSchema()));
    }

    @DeleteMapping("/register")
    public LowcodeApiCatalogService.UnregisterResult unregister(
            @RequestParam("platformKey") String platformKey, @RequestParam("apiCode") String apiCode)
            throws TaskException {
        return lowcodeApiCatalogService.unregister(platformKey, apiCode);
    }

    public record RegisterRequest(
            String platformKey,
            String appId,
            String appName,
            String apiId,
            String apiCode,
            String apiName,
            String description,
            String toolName,
            Object remoteSchema) {}
}
