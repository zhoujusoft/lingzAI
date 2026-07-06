package lingzhou.agent.backend.business.skill.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.skill.service.McpServerService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/mcp/servers")
public class McpServerManagementController {

    private final McpServerService mcpServerService;

    public McpServerManagementController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping
    public McpServerService.McpServerPageResult listServers(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        return mcpServerService.listServers(page, pageSize, keyword, resolveUserId(request));
    }

    @GetMapping("/{serverId}")
    public McpServerService.McpServerView getServer(@PathVariable("serverId") Long serverId, HttpServletRequest request)
            throws TaskException {
        return mcpServerService.getServer(serverId, resolveUserId(request));
    }

    @PostMapping
    public McpServerService.McpServerView createServer(
            @RequestBody CreateMcpServerRequest request, HttpServletRequest httpRequest) throws TaskException {
        return mcpServerService.createServer(
                new McpServerService.CreateCommand(
                        request.serverKey(),
                        request.displayName(),
                        request.description(),
                        request.permissionScope(),
                        request.serverScope(),
                        request.transportType(),
                        request.endpoint(),
                        request.authType(),
                        request.authConfigJson(),
                        request.headersJson(),
                        request.enabled()),
                resolveUserId(httpRequest));
    }

    @PutMapping("/{serverId}")
    public McpServerService.McpServerView updateServer(
            @PathVariable("serverId") Long serverId,
            @RequestBody UpdateMcpServerRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return mcpServerService.updateServer(
                serverId,
                new McpServerService.UpdateCommand(
                        request.displayName(),
                        request.description(),
                        request.permissionScope(),
                        request.serverScope(),
                        request.transportType(),
                        request.endpoint(),
                        request.authType(),
                        request.authConfigJson(),
                        request.headersJson(),
                        request.enabled()),
                resolveUserId(httpRequest));
    }

    @PostMapping("/{serverId}/refresh")
    public McpServerService.RefreshResult refreshServer(
            @PathVariable("serverId") Long serverId, HttpServletRequest request) throws TaskException {
        return mcpServerService.refreshServer(serverId, resolveUserId(request));
    }

    @DeleteMapping("/{serverId}")
    public void deleteServer(@PathVariable("serverId") Long serverId, HttpServletRequest request) throws TaskException {
        mcpServerService.deleteServer(serverId, resolveUserId(request));
    }

    public record CreateMcpServerRequest(
            String serverKey,
            String displayName,
            String description,
            Integer permissionScope,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            String headersJson,
            Boolean enabled) {}

    public record UpdateMcpServerRequest(
            String displayName,
            String description,
            Integer permissionScope,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            String headersJson,
            Boolean enabled) {}

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
