package lingzhou.agent.backend.business.datasets.service;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBasePublishBinding;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBasePublishBindingMapper;
import lingzhou.agent.backend.capability.tool.publish.KnowledgeBaseToolPublishService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBasePublishService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBasePublishBindingMapper publishBindingMapper;
    private final KnowledgeBaseToolPublishService knowledgeBaseToolPublishService;

    public KnowledgeBasePublishService(
            IKnowledgeBaseService knowledgeBaseService,
            KnowledgeBasePublishBindingMapper publishBindingMapper,
            KnowledgeBaseToolPublishService knowledgeBaseToolPublishService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.publishBindingMapper = publishBindingMapper;
        this.knowledgeBaseToolPublishService = knowledgeBaseToolPublishService;
    }

    public PublishStatusView getPublishStatus(Long kbId) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        KnowledgeBasePublishBinding binding = publishBindingMapper.selectByKbId(kbId);
        List<PublishedToolView> tools = toPublishedToolViews(knowledgeBaseToolPublishService.loadPublishedTools(kbId));
        return toStatusView(knowledgeBase, binding, tools);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView publish(Long kbId) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        List<String> toolNames = knowledgeBaseToolPublishService.publish(knowledgeBase);
        Date now = new Date();

        KnowledgeBasePublishBinding binding = publishBindingMapper.selectByKbId(kbId);
        if (binding == null) {
            binding = new KnowledgeBasePublishBinding();
            binding.setKbId(kbId);
            binding.setPublishedVersion(0);
            binding.setPublishStatus(STATUS_DRAFT);
        }
        binding.setPublishStatus(STATUS_PUBLISHED);
        binding.setPublishedToolCodes(toolNames.stream().collect(Collectors.joining(",")));
        binding.setPublishedVersion((binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion()) + 1);
        binding.setPublishedAt(now);
        binding.setLastCompiledAt(now);
        binding.setLastPublishMessage("已发布 " + toolNames.size() + " 个知识库工具");
        if (binding.getId() == null) {
            publishBindingMapper.insert(binding);
        } else {
            publishBindingMapper.updateById(binding);
        }
        return getPublishStatus(kbId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView disable(Long kbId) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        KnowledgeBasePublishBinding binding = publishBindingMapper.selectByKbId(kbId);
        Date now = new Date();
        if (binding == null) {
            binding = new KnowledgeBasePublishBinding();
            binding.setKbId(kbId);
            binding.setPublishedVersion(0);
        }
        binding.setPublishStatus(STATUS_DISABLED);
        binding.setLastCompiledAt(now);
        binding.setLastPublishMessage("已停用知识库工具");
        if (binding.getId() == null) {
            publishBindingMapper.insert(binding);
        } else {
            publishBindingMapper.updateById(binding);
        }
        knowledgeBaseToolPublishService.disable(kbId);
        return toStatusView(knowledgeBase, binding, List.of());
    }

    private PublishStatusView toStatusView(
            KnowledgeBase knowledgeBase, KnowledgeBasePublishBinding binding, List<PublishedToolView> tools) {
        String status = binding == null || !StringUtils.hasText(binding.getPublishStatus())
                ? STATUS_DRAFT
                : binding.getPublishStatus().trim().toUpperCase(Locale.ROOT);
        return new PublishStatusView(
                knowledgeBase.getKbId(),
                knowledgeBase.getKbName(),
                status,
                binding == null ? 0 : (binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion()),
                binding == null ? null : binding.getPublishedAt(),
                binding == null ? null : binding.getLastCompiledAt(),
                binding == null ? "" : normalizeText(binding.getLastPublishMessage()),
                tools);
    }

    private KnowledgeBase requireKnowledgeBase(Long kbId) throws TaskException {
        if (kbId == null) {
            throw new TaskException("kbId 不能为空", TaskException.Code.UNKNOWN);
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.selectKnowledgeBaseByKbId(kbId);
        if (knowledgeBase == null) {
            throw new TaskException("知识库不存在：" + kbId, TaskException.Code.UNKNOWN);
        }
        return knowledgeBase;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private List<PublishedToolView> toPublishedToolViews(List<KnowledgeBaseToolPublishService.PublishedToolView> source) {
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
            Long kbId,
            String kbName,
            String publishStatus,
            Integer publishedVersion,
            Date publishedAt,
            Date lastCompiledAt,
            String lastPublishMessage,
            List<PublishedToolView> tools) {}
}
