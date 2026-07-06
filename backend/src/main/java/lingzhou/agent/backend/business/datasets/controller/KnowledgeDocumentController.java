package lingzhou.agent.backend.business.datasets.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.VO.AppendDocumentChunkRequest;
import lingzhou.agent.backend.business.datasets.domain.VO.ChunkPreviewVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentDetailVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentTreeNodeVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentVo;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeDocumentService;
import lingzhou.agent.backend.business.datasets.service.KnowledgeBasePermissionService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.datasets.service.ProgressManager;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.quartz.SchedulerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/datasets/document")
public class KnowledgeDocumentController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".txt", ".md", ".doc", ".docx");

    private final IKnowledgeDocumentService knowledgeDocumentService;
    private final ProgressManager progressManager;
    private final MinioService minioService;
    private final KnowledgeBasePermissionService knowledgeBasePermissionService;

    public KnowledgeDocumentController(
            IKnowledgeDocumentService knowledgeDocumentService,
            ProgressManager progressManager,
            MinioService minioService,
            KnowledgeBasePermissionService knowledgeBasePermissionService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.progressManager = progressManager;
        this.minioService = minioService;
        this.knowledgeBasePermissionService = knowledgeBasePermissionService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            KnowledgeDocument knowledgeDocument,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        assertKbViewPermissionIfNeeded(knowledgeDocument, operator);
        IPage<KnowledgeDocument> page =
                knowledgeDocumentService.selectKnowledgeDocumentPage(knowledgeDocument, pageNum, pageSize);
        return toPageResult(page);
    }

    @GetMapping("/queryList")
    public Map<String, Object> queryList(KnowledgeDocument knowledgeDocument, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        assertKbViewPermissionIfNeeded(knowledgeDocument, operator);
        List<KnowledgeDocument> records = knowledgeDocumentService.selectKnowledgeDocumentListByKbId(knowledgeDocument);
        return toListResult(records);
    }

    @GetMapping("/{docId}")
    public ResponseEntity<KnowledgeDocument> getInfo(@PathVariable("docId") Long docId, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeDocument(docId, operator);
        KnowledgeDocument data = knowledgeDocumentService.selectKnowledgeDocumentByDocId(docId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public Map<String, Object> add(
            @RequestParam("kbId") Long kbId,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkStrategy", defaultValue = "AUTO") String chunkStrategy,
            @RequestParam(value = "chunkConfig", required = false) String chunkConfig,
            HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        validateFileType(file);
        KnowledgeDocument document = knowledgeDocumentService.createKnowledgeDocumentWithFile(
                kbId, parentId, file, chunkStrategy, chunkConfig);
        return Map.of(
                "docId",
                document.getDocId(),
                "status",
                0,
                "message",
                "文件已上传，请配置分块规则",
                "file",
                minioService.toKnowledgeDocumentDescriptor(
                        document.getDocId(),
                        document.getName(),
                        document.getFileSize() == null ? 0L : document.getFileSize(),
                        document.getObjectName()));
    }

    @PutMapping
    public Map<String, Object> edit(@RequestBody KnowledgeDocument knowledgeDocument, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        if (knowledgeDocument == null || knowledgeDocument.getDocId() == null) {
            throw new TaskException("文档ID不能为空", TaskException.Code.UNKNOWN);
        }
        knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(knowledgeDocument.getDocId(), operator);
        return Map.of("affected", knowledgeDocumentService.updateKnowledgeDocument(knowledgeDocument));
    }

    @DeleteMapping("/{docIds}")
    public Map<String, Object> remove(@PathVariable("docIds") Long[] docIds, HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        if (docIds != null) {
            for (Long docId : docIds) {
                if (docId != null) {
                    knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(docId, operator);
                }
            }
        }
        return Map.of("affected", knowledgeDocumentService.deleteKnowledgeDocumentByDocIds(docIds));
    }

    @GetMapping("/del/{kbId}/{docId}")
    public Map<String, Object> deleteWithIndex(
            @PathVariable("kbId") Long kbId, @PathVariable("docId") Long docId, HttpServletRequest request)
            throws Exception {
        return deleteDocument(kbId, docId, request);
    }

    @DeleteMapping("/{kbId}/{docId}")
    public Map<String, Object> deleteDocument(
            @PathVariable("kbId") Long kbId, @PathVariable("docId") Long docId, HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        return Map.of("affected", knowledgeDocumentService.deleteKnowledgeDocumentByDocId(kbId, docId));
    }

    @GetMapping("/tree/{kbId}")
    public List<KnowledgeDocumentTreeNodeVo> tree(@PathVariable("kbId") Long kbId, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeBase(kbId, operator);
        return knowledgeDocumentService.selectKnowledgeDocumentTree(kbId);
    }

    @GetMapping("/children/{kbId}")
    public List<KnowledgeDocument> children(
            @PathVariable("kbId") Long kbId,
            @RequestParam(value = "parentId", required = false) Long parentId,
            HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeBase(kbId, operator);
        return knowledgeDocumentService.selectKnowledgeDocumentChildren(kbId, parentId);
    }

    @PostMapping("/folder/{kbId}")
    public KnowledgeDocument createFolder(
            @PathVariable("kbId") Long kbId, @RequestBody Map<String, Object> body, HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        Long parentId = body.get("parentId") == null ? null : Long.valueOf(String.valueOf(body.get("parentId")));
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        return knowledgeDocumentService.createFolder(kbId, parentId, name);
    }

    @PutMapping("/folder/{kbId}/{docId}")
    public Map<String, Object> renameFolder(
            @PathVariable("kbId") Long kbId,
            @PathVariable("docId") Long docId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        return Map.of("affected", knowledgeDocumentService.renameFolder(kbId, docId, name));
    }

    @PutMapping("/move/{kbId}/{docId}")
    public Map<String, Object> moveNode(
            @PathVariable("kbId") Long kbId,
            @PathVariable("docId") Long docId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        Long targetParentId = body == null || body.get("targetParentId") == null
                ? null
                : Long.valueOf(String.valueOf(body.get("targetParentId")));
        return Map.of("affected", knowledgeDocumentService.moveNode(kbId, docId, targetParentId));
    }

    @DeleteMapping("/folder/{kbId}/{docId}")
    public Map<String, Object> deleteFolder(
            @PathVariable("kbId") Long kbId, @PathVariable("docId") Long docId, HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeBase(kbId, operator);
        return Map.of("affected", knowledgeDocumentService.deleteKnowledgeDocumentByDocId(kbId, docId));
    }

    @GetMapping("/preview/{docId}")
    public ResponseEntity<KnowledgeDocumentVo> preview(@PathVariable("docId") Long docId, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeDocument(docId, operator);
        KnowledgeDocumentVo data = knowledgeDocumentService.selectKnowledgeDocumentVoByDocId(docId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{docId}/detail")
    public ResponseEntity<KnowledgeDocumentDetailVo> detail(
            @PathVariable("docId") Long docId, HttpServletRequest request) throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeDocument(docId, operator);
        KnowledgeDocumentDetailVo data = knowledgeDocumentService.selectKnowledgeDocumentDetailByDocId(docId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/getProgress/{docId}")
    public Map<String, Object> getProgress(@PathVariable("docId") Long docId, HttpServletRequest request)
            throws TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanViewKnowledgeDocument(docId, operator);
        String progressJson = progressManager.getProgress(docId);
        if (progressJson != null) {
            Map<String, Object> progress = JSON.parseObject(progressJson, Map.class);
            return progress;
        }

        KnowledgeDocument document = knowledgeDocumentService.selectKnowledgeDocumentByDocId(docId);
        if (document == null) {
            return Map.of("docId", docId, "message", "文档不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("docId", docId);
        result.put("status", document.getStatus());
        result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (document.getStatus() == 0) {
            result.put("progress", 0);
            result.put("stage", "PENDING");
            result.put("message", "待处理");
        } else if (document.getStatus() == 1) {
            result.put("progress", 0);
            result.put("stage", "UNKNOWN");
            result.put("message", "处理中");
        } else if (document.getStatus() == 2) {
            result.put("progress", 100);
            result.put("stage", "COMPLETED");
            result.put("message", "处理完成");
        } else if (document.getStatus() == 3) {
            result.put("progress", 0);
            result.put("stage", "FAILED");
            result.put("message", document.getErrorMessage() != null ? document.getErrorMessage() : "处理失败");
        }

        return result;
    }

    @PostMapping("/{docId}/retry")
    public Map<String, Object> retryParse(@PathVariable("docId") Long docId, HttpServletRequest request)
            throws SchedulerException, TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(docId, operator);
        knowledgeDocumentService.retryParseDocument(docId);
        return Map.of("docId", docId, "status", 0, "message", "已重新加入解析队列");
    }

    @PostMapping("/{docId}/chunk-preview")
    public List<ChunkPreviewVo> previewChunks(
            @PathVariable("docId") Long docId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request)
            throws Exception {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(docId, operator);
        String chunkStrategy =
                body == null || body.get("chunkStrategy") == null ? null : String.valueOf(body.get("chunkStrategy"));
        String chunkConfig =
                body == null || body.get("chunkConfig") == null ? null : String.valueOf(body.get("chunkConfig"));
        return knowledgeDocumentService.previewChunks(docId, chunkStrategy, chunkConfig);
    }

    @PostMapping("/{docId}/process")
    public Map<String, Object> processDocument(
            @PathVariable("docId") Long docId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request)
            throws SchedulerException, TaskException {
        SysUserModel operator = resolveOperator(request);
        knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(docId, operator);
        String chunkStrategy =
                body == null || body.get("chunkStrategy") == null ? null : String.valueOf(body.get("chunkStrategy"));
        String chunkConfig =
                body == null || body.get("chunkConfig") == null ? null : String.valueOf(body.get("chunkConfig"));
        knowledgeDocumentService.submitChunkConfigAndParse(docId, chunkStrategy, chunkConfig);
        return Map.of("docId", docId, "status", 0, "message", "已提交分块处理任务");
    }

    @PostMapping("/{docId}/chunks")
    public Map<String, Object> appendChunk(
            @PathVariable("docId") Long docId,
            @RequestBody AppendDocumentChunkRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        SysUserModel operator = resolveOperator(httpRequest);
        knowledgeBasePermissionService.assertCanOperateKnowledgeDocument(docId, operator);
        DocumentChunk chunk = knowledgeDocumentService.appendDocumentChunk(docId, request);
        return Map.of("affected", 1, "chunkId", chunk.getChunkId());
    }

    private SysUserModel resolveOperator(HttpServletRequest request) throws TaskException {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new TaskException("用户信息缺失", TaskException.Code.UNKNOWN);
        }
        Long userId;
        if (value instanceof Number number) {
            userId = number.longValue();
        } else {
            userId = Long.valueOf(String.valueOf(value));
        }
        return knowledgeBasePermissionService.resolveOperator(userId);
    }

    private void assertKbViewPermissionIfNeeded(KnowledgeDocument knowledgeDocument, SysUserModel operator)
            throws TaskException {
        if (knowledgeDocument == null || knowledgeDocument.getKbId() == null) {
            if (!knowledgeBasePermissionService.isAdmin(operator)) {
                throw new TaskException("缺少知识库ID", TaskException.Code.UNKNOWN);
            }
            return;
        }
        knowledgeBasePermissionService.assertCanViewKnowledgeBase(knowledgeDocument.getKbId(), operator);
    }

    private void validateFileType(MultipartFile file) throws TaskException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new TaskException("文件名为空", TaskException.Code.UNKNOWN);
        }

        String normalizedFilename = filename.toLowerCase();
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(normalizedFilename::endsWith);
        if (!allowed) {
            throw new TaskException("不支持的文件类型，仅支持 PDF/TXT/MD/DOC/DOCX", TaskException.Code.UNKNOWN);
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            boolean isMarkdownFile = normalizedFilename.endsWith(".md");
            boolean validMimeType = contentType.equals("application/pdf")
                    || contentType.equals("text/plain")
                    || contentType.equals("text/markdown")
                    || contentType.equals("text/x-markdown")
                    || contentType.equals("application/msword")
                    || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || (isMarkdownFile && contentType.equals("application/octet-stream"));
            if (!validMimeType) {
                throw new TaskException("不支持的 MIME 类型: " + contentType, TaskException.Code.UNKNOWN);
            }
        }
    }

    private Map<String, Object> toListResult(List<?> records) {
        Map<String, Object> result = new HashMap<>();
        long total = records == null ? 0L : records.size();
        result.put("records", records);
        result.put("total", total);
        result.put("current", 1L);
        result.put("size", total);
        result.put("pages", total > 0 ? 1L : 0L);
        return result;
    }

    private Map<String, Object> toPageResult(IPage<?> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        result.put("pages", page.getPages());
        return result;
    }
}
