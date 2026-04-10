package lingzhou.agent.backend.business.datasets.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lingzhou.agent.backend.capability.tool.publish.KnowledgeBaseToolPublishService;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBasePublishBinding;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.mapper.DocumentChunkMapper;
import lingzhou.agent.backend.business.datasets.mapper.DocumentMetadataMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBaseMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBasePublishBindingMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeDocumentMapper;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeDocumentService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    private static final DateTimeFormatter KB_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern KB_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetadataMapper documentMetadataMapper;
    private final IKnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBasePublishBindingMapper knowledgeBasePublishBindingMapper;
    private final KnowledgeBaseToolPublishService knowledgeBaseToolPublishService;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMetadataMapper documentMetadataMapper,
            IKnowledgeDocumentService knowledgeDocumentService,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            DocumentChunkMapper documentChunkMapper,
            KnowledgeBasePublishBindingMapper knowledgeBasePublishBindingMapper,
            KnowledgeBaseToolPublishService knowledgeBaseToolPublishService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMetadataMapper = documentMetadataMapper;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBasePublishBindingMapper = knowledgeBasePublishBindingMapper;
        this.knowledgeBaseToolPublishService = knowledgeBaseToolPublishService;
    }

    @Override
    public KnowledgeBase selectKnowledgeBaseByKbId(Long kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        return enrichKnowledgeBaseStats(knowledgeBase);
    }

    @Override
    public KnowledgeBase selectKnowledgeBaseByKbCode(String kbCode) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbCode(kbCode);
        return enrichKnowledgeBaseStats(knowledgeBase);
    }

    @Override
    public List<KnowledgeBase> selectKnowledgeBaseList(KnowledgeBase knowledgeBase) {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectKnowledgeBaseList(knowledgeBase);
        knowledgeBases.forEach(this::enrichKnowledgeBaseStats);
        Map<Long, KnowledgeBasePublishBinding> bindingMap = knowledgeBasePublishBindingMapper
                .selectByKbIds(knowledgeBases.stream().map(KnowledgeBase::getKbId).toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeBasePublishBinding::getKbId, Function.identity()));
        knowledgeBases.forEach(item -> applyPublishBinding(item, bindingMap.get(item.getKbId())));
        return knowledgeBases;
    }

    @Override
    public IPage<KnowledgeBase> selectKnowledgeBasePage(KnowledgeBase knowledgeBase, long pageNum, long pageSize) {
        IPage<KnowledgeBase> page = knowledgeBaseMapper.selectKnowledgeBasePage(knowledgeBase, pageNum, pageSize);
        page.getRecords().forEach(this::enrichKnowledgeBaseStats);
        return page;
    }

    @Override
    public int insertKnowledgeBase(KnowledgeBase knowledgeBase) throws TaskException {
        if (knowledgeBase == null) {
            throw new TaskException("知识库参数不能为空", TaskException.Code.UNKNOWN);
        }
        knowledgeBase.setKbName(requireName(knowledgeBase.getKbName()));
        knowledgeBase.setKbCode(resolveCreateKbCode(knowledgeBase.getKbCode()));
        knowledgeBase.setDescription(normalizeDescription(knowledgeBase.getDescription()));
        ensureUniqueName(knowledgeBase);
        ensureUniqueCode(knowledgeBase);
        Date now = new Date();
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        int rows = knowledgeBaseMapper.insertKnowledgeBase(knowledgeBase);
        if (rows > 0) {
            documentMetadataMapper.insertKnowledgeDocumentBatch(knowledgeBase.getKbId());
        }
        return rows;
    }

    @Override
    public int updateKnowledgeBase(KnowledgeBase knowledgeBase) throws TaskException {
        if (knowledgeBase == null || knowledgeBase.getKbId() == null) {
            throw new TaskException("kbId 不能为空", TaskException.Code.UNKNOWN);
        }
        KnowledgeBase existing = knowledgeBaseMapper.selectKnowledgeBaseByKbId(knowledgeBase.getKbId());
        if (existing == null) {
            throw new TaskException("知识库不存在：" + knowledgeBase.getKbId(), TaskException.Code.UNKNOWN);
        }
        knowledgeBase.setKbName(resolveUpdatedName(knowledgeBase, existing));
        knowledgeBase.setKbCode(resolveUpdatedKbCode(knowledgeBase, existing));
        knowledgeBase.setDescription(resolveUpdatedDescription(knowledgeBase, existing));
        ensureUniqueName(knowledgeBase);
        ensureUniqueCode(knowledgeBase);
        resetPublishedStateIfCodeChanged(existing, knowledgeBase);
        knowledgeBase.setUpdatedAt(new Date());
        return knowledgeBaseMapper.updateKnowledgeBase(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbIds(Long[] kbIds) throws Exception {
        if (kbIds == null || kbIds.length == 0) {
            return 0;
        }
        int affected = 0;
        for (Long kbId : kbIds) {
            affected += deleteKnowledgeBaseByKbId(kbId);
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBaseByKbId(Long kbId) throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        List<String> docIds = knowledgeDocumentMapper.selectRootDocIdByKbId(kbId);
        for (String docId : docIds) {
            knowledgeDocumentService.deleteKnowledgeDocumentByDocId(kbId, Long.valueOf(docId));
        }
        if (knowledgeBase != null && StringUtils.hasText(knowledgeBase.getKbCode())) {
            knowledgeBaseToolPublishService.disable(knowledgeBase.getKbCode());
        }
        knowledgeBasePublishBindingMapper.deleteByKbId(kbId);
        documentMetadataMapper.deleteDocumentMetadataByKbId(kbId);
        return knowledgeBaseMapper.deleteKnowledgeBaseByKbId(kbId);
    }

    @Override
    public String selectKbName(String categoryName) {
        return knowledgeBaseMapper.selectKbName(categoryName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createKnowledgeBaseWithDocument(
            KnowledgeBase knowledgeBase, MultipartFile file, String chunkStrategy, String chunkConfig) throws Exception {
        try {
            int rows = insertKnowledgeBase(knowledgeBase);
            if (rows <= 0 || knowledgeBase.getKbId() == null) {
                throw new IllegalStateException("知识库创建失败");
            }
            return knowledgeDocumentService.createKnowledgeDocumentWithFile(
                    knowledgeBase.getKbId(), null, file, chunkStrategy, chunkConfig);
        } catch (Exception ex) {
            rollbackCreateKnowledgeBase(knowledgeBase);
            throw ex;
        }
    }

    private void rollbackCreateKnowledgeBase(KnowledgeBase knowledgeBase) {
        try {
            if (knowledgeBase != null && knowledgeBase.getKbId() != null) {
                documentMetadataMapper.deleteDocumentMetadataByKbId(knowledgeBase.getKbId());
                knowledgeBaseMapper.deleteKnowledgeBaseByKbId(knowledgeBase.getKbId());
            }
        } catch (Exception ignored) {
        }
    }

    private String generateUniqueKbCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "KB" + LocalDateTime.now().format(KB_CODE_FORMATTER) + randomAlphaNumeric(4);
            if (knowledgeBaseMapper.selectKnowledgeBaseByKbCode(candidate) == null) {
                return candidate;
            }
        }
        return "KB" + System.currentTimeMillis() + randomAlphaNumeric(6);
    }

    private void ensureUniqueName(KnowledgeBase knowledgeBase) throws TaskException {
        if (knowledgeBaseMapper.checkKbNameUnique(knowledgeBase) > 0) {
            throw new TaskException("知识库名称已存在：" + knowledgeBase.getKbName(), TaskException.Code.UNKNOWN);
        }
    }

    private void ensureUniqueCode(KnowledgeBase knowledgeBase) throws TaskException {
        if (knowledgeBaseMapper.checkKbCodeUnique(knowledgeBase) > 0) {
            throw new TaskException("知识库编码已存在：" + knowledgeBase.getKbCode(), TaskException.Code.UNKNOWN);
        }
    }

    private String resolveCreateKbCode(String value) throws TaskException {
        String normalized = normalizeKbCode(value);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return generateUniqueKbCode();
    }

    private String resolveUpdatedKbCode(KnowledgeBase incoming, KnowledgeBase existing) throws TaskException {
        String normalized = normalizeKbCode(incoming.getKbCode());
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return normalizeKbCode(existing.getKbCode());
    }

    private String resolveUpdatedName(KnowledgeBase incoming, KnowledgeBase existing) throws TaskException {
        if (StringUtils.hasText(incoming.getKbName())) {
            return requireName(incoming.getKbName());
        }
        return requireName(existing.getKbName());
    }

    private String resolveUpdatedDescription(KnowledgeBase incoming, KnowledgeBase existing) {
        if (incoming.getDescription() != null) {
            return normalizeDescription(incoming.getDescription());
        }
        return normalizeDescription(existing.getDescription());
    }

    private void resetPublishedStateIfCodeChanged(KnowledgeBase existing, KnowledgeBase updated) {
        String existingCode = normalizeText(existing == null ? null : existing.getKbCode());
        String updatedCode = normalizeText(updated == null ? null : updated.getKbCode());
        if (!StringUtils.hasText(existingCode) || Objects.equals(existingCode, updatedCode)) {
            return;
        }
        knowledgeBaseToolPublishService.disable(existingCode);
        knowledgeBasePublishBindingMapper.deleteByKbId(existing.getKbId());
    }

    private String requireName(String value) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException("知识库名称不能为空", TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String normalizeKbCode(String value) throws TaskException {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (!KB_CODE_PATTERN.matcher(normalized).matches()) {
            throw new TaskException("知识库编码仅支持字母、数字、点、下划线和中划线", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        return normalizeText(value);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String randomAlphaNumeric(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return builder.toString();
    }

    private KnowledgeBase enrichKnowledgeBaseStats(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.getKbId() == null) {
            return knowledgeBase;
        }
        knowledgeBase.setDocCount(knowledgeDocumentMapper.countFileDocumentsByKbId(knowledgeBase.getKbId()));
        knowledgeBase.setCharCount(documentChunkMapper.sumCharCountByKbId(knowledgeBase.getKbId()));
        knowledgeBase.setAppCount(0L);
        applyPublishBinding(knowledgeBase, knowledgeBasePublishBindingMapper.selectByKbId(knowledgeBase.getKbId()));
        return knowledgeBase;
    }

    private void applyPublishBinding(KnowledgeBase knowledgeBase, KnowledgeBasePublishBinding binding) {
        if (knowledgeBase == null) {
            return;
        }
        if (binding == null) {
            knowledgeBase.setPublishStatus("DRAFT");
            knowledgeBase.setPublishedVersion(0);
            knowledgeBase.setLastPublishMessage("");
            knowledgeBase.setPublishedAt(null);
            knowledgeBase.setLastCompiledAt(null);
            return;
        }
        knowledgeBase.setPublishStatus(binding.getPublishStatus());
        knowledgeBase.setPublishedVersion(binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion());
        knowledgeBase.setLastPublishMessage(binding.getLastPublishMessage());
        knowledgeBase.setPublishedAt(binding.getPublishedAt());
        knowledgeBase.setLastCompiledAt(binding.getLastCompiledAt());
    }
}
