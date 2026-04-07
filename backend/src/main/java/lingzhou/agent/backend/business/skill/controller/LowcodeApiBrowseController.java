package lingzhou.agent.backend.business.skill.controller;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.skill.service.LowcodeApiBrowseService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/lowcode/catalog")
public class LowcodeApiBrowseController {

    private final LowcodeApiBrowseService lowcodeApiBrowseService;

    public LowcodeApiBrowseController(LowcodeApiBrowseService lowcodeApiBrowseService) {
        this.lowcodeApiBrowseService = lowcodeApiBrowseService;
    }

    @GetMapping("/platforms")
    public List<LowcodeApiBrowseService.PlatformOption> listPlatforms() {
        return lowcodeApiBrowseService.listPlatforms();
    }

    @GetMapping("/platforms/{platformKey}/apps")
    public List<LowcodeApiBrowseService.AppView> listApps(@PathVariable("platformKey") String platformKey)
            throws TaskException {
        return lowcodeApiBrowseService.listApps(platformKey);
    }

    @GetMapping("/platforms/{platformKey}/apis")
    public List<LowcodeApiBrowseService.ApiView> listApis(
            @PathVariable("platformKey") String platformKey, @RequestParam("appId") String appId) throws TaskException {
        return lowcodeApiBrowseService.listApis(platformKey, appId);
    }

    @PostMapping("/test-execute")
    public LowcodeApiBrowseService.TestExecuteResult testExecute(@RequestBody TestExecuteRequest request) throws TaskException {
        return lowcodeApiBrowseService.testExecute(request.platformKey(), request.apiCode(), request.arguments());
    }

    public record TestExecuteRequest(String platformKey, String apiCode, Map<String, Object> arguments) {}
}
