package lingzhou.agent.backend.business.license.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class StoredLicensePayload {

    private String source;
    private String licenseId;
    private String serialNo;
    private Integer revision;
    private String productCode;
    private String edition;
    private LicenseType licType;
    private String customerName;
    private String instanceCode;
    private Date issuedAt;
    private Date effectiveAt;
    private Date expiresAt;
    private Integer maxActiveUsers;
    private Long maxTotalTokens;
    private List<String> featureFlags = List.of();
    private String rawPayload;
    private String rawSignature;
    private String fileSha256;
    private Long importedBy;
    private Date importedAt;
    private Date lastVerifiedAt;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", source);
        map.put("licenseId", licenseId);
        map.put("serialNo", serialNo);
        map.put("revision", revision);
        map.put("productCode", productCode);
        map.put("edition", edition);
        map.put("licType", licType == null ? null : licType.getCode());
        map.put("customerName", customerName);
        map.put("instanceCode", instanceCode);
        map.put("issuedAt", issuedAt);
        map.put("effectiveAt", effectiveAt);
        map.put("expiresAt", expiresAt);
        map.put("maxActiveUsers", maxActiveUsers);
        map.put("maxTotalTokens", maxTotalTokens);
        map.put("featureFlags", featureFlags == null ? new ArrayList<>() : featureFlags);
        map.put("rawPayload", rawPayload);
        map.put("rawSignature", rawSignature);
        map.put("fileSha256", fileSha256);
        map.put("importedBy", importedBy);
        map.put("importedAt", importedAt);
        map.put("lastVerifiedAt", lastVerifiedAt);
        return map;
    }
}
