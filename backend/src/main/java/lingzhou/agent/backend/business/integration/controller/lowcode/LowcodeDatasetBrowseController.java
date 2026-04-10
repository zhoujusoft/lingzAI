package lingzhou.agent.backend.business.integration.controller.lowcode;

import java.util.List;
import lingzhou.agent.backend.business.integration.service.lowcode.LowcodeDatasetBrowseService;
import lingzhou.agent.backend.business.skill.service.LowcodeApiBrowseService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration/lowcode")
public class LowcodeDatasetBrowseController {

    private final LowcodeDatasetBrowseService lowcodeDatasetBrowseService;

    public LowcodeDatasetBrowseController(LowcodeDatasetBrowseService lowcodeDatasetBrowseService) {
        this.lowcodeDatasetBrowseService = lowcodeDatasetBrowseService;
    }

    @GetMapping("/platforms")
    public List<LowcodeApiBrowseService.PlatformOption> listPlatforms() {
        return lowcodeDatasetBrowseService.listPlatforms();
    }

    @GetMapping("/platforms/{platformKey}/apps")
    public List<LowcodeApiBrowseService.AppView> listApps(@PathVariable("platformKey") String platformKey)
            throws TaskException {
        return lowcodeDatasetBrowseService.listApps(platformKey);
    }

    @GetMapping("/platforms/{platformKey}/objects")
    public List<LowcodeDatasetBrowseService.ObjectView> listObjects(
            @PathVariable("platformKey") String platformKey, @RequestParam("appId") String appId) throws TaskException {
        return lowcodeDatasetBrowseService.listObjects(platformKey, appId);
    }

    @GetMapping("/platforms/{platformKey}/fields")
    public List<LowcodeDatasetBrowseService.FieldView> listFields(
            @PathVariable("platformKey") String platformKey,
            @RequestParam("appId") String appId,
            @RequestParam("objectCode") String objectCode,
            @RequestParam(value = "formCode", required = false) String formCode)
            throws TaskException {
        return lowcodeDatasetBrowseService.listFields(platformKey, appId, objectCode, formCode);
    }

    @GetMapping("/platforms/{platformKey}/relations")
    public List<LowcodeDatasetBrowseService.RelationView> listRelations(
            @PathVariable("platformKey") String platformKey,
            @RequestParam("appId") String appId,
            @RequestParam(value = "objectCodes", required = false) List<String> objectCodes)
            throws TaskException {
        return lowcodeDatasetBrowseService.listRelations(platformKey, appId, objectCodes);
    }
}
