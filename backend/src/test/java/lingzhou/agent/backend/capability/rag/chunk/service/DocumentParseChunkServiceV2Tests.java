package lingzhou.agent.backend.capability.rag.chunk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkStrategy;
import lingzhou.agent.backend.capability.rag.chunk.extractor.DocumentTextExtractorFactory;
import lingzhou.agent.backend.capability.rag.chunk.model.ChunkedSection;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentParseChunkServiceV2Tests {

    private final DocumentParseChunkServiceV2 service =
            new DocumentParseChunkServiceV2(new DocumentTextExtractorFactory());

//    @Test
    void delimiterWindowKeepsOverlapWhenNextSegmentIsLong() throws Exception {
        ChunkRequest request = new ChunkRequest();
        request.setStrategy(ChunkStrategy.DELIMITER_WINDOW);
        request.setDelimiter("\n\n");
        request.setChunkSize(20);
        request.setOverlapSize(5);

        String text = "AAAAABBBBBCCCCCDDDDD\n\nEEEEEFFFFFGGGGGHHHHHIIIIJJJJJKKKKKLLLLLMMMMMNNNNNOOOOO";

        List<ChunkedSection> sections = service.parseAndChunk(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "sample.txt", request);

        assertThat(sections).hasSizeGreaterThanOrEqualTo(2);
        assertThat(sections.get(0).getContent()).isEqualTo("AAAAABBBBBCCCCCDDDDD");
        assertThat(sections.get(1).getContent()).startsWith("DDDDD");
    }

    @Test
    void headingDirectorySplitsDocxByDetectedHeadings() throws Exception {
        ChunkRequest request = new ChunkRequest();
        request.setStrategy(ChunkStrategy.HEADING_DIRECTORY);
        request.setChunkSize(200);
        request.setOverlapSize(20);

        byte[] docxBytes = buildDocx(List.of(
                "员工手册",
                "2026 版",
                "目录",
                "第一章 总则..........1",
                "第二章 范围..........3",
                "第一章 总则",
                "这是第一章的内容。",
                "第二章 范围",
                "这是第二章的内容。"));

        List<ChunkedSection> sections =
                service.parseAndChunk(new ByteArrayInputStream(docxBytes), "sample.docx", request);

        assertThat(sections).hasSize(2);
        assertThat(sections).extracting(ChunkedSection::getContent).noneMatch(content -> content.contains("员工手册"));
        assertThat(sections).extracting(ChunkedSection::getContent).noneMatch(content -> content.contains("目录"));
        assertThat(sections.get(0).getHeadings()).containsExactly("第一章 总则");
        assertThat(sections.get(0).getContent()).startsWith("## 第一章 总则");
        assertThat(sections.get(0).getContent()).contains("这是第一章的内容。");
        assertThat(sections.get(1).getHeadings()).containsExactly("第二章 范围");
        assertThat(sections.get(1).getContent()).startsWith("## 第二章 范围");
        assertThat(sections.get(1).getContent()).contains("这是第二章的内容。");
    }

    @Test
    void headingDirectorySplitsPdfByDetectedHeadings() throws Exception {
        ChunkRequest request = new ChunkRequest();
        request.setStrategy(ChunkStrategy.HEADING_DIRECTORY);
        request.setChunkSize(200);
        request.setOverlapSize(20);

        byte[] pdfBytes = buildPdf(List.of(
                "Employee Handbook",
                "Contents",
                "1 General ...... 1",
                "2 Scope ...... 3",
                "1 General",
                "This is the first section.",
                "2 Scope",
                "This is the second section."));

        List<ChunkedSection> sections =
                service.parseAndChunk(new ByteArrayInputStream(pdfBytes), "sample.pdf", request);

        assertThat(sections).hasSize(2);
        assertThat(sections).extracting(ChunkedSection::getContent).noneMatch(content -> content.contains("Employee Handbook"));
        assertThat(sections).extracting(ChunkedSection::getContent).noneMatch(content -> content.contains("Contents"));
        assertThat(sections.get(0).getHeadings()).containsExactly("1 General");
        assertThat(sections.get(0).getContent()).startsWith("## 1 General");
        assertThat(sections.get(0).getContent()).contains("This is the first section.");
        assertThat(sections.get(1).getHeadings()).containsExactly("2 Scope");
        assertThat(sections.get(1).getContent()).startsWith("## 2 Scope");
        assertThat(sections.get(1).getContent()).contains("This is the second section.");
    }

    @Test
    void headingDirectoryStoresWordTablesAsHtml() throws Exception {
        ChunkRequest request = new ChunkRequest();
        request.setStrategy(ChunkStrategy.HEADING_DIRECTORY);
        request.setChunkSize(3000);
        request.setOverlapSize(20);
        request.setTableMaxChars(400);

        byte[] docxBytes = buildDocxWithTable();

        List<ChunkedSection> sections =
                service.parseAndChunk(new ByteArrayInputStream(docxBytes), "sample.docx", request);

        ChunkedSection tableSection = sections.stream()
                .filter(section -> "TABLE".equals(section.getBlockType()))
                .findFirst()
                .orElseThrow();

        assertThat(tableSection.getHeadings()).containsExactly("3 油浸式电力变压器试验");
        assertThat(tableSection.getContent()).startsWith("## 3 油浸式电力变压器试验");
        assertThat(tableSection.getContent()).contains("表 1 试验项目");
        assertThat(tableSection.getContent()).contains("<table>").contains("<td");
        assertThat(TableChunkContentSupport.toPlainText(tableSection.getBlockType(), tableSection.getContent()))
                .contains("3 油浸式电力变压器试验")
                .contains("表 1 试验项目")
                .contains("试验项目 | 要求");
    }

//    @Test
    void lawModeKeepsArticleOpeningSentenceInsideArticleChunk() throws Exception {
        Path docxPath = Path.of("/Users/xiehb/Downloads/国家法律法规数据库_批量下载_20260323_160656/中华人民共和国教育法_20210429.docx");
        assertThat(Files.exists(docxPath)).isTrue();

        ChunkRequest request = new ChunkRequest();
        request.setStrategy(ChunkStrategy.HEADING_DIRECTORY);
        request.setChunkSize(3000);
        request.setOverlapSize(50);
        request.setDocumentDomain(ChunkRequest.DOCUMENT_DOMAIN_LAW);
        request.setPreserveWholeArticle(true);
        request.setArticleMaxChars(1800);
        request.setClauseSplitThreshold(2200);

        List<ChunkedSection> sections = service.parseAndChunk(Files.newInputStream(docxPath), "中华人民共和国教育法_20210429.docx", request);

        ChunkedSection articleTwelve = sections.stream()
                .filter(section -> section.getHeadings() != null
                        && section.getHeadings().stream().anyMatch(h -> h.startsWith("第十二条")))
                .findFirst()
                .orElseThrow();

        assertThat(articleTwelve.getHeadings())
                .anyMatch(heading -> heading.replace('\u3000', ' ').replaceAll("\\s+", "").contains("第一章总则"));
        assertThat(articleTwelve.getHeadings()).doesNotContain("第一条");
        assertThat(articleTwelve.getContent()).contains("第十二条");
        assertThat(articleTwelve.getContent()).contains("国家通用语言文字为学校及其他教育机构的基本教育教学语言文字");
        assertThat(articleTwelve.getContent()).contains("实施双语教育");
        assertThat(articleTwelve.getContent()).contains("提供条件和支持");
        assertThat(articleTwelve.getContent()).doesNotContain("## 第十二条　国家通用语言文字为学校及其他教育机构的基本教育教学语言文字");
    }

    private byte[] buildDocx(List<String> paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] buildDocxWithTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("电力变压器试验标准");
            document.createParagraph().createRun().setText("3 油浸式电力变压器试验");
            document.createParagraph().createRun().setText("本章说明。");
            document.createParagraph().createRun().setText("表 1 试验项目");

            XWPFTable table = document.createTable(3, 2);
            table.getRow(0).getCell(0).setText("试验项目");
            table.getRow(0).getCell(1).setText("要求");
            table.getRow(1).getCell(0).setText("绝缘油试验");
            table.getRow(1).getCell(1).setText("合格");
            table.getRow(2).getCell(0).setText("绕组试验");
            table.getRow(2).getCell(1).setText("记录数据");

            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] buildPdf(List<String> lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 720);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
