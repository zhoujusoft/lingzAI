package lingzhou.agent.backend.capability.webfetch;

import java.util.List;

public record WebFetchRequest(
        String url,
        String selector,
        Integer maxChars,
        Boolean includeImages,
        Boolean includeLinks,
        List<String> allowedDomains) {}
