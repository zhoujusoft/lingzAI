package lingzhou.agent.backend.business.skill.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.skill.service.SkillPackageService;
import lingzhou.agent.backend.business.skill.service.SkillPublishService;
import lingzhou.agent.backend.business.skill.service.SkillPythonEnvAdminService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/skills")
public class SkillManagementController {

    private final SkillCatalogService skillCatalogService;
    private final ChatConversationService chatConversationService;
    private final SkillPackageService skillPackageService;
    private final SkillPublishService skillPublishService;
    private final SkillPythonEnvAdminService skillPythonEnvAdminService;

    public SkillManagementController(
            SkillCatalogService skillCatalogService,
            ChatConversationService chatConversationService,
            SkillPackageService skillPackageService,
            SkillPublishService skillPublishService,
            SkillPythonEnvAdminService skillPythonEnvAdminService) {
        this.skillCatalogService = skillCatalogService;
        this.chatConversationService = chatConversationService;
        this.skillPackageService = skillPackageService;
        this.skillPublishService = skillPublishService;
        this.skillPythonEnvAdminService = skillPythonEnvAdminService;
    }

    @GetMapping("/catalog")
    public List<SkillCatalogService.SkillCatalogView> listCatalogs(
            @RequestParam(value = "visibleOnly", required = false, defaultValue = "false") boolean visibleOnly,
            HttpServletRequest request) {
        Long userId = chatConversationService.resolveUserId(request);
        return skillCatalogService.listCatalogs(userId, visibleOnly);
    }

    @GetMapping("/tools")
    public List<SkillCatalogService.ToolLibraryItem> listToolLibrary(HttpServletRequest request) {
        Long userId = chatConversationService.resolveUserId(request);
        return skillCatalogService.listToolLibrary(userId);
    }

    @PutMapping("/tools/{toolName}")
    public SkillCatalogService.ToolLibraryItem updateToolGlobalAvailability(
            @PathVariable("toolName") String toolName,
            @RequestBody ToolGlobalAvailabilityUpdateRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        ToolGlobalAvailabilityUpdateRequest payload =
                request == null ? new ToolGlobalAvailabilityUpdateRequest(null) : request;
        Long userId = chatConversationService.resolveUserId(httpRequest);
        return skillCatalogService.updateToolGlobalAvailability(toolName, payload.enabledGlobal(), userId);
    }

    @PutMapping("/tools/batch-global")
    public List<SkillCatalogService.ToolLibraryItem> batchUpdateToolGlobalAvailability(
            @RequestBody ToolBatchGlobalAvailabilityUpdateRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        ToolBatchGlobalAvailabilityUpdateRequest payload =
                request == null
                        ? new ToolBatchGlobalAvailabilityUpdateRequest(List.of(), null)
                        : request;
        Long userId = chatConversationService.resolveUserId(httpRequest);
        return skillCatalogService.batchUpdateToolGlobalAvailability(
                payload.toolNames(), payload.enabledGlobal(), userId);
    }

    @PutMapping("/catalog/{skillId}")
    public SkillCatalogService.SkillCatalogView updateCatalog(
            @PathVariable("skillId") Long skillId, @RequestBody SkillCatalogUpdateRequest request)
            throws TaskException {
        return skillCatalogService.updateCatalog(
                skillId,
                new SkillCatalogService.SkillCatalogUpdateCommand(
                        request.displayName(),
                        request.description(),
                        request.category(),
                        request.sortOrder(),
                        request.visible(),
                        request.icon(),
                        request.iconColor()));
    }

    @PutMapping("/catalog/{skillId}/bindings")
    public Map<String, Object> updateBindings(
            @PathVariable("skillId") Long skillId,
            @RequestBody SkillBindingUpdateRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = chatConversationService.resolveUserId(httpRequest);
        List<String> toolNames = skillCatalogService.updateBindings(skillId, request.toolNames(), userId);
        return Map.of("skillId", skillId, "toolNames", toolNames);
    }

    @PostMapping("/catalog/{skillId}/bindings/refresh")
    public SkillPackageService.BindingRefreshResult refreshBindings(@PathVariable("skillId") Long skillId)
            throws TaskException {
        return skillPackageService.refreshToolBindings(skillId);
    }

    @GetMapping("/catalog/{skillId}/runtime/python-env")
    public ApiResponse<SkillPythonEnvAdminService.SkillPythonEnvView> getPythonEnvStatus(
            @PathVariable("skillId") Long skillId) throws TaskException {
        return ApiResponse.success(skillPythonEnvAdminService.getEnvStatus(skillId));
    }

