package lingzhou.agent.backend.business.skill.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalogInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SkillCatalogInitializer.class);

    private final SkillCatalogService skillCatalogService;

    public SkillCatalogInitializer(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    @PostConstruct
    public void initialize() {
        try {
            skillCatalogService.initializeCatalogData();
            logger.info("技能与工具目录初始化完成");
        } catch (Exception ex) {
            logger.warn("技能与工具目录初始化失败，将在首次访问时重试：{}", ex.getMessage(), ex);
        }
    }
}
