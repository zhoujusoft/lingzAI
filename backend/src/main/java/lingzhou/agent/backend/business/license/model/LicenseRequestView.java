package lingzhou.agent.backend.business.license.model;

public record LicenseRequestView(
        String productCode,
        String instanceCode,
        String customerName,
        String currentLicenseId,
        LicenseType currentLicType,
        String currentExpiresAt) {}
