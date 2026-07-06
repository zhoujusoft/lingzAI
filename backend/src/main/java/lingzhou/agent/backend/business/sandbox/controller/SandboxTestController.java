package lingzhou.agent.backend.business.sandbox.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.capability.sandbox.DockerGuiSandboxService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sandbox-test")
public class SandboxTestController {

    private final DockerGuiSandboxService dockerGuiSandboxService;

    public SandboxTestController(DockerGuiSandboxService dockerGuiSandboxService) {
        this.dockerGuiSandboxService = dockerGuiSandboxService;
    }

    @PostMapping("/start")
    public DockerGuiSandboxService.SandboxSessionView start(
            @RequestBody(required = false) DockerGuiSandboxService.SandboxStartRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return dockerGuiSandboxService.start(resolveUserId(httpRequest), request);
    }

    @GetMapping("/{sessionId}")
    public DockerGuiSandboxService.SandboxSessionView getInfo(
            @PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest) throws TaskException {
        return dockerGuiSandboxService.getInfo(resolveUserId(httpRequest), sessionId);
    }

    @PostMapping("/{sessionId}/open-baidu")
    public DockerGuiSandboxService.SandboxSessionView openBaidu(
            @PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest) throws TaskException {
        return dockerGuiSandboxService.openBaidu(resolveUserId(httpRequest), sessionId);
    }

    @PostMapping("/{sessionId}/navigate")
    public DockerGuiSandboxService.SandboxSessionView navigate(
            @PathVariable("sessionId") String sessionId,
            @RequestBody DockerGuiSandboxService.SandboxNavigateRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return dockerGuiSandboxService.navigate(resolveUserId(httpRequest), sessionId, request);
    }

    @PostMapping("/{sessionId}/screenshot")
    public DockerGuiSandboxService.SandboxSessionView takeScreenshot(
            @PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest) throws TaskException {
        return dockerGuiSandboxService.takeScreenshot(resolveUserId(httpRequest), sessionId);
    }

    @PostMapping("/{sessionId}/snapshot")
    public DockerGuiSandboxService.SandboxSessionView snapshot(
            @PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest) throws TaskException {
        return dockerGuiSandboxService.snapshot(resolveUserId(httpRequest), sessionId);
    }

    @PostMapping("/{sessionId}/tool")
    public DockerGuiSandboxService.SandboxSessionView callTool(
            @PathVariable("sessionId") String sessionId,
            @RequestBody DockerGuiSandboxService.SandboxToolRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return dockerGuiSandboxService.callTool(resolveUserId(httpRequest), sessionId, request);
    }

    @PostMapping("/{sessionId}/stop")
    public DockerGuiSandboxService.SandboxSessionView stop(
            @PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest) throws TaskException {
        return dockerGuiSandboxService.stop(resolveUserId(httpRequest), sessionId);
    }

    private Long resolveUserId(HttpServletRequest request) throws TaskException {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN, ex);
        }
    }
}
