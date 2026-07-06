package lingzhou.agent.backend.business.license.model;

import java.util.Date;
import java.util.List;

public record LicenseSnapshot(
        Long id,
        String licenseId,
        Integer revision,
        String productCode,
        String customerName,
        String edition,
        LicenseType licType,
        String instanceCode,
        String status,
        Date effectiveAt,
        Date expiresAt,
        Integer maxActiveUsers,
        Long maxTotalTokens,
        List<String> featureFlags,
        Date importedAt) {}
