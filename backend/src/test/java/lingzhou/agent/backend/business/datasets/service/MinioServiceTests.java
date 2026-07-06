package lingzhou.agent.backend.business.datasets.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MinioServiceTests {

    @Test
    void shouldBuildMediaObjectNameWithUnifiedNamespace() {
        MinioService minioService = new MinioService();

        String objectName = minioService.buildMediaObjectName("users", 12L, "avatar", "avatar", 1716712345678L, ".png");

        assertThat(objectName).isEqualTo("media/users/12/avatar/avatar_12_1716712345678.png");
    }

    @Test
    void shouldBuildPreviewUrlFromObjectName() {
        MinioService minioService = new MinioService();

        String previewUrl = minioService.buildObjectPreviewUrl("media/users/12/avatar/avatar_12_1716712345678.png");

        assertThat(previewUrl)
                .contains("/api/files/artifacts/")
                .contains("/preview")
                .contains("fileName=avatar_12_1716712345678.png");
    }
}
