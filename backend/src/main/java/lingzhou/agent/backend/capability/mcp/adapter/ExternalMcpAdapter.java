package lingzhou.agent.backend.capability.mcp.adapter;

import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.lzException.TaskException;

public interface ExternalMcpAdapter {

    boolean supports(McpServer server);

    ExternalMcpSession openSession(McpServer server) throws TaskException;
}
