package lingzhou.agent.backend.business.skill.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.skill.service.SkillPackageService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
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

    public SkillManagementController(
            SkillCatalogService skillCatalogService,
            ChatConversationService chatConversationService,
            SkillPackageService skillPackageService) {
        this.skillCatalogService = skillCatalogService;
        this.chatConversationService = chatConversationService;
        this.skillPackageService = skillPackageService;
    }

    @GetMapping("/catalog")
    public List<SkillCatalogService.SkillCatalogView> listCatalogs(
            @RequestParam(value = "visibleOnly", required = false, defaultValue = "false") boolean visibleOnly,
            HttpServletRequest request) {
        Long userId = chatConversationService.resolveUserId(request);
        return skillCatalogService.listCatalogs(userId, visibleOnly);
    }

    @GetMapping("/tools")
    public List<SkillCatalogService.ToolLibraryItem> listToolLibrary() {
        return skillCatalogService.listToolLibrary();
    }

    @PutMapping("/catalog/{skillId}")
    public SkillCatalogService.SkillCatalogView updateCatalog(
            @PathVariable("skillId") Long skillId, @RequestBody SkillCatalogUpdateRequest request) throws TaskException {
        return skillCatalogService.updateCatalog(
                skillId,
                new SkillCatalogService.SkillCatalogUpdateCommand(
                        request.displayName(),
                        request.description(),
                        request.category(),
                        request.sortOrder(),
                        request.visible()));
    }

    @PutMapping("/catalog/{skillId}/bindings")
    public Map<String, Object> updateBindings(
            @PathVariable("skillId") Long skillId, @RequestBody SkillBindingUpdateRequest request) throws TaskException {
        List<String> toolNames = skillCatalogService.updateBindings(skillId, request.toolNames());
        return Map.of("skillId", skillId, "toolNames", toolNames);
    }

    @GetMapping("/catalog/{skillId}/package/export")
    public ResponseEntity<byte[]> exportSkillPackage(
            @PathVariable("skillId") Long skillId, HttpServletRequest request) throws TaskException {
        Long userId = chatConversationService.resolveUserId(request);
        SkillPackageService.ExportedPackage exportedPackage = skillPackageService.exportSkillPackage(skillId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportedPackage.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(exportedPackage.content());
    }

    @PostMapping(value = "/packages/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillPackageService.PreviewResult previewImport(@RequestParam("file") MultipartFile file) throws TaskException {
        return skillPackageService.previewImport(file);
    }

    @PostMapping(value = "/packages/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillPackageService.ImportResult confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confirmDowngrade", required = false, defaultValue = "false") boolean confirmDowngrade,
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
            String displayName, String description, String category, Integer sortOrder, Boolean visible) {}

    public record SkillBindingUpdateRequest(List<String> toolNames) {}
}
