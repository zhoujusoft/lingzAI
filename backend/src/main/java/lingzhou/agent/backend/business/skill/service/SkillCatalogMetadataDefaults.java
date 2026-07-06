package lingzhou.agent.backend.business.skill.service;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.springframework.util.StringUtils;

final class SkillCatalogMetadataDefaults {

    static final String DEFAULT_SKILL_VERSION = "1.0";
    static final String DEFAULT_SKILL_AUTHOR = "zhouju";

    private static final String[] DEFAULT_SKILL_ICONS = {
        "grid_view",
        "smart_toy",
        "rocket_launch",
        "inventory_2",
        "dataset",
        "hub",
        "description",
        "article",
        "table_chart",
        "design_services",
        "palette",
        "rule",
        "gavel",
        "policy",
        "account_balance",
        "analytics",
        "travel_explore",
        "fact_check",
        "checklist",
        "assignment",
        "psychology",
        "monitor_heart",
        "medical_services",
        "auto_awesome",
        "dashboard"
    };

    private SkillCatalogMetadataDefaults() {}

    static String resolveVersion(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_SKILL_VERSION;
    }

    static String resolveAuthor(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_SKILL_AUTHOR;
    }

    static String resolveIcon(String runtimeSkillName, String value) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return defaultIcon(runtimeSkillName);
    }

    static String defaultIcon(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return DEFAULT_SKILL_ICONS[0];
        }
        CRC32 crc32 = new CRC32();
        crc32.update(runtimeSkillName.trim().getBytes(StandardCharsets.UTF_8));
        int index = (int) (crc32.getValue() % DEFAULT_SKILL_ICONS.length);
        return DEFAULT_SKILL_ICONS[index];
    }
}
