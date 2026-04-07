package lingzhou.agent.backend.capability.dataset.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.skill.service.LowcodePlatformConfigService;
import lingzhou.agent.backend.business.skill.service.LowcodeTokenService;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeDatasetSqlExecutor {

    private final LowcodePlatformConfigService lowcodePlatformConfigService;
    private final LowcodeTokenService lowcodeTokenService;
    private final LowcodePlatformClient lowcodePlatformClient;
    private final LowcodeSqlCryptoService lowcodeSqlCryptoService;

    public LowcodeDatasetSqlExecutor(
            LowcodePlatformConfigService lowcodePlatformConfigService,
            LowcodeTokenService lowcodeTokenService,
            LowcodePlatformClient lowcodePlatformClient,
            LowcodeSqlCryptoService lowcodeSqlCryptoService) {
        this.lowcodePlatformConfigService = lowcodePlatformConfigService;
        this.lowcodeTokenService = lowcodeTokenService;
        this.lowcodePlatformClient = lowcodePlatformClient;
        this.lowcodeSqlCryptoService = lowcodeSqlCryptoService;
    }

    public IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult execute(
            IntegrationDatasetService.DatasetDetail detail, String executableSql) throws TaskException {
        String platformKey = detail.lowcodePlatformKey();
        if (!StringUtils.hasText(platformKey)) {
            throw new TaskException("低代码数据集未配置平台", TaskException.Code.UNKNOWN);
        }
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey.trim());
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        String encryptedSql = lowcodeSqlCryptoService.encryptSelectSql(executableSql);
        List<Map<String, Object>> rawRows = lowcodePlatformClient.sqlSelect(platform, token, encryptedSql, List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            if (rawRow != null) {
                normalized.putAll(rawRow);
            }
            rows.add(normalized);
        }
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        return new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                detail.id(),
                detail.name(),
                executableSql,
                columns,
                rows,
                rows.size());
    }
}