    @PostMapping("/catalog/{skillId}/runtime/python-env/rebuild")
    public ApiResponse<SkillPythonEnvAdminService.SkillPythonEnvView> rebuildPythonEnv(
            @PathVariable("skillId") Long skillId) throws TaskException {
        return ApiResponse.success(skillPythonEnvAdminService.rebuild(skillId));
    }

    @GetMapping("/catalog/{skillId}/publish")
    public SkillPublishService.PublishStatusView getPublishStatus(@PathVariable("skillId") Long skillId)
            throws TaskException {
        return skillPublishService.getPublishStatus(skillId);
    }

    @PutMapping("/catalog/{skillId}/publish")
    public SkillPublishService.PublishStatusView publish(
            @PathVariable("skillId") Long skillId, @RequestBody(required = false) SkillPublishUpdateRequest request)
            throws TaskException {
        SkillPublishUpdateRequest payload = request == null ? new SkillPublishUpdateRequest(null, null) : request;
        return skillPublishService.publish(skillId, payload.appName(), payload.appDescription());
    }

    @PutMapping("/catalog/{skillId}/publish/info")
    public SkillPublishService.PublishStatusView updatePublishInfo(
            @PathVariable("skillId") Long skillId, @RequestBody(required = false) SkillPublishUpdateRequest request)
            throws TaskException {
        SkillPublishUpdateRequest payload = request == null ? new SkillPublishUpdateRequest(null, null) : request;
        return skillPublishService.updatePublishedInfo(skillId, payload.appName(), payload.appDescription());
    }

    @PostMapping("/catalog/{skillId}/publish/regenerate")
    public SkillPublishService.PublishStatusView regeneratePublishCode(@PathVariable("skillId") Long skillId)
            throws TaskException {
        return skillPublishService.regenerateAppCode(skillId);
    }

    @PostMapping("/catalog/{skillId}/publish/disable")
    public SkillPublishService.PublishStatusView disablePublish(@PathVariable("skillId") Long skillId)
            throws TaskException {
        return skillPublishService.disable(skillId);
    }

    @GetMapping("/catalog/{skillId}/package/export")
    public ResponseEntity<byte[]> exportSkillPackage(@PathVariable("skillId") Long skillId, HttpServletRequest request)
            throws TaskException {
        Long userId = chatConversationService.resolveUserId(request);
        SkillPackageService.ExportedPackage exportedPackage = skillPackageService.exportSkillPackage(skillId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportedPackage.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(exportedPackage.content());
    }

    @DeleteMapping("/catalog/{skillId}")
    public SkillPackageService.DeleteSkillResult deleteSkill(
            @PathVariable("skillId") Long skillId, HttpServletRequest request) throws TaskException {
        Long userId = chatConversationService.resolveUserId(request);
        return skillPackageService.deleteSkill(skillId, userId);
    }

    @PostMapping(value = "/packages/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillPackageService.PreviewResult previewImport(@RequestParam("file") MultipartFile file)
            throws TaskException {
        return skillPackageService.previewImport(file);
    }

    @PostMapping(value = "/packages/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillPackageService.ImportResult confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confirmDowngrade", required = false, defaultValue = "false")
                    boolean confirmDowngrade,
            HttpServletRequest request)
            throws TaskException {
        Long userId = chatConversationService.resolveUserId(request);
        return skillPackageService.confirmImport(file, confirmDowngrade, userId);
    }

    @PostMapping("/packages/refresh")
    public SkillPackageService.RefreshResult refreshPackages() {
        return skillPackageService.refreshSkillRuntime();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestBody(required = false) ChatConversationService.SkillChatRequest request,
            HttpServletRequest httpRequest) {
        Long userId = chatConversationService.resolveUserId(httpRequest);
        return chatConversationService.streamSkill(request, userId);
    }

    public record SkillCatalogUpdateRequest(
            String displayName,
            String description,
            String category,
            Integer sortOrder,
            Boolean visible,
            String icon,
            String iconColor) {}

    public record SkillBindingUpdateRequest(List<String> toolNames) {}

    public record ToolGlobalAvailabilityUpdateRequest(Boolean enabledGlobal) {}

    public record ToolBatchGlobalAvailabilityUpdateRequest(
            List<String> toolNames, Boolean enabledGlobal) {}

    public record SkillPublishUpdateRequest(String appName, String appDescription) {}
}
