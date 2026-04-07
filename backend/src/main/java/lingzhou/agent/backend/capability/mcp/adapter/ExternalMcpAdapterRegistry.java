package lingzhou.agent.backend.capability.mcp.adapter;

import java.util.List;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExternalMcpAdapterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ExternalMcpAdapterRegistry.class);

    private final List<ExternalMcpAdapter> adapters;

    public ExternalMcpAdapterRegistry(List<ExternalMcpAdapter> adapters) {
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    public ExternalMcpSession openSession(McpServer server) throws TaskException {
        logger.info(
                "Resolving external MCP adapter: serverKey={}, serverScope={}, transportType={}, adapterCount={}",
                server == null ? "" : server.getServerKey(),
                server == null ? "" : server.getServerScope(),
                server == null ? "" : server.getTransportType(),
                adapters.size());
        for (ExternalMcpAdapter adapter : adapters) {
            if (adapter == null) {
                continue;
            }
            boolean supported = adapter.supports(server);
            logger.info(
                    "External MCP adapter candidate: serverKey={}, adapter={}, supported={}",
                    server == null ? "" : server.getServerKey(),
                    adapter.getClass().getSimpleName(),
                    supported);
            if (supported) {
                return adapter.openSession(server);
            }
        }
        throw new TaskException("未找到匹配的外部 MCP 适配器", TaskException.Code.UNKNOWN);
    }
}
