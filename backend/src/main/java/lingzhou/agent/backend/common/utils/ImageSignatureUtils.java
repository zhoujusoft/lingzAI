package lingzhou.agent.backend.common.utils;

public final class ImageSignatureUtils {

    private ImageSignatureUtils() {}

    public static SupportedImageType detectSupportedImageType(byte[] content) {
        if (isJpeg(content)) {
            return SupportedImageType.JPEG;
        }
        if (isPng(content)) {
            return SupportedImageType.PNG;
        }
        return null;
    }

    private static boolean isJpeg(byte[] content) {
        return content != null
                && content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] content) {
        return content != null
                && content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && (content[1] & 0xFF) == 0x50
                && (content[2] & 0xFF) == 0x4E
                && (content[3] & 0xFF) == 0x47
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A;
    }

    public enum SupportedImageType {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png");

        private final String extension;
        private final String contentType;

        SupportedImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }
}
