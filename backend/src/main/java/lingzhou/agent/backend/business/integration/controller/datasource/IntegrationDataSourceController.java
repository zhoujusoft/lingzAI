package lingzhou.agent.backend.business.integration.controller.datasource;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.integration.service.datasource.IntegrationDataSourceService;
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
@RequestMapping("/integration/data-sources")
public class IntegrationDataSourceController {

    private final IntegrationDataSourceService integrationDataSourceService;

    public IntegrationDataSourceController(IntegrationDataSourceService integrationDataSourceService) {
        this.integrationDataSourceService = integrationDataSourceService;
    }

    @GetMapping
    public List<IntegrationDataSourceService.DataSourceSummary> listDataSources(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dbType", required = false) String dbType,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        return integrationDataSourceService.listDataSources(keyword, dbType, status, userId);
    }

    @GetMapping("/{id}")
    public IntegrationDataSourceService.DataSourceDetail getDataSource(
            @PathVariable("id") Long id, HttpServletRequest request) throws TaskException {
        Long userId = resolveUserId(request);
        return integrationDataSourceService.getDataSource(id, userId);
    }

    @PostMapping
    public IntegrationDataSourceService.DataSourceDetail create(
            @RequestBody IntegrationDataSourceService.CreateOrUpdateDataSourceRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = resolveUserId(httpRequest);
        return integrationDataSourceService.create(request, userId);
    }

    @PutMapping("/{id}")
    public IntegrationDataSourceService.DataSourceDetail update(
            @PathVariable("id") Long id,
            @RequestBody IntegrationDataSourceService.CreateOrUpdateDataSourceRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = resolveUserId(httpRequest);
        return integrationDataSourceService.update(id, request, userId);
    }

    @PostMapping("/test-connection")
    public IntegrationDataSourceService.ConnectionTestResult testConnection(
            @RequestBody IntegrationDataSourceService.ConnectionTestRequest request, HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = resolveUserId(httpRequest);
        return integrationDataSourceService.testConnection(request, userId);
    }

    @GetMapping("/{id}/objects")
    public List<IntegrationDataSourceService.ObjectView> listObjects(
            @PathVariable("id") Long id, HttpServletRequest request) throws TaskException {
        Long userId = resolveUserId(request);
        return integrationDataSourceService.listObjects(id, userId);
    }

    @GetMapping("/{id}/fields")
    public List<IntegrationDataSourceService.FieldView> listFields(
            @PathVariable("id") Long id, @RequestParam("objectCode") String objectCode, HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        return integrationDataSourceService.listFields(id, objectCode, userId);
    }

    @GetMapping("/{id}/relations")
    public List<IntegrationDataSourceService.RelationView> listRelations(
            @PathVariable("id") Long id,
            @RequestParam(value = "objectCodes", required = false) List<String> objectCodes,
            HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        return integrationDataSourceService.listRelations(id, objectCodes, userId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id, HttpServletRequest request) throws TaskException {
        Long userId = resolveUserId(request);
        integrationDataSourceService.delete(id, userId);
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
