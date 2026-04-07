package lingzhou.agent.backend.business.datasets.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.VO.RecallChunkVo;
import lingzhou.agent.backend.business.datasets.service.IKnowledgeBaseService;
import lingzhou.agent.backend.business.datasets.service.KnowledgeBasePublishService;
import lingzhou.agent.backend.business.datasets.service.knowledge.KnowledgeChunkSearchService;
import lingzhou.agent.backend.business.datasets.service.knowledge.KnowledgeQaService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/datasets/base")
public class KnowledgeBaseController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".pdf", ".txt", ".md", ".doc", ".docx");

    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBasePublishService knowledgeBasePublishService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final KnowledgeQaService knowledgeQaService;

    public KnowledgeBaseController(
            IKnowledgeBaseService knowledgeBaseService,
            KnowledgeBasePublishService knowledgeBasePublishService,
            KnowledgeChunkSearchService knowledgeChunkSearchService,
            KnowledgeQaService knowledgeQaService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBasePublishService = knowledgeBasePublishService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.knowledgeQaService = knowledgeQaService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(KnowledgeBase knowledgeBase) {
        List<KnowledgeBase> records = knowledgeBaseService.selectKnowledgeBaseList(knowledgeBase);
        return toListResult(records);
    }

    @GetMapping("/{kbId}")
    public ResponseEntity<KnowledgeBase> getInfo(@PathVariable("kbId") Long kbId) {
        KnowledgeBase data = knowledgeBaseService.selectKnowledgeBaseByKbId(kbId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody KnowledgeBase knowledgeBase) {
        int rows = knowledgeBaseService.insertKnowledgeBase(knowledgeBase);
        return Map.of("affected", rows, "kbId", knowledgeBase.getKbId());
    }

    @PostMapping("/upload")
    public Map<String, Object> createWithDocument(
            @RequestParam("kbName") String kbName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkStrategy", defaultValue = "AUTO") String chunkStrategy,
            @RequestParam(value = "chunkConfig", required = false) String chunkConfig)
            throws Exception {
        validateFileType(file);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbName(kbName);
        knowledgeBase.setDescription(description);
        KnowledgeDocument document =
                knowledgeBaseService.createKnowledgeBaseWithDocument(knowledgeBase, file, chunkStrategy, chunkConfig);
        return Map.of(
                "affected",
                1,
                "kbId",
                knowledgeBase.getKbId(),
                "docId",
                document.getDocId(),
                "status",
                0,
                "message",
                "知识库已创建，文件已上传，请配置分块规则");
    }

    @PutMapping
    public Map<String, Object> edit(@RequestBody KnowledgeBase knowledgeBase) {
        return Map.of("affected", knowledgeBaseService.updateKnowledgeBase(knowledgeBase));
    }

    @PostMapping("/{kbId}/recall-test")
    public List<RecallChunkVo> recallTest(
            @PathVariable("kbId") Long kbId, @RequestBody(required = false) Map<String, Object> body) {
        String query = body == null || body.get("query") == null ? null : String.valueOf(body.get("query"));
        Integer topK = body == null || body.get("topK") == null ? 5 : Integer.valueOf(String.valueOf(body.get("topK")));
        return knowledgeChunkSearchService.recall(kbId, query, topK);
    }

    @GetMapping("/{kbId}/publish-status")
    public KnowledgeBasePublishService.PublishStatusView getPublishStatus(@PathVariable("kbId") Long kbId)
            throws TaskException {
        return knowledgeBasePublishService.getPublishStatus(kbId);
    }

    @PostMapping("/{kbId}/publish")
    public KnowledgeBasePublishService.PublishStatusView publish(@PathVariable("kbId") Long kbId) throws TaskException {
        return knowledgeBasePublishService.publish(kbId);
    }

    @PostMapping("/{kbId}/disable")
    public KnowledgeBasePublishService.PublishStatusView disable(@PathVariable("kbId") Long kbId) throws TaskException {
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
        return knowledgeQaService.streamAnswer(kbId, kb, request, userId);
    }

    @DeleteMapping("/{kbId}")
    public Map<String, Object> remove(@PathVariable("kbId") Long kbId) throws Exception {
        return Map.of("affected", knowledgeBaseService.deleteKnowledgeBaseByKbId(kbId));
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
                throw new TaskException("不支持的 MIME 类型：" + contentType, TaskException.Code.UNKNOWN);
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
