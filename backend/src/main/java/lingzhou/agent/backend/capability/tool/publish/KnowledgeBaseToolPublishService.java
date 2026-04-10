package lingzhou.agent.backend.capability.tool.publish;

import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseToolPublishService {

    private static final String TOOL_TYPE_KNOWLEDGE_BASE = "KNOWLEDGE_BASE_TOOL";

    private final ToolCatalogMapper toolCatalogMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;

    public KnowledgeBaseToolPublishService(
            ToolCatalogMapper toolCatalogMapper, SkillToolBindingMapper skillToolBindingMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
    }

    public List<PublishedToolView> loadPublishedTools(String kbCode) {
        return toolCatalogMapper.selectBySource(buildSource(kbCode)).stream()
                .map(item -> new PublishedToolView(
                        item.getToolName(),
                        item.getDisplayName(),
                        normalizeText(item.getDescription()),
                        item.getToolType()))
                .toList();
    }

    public List<String> publish(KnowledgeBase knowledgeBase) {
        String kbCode = normalizeCode(knowledgeBase == null ? null : knowledgeBase.getKbCode());
        PublishedToolSeed seed = buildToolSeed(knowledgeBase);
        ToolCatalog toolCatalog = toolCatalogMapper.selectByToolName(seed.toolName());
        if (toolCatalog == null) {
            toolCatalog = new ToolCatalog();
            toolCatalog.setToolName(seed.toolName());
            toolCatalog.setSortOrder(seed.sortOrder());
        }
        toolCatalog.setDisplayName(seed.displayName());
        toolCatalog.setDescription(seed.description());
        toolCatalog.setToolType(TOOL_TYPE_KNOWLEDGE_BASE);
        toolCatalog.setBindable(1);
        toolCatalog.setOwnerSkillName(null);
        toolCatalog.setSource(buildSource(kbCode));
        if (toolCatalog.getId() == null) {
            toolCatalogMapper.insert(toolCatalog);
        } else {
            toolCatalogMapper.updateById(toolCatalog);
        }
        return List.of(seed.toolName());
    }

    public void disable(String kbCode) {
        List<String> toolNames = listToolNames(kbCode);
        if (!toolNames.isEmpty()) {
            skillToolBindingMapper.deleteByToolNames(toolNames);
        }
        toolCatalogMapper.deleteBySource(buildSource(kbCode));
    }

    public List<String> listToolNames(String kbCode) {
        return toolCatalogMapper.selectBySource(buildSource(kbCode)).stream()
                .map(ToolCatalog::getToolName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private PublishedToolSeed buildToolSeed(KnowledgeBase knowledgeBase) {
        String kbName = normalizeText(knowledgeBase.getKbName());
        String description = "在知识库“" + kbName + "”中检索与问题最相关的文档片段并返回命中结果。";
        if (StringUtils.hasText(knowledgeBase.getDescription())) {
            description = description + " " + knowledgeBase.getDescription().trim();
        }
        return new PublishedToolSeed(
                "knowledge_base." + normalizeCode(knowledgeBase.getKbCode()) + ".search",
                kbName + " / 内容检索",
                description,
                72000);
    }

    private String buildSource(String kbCode) {
        return "knowledge_base:" + normalizeCode(kbCode);
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("kbCode 不能为空，无法发布知识库工具");
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record PublishedToolSeed(String toolName, String displayName, String description, int sortOrder) {}

    public record PublishedToolView(String toolName, String displayName, String description, String toolType) {}
}
