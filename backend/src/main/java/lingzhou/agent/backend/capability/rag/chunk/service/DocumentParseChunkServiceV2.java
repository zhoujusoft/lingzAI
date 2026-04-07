package lingzhou.agent.backend.capability.rag.chunk.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lingzhou.agent.backend.capability.rag.chunk.config.ChunkRequest;
import lingzhou.agent.backend.capability.rag.chunk.extractor.DocumentTextExtractor;
import lingzhou.agent.backend.capability.rag.chunk.extractor.DocumentTextExtractorFactory;
import lingzhou.agent.backend.capability.rag.chunk.model.ChunkedSection;
import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import lingzhou.agent.backend.capability.rag.chunk.tool.ChineseHeadingAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.LawDocumentStructureAnalyzer;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

/**
 * 纯解析 + 分块服务（不包含向量化和 ES）。
 */
@Service
@RequiredArgsConstructor
public class DocumentParseChunkServiceV2 {

    private static final Pattern TABLE_CAPTION_PATTERN = Pattern.compile("^(表|附表)\\s*\\d+.*$");
    private static final Pattern LAW_ARTICLE_PARAGRAPH_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十百千万零两〇0-9]+条)[\\s\\u3000]*(.*)$");
    private static final Pattern LAW_ARTICLE_OR_CLAUSE_HEADING_PATTERN =
            Pattern.compile("^第[一二三四五六七八九十百千万零两〇0-9]+([条款项])\\s*.*$");

    private final DocumentTextExtractorFactory extractorFactory;
    private final LawDocumentStructureAnalyzer lawDocumentStructureAnalyzer = new LawDocumentStructureAnalyzer();

    public List<ChunkedSection> parseAndChunk(InputStream inputStream, String fileName, ChunkRequest request)
            throws Exception {
        if (request == null || request.getStrategy() == null) {
            throw new IllegalArgumentException("ChunkRequest.strategy 不能为空");
        }
        if (request.getChunkSize() <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }

        byte[] bytes = inputStream.readAllBytes();

        return switch (request.getStrategy()) {
            case DELIMITER_WINDOW -> delimiterWindowChunk(new ByteArrayInputStream(bytes), fileName, request);
            case HEADING_DIRECTORY -> headingDirectoryChunk(new ByteArrayInputStream(bytes), fileName, request);
        };
    }

    private List<ChunkedSection> delimiterWindowChunk(InputStream inputStream, String fileName, ChunkRequest request)
            throws Exception {
        DocumentTextExtractor extractor = extractorFactory.create(fileName);
        String text = extractor.extractText(inputStream, fileName);

        List<String> sections =
                splitByDelimiterWindow(text, request.getDelimiter(), request.getChunkSize(), request.getOverlapSize());
        List<ChunkedSection> results = new ArrayList<>();
        int order = 1;

        for (String section : sections) {
            if (request.isDropEmpty() && StringUtils.isEmpty(section.trim())) {
                continue;
            }
            ChunkedSection cs = new ChunkedSection();
            cs.setId(UUID.randomUUID().toString());
            cs.setBlockType("WINDOW");
            cs.setContent(section.trim());
            cs.setOrderNo(order++);
            results.add(cs);
        }
        return results;
    }

    private List<ChunkedSection> headingDirectoryChunk(InputStream inputStream, String fileName, ChunkRequest request)
            throws Exception {
        String fileType = resolveFileType(fileName);
        if (FileType.DOCX.equalsIgnoreCase(fileType)) {
            List<ChunkedSection> headingSections = parseWordStructured(inputStream, request);
            return splitStructuredSections(cleanupStructuredSections(headingSections, request), request);
        }

        if (!FileType.supportsHeadingDirectory(fileType)) {
            throw new IllegalArgumentException("HEADING_DIRECTORY 仅支持 Word/PDF 文档");
        }

        DocumentTextExtractor extractor = extractorFactory.create(fileName);
        String text = extractor.extractText(inputStream, fileName);
        List<ChunkedSection> headingSections = parseStructuredText(text, request);
        return splitStructuredSections(cleanupStructuredSections(headingSections, request), request);
    }

    private List<ChunkedSection> cleanupStructuredSections(List<ChunkedSection> sections, ChunkRequest request) {
        boolean hasHeadingSection = sections.stream()
                .anyMatch(section -> section.getHeadings() != null && !section.getHeadings().isEmpty());
        if (!hasHeadingSection) {
            return sections;
        }

        List<ChunkedSection> cleaned = new ArrayList<>();
        boolean seenFirstHeading = false;
        for (ChunkedSection section : sections) {
            List<String> headings = section.getHeadings() == null ? List.of() : section.getHeadings();
            boolean hasHeadings = !headings.isEmpty();
            if (!seenFirstHeading && !hasHeadings) {
                continue;
            }
            if (hasHeadings) {
                seenFirstHeading = true;
            }

            if (isDirectorySection(headings, section.getContent())) {
                continue;
            }

            String content = section.getContent() == null ? "" : stripTocLines(section.getContent()).trim();
            if (request.isDropEmpty() && content.isEmpty()) {
                continue;
            }
            section.setContent(content);
            cleaned.add(section);
        }
        return cleaned;
    }

    private boolean isDirectorySection(List<String> headings, String content) {
        if (headings != null) {
            for (String heading : headings) {
                if (isDirectoryHeading(heading)) {
                    return true;
                }
            }
        }
        return isTocOnlyContent(content);
    }

    private boolean isDirectoryHeading(String heading) {
        String normalized = normalizeHeadingToken(heading);
        return "目录".equals(normalized)
                || "目次".equals(normalized)
                || "contents".equals(normalized)
                || "tableofcontents".equals(normalized);
    }

    private boolean isTocOnlyContent(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String[] lines = content.split("\\R");
        int tocLineCount = 0;
        int nonEmptyLineCount = 0;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonEmptyLineCount++;
            if (isTocLine(trimmed)) {
                tocLineCount++;
            }
        }
        return nonEmptyLineCount > 0 && tocLineCount == nonEmptyLineCount;
    }

    private String stripTocLines(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        List<String> keptLines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty() || isTocLine(trimmed)) {
                continue;
            }
            keptLines.add(trimmed);
        }
        return String.join("\n", keptLines);
    }

    private boolean isTocLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String normalized = line.trim();
        return normalized.matches("^.{1,120}[.．。·•⋯…]{2,}\\s*\\d+\\s*$")
                || normalized.matches("^第?[一二三四五六七八九十百千万零0-9]+[章节条款].{0,80}\\s+\\d+\\s*$")
                || normalized.matches("^(chapter|section|article)\\s+.{0,80}\\s+\\d+\\s*$");
    }

    private String normalizeHeadingToken(String heading) {
        if (heading == null) {
            return "";
        }
        return heading.replaceAll("\\s+", "").replaceAll("[：:]+$", "").toLowerCase();
    }

    private List<ChunkedSection> splitStructuredSections(List<ChunkedSection> headingSections, ChunkRequest request) {
        List<ChunkedSection> results = new ArrayList<>();
        int order = 1;

        for (ChunkedSection section : headingSections) {
            if ("TABLE".equals(section.getBlockType())) {
                String content =
                        section.getContent() == null ? "" : section.getContent().trim();
                if (request.isDropEmpty() && content.isEmpty()) {
                    continue;
                }
                section.setOrderNo(order++);
                results.add(section);
                continue;
            }

            if (shouldKeepWholeLawArticle(section, request)) {
                String content = section.getContent() == null ? "" : section.getContent().trim();
                if (request.isDropEmpty() && content.isEmpty()) {
                    continue;
                }
                results.add(buildSection(
                        section.getHeadings(),
                        "TEXT",
                        TableChunkContentSupport.formatTextContent(section.getHeadings(), content),
                        order++));
                continue;
            }

            List<String> windows =
                    splitByWindow(section.getContent(), request.getChunkSize(), request.getOverlapSize());
            for (String window : windows) {
                if (request.isDropEmpty() && StringUtils.isEmpty(window.trim())) {
                    continue;
                }
                results.add(buildSection(
                        section.getHeadings(),
                        "TEXT",
                        TableChunkContentSupport.formatTextContent(section.getHeadings(), window.trim()),
                        order++));
            }
        }

        return results;
    }

    private boolean shouldKeepWholeLawArticle(ChunkedSection section, ChunkRequest request) {
        if (!request.isLawDocument() || !request.isPreserveWholeArticle() || section == null) {
            return false;
        }
        LawDocumentStructureAnalyzer.LawMetadata metadata =
                lawDocumentStructureAnalyzer.analyze(null, section.getHeadings(), section.getContent());
        if (metadata.articleNo() == null || !lawDocumentStructureAnalyzer.isLikelyLawDocument(null, section.getHeadings())) {
            return false;
        }
        String content = section.getContent() == null ? "" : section.getContent().trim();
        if (content.isEmpty()) {
            return false;
        }
        int threshold = request.getClauseSplitThreshold() > 0
                ? request.getClauseSplitThreshold()
                : request.getArticleMaxChars();
        return threshold <= 0 || content.length() <= threshold;
    }

    private List<ChunkedSection> parseStructuredText(String text, ChunkRequest request) {
        List<ChunkedSection> sections = new ArrayList<>();
        ChineseHeadingAnalyzer analyzer = new ChineseHeadingAnalyzer();
        List<String> currentHeadings = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        String source = text == null ? "" : text;
        for (String line : source.split("\\R")) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (applyLawArticleBoundary(request, trimmed, sections, currentHeadings, textBuilder)) {
                continue;
            }

            int headingLevel = analyzer.getHeadingLevel(trimmed);
            if (headingLevel > 0) {
                flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
                while (currentHeadings.size() >= headingLevel) {
                    currentHeadings.remove(currentHeadings.size() - 1);
                }
                currentHeadings.add(trimmed);
                continue;
            }

            appendLine(textBuilder, trimmed);
        }

        flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
        return sections;
    }

    private List<ChunkedSection> parseWordStructured(InputStream inputStream, ChunkRequest request) throws Exception {
        List<ChunkedSection> sections = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            ChineseHeadingAnalyzer analyzer = new ChineseHeadingAnalyzer();
            List<String> currentHeadings = new ArrayList<>();
            StringBuilder textBuilder = new StringBuilder();
            String potentialTableCaption = null;
            String bridgeText = null;

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText() == null
                            ? ""
                            : paragraph.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }

                    if (applyLawArticleBoundary(request, text, sections, currentHeadings, textBuilder)) {
                        potentialTableCaption = null;
                        bridgeText = null;
                        continue;
                    }

                    int headingLevel = analyzer.getHeadingLevel(paragraph);
                    if (headingLevel > 0) {
                        flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
                        potentialTableCaption = null;
                        bridgeText = null;
                        while (currentHeadings.size() >= headingLevel) {
                            currentHeadings.remove(currentHeadings.size() - 1);
                        }
                        currentHeadings.add(text);
                        continue;
                    }

                    if (isTableCaption(text)) {
                        flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
                        potentialTableCaption = text;
                        bridgeText = null;
                        continue;
                    }

                    if (potentialTableCaption != null && isBridgeText(text)) {
                        bridgeText = text;
                        continue;
                    }

                    if (potentialTableCaption != null) {
                        appendLine(textBuilder, potentialTableCaption);
                        potentialTableCaption = null;
                        bridgeText = null;
                    }
                    appendLine(textBuilder, text);
                    continue;
                }

                if (!(element instanceof XWPFTable table)) {
                    continue;
                }

                flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
                if (!request.isIncludeTables()) {
                    potentialTableCaption = null;
                    bridgeText = null;
                    continue;
                }

                String caption = potentialTableCaption;
                if (caption != null && bridgeText != null) {
                    caption = caption + bridgeText;
                }
                List<String> htmlChunks = TableChunkContentSupport.toHtmlChunks(table, request.getTableMaxChars());
                for (String tableChunk : htmlChunks) {
                    String markdownTable = TableChunkContentSupport.formatTableContent(
                            currentHeadings,
                            request.isTableKeepCaption() ? caption : null,
                            tableChunk);
                    if (request.isDropEmpty() && StringUtils.isEmpty(markdownTable.trim())) {
                        continue;
                    }
                    sections.add(buildSection(currentHeadings, "TABLE", markdownTable.trim(), null));
                }
                potentialTableCaption = null;
                bridgeText = null;
            }

            if (potentialTableCaption != null) {
                appendLine(textBuilder, potentialTableCaption);
            }
            flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
        }
        return sections;
    }

    private boolean applyLawArticleBoundary(
            ChunkRequest request,
            String text,
            List<ChunkedSection> sections,
            List<String> currentHeadings,
            StringBuilder textBuilder) {
        if (request == null || !request.isLawDocument() || StringUtils.isBlank(text)) {
            return false;
        }
        java.util.regex.Matcher matcher = LAW_ARTICLE_PARAGRAPH_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return false;
        }

        flushTextSection(sections, currentHeadings, textBuilder, request.isDropEmpty());
        while (!currentHeadings.isEmpty()
                && isLawArticleOrClauseHeading(currentHeadings.get(currentHeadings.size() - 1))) {
            currentHeadings.remove(currentHeadings.size() - 1);
        }
        currentHeadings.add(matcher.group(1).trim());
        appendLine(textBuilder, text.trim());
        return true;
    }

    private boolean isLawArticleOrClauseHeading(String heading) {
        return StringUtils.isNotBlank(heading)
                && LAW_ARTICLE_OR_CLAUSE_HEADING_PATTERN.matcher(heading.trim()).matches();
    }

    private void flushTextSection(
            List<ChunkedSection> sections, List<String> headings, StringBuilder builder, boolean dropEmpty) {
        String content = builder.toString().trim();
        if (dropEmpty && content.isEmpty()) {
            builder.setLength(0);
            return;
        }
        sections.add(buildSection(headings, "TEXT", content, null));
        builder.setLength(0);
    }

    private ChunkedSection buildSection(List<String> headings, String blockType, String content, Integer orderNo) {
        ChunkedSection section = new ChunkedSection();
        section.setId(UUID.randomUUID().toString());
        section.setHeadings(new ArrayList<>(headings));
        section.setBlockType(blockType);
        section.setContent(content);
        section.setOrderNo(orderNo);
        return section;
    }

    private void appendLine(StringBuilder builder, String text) {
        if (StringUtils.isEmpty(text)) {
            return;
        }
        builder.append(text).append("\n");
    }

    private boolean isTableCaption(String text) {
        return TABLE_CAPTION_PATTERN.matcher(text).matches();
    }

    private boolean isBridgeText(String text) {
        return text.length() <= 30 && !isTableCaption(text);
    }

    private List<String> splitByDelimiter(String text, String delimiter) {
        String source = text == null ? "" : text;
        if (StringUtils.isEmpty(delimiter)) {
            return List.of(source);
        }
        String[] parts = source.split(Pattern.quote(delimiter));
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            list.add(part);
        }
        return list;
    }

    private List<String> splitByDelimiterWindow(String text, String delimiter, int chunkSize, int overlapSize) {
        if (StringUtils.isEmpty(delimiter)) {
            return splitByWindow(text, chunkSize, overlapSize);
        }

        List<String> segments = splitByDelimiter(text, delimiter);
        List<String> chunks = new ArrayList<>();
        String overlapPrefix = "";
        int index = 0;

        while (index < segments.size()) {
            StringBuilder current = new StringBuilder();
            if (StringUtils.isNotEmpty(overlapPrefix)) {
                current.append(overlapPrefix);
            }

            boolean appendedSegment = false;
            while (index < segments.size()) {
                String segment = segments.get(index);
                String normalized = segment == null ? "" : segment;
                String piece;
                if (current.length() == 0) {
                    piece = normalized;
                } else {
                    piece = delimiter + normalized;
                }

                if (current.length() == 0 && normalized.length() > chunkSize) {
                    List<String> windows = splitByWindow(normalized, chunkSize, overlapSize);
                    chunks.addAll(windows);
                    overlapPrefix = takeOverlap(windows.get(windows.size() - 1), overlapSize);
                    index++;
                    appendedSegment = false;
                    break;
                }

                if (current.length() > 0 && current.length() + piece.length() > chunkSize && appendedSegment) {
                    break;
                }

                if (current.length() + piece.length() > chunkSize && !appendedSegment) {
                    List<String> windows = splitByWindow(current + piece, chunkSize, overlapSize);
                    chunks.addAll(windows);
                    overlapPrefix = takeOverlap(windows.get(windows.size() - 1), overlapSize);
                    index++;
                    break;
                }

                current.append(piece);
                appendedSegment = true;
                index++;
            }

            if (current.length() > 0) {
                String chunk = current.toString();
                chunks.add(chunk);
                overlapPrefix = takeOverlap(chunk, overlapSize);
            }
        }

        return chunks;
    }

    private List<String> splitByWindow(String text, int chunkSize, int overlapSize) {
        String source = text == null ? "" : text;
        int overlap = Math.max(0, Math.min(overlapSize, chunkSize - 1));
        int step = chunkSize - overlap;

        List<String> chunks = new ArrayList<>();
        if (source.isEmpty()) {
            chunks.add("");
            return chunks;
        }

        for (int start = 0; start < source.length(); start += step) {
            int end = Math.min(start + chunkSize, source.length());
            chunks.add(source.substring(start, end));
            if (end >= source.length()) {
                break;
            }
        }
        return chunks;
    }

    private String takeOverlap(String text, int overlapSize) {
        if (text == null || text.isEmpty() || overlapSize <= 0) {
            return "";
        }
        int overlap = Math.max(0, Math.min(overlapSize, text.length()));
        return text.substring(text.length() - overlap);
    }

    private String resolveFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
