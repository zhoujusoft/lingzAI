package lingzhou.agent.backend.business.integration.controller.datasource;

import java.util.List;
import lingzhou.agent.backend.business.integration.service.datasource.IntegrationDataSourceService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
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
            @RequestParam(value = "status", required = false) String status) {
        return integrationDataSourceService.listDataSources(keyword, dbType, status);
    }

    @GetMapping("/{id}")
    public IntegrationDataSourceService.DataSourceDetail getDataSource(@PathVariable("id") Long id) throws TaskException {
        return integrationDataSourceService.getDataSource(id);
    }

    @PostMapping
    public IntegrationDataSourceService.DataSourceDetail create(
            @RequestBody IntegrationDataSourceService.CreateOrUpdateDataSourceRequest request) throws TaskException {
        return integrationDataSourceService.create(request);
    }

    @PutMapping("/{id}")
    public IntegrationDataSourceService.DataSourceDetail update(
            @PathVariable("id") Long id,
            @RequestBody IntegrationDataSourceService.CreateOrUpdateDataSourceRequest request)
            throws TaskException {
        return integrationDataSourceService.update(id, request);
    }

    @PostMapping("/test-connection")
    public IntegrationDataSourceService.ConnectionTestResult testConnection(
            @RequestBody IntegrationDataSourceService.ConnectionTestRequest request) throws TaskException {
        return integrationDataSourceService.testConnection(request);
    }

    @GetMapping("/{id}/objects")
    public List<IntegrationDataSourceService.ObjectView> listObjects(@PathVariable("id") Long id) throws TaskException {
        return integrationDataSourceService.listObjects(id);
    }

    @GetMapping("/{id}/fields")
    public List<IntegrationDataSourceService.FieldView> listFields(
            @PathVariable("id") Long id, @RequestParam("objectCode") String objectCode) throws TaskException {
        return integrationDataSourceService.listFields(id, objectCode);
    }

    @GetMapping("/{id}/relations")
    public List<IntegrationDataSourceService.RelationView> listRelations(
            @PathVariable("id") Long id, @RequestParam(value = "objectCodes", required = false) List<String> objectCodes)
            throws TaskException {
        return integrationDataSourceService.listRelations(id, objectCodes);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) throws TaskException {
        integrationDataSourceService.delete(id);
    }
}
