package lingzhou.agent.backend.business.datasets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.KnowledgeBasePermissionService;
import lingzhou.agent.backend.business.datasets.service.KnowledgeBasePublishService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.datasets.service.knowledge.KnowledgeChunkSearchService;
import lingzhou.agent.backend.business.datasets.service.knowledge.KnowledgeQaService;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/datasets/base")
public class KnowledgeBaseController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".txt", ".md", ".doc", ".docx");

    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBasePublishService knowledgeBasePublishService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final KnowledgeQaService knowledgeQaService;
    private final MinioService minioService;
    private final KnowledgeBasePermissionService knowledgeBasePermissionService;

    public KnowledgeBaseController(
            IKnowledgeBaseService knowledgeBaseService,
            KnowledgeBasePublishService knowledgeBasePublishService,
            KnowledgeChunkSearchService knowledgeChunkSearchService,
            KnowledgeQaService knowledgeQaService,
            MinioService minioService,
            KnowledgeBasePermissionService knowledgeBasePermissionService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBasePublishService = knowledgeBasePublishService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.knowledgeQaService = knowledgeQaService;
        this.minioService = minioService;
        this.knowledgeBasePermissionService = knowledgeBasePermissionService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            KnowledgeBase knowledgeBase,
            @RequestParam(value = "page", required = false) Long page,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        SysUserModel operator = resolveOperator(request);
        if (page == null && pageSize == null) {
            List<KnowledgeBase> records = knowledgeBaseService.selectKnowledgeBaseList(knowledgeBase).stream()
                    .filter(item -> knowledgeBasePermissionService.canViewKnowledgeBase(item, operator))
                    .filter(item -> matchesKeyword(item, keyword))
                    .toList();
            return toListResult(records);
        }
        IPage<KnowledgeBase> pageData = knowledgeBaseService.selectVisibleKnowledgeBasePage(
                knowledgeBase,
                Math.max(page == null ? 1L : page, 1L),
                Math.max(1L, Math.min(pageSize == null ? 10L : pageSize, 100L)),
                keyword,
                knowledgeBasePermissionService.isAdmin(operator),
                operator == null ? null : operator.getId());
        return toPageResult(pageData);
    }

    @GetMapping("/{kbId}")
    public ResponseEntity<KnowledgeBase> getInfo(@PathVariable("kbId") Long kbId, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        KnowledgeBase data = knowledgeBasePermissionService.requireKnowledgeBase(kbId);
        knowledgeBasePermissionService.assertCanViewKnowledgeBase(data, operator);
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody KnowledgeBase knowledgeBase, HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        knowledgeBase.setOwnerUserId(userId);
        knowledgeBase.setPermissionScope(
                knowledgeBasePermissionService.normalizePermissionScope(knowledgeBase.getPermissionScope()));
        int rows = knowledgeBaseService.insertKnowledgeBase(knowledgeBase);
        return Map.of(
                "affected",
                rows,
                "kbId",
                knowledgeBase.getKbId(),
                "kbCode",
                knowledgeBase.getKbCode(),
                "kbName",
                knowledgeBase.getKbName(),
                "ownerUserId",
                knowledgeBase.getOwnerUserId(),
                "permissionScope",
                knowledgeBase.getPermissionScope());
    }

    @PostMapping("/upload")
    public Map<String, Object> createWithDocument(
            @RequestParam("kbName") String kbName,
            @RequestParam(value = "kbCode", required = false) String kbCode,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "permissionScope", required = false) Integer permissionScope,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkStrategy", defaultValue = "AUTO") String chunkStrategy,
            @RequestParam(value = "chunkConfig", required = false) String chunkConfig,
            HttpServletRequest request)
            throws Exception {
        Long userId = resolveUserId(request);
        validateFileType(file);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbName(kbName);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setDescription(description);
        knowledgeBase.setOwnerUserId(userId);
        knowledgeBase.setPermissionScope(knowledgeBasePermissionService.normalizePermissionScope(permissionScope));
        KnowledgeDocument document =
                knowledgeBaseService.createKnowledgeBaseWithDocument(knowledgeBase, file, chunkStrategy, chunkConfig);
        return Map.of(
                "affected",
                1,
                "kbId",
                knowledgeBase.getKbId(),
                "kbCode",
                knowledgeBase.getKbCode(),
                "kbName",
                knowledgeBase.getKbName(),
                "ownerUserId",
                knowledgeBase.getOwnerUserId(),
                "permissionScope",
                knowledgeBase.getPermissionScope(),
                "docId",
                document.getDocId(),
                "file",
                minioService.toKnowledgeDocumentDescriptor(
                        document.getDocId(),
                        document.getName(),
                        document.getFileSize() == null ? 0L : document.getFileSize(),
                        document.getObjectName()),
                "status",
                0,
                "message",
                "知识库已创建，文件已上传，请配置分块规则");
    }

    @PutMapping
    public Map<String, Object> edit(@RequestBody KnowledgeBase knowledgeBase, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        KnowledgeBase existing = knowledgeBasePermissionService.requireKnowledgeBase(knowledgeBase.getKbId());
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(existing, operator);
        Integer existingScope = knowledgeBasePermissionService.normalizePermissionScope(existing.getPermissionScope());
        Integer requestScope = knowledgeBase.getPermissionScope();
        if (requestScope != null) {
            int normalizedRequestScope = knowledgeBasePermissionService.normalizePermissionScope(requestScope);
            if (normalizedRequestScope != existingScope) {
                knowledgeBasePermissionService.assertCanChangePermissionScope(existing, operator);
            }
            knowledgeBase.setPermissionScope(normalizedRequestScope);
        } else {
            knowledgeBase.setPermissionScope(existingScope);
        }
        if (!knowledgeBasePermissionService.isAdmin(operator)) {
            knowledgeBase.setOwnerUserId(existing.getOwnerUserId());
        }
        int affected = knowledgeBaseService.updateKnowledgeBase(knowledgeBase);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("affected", affected);
        response.put("kbId", knowledgeBase.getKbId());
        response.put("kbCode", knowledgeBase.getKbCode());
        response.put("kbName", knowledgeBase.getKbName());
        response.put("ownerUserId", knowledgeBase.getOwnerUserId());
        response.put("permissionScope", knowledgeBase.getPermissionScope());
        return response;
    }

    @PostMapping("/{kbId}/recall-test")
    public List<RecallChunkVo> recallTest(
            @PathVariable("kbId") Long kbId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        String query = body == null || body.get("query") == null ? null : String.valueOf(body.get("query"));
        Integer topK = body == null || body.get("topK") == null ? 5 : Integer.valueOf(String.valueOf(body.get("topK")));
        return knowledgeChunkSearchService.recall(kbId, query, topK);
    }

    @GetMapping("/{kbId}/publish-status")
    public KnowledgeBasePublishService.PublishStatusView getPublishStatus(
            @PathVariable("kbId") Long kbId, HttpServletRequest request) throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeBase(kbId, operator);
        return knowledgeBasePublishService.getPublishStatus(kbId);
    }

    @PostMapping("/{kbId}/publish")
    public KnowledgeBasePublishService.PublishStatusView publish(
            @PathVariable("kbId") Long kbId, HttpServletRequest request) throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        return knowledgeBasePublishService.publish(kbId);
    }

    @PostMapping("/{kbId}/disable")
    public KnowledgeBasePublishService.PublishStatusView disable(
            @PathVariable("kbId") Long kbId, HttpServletRequest request) throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        return knowledgeBasePublishService.disable(kbId);
    }

    @PostMapping(value = "/{kbId}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQa(
            @PathVariable("kbId") Long kbId,
            @RequestBody(required = false) KnowledgeQaService.QaStreamRequest request,
            HttpServletRequest httpRequest) {
        KnowledgeBase kb = knowledgeBaseService.selectKnowledgeBaseByKbId(kbId);
        if (kb == null) {
            return Flux.just(ServerSentEvent.builder("{\"type\":\"error\",\"content\":\"知识库不存在\"}")
                    .event("error")
                    .build());
        }
        Long userId = resolveUserId(httpRequest);
        SysUserModel operator = knowledgeBasePermissionService.resolveOperator(userId);
        if (!knowledgeBasePermissionService.canViewKnowledgeBase(kb, operator)) {
            return Flux.just(ServerSentEvent.builder("{\"type\":\"error\",\"content\":\"无权限访问该知识库\"}")
                    .event("error")
                    .build());
        }
        return knowledgeQaService.streamAnswer(kbId, kb, request, userId);
    }

    @DeleteMapping("/{kbId}")
    public Map<String, Object> remove(@PathVariable("kbId") Long kbId, HttpServletRequest request) throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        return Map.of("affected", knowledgeBaseService.deleteKnowledgeBaseByKbId(kbId));
    }

    private SysUserModel resolveOperator(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        return knowledgeBasePermissionService.resolveOperator(userId);
    }

    private Map<String, Object> toListResult(List<?> records) {
        Map<String, Object> result = new HashMap<>();
        long total = records == null ? 0L : records.size();
        result.put("records", records);
        result.put("list", records);
        result.put("total", total);
        result.put("current", 1L);
        result.put("page", 1L);
        result.put("size", total);
        result.put("pageSize", total);
        result.put("pages", total > 0 ? 1L : 0L);
        return result;
    }

    private Map<String, Object> toPageResult(IPage<?> page) {
        Map<String, Object> result = new HashMap<>();
        List<?> records = page == null ? List.of() : page.getRecords();
        long total = page == null ? 0L : page.getTotal();
        long current = page == null ? 1L : page.getCurrent();
        long size = page == null ? 10L : page.getSize();
        result.put("records", records);
        result.put("list", records);
        result.put("total", total);
        result.put("current", current);
        result.put("page", current);
        result.put("size", size);
        result.put("pageSize", size);
        result.put("pages", page == null ? 0L : page.getPages());
        return result;
    }

    private boolean matchesKeyword(KnowledgeBase knowledgeBase, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        return containsKeyword(knowledgeBase.getKbName(), normalizedKeyword)
                || containsKeyword(knowledgeBase.getKbCode(), normalizedKeyword)
                || containsKeyword(knowledgeBase.getDescription(), normalizedKeyword);
    }

    private boolean containsKeyword(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword);
    }

    private void validateFileType(MultipartFile file) throws TaskException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new TaskException("文件名为空", TaskException.Code.UNKNOWN);
        }

        boolean allowed = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> filename.toLowerCase().endsWith(ext));
        if (!allowed) {
            throw new TaskException("不支持的文件类型，仅支持 PDF/TXT/MD/DOC/DOCX", TaskException.Code.UNKNOWN);
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            boolean validMimeType = contentType.equals("application/pdf")
                    || contentType.equals("text/plain")
                    || contentType.equals("text/markdown")
                    || contentType.equals("text/x-markdown")
                    || contentType.equals("application/msword")
                    || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            if (!validMimeType) {
                throw new TaskException("不支持的 MIME 类型: " + contentType, TaskException.Code.UNKNOWN);
            }
        }
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
