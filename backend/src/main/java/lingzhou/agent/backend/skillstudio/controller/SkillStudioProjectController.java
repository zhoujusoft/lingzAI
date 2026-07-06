package lingzhou.agent.backend.skillstudio.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectChatService;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectPreviewService;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectService;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService;
import org.springframework.http.MediaType;
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
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/skillstudio/projects")
public class SkillStudioProjectController {

    private final SkillStudioProjectService projectService;
    private final SkillStudioProjectChatService projectChatService;
    private final SkillStudioProjectPreviewService projectPreviewService;
    private final SkillStudioProjectSettingsService projectSettingsService;

    public SkillStudioProjectController(
            SkillStudioProjectService projectService,
            SkillStudioProjectChatService projectChatService,
            SkillStudioProjectPreviewService projectPreviewService,
            SkillStudioProjectSettingsService projectSettingsService) {
        this.projectService = projectService;
        this.projectChatService = projectChatService;
        this.projectPreviewService = projectPreviewService;
        this.projectSettingsService = projectSettingsService;
    }

    @GetMapping
    public SkillStudioProjectService.ProjectPageResult listProjects(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "projectType", required = false) String projectType,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        return projectService.listProjects(resolveUserId(request), page, pageSize, keyword, projectType, status);
    }

    @PostMapping
    public SkillStudioProjectService.ProjectDetail createProject(
            @RequestBody SkillStudioProjectService.CreateProjectRequest request, HttpServletRequest httpRequest)
            throws TaskException {
        return projectService.createProject(resolveUserId(httpRequest), request);
    }

    @GetMapping("/{projectId}")
    public SkillStudioProjectService.ProjectDetail getProject(
            @PathVariable("projectId") Long projectId, HttpServletRequest request) throws TaskException {
        return projectService.getProject(resolveUserId(request), projectId);
    }

    @GetMapping("/{projectId}/settings")
    public SkillStudioProjectSettingsService.ProjectSettingsView getProjectSettings(
            @PathVariable("projectId") Long projectId, HttpServletRequest request) throws TaskException {
        return projectSettingsService.getSettings(resolveUserId(request), projectId);
    }

    @PutMapping("/{projectId}")
    public SkillStudioProjectService.ProjectDetail updateProject(
            @PathVariable("projectId") Long projectId,
            @RequestBody SkillStudioProjectService.UpdateProjectRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return projectService.updateProject(resolveUserId(httpRequest), projectId, request);
    }

    @PutMapping("/{projectId}/settings")
    public SkillStudioProjectSettingsService.ProjectSettingsView updateProjectSettings(
            @PathVariable("projectId") Long projectId,
            @RequestBody SkillStudioProjectSettingsService.UpdateProjectSettingsRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return projectSettingsService.updateSettings(resolveUserId(httpRequest), projectId, request);
    }

    @PostMapping("/{projectId}/publish")
    public SkillStudioProjectService.ProjectDetail publishProject(
            @PathVariable("projectId") Long projectId, HttpServletRequest request) throws TaskException {
        return projectService.publishProject(resolveUserId(request), projectId);
    }

    @DeleteMapping("/{projectId}")
    public SkillStudioProjectService.DeleteProjectResult deleteProject(
            @PathVariable("projectId") Long projectId, HttpServletRequest request) throws TaskException {
        return projectService.deleteProject(resolveUserId(request), projectId);
    }

    @GetMapping("/{projectId}/sessions")
    public List<ChatSessionVo> listProjectSessions(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
            HttpServletRequest request)
            throws TaskException {
        return projectService.listProjectSessions(resolveUserId(request), projectId, limit == null ? 20 : limit);
    }

    @GetMapping("/{projectId}/sessions/{sessionId}/messages")
    public List<ChatMessageVo> listProjectMessages(
            @PathVariable("projectId") Long projectId,
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "100") Integer pageSize,
            HttpServletRequest request)
            throws TaskException {
        return projectService.listProjectMessages(
                resolveUserId(request),
                projectId,
                sessionId,
                pageNo == null ? 1 : pageNo,
                pageSize == null ? 100 : pageSize);
    }

    @GetMapping("/{projectId}/files")
    public List<SkillStudioProjectService.ProjectFileNode> listProjectFiles(
            @PathVariable("projectId") Long projectId, HttpServletRequest request) throws TaskException {
        return projectService.listProjectFiles(resolveUserId(request), projectId);
    }

    @GetMapping("/{projectId}/files/content")
    public SkillStudioProjectService.ProjectFileContent getProjectFileContent(
            @PathVariable("projectId") Long projectId, @RequestParam("path") String path, HttpServletRequest request)
            throws TaskException {
        return projectService.getProjectFileContent(resolveUserId(request), projectId, path);
    }

    @PostMapping("/{projectId}/chat")
    public SkillStudioProjectChatService.ProjectChatReply sendProjectMessage(
            @PathVariable("projectId") Long projectId,
            @RequestBody SkillStudioProjectChatService.ProjectChatCommand request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return projectChatService.sendMessage(resolveUserId(httpRequest), projectId, request);
    }

    @PostMapping(value = "/{projectId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamProjectMessage(
            @PathVariable("projectId") Long projectId,
            @RequestBody SkillStudioProjectChatService.ProjectChatCommand request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return projectChatService.streamMessage(resolveUserId(httpRequest), projectId, request);
    }

    @PostMapping(value = "/{projectId}/preview/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamProjectPreviewRun(
            @PathVariable("projectId") Long projectId,
            @RequestBody(required = false) SkillStudioProjectPreviewService.PreviewRunCommand request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return projectPreviewService.streamPreviewRun(resolveUserId(httpRequest), projectId, request);
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
