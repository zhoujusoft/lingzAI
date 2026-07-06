package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeV2ObservationProjectorTest {

    private final RuntimeV2ObservationProjector projector = new RuntimeV2ObservationProjector();

    @Test
    void shouldProjectHtmlFileReadIntoStructuredDocumentState() {
        String toolResult =
                """
                {
                  "success": true,
                  "action": "FILE_READ",
                  "data": {
                    "path": "/outputs/sales_monthly_report.html",
                    "content": "<!DOCTYPE html><html><head><title>月度销售与利润分析</title></head><body><h2>各产品线月度销售额与利润分析</h2><div id=\\"salesChart\\"></div><div class=\\"conclusion\\"><p>这里是结论</p></div></body></html>"
                  }
                }
                """;

        Map<String, Object> projection =
                projector.project("file_read", Map.of("arg0", "/outputs/sales_monthly_report.html"), toolResult);

        Map<String, Object> toolState = castMap(projection.get("toolState"));
        Map<String, Object> documentState = castMap(projection.get("documentState"));

        assertEquals("html", toolState.get("resultKind"));
        assertEquals("html", documentState.get("documentKind"));
        assertEquals("月度销售与利润分析", documentState.get("title"));
        assertTrue(String.valueOf(documentState.get("chartIds")).contains("salesChart"));
    }

    @Test
    void shouldMarkSchemaOnlyParseFileAsTabularSchema() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileType": "xlsx",
                  "contentView": {
                    "text": "",
                    "markdown": "",
                    "sections": [
                      {
                        "type": "sheet",
                        "name": "销售明细",
                        "rowCount": 1000,
                        "columnCount": 13,
                        "header": ["订单ID", "订单日期"]
                      }
                    ],
                    "entities": {
                      "sheetNames": ["销售明细", "销售汇总"]
                    }
                  }
                }
                """;

        Map<String, Object> projection =
                projector.project("parse_file", Map.of("arg0", "/uploads/test.xlsx", "arg1", "structured"), toolResult);

        Map<String, Object> toolState = castMap(projection.get("toolState"));
        Map<String, Object> documentState = castMap(projection.get("documentState"));

        assertEquals("tabular-schema", toolState.get("resultKind"));
        assertEquals("tabular", documentState.get("documentKind"));
        assertEquals(Boolean.FALSE, documentState.get("hasSampleRows"));
    }

    @Test
    void shouldKeepZipSchemaParseFileAsArchiveInspectionState() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileType": "zip",
                  "contentView": {
                    "contentScope": "schema-only",
                    "schemaOnly": true,
                    "sections": [
                      {
                        "type": "archive-pdf-files",
                        "name": "pdfFiles",
                        "header": ["path", "sizeBytes", "depth", "flags"],
                        "sampleRows": [["nested/a.pdf", "1024", "0", "pdf"]]
                      }
                    ],
                    "entities": {
                      "sheetNames": []
                    }
                  }
                }
                """;

        Map<String, Object> projection =
                projector.project("parse_file", Map.of("arg0", "/uploads/test.zip", "arg1", "structured"), toolResult);

        Map<String, Object> toolState = castMap(projection.get("toolState"));
        Map<String, Object> documentState = castMap(projection.get("documentState"));

        assertEquals("archive-schema", toolState.get("resultKind"));
        assertEquals("archive", documentState.get("documentKind"));
        assertEquals(Boolean.TRUE, documentState.get("schemaOnly"));
        assertEquals(Boolean.TRUE, documentState.get("hasSampleRows"));
    }

    @Test
    void shouldProjectBlockedPythonFileWriteAsRewriteSignal() {
        String toolResult =
                """
                {
                  "success": false,
                  "action": "FILE_WRITE",
                  "errorCode": "FILE_WRITE_PYTHON_BLOCKED",
                  "errorMessage": "写入的 Python 脚本包含高风险调用 `os.system(`，已被拦截。",
                  "data": {
                    "path": "/workspace/task.py"
                  }
                }
                """;

        Map<String, Object> projection =
                projector.project("file_write", Map.of("arg0", "/workspace/task.py"), toolResult);

        Map<String, Object> toolState = castMap(projection.get("toolState"));

        assertEquals("file-write-blocked", toolState.get("resultKind"));
        assertEquals("rewrite-python-script", toolState.get("recoveryHint"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
