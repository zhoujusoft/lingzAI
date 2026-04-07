package lingzhou.agent.backend.business.chat.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class AttachmentParseServiceTests {

    @Test
    void parseResolvedFilesExtractsDocxStructure() throws Exception {
        FakeMinioService minioService = new FakeMinioService();
        minioService.addObject("chat-files/1/spec.docx", createDocxBytes());
        ChatFileService chatFileService = new ChatFileService(minioService);
        AttachmentParseService service = new AttachmentParseService(
                chatFileService,
                new AttachmentParserFactory(List.of(new WordAttachmentParser(), new ExcelAttachmentParser())));

        List<AttachmentParseResult> results = service.parseResolvedFiles(List.of(
                new ChatFileService.UploadedFile(
                        "docx-1", "spec.docx", "chat-upload://chat-files/1/spec.docx", 1, "chat-files/1/spec.docx")));

        assertThat(results).hasSize(1);
        AttachmentParseResult result = results.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.fileType()).isEqualTo("docx");
        assertThat(result.summary().paragraphCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.summary().tableCount()).isEqualTo(1);
        assertThat(result.entities().headings()).contains("报销申请");
        assertThat(result.entities().tables()).hasSize(1);
    }

    @Test
    void parseResolvedFilesExtractsXlsxStructure() throws Exception {
        FakeMinioService minioService = new FakeMinioService();
        minioService.addObject("chat-files/1/template.xlsx", createXlsxBytes());
        ChatFileService chatFileService = new ChatFileService(minioService);
        AttachmentParseService service = new AttachmentParseService(
                chatFileService,
                new AttachmentParserFactory(List.of(new WordAttachmentParser(), new ExcelAttachmentParser())));

        List<AttachmentParseResult> results = service.parseResolvedFiles(List.of(
                new ChatFileService.UploadedFile(
                        "xlsx-1",
                        "template.xlsx",
                        "chat-upload://chat-files/1/template.xlsx",
                        1,
                        "chat-files/1/template.xlsx")));

        assertThat(results).hasSize(1);
        AttachmentParseResult result = results.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.fileType()).isEqualTo("xlsx");
        assertThat(result.summary().sheetCount()).isEqualTo(1);
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().get(0).columns()).extracting(AttachmentParseResult.Column::name)
                .contains("申请日期", "金额");
        assertThat(result.sections().get(0).columns()).extracting(AttachmentParseResult.Column::inferredType)
                .contains("date", "number");
    }

    @Test
    void buildPromptContextIncludesSerializableAttachmentPayload() throws Exception {
        FakeMinioService minioService = new FakeMinioService();
        minioService.addObject("chat-files/1/template.xlsx", createXlsxBytes());
        ChatFileService chatFileService = new ChatFileService(minioService);
        AttachmentParseService service = new AttachmentParseService(
                chatFileService,
                new AttachmentParserFactory(List.of(new WordAttachmentParser(), new ExcelAttachmentParser())));
        List<AttachmentParseResult> results = service.parseResolvedFiles(List.of(
                new ChatFileService.UploadedFile(
                        "xlsx-1",
                        "template.xlsx",
                        "chat-upload://chat-files/1/template.xlsx",
                        1,
                        "chat-files/1/template.xlsx")));

        String promptContext = service.buildPromptContext(results);

        assertThat(promptContext).contains("System pre-parsed attachment context");
        assertThat(promptContext).contains("template.xlsx");
        assertThat(promptContext).contains("申请日期");
    }

    private byte[] createDocxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("报销申请");
            document.createParagraph().createRun().setText("基本信息：申请人、部门、申请日期");
            XWPFTable table = document.createTable(2, 3);
            table.getRow(0).getCell(0).setText("项目");
            table.getRow(0).getCell(1).setText("金额");
            table.getRow(0).getCell(2).setText("备注");
            table.getRow(1).getCell(0).setText("交通");
            table.getRow(1).getCell(1).setText("120");
            table.getRow(1).getCell(2).setText("出租车");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createXlsxBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("报销单");
            CellStyle dateStyle = workbook.createCellStyle();
            short dataFormat = workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd");
            dateStyle.setDataFormat(dataFormat);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("申请日期");
            header.createCell(1).setCellValue("金额");
            header.createCell(2).setCellValue("类型");
            header.createCell(3).setCellValue("备注");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(LocalDate.of(2026, 3, 20));
            row1.getCell(0).setCellStyle(dateStyle);
            row1.createCell(1).setCellValue(120.5);
            row1.createCell(2).setCellValue("交通");
            row1.createCell(3).setCellValue("出租车");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(LocalDate.of(2026, 3, 21));
            row2.getCell(0).setCellStyle(dateStyle);
            row2.createCell(1).setCellValue(88.0);
            row2.createCell(2).setCellValue("餐饮");
            row2.createCell(3).setCellValue("午餐");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static final class FakeMinioService extends MinioService {

        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        void addObject(String objectName, byte[] content) {
            objects.put(objectName, content);
        }

        @Override
        public String uploadChatFile(MultipartFile file, Long userId, String fileId) {
            throw new UnsupportedOperationException("Not needed in this test");
        }

        @Override
        public InputStream getFile(String objectName) {
            byte[] content = objects.get(objectName);
            return new ByteArrayInputStream(content == null ? new byte[0] : content);
        }

        @Override
        public void deleteFile(String objectName) {
            objects.remove(objectName);
        }
    }
}
