package lingzhou.agent.backend.framework.config;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@Slf4j
public class FlywayConfig {

    private static final int MAX_PENDING_SCRIPTS_TO_LOG = 5;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            MigrationSnapshot snapshot = collectSnapshot(flyway);
            if (snapshot.reliable() && snapshot.pendingCount() == 0) {
                log.info("Flyway 启动检查完成：无待执行 migration，currentVersion={}", snapshot.currentVersion());
                return;
            }
            if (!snapshot.reliable()) {
                log.warn("Flyway 迁移前状态不可靠，将直接尝试 migrate：currentVersion={}", snapshot.currentVersion());
            }
            log.info(
                    "Flyway 启动迁移开始：currentVersion={}, pendingCount={}, pendingScripts={}",
                    snapshot.currentVersion(),
                    snapshot.pendingCount(),
                    snapshot.pendingScriptsSummary());
            try {
                MigrateResult result = flyway.migrate();
                log.info(
                        "Flyway 启动迁移完成：initialVersion={}, targetVersion={}, executedCount={}, success={}",
                        blankToDash(result.initialSchemaVersion),
                        blankToDash(result.targetSchemaVersion),
                        result.migrationsExecuted,
                        result.success);
            } catch (Exception e) {
                log.error(
                        "Flyway 启动迁移失败：currentVersion={}, pendingCount={}, pendingScripts={}, error={}. "
                                + "请先修复数据库版本状态或执行缺失迁移后再启动应用。",
                        snapshot.currentVersion(),
                        snapshot.pendingCount(),
                        snapshot.pendingScriptsSummary(),
                        e.getMessage(),
                        e);
                throw e;
            }
        };
    }

    private MigrationSnapshot collectSnapshot(Flyway flyway) {
        try {
            MigrationInfoService infoService = flyway.info();
            MigrationInfo current = infoService == null ? null : infoService.current();
            MigrationInfo[] pending = infoService == null ? new MigrationInfo[0] : infoService.pending();
            String currentVersion = current == null || current.getVersion() == null
                    ? "baseline/empty"
                    : current.getVersion().getVersion();
            return new MigrationSnapshot(
                    currentVersion,
                    pending == null ? 0 : pending.length,
                    summarizePendingScripts(pending),
                    true);
        } catch (Exception e) {
            log.warn("Flyway 迁移前状态探测失败，将直接尝试 migrate：error={}", e.getMessage(), e);
            return new MigrationSnapshot("unknown", -1, "unknown", false);
        }
    }

    private String summarizePendingScripts(MigrationInfo[] pending) {
        if (pending == null || pending.length == 0) {
            return "[]";
        }
        String summary = Arrays.stream(pending)
                .limit(MAX_PENDING_SCRIPTS_TO_LOG)
                .map(this::describeMigration)
                .collect(Collectors.joining(", ", "[", "]"));
        if (pending.length <= MAX_PENDING_SCRIPTS_TO_LOG) {
            return summary;
        }
        return summary.substring(0, summary.length() - 1) + ", ...]";
    }

    private String describeMigration(MigrationInfo migrationInfo) {
        if (migrationInfo == null) {
            return "unknown";
        }
        String version = migrationInfo.getVersion() == null
                ? "repeatable"
                : migrationInfo.getVersion().getVersion();
        String script = StringUtils.hasText(migrationInfo.getScript())
                ? migrationInfo.getScript().trim()
                : migrationInfo.getDescription();
        return version + ":" + blankToDash(script);
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private record MigrationSnapshot(
            String currentVersion, int pendingCount, String pendingScriptsSummary, boolean reliable) {}
}
