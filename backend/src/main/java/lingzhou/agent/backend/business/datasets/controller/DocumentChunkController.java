package lingzhou.agent.backend.business.datasets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.HashMap;
import java.util.Map;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.service.IDocumentChunkService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/datasets/chunk")
public class DocumentChunkController {

    private final IDocumentChunkService documentChunkService;

    public DocumentChunkController(IDocumentChunkService documentChunkService) {
        this.documentChunkService = documentChunkService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            DocumentChunk documentChunk,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        IPage<DocumentChunk> page = documentChunkService.selectDocumentChunkPage(documentChunk, pageNum, pageSize);
        return toPageResult(page);
    }

    @GetMapping("/queryByIndex/{indexId}")
    public ResponseEntity<DocumentChunk> queryByIndex(@PathVariable("indexId") String indexId) {
        DocumentChunk chunk = documentChunkService.selectDocumentChunkByIndexId(indexId);
        if (chunk == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(chunk);
    }

    @GetMapping("/{chunkId}")
    public ResponseEntity<DocumentChunk> getInfo(@PathVariable("chunkId") Long chunkId) {
        DocumentChunk data = documentChunkService.selectDocumentChunkByChunkId(chunkId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody DocumentChunk documentChunk) {
        return Map.of(
                "affected",
                documentChunkService.insertDocumentChunk(documentChunk),
                "chunkId",
                documentChunk.getChunkId());
    }

    @PutMapping("/{kbId}")
    public Map<String, Object> edit(@PathVariable String kbId, @RequestBody DocumentChunk documentChunk)
            throws TaskException {
        return Map.of(
                "affected",
                documentChunkService.updateDocumentChunk(kbId, documentChunk),
                "chunkId",
                documentChunk == null ? null : documentChunk.getChunkId());
    }

    @DeleteMapping("/{chunkIds}")
    public Map<String, Object> remove(@PathVariable Long[] chunkIds) {
        return Map.of("affected", documentChunkService.deleteDocumentChunkByChunkIds(chunkIds));
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
