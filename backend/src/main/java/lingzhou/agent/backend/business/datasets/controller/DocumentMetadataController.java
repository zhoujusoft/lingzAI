package lingzhou.agent.backend.business.datasets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.HashMap;
import java.util.Map;
import lingzhou.agent.backend.business.datasets.domain.DocumentMetadata;
import lingzhou.agent.backend.business.datasets.service.IDocumentMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/datasets/metadata")
public class DocumentMetadataController {

    private final IDocumentMetadataService documentMetadataService;

    public DocumentMetadataController(IDocumentMetadataService documentMetadataService) {
        this.documentMetadataService = documentMetadataService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            DocumentMetadata documentMetadata,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        IPage<DocumentMetadata> page =
                documentMetadataService.selectDocumentMetadataPage(documentMetadata, pageNum, pageSize);
        return toPageResult(page);
    }

    @GetMapping("/queryList")
    public Map<String, Object> queryList(
            DocumentMetadata documentMetadata,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        IPage<DocumentMetadata> page =
                documentMetadataService.selectDocumentMetadataPage(documentMetadata, pageNum, pageSize);
        return toPageResult(page);
    }

    @GetMapping("/{metadataId}")
    public ResponseEntity<DocumentMetadata> getInfo(@PathVariable("metadataId") Long metadataId) {
        DocumentMetadata data = documentMetadataService.selectDocumentMetadataByMetadataId(metadataId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody DocumentMetadata documentMetadata) {
        return Map.of("affected", documentMetadataService.insertDocumentMetadata(documentMetadata));
    }

    @PutMapping
    public Map<String, Object> edit(@RequestBody DocumentMetadata documentMetadata) {
        return Map.of("affected", documentMetadataService.updateDocumentMetadata(documentMetadata));
    }

    @DeleteMapping("/{metadataIds}")
    public Map<String, Object> remove(@PathVariable Long[] metadataIds) {
        return Map.of("affected", documentMetadataService.deleteDocumentMetadataByMetadataIds(metadataIds));
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
