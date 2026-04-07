package lingzhou.agent.backend.business.skill.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.chat.domain.ChatSession;
import lingzhou.agent.backend.business.chat.mapper.ChatSessionMapper;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillRecommendationService {

    private static final String SKILL_CHAT_SESSION_TYPE = "SKILL_CHAT";
    private static final int MAX_RECENT_SKILL_SESSIONS = 200;
    private static final int MAX_RECENCY_WINDOW = 18;

    private final ChatSessionMapper chatSessionMapper;

    public SkillRecommendationService(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    public Map<Long, RecommendationProfile> buildRecommendationMap(Long userId, List<SkillCatalog> catalogs) {
        if (userId == null || userId <= 0 || catalogs == null || catalogs.isEmpty()) {
            return Map.of();
        }

        Map<Long, SkillCatalog> catalogById = new LinkedHashMap<>();
        for (SkillCatalog catalog : catalogs) {
            if (catalog == null || catalog.getId() == null || catalog.getId() <= 0) {
                continue;
            }
            catalogById.put(catalog.getId(), catalog);
        }
        if (catalogById.isEmpty()) {
            return Map.of();
        }

        List<ChatSession> sessions = chatSessionMapper.selectRecentSkillSessions(
                userId,
                catalogById.keySet().stream().toList(),
                MAX_RECENT_SKILL_SESSIONS);
        if (sessions.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> usageCountBySkill = new LinkedHashMap<>();
        Map<Long, Integer> recencyBonusBySkill = new LinkedHashMap<>();
        Map<Long, Date> lastUsedAtBySkill = new LinkedHashMap<>();
        Map<String, Integer> categoryUsageCount = new LinkedHashMap<>();

        for (int index = 0; index < sessions.size(); index += 1) {
            ChatSession session = sessions.get(index);
            Long skillId = session == null ? null : session.getScopeId();
            if (skillId == null || !catalogById.containsKey(skillId)) {
                continue;
            }

            usageCountBySkill.merge(skillId, 1, Integer::sum);

            Date usedAt = session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getCreatedAt();
            if (usedAt != null) {
                Date existing = lastUsedAtBySkill.get(skillId);
                if (existing == null || usedAt.after(existing)) {
                    lastUsedAtBySkill.put(skillId, usedAt);
                }
            }

            int recentWeight = Math.max(0, MAX_RECENCY_WINDOW - index);
            if (recentWeight > 0) {
                recencyBonusBySkill.merge(skillId, recentWeight, Integer::sum);
            }

            String category = normalizeCategory(catalogById.get(skillId));
            if (StringUtils.hasText(category)) {
                categoryUsageCount.merge(category, 1, Integer::sum);
            }
        }

        Map<Long, RecommendationProfile> recommendationMap = new LinkedHashMap<>();
        for (SkillCatalog catalog : catalogs) {
            if (catalog == null || catalog.getId() == null || catalog.getId() <= 0) {
                continue;
            }

            int usageCount = usageCountBySkill.getOrDefault(catalog.getId(), 0);
            String category = normalizeCategory(catalog);
            int categoryCount = StringUtils.hasText(category) ? categoryUsageCount.getOrDefault(category, 0) : 0;
            int categoryAffinityCount = Math.max(0, categoryCount - usageCount);
            int recencyBonus = recencyBonusBySkill.getOrDefault(catalog.getId(), 0);
            int recommendationScore = usageCount * 100 + categoryAffinityCount * 15 + recencyBonus;
            String recommendationReason =
                    resolveRecommendationReason(usageCount, categoryAffinityCount, category, recencyBonus);

            recommendationMap.put(
                    catalog.getId(),
                    new RecommendationProfile(
                            recommendationScore > 0,
                            recommendationScore,
                            usageCount,
                            categoryAffinityCount,
                            recommendationReason,
                            lastUsedAtBySkill.get(catalog.getId())));
        }

        return recommendationMap;
    }

    private String normalizeCategory(SkillCatalog catalog) {
        if (catalog == null || !StringUtils.hasText(catalog.getCategory())) {
            return "";
        }
        return catalog.getCategory().trim();
    }

    private String resolveRecommendationReason(
            int usageCount, int categoryAffinityCount, String category, int recencyBonus) {
        if (usageCount >= 3) {
            return "近期高频使用";
        }
        if (usageCount == 2) {
            return "近期多次使用";
        }
        if (usageCount == 1) {
            return recencyBonus >= 12 ? "最近刚用过" : "你用过这个技能";
        }
        if (categoryAffinityCount > 0 && StringUtils.hasText(category)) {
            return "偏好「" + category + "」类能力";
        }
        return "";
    }

    public record RecommendationProfile(
            boolean recommended,
            int recommendationScore,
            int usageCount,
            int categoryAffinityCount,
            String recommendationReason,
            Date lastUsedAt) {

        public RecommendationProfile {
            recommendationReason = Objects.requireNonNullElse(recommendationReason, "");
        }
    }
}
