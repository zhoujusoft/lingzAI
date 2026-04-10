package lingzhou.agent.backend.business.datasets.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lingzhou.agent.backend.capability.tool.publish.DatasetToolPublishService;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDatasetPublishBinding;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetPublishBindingMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDatasetPublishService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final IntegrationDatasetMapper integrationDatasetMapper;
    private final IntegrationDatasetPublishBindingMapper publishBindingMapper;
    private final DatasetToolPublishService datasetToolPublishService;

    public IntegrationDatasetPublishService(
            IntegrationDatasetMapper integrationDatasetMapper,
            IntegrationDatasetPublishBindingMapper publishBindingMapper,
            DatasetToolPublishService datasetToolPublishService) {
        this.integrationDatasetMapper = integrationDatasetMapper;
        this.publishBindingMapper = publishBindingMapper;
        this.datasetToolPublishService = datasetToolPublishService;
    }

    public PublishStatusView getPublishStatus(Long datasetId) throws TaskException {
        IntegrationDataset dataset = requireDataset(datasetId);
        String datasetCode = requireDatasetCode(dataset);
        IntegrationDatasetPublishBinding binding = publishBindingMapper.selectByDatasetId(datasetId);
        List<PublishedToolView> tools = toPublishedToolViews(datasetToolPublishService.loadPublishedTools(datasetCode));
        return toStatusView(dataset, binding, tools);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView publish(Long datasetId) throws TaskException {
        IntegrationDataset dataset = requireDataset(datasetId);
        requireDatasetCode(dataset);
        List<String> toolNames = datasetToolPublishService.publish(dataset);
        Date now = new Date();

        IntegrationDatasetPublishBinding binding = publishBindingMapper.selectByDatasetId(datasetId);
        if (binding == null) {
            binding = new IntegrationDatasetPublishBinding();
            binding.setDatasetId(datasetId);
            binding.setPublishedVersion(0);
            binding.setPublishStatus(STATUS_DRAFT);
        }
        binding.setPublishStatus(STATUS_PUBLISHED);
        binding.setPublishedToolCodes(toolNames.stream().collect(Collectors.joining(",")));
        binding.setPublishedVersion((binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion()) + 1);
        binding.setPublishedAt(now);
        binding.setLastCompiledAt(now);
        binding.setLastPublishMessage("已发布 " + toolNames.size() + " 个数据集工具");
        if (binding.getId() == null) {
            publishBindingMapper.insert(binding);
        } else {
            publishBindingMapper.updateById(binding);
        }
        return getPublishStatus(datasetId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView disable(Long datasetId) throws TaskException {
        IntegrationDataset dataset = requireDataset(datasetId);
        String datasetCode = requireDatasetCode(dataset);
        IntegrationDatasetPublishBinding binding = publishBindingMapper.selectByDatasetId(datasetId);
        Date now = new Date();
        if (binding == null) {
            binding = new IntegrationDatasetPublishBinding();
            binding.setDatasetId(datasetId);
            binding.setPublishedVersion(0);
        }
        binding.setPublishStatus(STATUS_DISABLED);
        binding.setLastCompiledAt(now);
        binding.setLastPublishMessage("已停用数据集工具");
        if (binding.getId() == null) {
            publishBindingMapper.insert(binding);
        } else {
            publishBindingMapper.updateById(binding);
        }
        datasetToolPublishService.disable(datasetCode);
        return toStatusView(dataset, binding, List.of());
    }

    private PublishStatusView toStatusView(
            IntegrationDataset dataset,
            IntegrationDatasetPublishBinding binding,
            List<PublishedToolView> tools) {
        String status = binding == null || !StringUtils.hasText(binding.getPublishStatus())
                ? STATUS_DRAFT
                : binding.getPublishStatus().trim().toUpperCase(Locale.ROOT);
        return new PublishStatusView(
                dataset.getId(),
                dataset.getName(),
                status,
                binding == null ? 0 : (binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion()),
                binding == null ? null : binding.getPublishedAt(),
                binding == null ? null : binding.getLastCompiledAt(),
                binding == null ? "" : normalizeText(binding.getLastPublishMessage()),
                tools);
    }

    private IntegrationDataset requireDataset(Long datasetId) throws TaskException {
        if (datasetId == null) {
            throw new TaskException("datasetId 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationDataset dataset = integrationDatasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new TaskException("数据集不存在：" + datasetId, TaskException.Code.UNKNOWN);
        }
        return dataset;
    }

    private String requireDatasetCode(IntegrationDataset dataset) throws TaskException {
        if (dataset == null || !StringUtils.hasText(dataset.getDatasetCode())) {
            throw new TaskException("数据集编码缺失，请先执行数据库增量脚本后重试", TaskException.Code.UNKNOWN);
        }
        return dataset.getDatasetCode().trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private List<PublishedToolView> toPublishedToolViews(List<DatasetToolPublishService.PublishedToolView> source) {
        return source.stream()
                .map(item -> new PublishedToolView(
                        item.toolName(),
                        item.displayName(),
                        item.description(),
                        item.toolType()))
                .toList();
    }

    public record PublishedToolView(String toolName, String displayName, String description, String toolType) {}

    public record PublishStatusView(
            Long datasetId,
            String datasetName,
            String publishStatus,
            Integer publishedVersion,
            Date publishedAt,
            Date lastCompiledAt,
            String lastPublishMessage,
            List<PublishedToolView> tools) {}
}
