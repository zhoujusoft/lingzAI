package lingzhou.agent.backend.business.chat.attachment;

import com.alibaba.fastjson.JSON;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AttachmentParseService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentParseService.class);

    private final ChatFileService chatFileService;
    private final AttachmentParserFactory parserFactory;

    public AttachmentParseService(ChatFileService chatFileService, AttachmentParserFactory parserFactory) {
        this.chatFileService = chatFileService;
        this.parserFactory = parserFactory;
    }

    public List<AttachmentParseResult> parseUploads(List<String> fileIds) {
        return parseResolvedFiles(chatFileService.resolveFiles(fileIds));
    }

    public List<AttachmentParseResult> parseResolvedFiles(List<ChatFileService.UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<AttachmentParseResult> results = new ArrayList<>();
        for (ChatFileService.UploadedFile file : files) {
            if (file == null || !StringUtils.hasText(file.name())) {
                continue;
            }
            parserFactory.findParser(file.name()).ifPresent(parser -> results.add(parseSingle(file, parser)));
        }
        return List.copyOf(results);
    }

    public String buildPromptContext(List<AttachmentParseResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        String payload = JSON.toJSONString(toSerializablePayload(results));
        return "\n\nSystem pre-parsed attachment context:\n" + payload
                + "\n\nThe system has already pre-parsed supported attachments. "
                + "Use the parsed attachment context above as the primary file evidence. "
                + "Do not ask the user to restate file contents that are already covered there. "
                + "If the parsed context is insufficient, explain what is missing instead of ignoring the attachment.";
    }

    public List<Map<String, Object>> toSerializablePayload(List<AttachmentParseResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (AttachmentParseResult result : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("success", result.success());
            item.put("fileName", result.fileName());
            item.put("fileType", result.fileType());
            item.put("summary", Map.of(
                    "paragraphCount", result.summary().paragraphCount(),
                    "tableCount", result.summary().tableCount(),
                    "sheetCount", result.summary().sheetCount(),
                    "sectionCount", result.summary().sectionCount()));
            item.put("sections", result.sections().stream().limit(12).map(section -> {
                Map<String, Object> sectionMap = new LinkedHashMap<>();
                sectionMap.put("type", section.type());
                sectionMap.put("index", section.index());
                if (StringUtils.hasText(section.name())) {
                    sectionMap.put("name", section.name());
                }
                if (StringUtils.hasText(section.text())) {
                    sectionMap.put("text", section.text());
                }
                if (section.rowCount() != null) {
                    sectionMap.put("rowCount", section.rowCount());
                }
                if (section.columnCount() != null) {
                    sectionMap.put("columnCount", section.columnCount());
                }
                if (section.headerRowIndex() != null) {
                    sectionMap.put("headerRowIndex", section.headerRowIndex());
                }
                if (!section.header().isEmpty()) {
                    sectionMap.put("header", section.header());
                }
                if (!section.sampleRows().isEmpty()) {
                    sectionMap.put("sampleRows", section.sampleRows());
                }
                if (!section.columns().isEmpty()) {
                    sectionMap.put("columns", section.columns().stream().limit(20).map(column -> Map.of(
                                    "index", column.index(),
                                    "name", column.name(),
                                    "inferredType", column.inferredType(),
                                    "nullCount", column.nullCount(),
                                    "totalCount", column.totalCount(),
                                    "sampleValues", column.sampleValues()))
                            .toList());
                }
                return sectionMap;
            }).toList());
            item.put("entities", Map.of(
                    "headings", result.entities().headings(),
                    "labels", result.entities().labels(),
                    "tables", result.entities().tables().stream().map(table -> Map.of(
                                    "name", table.name(),
                                    "header", table.header(),
                                    "sampleRows", table.sampleRows()))
                            .toList(),
                    "sheetNames", result.entities().sheetNames()));
            item.put("warnings", result.warnings());
            if (StringUtils.hasText(result.error())) {
                item.put("error", result.error());
            }
            payload.add(item);
        }
        return List.copyOf(payload);
    }

    private AttachmentParseResult parseSingle(ChatFileService.UploadedFile file, AttachmentParser parser) {
        String fileName = file.name();
        String fileType = extractExtension(fileName);
        try (InputStream inputStream = chatFileService.openInputStream(file)) {
            return parser.parse(inputStream, fileName);
        } catch (Exception ex) {
            logger.warn("解析聊天附件失败：fileName={}, error={}", fileName, ex.getMessage(), ex);
            return AttachmentParseResult.failure(fileName, fileType, ex.getMessage());
        }
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }
}
