package lingzhou.agent.backend.capability.mcp.adapter;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.common.lzException.TaskException;

public interface ExternalMcpSession extends AutoCloseable {

    List<McpSchema.Tool> listTools() throws TaskException;

    Object callTool(String remoteToolName, Map<String, Object> arguments) throws TaskException;

    @Override
    default void close() {}
}
