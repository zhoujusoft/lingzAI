package lingzhou.agent.backend.business.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

class ChatFileServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void readFileAsStringReadsTextFromChatUploadPath() {
        FakeMinioService minioService = new FakeMinioService();
        minioService.addObject("chat-files/1/file-1.txt", "hello from minio");
        ChatFileService service = new ChatFileService(minioService);

        String content = service.readFileAsString("chat-upload://chat-files/1/file-1.txt");

        assertThat(content).isEqualTo("hello from minio");
    }

    @Test
    void deletePersistedFilesDeletesChatUploadsAndLegacyLocalFiles() throws Exception {
        FakeMinioService minioService = new FakeMinioService();
        ChatFileService service = new ChatFileService(minioService);
        Path legacyFile = tempDir.resolve("legacy-upload.txt");
        Files.writeString(legacyFile, "legacy", StandardCharsets.UTF_8);

        List<Map<String, Object>> payload = new ArrayList<>();
        Map<String, Object> minioFile = new LinkedHashMap<>();
        minioFile.put("id", "file-1");
        minioFile.put("name", "file-1.txt");
        minioFile.put("path", "chat-upload://chat-files/1/file-1.txt");
        payload.add(minioFile);

        Map<String, Object> localFile = new LinkedHashMap<>();
        localFile.put("id", "file-2");
        localFile.put("name", "legacy-upload.txt");
        localFile.put("path", legacyFile.toString());
        payload.add(localFile);

        service.deletePersistedFiles(List.of(com.alibaba.fastjson.JSON.toJSONString(payload)));

        assertThat(minioService.deletedObjects).containsExactly("chat-files/1/file-1.txt");
        assertThat(Files.exists(legacyFile)).isFalse();
    }

    @Test
    void materializeToLocalPathCopiesChatUploadToTempFile() throws Exception {
        FakeMinioService minioService = new FakeMinioService();
        byte[] workbookBytes = "fake-xlsx-content".getBytes(StandardCharsets.UTF_8);
        minioService.addObject("chat-files/1/file-1.xlsx", workbookBytes);
        ChatFileService service = new ChatFileService(minioService);

        Path materialized = service.materializeToLocalPath("chat-upload://chat-files/1/file-1.xlsx");

        assertThat(materialized).isNotNull();
        assertThat(Files.exists(materialized)).isTrue();
        assertThat(materialized.getFileName().toString()).endsWith(".xlsx");
        assertThat(Files.readAllBytes(materialized)).isEqualTo(workbookBytes);
    }

    private static final class FakeMinioService extends MinioService {

        private final Map<String, byte[]> objects = new LinkedHashMap<>();
        private final List<String> deletedObjects = new ArrayList<>();

        void addObject(String objectName, String content) {
            objects.put(objectName, content.getBytes(StandardCharsets.UTF_8));
        }

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
            deletedObjects.add(objectName);
            objects.remove(objectName);
        }
    }
}
