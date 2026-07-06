package lingzhou.agent.backend.business.integration.controller.connector;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.integration.service.connector.IntegrationConnectorService;
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
@RequestMapping("/integration/connectors")
public class IntegrationConnectorController {

    private final IntegrationConnectorService integrationConnectorService;

    public IntegrationConnectorController(IntegrationConnectorService integrationConnectorService) {
        this.integrationConnectorService = integrationConnectorService;
    }

    @GetMapping
    public IntegrationConnectorService.ConnectorPageResult listConnectors(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        return integrationConnectorService.listConnectors(page, pageSize, keyword, status, resolveUserId(request));
    }

    @GetMapping("/{id}")
    public IntegrationConnectorService.ConnectorDetail getConnector(
            @PathVariable("id") Long id, HttpServletRequest request) throws TaskException {
        return integrationConnectorService.getConnector(id, resolveUserId(request));
    }

    @PostMapping
    public IntegrationConnectorService.ConnectorDetail createConnector(
            @RequestBody IntegrationConnectorService.CreateOrUpdateConnectorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.createConnector(request, resolveUserId(httpRequest));
    }

    @PutMapping("/{id}")
    public IntegrationConnectorService.ConnectorDetail updateConnector(
            @PathVariable("id") Long id,
            @RequestBody IntegrationConnectorService.CreateOrUpdateConnectorRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.updateConnector(id, request, resolveUserId(httpRequest));
    }

    @DeleteMapping("/{id}")
    public void deleteConnector(@PathVariable("id") Long id, HttpServletRequest request) throws TaskException {
        integrationConnectorService.deleteConnector(id, resolveUserId(request));
    }

    @PostMapping("/{id}/auth/test")
    public IntegrationConnectorService.ConnectorAuthTestResult testConnectorAuth(
            @PathVariable("id") Long connectorId,
            @RequestBody(required = false) IntegrationConnectorService.ConnectorAuthTestRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.testConnectorAuth(connectorId, request, resolveUserId(httpRequest));
    }

    @GetMapping("/{id}/apis")
    public List<IntegrationConnectorService.ConnectorApiSummary> listConnectorApis(
            @PathVariable("id") Long connectorId, HttpServletRequest request) throws TaskException {
        return integrationConnectorService.listConnectorApis(connectorId, resolveUserId(request));
    }

    @GetMapping("/code-preview")
    public IntegrationConnectorService.ConnectorCodePreview previewConnectorCode(
            @RequestParam("connectorName") String connectorName, HttpServletRequest request) {
        resolveUserId(request);
        return integrationConnectorService.previewConnectorCode(connectorName);
    }

    @GetMapping("/apis/code-preview")
    public IntegrationConnectorService.ConnectorApiCodePreview previewConnectorApiCode(
            @RequestParam("connectorId") Long connectorId,
            @RequestParam("apiName") String apiName,
            HttpServletRequest request)
            throws TaskException {
        resolveUserId(request);
        return integrationConnectorService.previewConnectorApiCode(connectorId, apiName);
    }

    @GetMapping("/apis/{apiId}")
    public IntegrationConnectorService.ConnectorApiDetail getConnectorApi(
            @PathVariable("apiId") Long apiId, HttpServletRequest request) throws TaskException {
        return integrationConnectorService.getConnectorApi(apiId, resolveUserId(request));
    }

    @PostMapping("/{id}/apis")
    public IntegrationConnectorService.ConnectorApiDetail createConnectorApi(
            @PathVariable("id") Long connectorId,
            @RequestBody IntegrationConnectorService.CreateOrUpdateConnectorApiRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.createConnectorApi(connectorId, request, resolveUserId(httpRequest));
    }

    @PutMapping("/apis/{apiId}")
    public IntegrationConnectorService.ConnectorApiDetail updateConnectorApi(
            @PathVariable("apiId") Long apiId,
            @RequestBody IntegrationConnectorService.CreateOrUpdateConnectorApiRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.updateConnectorApi(apiId, request, resolveUserId(httpRequest));
    }

    @DeleteMapping("/apis/{apiId}")
    public void deleteConnectorApi(@PathVariable("apiId") Long apiId, HttpServletRequest request) throws TaskException {
        integrationConnectorService.deleteConnectorApi(apiId, resolveUserId(request));
    }

    @PostMapping("/apis/{apiId}/publish")
    public IntegrationConnectorService.ConnectorApiDetail publishConnectorApi(
            @PathVariable("apiId") Long apiId, HttpServletRequest request) throws TaskException {
        return integrationConnectorService.publishConnectorApi(apiId, resolveUserId(request));
    }

    @PostMapping("/apis/{apiId}/disable")
    public IntegrationConnectorService.ConnectorApiDetail disableConnectorApi(
            @PathVariable("apiId") Long apiId, HttpServletRequest request) throws TaskException {
        return integrationConnectorService.disableConnectorApi(apiId, resolveUserId(request));
    }

    @PostMapping("/apis/{apiId}/test")
    public IntegrationConnectorService.ConnectorApiTestResult testConnectorApi(
            @PathVariable("apiId") Long apiId,
            @RequestBody(required = false) IntegrationConnectorService.ConnectorApiTestRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        return integrationConnectorService.testConnectorApi(apiId, request, resolveUserId(httpRequest));
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
