package lingzhou.agent.backend.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageSignatureUtilsTests {

    @Test
    void shouldDetectJpegByMagicNumber() {
        byte[] content = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        ImageSignatureUtils.SupportedImageType imageType = ImageSignatureUtils.detectSupportedImageType(content);

        assertThat(imageType).isEqualTo(ImageSignatureUtils.SupportedImageType.JPEG);
    }

    @Test
    void shouldDetectPngByMagicNumber() {
        byte[] content = new byte[] {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00
        };

        ImageSignatureUtils.SupportedImageType imageType = ImageSignatureUtils.detectSupportedImageType(content);

        assertThat(imageType).isEqualTo(ImageSignatureUtils.SupportedImageType.PNG);
    }

    @Test
    void shouldReturnNullForUnsupportedImage() {
        byte[] content = new byte[] {0x00, 0x01, 0x02, 0x03};

        ImageSignatureUtils.SupportedImageType imageType = ImageSignatureUtils.detectSupportedImageType(content);

        assertThat(imageType).isNull();
    }
}
