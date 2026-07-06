package lingzhou.agent.backend.business.license.model;

public record LicenseImportResult(
        String licenseId,
        Integer revision,
        String status,
        String customerName,
        LicenseType licType,
        String expiresAt) {}
