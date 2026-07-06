package lingzhou.agent.backend.business.system.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

public final class AdminDashboardApiModels {

    private AdminDashboardApiModels() {}

    public record DashboardResponse(SummaryView summary, LicenseView license, List<ModuleView> modules) {}

    public record SummaryView(
            int moduleCount,
            long resourceCount,
            long activeCount,
            String largestModuleId,
            String largestModuleLabel,
            long largestModuleCount) {}

    public record ModuleView(String moduleId, String label, long total, List<StatusView> statuses) {}

    public record StatusView(String key, String label, long count) {}

    public record LicenseView(
            boolean enabled,
            String status,
            String customerName,
            String edition,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date expiresAt,
            boolean expirationUnlimited,
            long remainingDays,
            int registeredUsers,
            int activeUsers,
            Integer maxActiveUsers,
            boolean userUnlimited,
            long consumedTokens,
            Long maxTotalTokens,
            Long remainingTokens,
            boolean tokenUnlimited) {}
}
