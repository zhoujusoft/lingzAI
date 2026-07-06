package lingzhou.agent.backend.business.chat.attachment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.util.StringUtils;

final class ZipStructuredSchemaParser {

    private static final int MAX_DEPTH = 3;
    private static final int MAX_TOTAL_ENTRIES = 400;
    private static final int MAX_SECTION_ROWS = 24;
    private static final int MAX_NESTED_ZIP_BYTES = 20 * 1024 * 1024;

    AttachmentParseResult parse(Path archivePath, String fileName) {
        String normalizedFileName = StringUtils.hasText(fileName) ? fileName : "archive.zip";
        if (archivePath == null || !Files.exists(archivePath) || !Files.isRegularFile(archivePath)) {
            return AttachmentParseResult.failure(normalizedFileName, "zip", "ZIP 文件不存在或不可读取");
        }
        ParseState state = new ParseState();
        try (InputStream inputStream = Files.newInputStream(archivePath);
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            readZipStream(zipInputStream, "", 0, state);
        } catch (IOException ex) {
            return AttachmentParseResult.failure(normalizedFileName, "zip", ex.getMessage());
        }
        return toResult(normalizedFileName, state);
    }

    private void readZipStream(ZipInputStream zipInputStream, String prefix, int depth, ParseState state)
            throws IOException {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (state.entries.size() >= MAX_TOTAL_ENTRIES) {
                state.warnings.add("ZIP 条目过多，仅保留前 " + MAX_TOTAL_ENTRIES + " 个条目用于 schema");
                zipInputStream.closeEntry();
                break;
            }
            String entryName = normalizeEntryName(entry.getName());
            if (!StringUtils.hasText(entryName)) {
                zipInputStream.closeEntry();
                continue;
            }
            String logicalPath = prefix + entryName;
            String extension = extension(entryName);
            boolean directory = entry.isDirectory();
            boolean pdf = !directory && "pdf".equals(extension);
            boolean nestedZip = !directory && "zip".equals(extension);
            state.entries.add(new ArchiveEntry(
                    logicalPath, directory ? "directory" : "file", extension, entry.getSize(), depth, pdf, nestedZip));

            if (nestedZip) {
                if (depth >= MAX_DEPTH) {
                    state.warnings.add("检测到嵌套 ZIP 超过最大深度 " + MAX_DEPTH + "，后续层级未继续展开");
                    zipInputStream.closeEntry();
                    continue;
                }
                byte[] nestedBytes = readEntryBytes(zipInputStream, MAX_NESTED_ZIP_BYTES);
                if (nestedBytes == null) {
                    state.warnings.add("嵌套 ZIP 条目过大，已跳过进一步展开: " + logicalPath);
                    zipInputStream.closeEntry();
                    continue;
                }
                try (ZipInputStream nestedStream = new ZipInputStream(new ByteArrayInputStream(nestedBytes))) {
                    readZipStream(nestedStream, logicalPath + "!/", depth + 1, state);
                } catch (IOException ignored) {
                    state.warnings.add("嵌套 ZIP 解析失败，已保留外层条目但未展开: " + logicalPath);
                }
                zipInputStream.closeEntry();
            } else {
                zipInputStream.closeEntry();
            }
        }
    }

    private AttachmentParseResult toResult(String fileName, ParseState state) {
        List<ArchiveEntry> allEntries = List.copyOf(state.entries);
        List<ArchiveEntry> pdfEntries =
                allEntries.stream().filter(ArchiveEntry::pdf).toList();
        List<ArchiveEntry> nestedZipEntries =
                allEntries.stream().filter(ArchiveEntry::nestedZip).toList();

        List<AttachmentParseResult.Section> sections = new ArrayList<>();
        if (!pdfEntries.isEmpty()) {
            sections.add(new AttachmentParseResult.Section(
                    "archive-pdf-files",
                    sections.size(),
                    "pdfFiles",
                    "PDF files discovered inside archive schema",
                    pdfEntries.size(),
                    4,
                    null,
                    List.of("path", "sizeBytes", "depth", "flags"),
                    toRows(pdfEntries, true),
                    List.of()));
        }
        if (!nestedZipEntries.isEmpty()) {
            sections.add(new AttachmentParseResult.Section(
                    "archive-nested-zips",
                    sections.size(),
                    "nestedArchives",
                    "Nested ZIP files discovered inside archive schema",
                    nestedZipEntries.size(),
                    4,
                    null,
                    List.of("path", "sizeBytes", "depth", "flags"),
                    toRows(nestedZipEntries, false),
                    List.of()));
        }
        sections.add(new AttachmentParseResult.Section(
                "archive-entries",
                sections.size(),
                "archiveEntries",
                buildSummaryText(allEntries.size(), pdfEntries.size(), nestedZipEntries.size()),
                allEntries.size(),
                6,
                null,
                List.of("path", "kind", "ext", "sizeBytes", "depth", "flags"),
                toRows(allEntries, false),
                List.of()));

        Set<String> labels = new LinkedHashSet<>();
        labels.add("archive");
        if (!pdfEntries.isEmpty()) {
            labels.add("pdf");
        }
        if (!nestedZipEntries.isEmpty()) {
            labels.add("nested-zip");
        }
        labels.add("schema-only");

        return new AttachmentParseResult(
                true,
                fileName,
                "zip",
                new AttachmentParseResult.Summary(sections.size(), 0, 0, sections.size()),
                List.copyOf(sections),
                new AttachmentParseResult.Entities(
                        List.of("Archive Schema", "PDF Files", "Nested ZIP Files"),
                        List.copyOf(labels),
                        List.of(new AttachmentParseResult.EntityTable(
                                "archiveEntries",
                                List.of("path", "kind", "ext", "sizeBytes", "depth", "flags"),
                                toRows(allEntries, false))),
                        List.of()),
                List.copyOf(state.warnings),
                "");
    }

    private List<List<String>> toRows(List<ArchiveEntry> entries, boolean pdfSection) {
        return entries.stream()
                .sorted(Comparator.comparingInt(ArchiveEntry::depth).thenComparing(ArchiveEntry::path))
                .limit(MAX_SECTION_ROWS)
                .map(entry -> {
                    List<String> row = new ArrayList<>();
                    row.add(entry.path());
                    if (!pdfSection) {
                        row.add(entry.kind());
                        row.add(entry.extension());
                    }
                    row.add(formatSize(entry.sizeBytes()));
                    row.add(String.valueOf(entry.depth()));
                    row.add(buildFlags(entry));
                    return List.copyOf(row);
                })
                .toList();
    }

    private String buildSummaryText(int totalEntries, int pdfCount, int nestedZipCount) {
        return "Archive schema only. totalEntries="
                + totalEntries
                + ", pdfCount="
                + pdfCount
                + ", nestedZipCount="
                + nestedZipCount;
    }

    private String buildFlags(ArchiveEntry entry) {
        List<String> flags = new ArrayList<>();
        if (entry.pdf()) {
            flags.add("pdf");
        }
        if (entry.nestedZip()) {
            flags.add("nested-zip");
        }
        if ("directory".equals(entry.kind())) {
            flags.add("dir");
        }
        return flags.isEmpty() ? "" : String.join(",", flags);
    }

    private String formatSize(long sizeBytes) {
        return sizeBytes >= 0 ? String.valueOf(sizeBytes) : "";
    }

    private byte[] readEntryBytes(ZipInputStream zipInputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        int read;
        while ((read = zipInputStream.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > maxBytes) {
                return null;
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private String normalizeEntryName(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return "";
        }
        return entryName.replace('\\', '/').trim();
    }

    private String extension(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return "";
        }
        int dotIndex = entryName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= entryName.length() - 1) {
            return "";
        }
        return entryName.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
    }

    private record ParseState(List<ArchiveEntry> entries, Set<String> warnings) {

        private ParseState() {
            this(new ArrayList<>(), new LinkedHashSet<>());
        }
    }

    private record ArchiveEntry(
            String path, String kind, String extension, long sizeBytes, int depth, boolean pdf, boolean nestedZip) {}
}
