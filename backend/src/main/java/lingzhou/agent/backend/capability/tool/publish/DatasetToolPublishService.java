package lingzhou.agent.backend.capability.tool.publish;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DatasetToolPublishService {

    private static final String TOOL_TYPE_DATASET = "DATASET_TOOL";

    private final ToolCatalogMapper toolCatalogMapper;

    public DatasetToolPublishService(ToolCatalogMapper toolCatalogMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
    }

    public List<PublishedToolView> loadPublishedTools(Long datasetId) {
        return toolCatalogMapper.selectBySource(buildSource(datasetId)).stream()
                .map(item -> new PublishedToolView(
                        item.getToolName(),
                        item.getDisplayName(),
                        normalizeText(item.getDescription()),
                        item.getToolType()))
                .toList();
    }

    public List<String> publish(IntegrationDataset dataset) {
        List<PublishedToolSeed> toolSeeds = buildToolSeeds(dataset);
        for (PublishedToolSeed seed : toolSeeds) {
            ToolCatalog toolCatalog = toolCatalogMapper.selectByToolName(seed.toolName());
            if (toolCatalog == null) {
                toolCatalog = new ToolCatalog();
                toolCatalog.setToolName(seed.toolName());
                toolCatalog.setSortOrder(seed.sortOrder());
            }
            toolCatalog.setDisplayName(seed.displayName());
            toolCatalog.setDescription(seed.description());
            toolCatalog.setToolType(TOOL_TYPE_DATASET);
            toolCatalog.setBindable(1);
            toolCatalog.setOwnerSkillName(null);
            toolCatalog.setSource(buildSource(dataset.getId()));
            if (toolCatalog.getId() == null) {
                toolCatalogMapper.insert(toolCatalog);
            } else {
                toolCatalogMapper.updateById(toolCatalog);
            }
        }
        return toolSeeds.stream().map(PublishedToolSeed::toolName).toList();
    }

    public void disable(Long datasetId) {
        toolCatalogMapper.deleteBySource(buildSource(datasetId));
    }

    private List<PublishedToolSeed> buildToolSeeds(IntegrationDataset dataset) {
        String baseName = "dataset." + dataset.getId();
        String datasetName = normalizeText(dataset.getName());
        String summaryText = StringUtils.hasText(dataset.getBusinessLogic())
                ? dataset.getBusinessLogic().trim()
                : "用于数据集摘要检索与候选表判断";
        List<PublishedToolSeed> seeds = new ArrayList<>();
        seeds.add(new PublishedToolSeed(
                baseName + ".search_dataset_summary",
                datasetName + " / 摘要检索",
                "基于数据集“" + datasetName + "”返回摘要说明、关系说明和候选对象。"
                        + (StringUtils.hasText(summaryText) ? " " + summaryText : ""),
                71000));
        seeds.add(new PublishedToolSeed(
                baseName + ".get_dataset_schema",
                datasetName + " / 结构获取",
                "基于数据集“" + datasetName + "”按需返回对象、字段、别名和结构说明。",
                71001));
        seeds.add(new PublishedToolSeed(
                baseName + ".execute_dataset_sql",
                datasetName + " / SQL 执行",
                "在数据集“" + datasetName + "”允许范围内执行查询 SQL 并返回结果。",
                71002));
        return List.copyOf(seeds);
    }

    private String buildSource(Long datasetId) {
        return "dataset:" + datasetId;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record PublishedToolSeed(String toolName, String displayName, String description, int sortOrder) {}

    public record PublishedToolView(String toolName, String displayName, String description, String toolType) {}
}
