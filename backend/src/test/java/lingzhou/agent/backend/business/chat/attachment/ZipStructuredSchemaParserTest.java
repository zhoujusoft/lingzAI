package lingzhou.agent.backend.business.chat.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipStructuredSchemaParserTest {

    private final ZipStructuredSchemaParser parser = new ZipStructuredSchemaParser();

    @TempDir
    Path tempDir;

    @Test
    void shouldExpandNestedZipAndExposePdfSchema() throws Exception {
        Path outerZip = tempDir.resolve("outer.zip");
        byte[] innerZipBytes = buildZipBytes(Map.of(
                "invoice-2.pdf", "pdf-2".getBytes(),
                "notes.txt", "note".getBytes()));
        writeZip(
                outerZip,
                List.of(
                        zipEntry("invoice-1.pdf", "pdf-1".getBytes()),
                        zipEntry("nested/inner.zip", innerZipBytes),
                        zipEntry("nested/readme.txt", "readme".getBytes())));

        AttachmentParseResult result = parser.parse(outerZip, "outer.zip");

        assertThat(result.success()).isTrue();
        assertThat(result.sections()).hasSizeGreaterThanOrEqualTo(3);
        AttachmentParseResult.Section pdfSection = result.sections().get(0);
        AttachmentParseResult.Section nestedSection = result.sections().get(1);
        AttachmentParseResult.Section allEntriesSection =
                result.sections().get(result.sections().size() - 1);

        assertThat(pdfSection.name()).isEqualTo("pdfFiles");
        assertThat(pdfSection.rowCount()).isEqualTo(2);
        assertThat(pdfSection.sampleRows().stream().flatMap(List::stream))
                .anyMatch(value -> value.contains("invoice-1.pdf"));
        assertThat(pdfSection.sampleRows().stream().flatMap(List::stream))
                .anyMatch(value -> value.contains("nested/inner.zip!/invoice-2.pdf"));

        assertThat(nestedSection.name()).isEqualTo("nestedArchives");
        assertThat(nestedSection.sampleRows().stream().flatMap(List::stream))
                .anyMatch(value -> value.contains("nested/inner.zip"));

        assertThat(allEntriesSection.text()).contains("Archive schema only");
        assertThat(result.entities().labels()).contains("archive", "pdf", "nested-zip", "schema-only");
        assertThat(result.entities().tables()).hasSize(1);
    }

    private byte[] buildZipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private void writeZip(Path path, List<ZipEntryData> entries) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(path))) {
            for (ZipEntryData entry : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.path()));
                zipOutputStream.write(entry.bytes());
                zipOutputStream.closeEntry();
            }
        }
    }

    private ZipEntryData zipEntry(String path, byte[] bytes) {
        return new ZipEntryData(path, bytes);
    }

    private record ZipEntryData(String path, byte[] bytes) {}
}
