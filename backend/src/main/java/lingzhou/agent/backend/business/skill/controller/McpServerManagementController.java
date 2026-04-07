package lingzhou.agent.backend.business.skill.controller;

import java.util.List;
import lingzhou.agent.backend.business.skill.service.McpServerService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/mcp/servers")
public class McpServerManagementController {

    private final McpServerService mcpServerService;

    public McpServerManagementController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping
    public List<McpServerService.McpServerView> listServers() {
        return mcpServerService.listServers();
    }

    @GetMapping("/{serverId}")
    public McpServerService.McpServerView getServer(@PathVariable("serverId") Long serverId) throws TaskException {
        return mcpServerService.getServer(serverId);
    }

    @PostMapping
    public McpServerService.McpServerView createServer(@RequestBody CreateMcpServerRequest request) throws TaskException {
        return mcpServerService.createServer(new McpServerService.CreateCommand(
                request.serverKey(),
                request.displayName(),
                request.description(),
                request.serverScope(),
                request.transportType(),
                request.endpoint(),
                request.authType(),
                request.authConfigJson(),
                request.enabled()));
    }

    @PutMapping("/{serverId}")
    public McpServerService.McpServerView updateServer(
            @PathVariable("serverId") Long serverId, @RequestBody UpdateMcpServerRequest request) throws TaskException {
        return mcpServerService.updateServer(serverId, new McpServerService.UpdateCommand(
                request.displayName(),
                request.description(),
                request.serverScope(),
                request.transportType(),
                request.endpoint(),
                request.authType(),
                request.authConfigJson(),
                request.enabled()));
    }

    @PostMapping("/{serverId}/refresh")
    public McpServerService.RefreshResult refreshServer(@PathVariable("serverId") Long serverId) throws TaskException {
        return mcpServerService.refreshServer(serverId);
    }

    @DeleteMapping("/{serverId}")
    public void deleteServer(@PathVariable("serverId") Long serverId) throws TaskException {
        mcpServerService.deleteServer(serverId);
    }

    public record CreateMcpServerRequest(
            String serverKey,
            String displayName,
            String description,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            Boolean enabled) {}

    public record UpdateMcpServerRequest(
            String displayName,
            String description,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            Boolean enabled) {}
}
