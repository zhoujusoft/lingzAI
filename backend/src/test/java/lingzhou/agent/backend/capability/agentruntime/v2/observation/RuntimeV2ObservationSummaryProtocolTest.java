package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeV2ObservationSummaryProtocolTest {

    private final RuntimeV2ObservationSummaryProtocol protocol = new RuntimeV2ObservationSummaryProtocol();

    @Test
    void shouldSummarizeHtmlFileReadResult() {
        String toolResult =
                """
                {
                  "success": true,
                  "action": "FILE_READ",
                  "data": {
                    "path": "/outputs/report.html",
                    "content": "<!DOCTYPE html><html><head><title>月报</title></head><body><h2>销售分析</h2><div class=\\"conclusion\\"><p>这里是结论</p></div><div id=\\"chart\\"></div></body></html>"
                  }
                }
                """;

        String summary = protocol.summarize(
                "file_read",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("fileKind: html");
        assertThat(summary).contains("title: 月报");
        assertThat(summary).contains("conclusionPreview: 这里是结论");
        assertThat(summary).contains("chartIds");
    }

    @Test
    void shouldSummarizeLoadedSkillMarkdownIntoCompactEvidence() {
        String toolResult =
                """
                # 技能：报销助手

                - 运行时技能名：`expense-assistant`
                - 描述：报销助手，帮助员工和财务人员查询报销制度、理解数据集结构、检索报销相关数据。

                ## 技能使用说明

                You are operating in skill mode.
                Current skill display name: 报销助手
                Current skill runtime name: expense-assistant

                Current skill available tools:
                - knowledge_base.KB00000053.search (报销制度知识库 / 内容检索): 用于制度查询
                - dataset.DS20260420103211J78Q.search_dataset_summary (报销分析数据集 / 摘要检索): 用于确认候选对象
                - dataset.DS20260420103211J78Q.get_dataset_schema (报销分析数据集 / 结构获取): 用于确认字段

                Follow the skill instructions below. Use available tools only when needed.

                - 必须先调用 search_dataset_summary，再按需要调用 get_dataset_schema。
                - 不要直接凭空回答制度问题。
                """;

        String summary = protocol.summarize(
                "loadSkillContent",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("observationClass: skill-doc");
        assertThat(summary).contains("skillRuntimeName: expense-assistant");
        assertThat(summary).contains("skillDisplayName: 报销助手");
        assertThat(summary).contains("keyRules");
        assertThat(summary).doesNotContain("availableTools");
        assertThat(summary).doesNotContain("Current skill available tools:");
        assertThat(summary).doesNotContain("Current skill display name:");
    }

    @Test
    void shouldSummarizeKnowledgeBaseHitsIntoEvidenceList() {
        String toolResult =
                """
                {
                  "kbId": 53,
                  "kbName": "报销制度知识库",
                  "query": "出差武汉 报销标准",
                  "topK": 5,
                  "hits": [
                    {
                      "chunkId": 27618,
                      "docId": 516,
                      "documentName": "travel-reimbursement-policy.md",
                      "chunkLabel": "db2bf808 · 1112 字符",
                      "content": "武汉属于新一线城市，住宿上限 600 元/晚，市内交通 250 元/天，餐补 120 元/天。",
                      "score": 0.31
                    }
                  ]
                }
                """;

        String summary = protocol.summarize(
                "knowledge_base.KB00000053.search",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("observationClass: knowledge-base-hit");
        assertThat(summary).contains("kbName: 报销制度知识库");
        assertThat(summary).contains("hitCount: 1");
        assertThat(summary).contains("travel-reimbursement-policy.md");
        assertThat(summary).contains("snippet");
    }

    @Test
    void shouldSummarizeDatasetSummaryIntoCandidateObjects() {
        String toolResult =
                """
                {
                  "datasetId": 1,
                  "datasetName": "报销分析数据集",
                  "sourceKind": "AI_SOURCE",
                  "summary": "主表为报销申请单表，通过 claim_no 关联报销明细项表。",
                  "relationDescription": "报销申请单可关联报销明细项、制度规则和城市标准。",
                  "candidateObjects": [
                    {
                      "objectCode": "expense_claim_order",
                      "objectName": "报销申请单",
                      "objectSource": "LOWCODE",
                      "fields": ["claim_no", "department_name", "trip_city"]
                    }
                  ]
                }
                """;

        String summary = protocol.summarize(
                "dataset.DS20260420103211J78Q.search_dataset_summary",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("observationClass: dataset-summary");
        assertThat(summary).contains("datasetName: 报销分析数据集");
        assertThat(summary).contains("candidateObjects");
        assertThat(summary).contains("expense_claim_order");
    }

    @Test
    void shouldSummarizeDatasetSchemaIntoObjectAndFieldNames() {
        String toolResult =
                """
                {
                  "datasetId": 1,
                  "datasetName": "报销分析数据集",
                  "sourceKind": "AI_SOURCE",
                  "objects": [
                    {
                      "objectCode": "expense_claim_order",
                      "objectName": "报销申请单",
                      "objectSource": "LOWCODE",
                      "fields": [
                        {"fieldName": "claim_no", "fieldLabel": "报销单号"},
                        {"fieldName": "department_name", "fieldLabel": "部门名称"}
                      ],
                      "subObjects": [
                        {
                          "objectCode": "expense_claim_item",
                          "objectName": "报销明细项",
                          "fields": [
                            {"fieldName": "expense_type", "fieldLabel": "费用类型"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        String summary = protocol.summarize(
                "dataset.DS20260420103211J78Q.get_dataset_schema",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("observationClass: dataset-schema");
        assertThat(summary).contains("objectCount: 1");
        assertThat(summary).contains("fieldNames");
        assertThat(summary).contains("expense_claim_item");
    }

    @Test
    void shouldMarkTabularSchemaWithSamplesForStructuredParseFile() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileName": "sales.xlsx",
                  "fileType": "xlsx",
                  "mode": "structured",
                  "qualityHint": "NORMAL",
                  "summary": {"sheetCount": 1, "tableCount": 0, "sectionCount": 1, "paragraphCount": 0},
                  "contentView": {
                    "text": "",
                    "markdown": "",
                    "sections": [
                      {"type": "sheet", "index": 0, "name": "销售明细", "rowCount": 1000, "columnCount": 13, "header": ["订单ID", "订单日期"]}
                    ],
                    "entities": {"sheetNames": ["销售明细"]}
                  },
                  "warnings": []
                }
                """;

        String summary = protocol.summarize(
                "parse_file",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(true, 4000, ""));

        assertThat(summary).contains("sheetNames");
        assertThat(summary).contains("observationClass: tabular-schema-with-samples");
        assertThat(summary).contains("hasHeader: true");
    }

    @Test
    void shouldUseTextPreviewWhenStructuredParseFileOmitsRawText() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileName": "nested.zip",
                  "fileType": "zip",
                  "mode": "structured",
                  "qualityHint": "NORMAL",
                  "summary": {"sheetCount": 0, "tableCount": 0, "sectionCount": 1, "paragraphCount": 1},
                  "contentView": {
                    "contentScope": "schema-only",
                    "schemaOnly": true,
                    "sections": [
                      {"type": "paragraph", "index": 0, "name": "inner.zip", "textPreview": "invoice.pdf inside inner.zip"}
                    ],
                    "entities": {"sheetNames": []}
                  },
                  "warnings": []
                }
                """;

        String summary = protocol.summarize(
                "parse_file",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("textPreview");
        assertThat(summary).contains("invoice.pdf inside inner.zip");
    }

    @Test
    void shouldMarkArchiveSchemaForZipStructuredParseFile() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileName": "invoices.zip",
                  "fileType": "zip",
                  "mode": "structured",
                  "qualityHint": "NORMAL",
                  "summary": {"sheetCount": 0, "tableCount": 0, "sectionCount": 2, "paragraphCount": 2},
                  "contentView": {
                    "contentScope": "schema-only",
                    "schemaOnly": true,
                    "sections": [
                      {
                        "type": "archive-pdf-files",
                        "index": 0,
                        "name": "pdfFiles",
                        "header": ["path", "sizeBytes", "depth", "flags"],
                        "sampleRows": [["nested/a.pdf", "1024", "0", "pdf"]]
                      }
                    ],
                    "entities": {"sheetNames": []}
                  },
                  "warnings": []
                }
                """;

        String summary = protocol.summarize(
                "parse_file",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(
                        true, 4000, "帮我提取压缩包里的 PDF 发票，然后重新打包给我"));

        assertThat(summary).contains("observationClass: archive-schema");
        assertThat(summary).contains("archiveContainsPdf: true");
    }

    @Test
    void shouldEmitRewriteHintForBlockedPythonFileWrite() {
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

        String summary = protocol.summarize(
                "file_write",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(true, 4000, ""));

        assertThat(summary).contains("FILE_WRITE_PYTHON_BLOCKED");
        assertThat(summary).contains("failureKind: python-blocked");
    }

    @Test
    void shouldKeepFullTextInParseFileObservationForPdfTextMode() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileName": "invoice.pdf",
                  "fileType": "pdf",
                  "mode": "text",
                  "qualityHint": "NORMAL",
                  "summary": {"sheetCount": 0, "tableCount": 0, "sectionCount": 1, "paragraphCount": 1},
                  "contentView": {
                    "contentScope": "full-text",
                    "text": "电子发票（普通发票） 发票号码：26427909420500014029 开票日期：2026年05月06日 名称：武汉洲聚软件有限公司 名称：湖北中交武深高速公路有限公司 价税合计（小写） ¥13.29",
                    "sections": [
                      {"type": "paragraph", "index": 0, "textPreview": "电子发票（普通发票） 发票号码：26427909420500014029"}
                    ],
                    "entities": {"sheetNames": []}
                  },
                  "warnings": []
                }
                """;

        String summary = protocol.summarize(
                "parse_file",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(false, 4000, ""));

        assertThat(summary).contains("content: 电子发票（普通发票） 发票号码：26427909420500014029");
        assertThat(summary).contains("价税合计（小写） ¥13.29");
        assertThat(summary).contains("observationClass: readable-content");
        assertThat(summary).contains("readableContentAvailable: true");
    }

    @Test
    void shouldRemainArchiveSchemaForArchiveInspectionRequest() {
        String toolResult =
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "fileName": "invoices.zip",
                  "fileType": "zip",
                  "mode": "structured",
                  "qualityHint": "NORMAL",
                  "summary": {"sheetCount": 0, "tableCount": 0, "sectionCount": 2, "paragraphCount": 2},
                  "contentView": {
                    "contentScope": "schema-only",
                    "schemaOnly": true,
                    "sections": [
                      {
                        "type": "archive-pdf-files",
                        "index": 0,
                        "name": "pdfFiles",
                        "header": ["path", "sizeBytes", "depth", "flags"],
                        "sampleRows": [["nested/a.pdf", "1024", "0", "pdf"]]
                      }
                    ],
                    "entities": {"sheetNames": []}
                  },
                  "warnings": []
                }
                """;

        String summary = protocol.summarize(
                "parse_file",
                toolResult,
                new RuntimeV2ObservationSummaryProtocol.ObservationSummaryOptions(true, 4000, "帮我看看这个压缩包里都有什么内容"));

        assertThat(summary).contains("observationClass: archive-schema");
    }
}
