package lingzhou.agent.backend.business.channel.adapter.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WeComChannelAdapterTests {

    @Test
    void supportsOutboundFileMessage() {
        WeComChannelAdapter adapter = new WeComChannelAdapter(channelConfig(), null, null);

        assertThat(adapter.supportsFileMessage()).isTrue();
    }

    @Test
    void fileCallbackRegistersChatAttachmentAndPropagatesFileIds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] fileBytes = "name,amount\nfoo,1\n".getBytes(StandardCharsets.UTF_8);
        server.createContext("/file.csv", exchange -> {
            exchange.sendResponseHeaders(200, fileBytes.length);
            exchange.getResponseBody().write(fileBytes);
            exchange.close();
        });
        server.start();
        try {
            String downloadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/file.csv";
            ChatFileService chatFileService = mock(ChatFileService.class);
            when(chatFileService.uploadBytes(eq("report.csv"), any(byte[].class), eq("text/csv"), eq(7L), eq(null)))
                    .thenReturn(new ChatFileService.UploadResponse(
                            "file-1",
                            "report.csv",
                            fileBytes.length,
                            null,
                            new MinioService.StoredFileDescriptor(
                                    "file-1",
                                    "report.csv",
                                    (long) fileBytes.length,
                                    "bucket",
                                    "chat/7/file-1/report.csv",
                                    "chat-upload://chat/7/file-1/report.csv",
                                    "/api/files/chat/file-1",
                                    "text/csv")));
            CapturingWeComChannelAdapter adapter =
                    new CapturingWeComChannelAdapter(channelConfig(), null, chatFileService);
            Object runtime = runtime(adapter, 7L);
            Method handleMessageCallback =
                    WeComChannelAdapter.class.getDeclaredMethod("handleMessageCallback", runtime.getClass(), Map.class);
            handleMessageCallback.setAccessible(true);

            handleMessageCallback.invoke(
                    adapter,
                    runtime,
                    Map.of(
                            "cmd",
                            "aibot_msg_callback",
                            "headers",
                            Map.of("req_id", "req-1"),
                            "body",
                            Map.of(
                                    "msgtype",
                                    "file",
                                    "msgid",
                                    "msg-1",
                                    "from",
                                    Map.of("userid", "user-a"),
                                    "file",
                                    Map.of("filename", "report.csv", "download_url", downloadUrl))));

            assertThat(adapter.capturedMessage).isNotNull();
            assertThat(adapter.capturedMessage.getInputMode()).isEqualTo("file");
            assertThat(adapter.capturedMessage.getContentType()).isEqualTo("file");
            assertThat(adapter.capturedMessage.getFileIds()).containsExactly("file-1");
            assertThat(adapter.capturedMessage.getMetadata()).containsKey("parts");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void encryptedFileCallbackDecryptsBeforeUpload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] plainBytes = "name,amount\nbar,2\n".getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String aesKeyText = Base64.getEncoder().withoutPadding().encodeToString(aesKey);
        byte[] encryptedBytes = encryptWeComAesCbc(plainBytes, aesKey);
        server.createContext("/encrypted.csv", exchange -> {
            exchange.sendResponseHeaders(200, encryptedBytes.length);
            exchange.getResponseBody().write(encryptedBytes);
            exchange.close();
        });
        server.start();
        try {
            String downloadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/encrypted.csv";
            ChatFileService chatFileService = mock(ChatFileService.class);
            when(chatFileService.uploadBytes(eq("encrypted.csv"), any(byte[].class), eq("text/csv"), eq(7L), eq(null)))
                    .thenReturn(new ChatFileService.UploadResponse(
                            "file-2",
                            "encrypted.csv",
                            plainBytes.length,
                            null,
                            new MinioService.StoredFileDescriptor(
                                    "file-2",
                                    "encrypted.csv",
                                    (long) plainBytes.length,
                                    "bucket",
                                    "chat/7/file-2/encrypted.csv",
                                    "chat-upload://chat/7/file-2/encrypted.csv",
                                    "/api/files/chat/file-2",
                                    "text/csv")));
            CapturingWeComChannelAdapter adapter =
                    new CapturingWeComChannelAdapter(channelConfig(), null, chatFileService);
            Object runtime = runtime(adapter, 7L);
            Method handleMessageCallback =
                    WeComChannelAdapter.class.getDeclaredMethod("handleMessageCallback", runtime.getClass(), Map.class);
            handleMessageCallback.setAccessible(true);

            handleMessageCallback.invoke(
                    adapter,
                    runtime,
                    Map.of(
                            "cmd",
                            "aibot_msg_callback",
                            "headers",
                            Map.of("req_id", "req-2"),
                            "body",
                            Map.of(
                                    "msgtype",
                                    "file",
                                    "msgid",
                                    "msg-2",
                                    "from",
                                    Map.of("userid", "user-a"),
                                    "file",
                                    Map.of(
                                            "filename",
                                            "encrypted.csv",
                                            "url",
                                            downloadUrl,
                                            "aeskey",
                                            aesKeyText))));

            ArgumentCaptor<byte[]> uploadedBytes = ArgumentCaptor.forClass(byte[].class);
            verify(chatFileService)
                    .uploadBytes(eq("encrypted.csv"), uploadedBytes.capture(), eq("text/csv"), eq(7L), eq(null));
            assertThat(uploadedBytes.getValue()).isEqualTo(plainBytes);
            assertThat(adapter.capturedMessage).isNotNull();
            assertThat(adapter.capturedMessage.getFileIds()).containsExactly("file-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileCallbackFallsBackToPlainContentWhenAesKeyContentIsAlreadyPlain() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] plainBytes = "name,amount\nplain,3\n".getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String aesKeyText = Base64.getEncoder().withoutPadding().encodeToString(aesKey);
        server.createContext("/plain.csv", exchange -> {
            exchange.sendResponseHeaders(200, plainBytes.length);
            exchange.getResponseBody().write(plainBytes);
            exchange.close();
        });
        server.start();
        try {
            String downloadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/plain.csv";
            ChatFileService chatFileService = mock(ChatFileService.class);
            when(chatFileService.uploadBytes(eq("plain.csv"), any(byte[].class), eq("text/csv"), eq(7L), eq(null)))
                    .thenReturn(new ChatFileService.UploadResponse(
                            "file-3",
                            "plain.csv",
                            plainBytes.length,
                            null,
                            new MinioService.StoredFileDescriptor(
                                    "file-3",
                                    "plain.csv",
                                    (long) plainBytes.length,
                                    "bucket",
                                    "chat/7/file-3/plain.csv",
                                    "chat-upload://chat/7/file-3/plain.csv",
                                    "/api/files/chat/file-3",
                                    "text/csv")));
            CapturingWeComChannelAdapter adapter =
                    new CapturingWeComChannelAdapter(channelConfig(), null, chatFileService);
            Object runtime = runtime(adapter, 7L);
            Method handleMessageCallback =
                    WeComChannelAdapter.class.getDeclaredMethod("handleMessageCallback", runtime.getClass(), Map.class);
            handleMessageCallback.setAccessible(true);

            handleMessageCallback.invoke(
                    adapter,
                    runtime,
                    Map.of(
                            "cmd",
                            "aibot_msg_callback",
                            "headers",
                            Map.of("req_id", "req-3"),
                            "body",
                            Map.of(
                                    "msgtype",
                                    "file",
                                    "msgid",
                                    "msg-3",
                                    "from",
                                    Map.of("userid", "user-a"),
                                    "file",
                                    Map.of(
                                            "filename",
                                            "plain.csv",
                                            "url",
                                            downloadUrl,
                                            "aeskey",
                                            aesKeyText))));

            ArgumentCaptor<byte[]> uploadedBytes = ArgumentCaptor.forClass(byte[].class);
            verify(chatFileService)
                    .uploadBytes(eq("plain.csv"), uploadedBytes.capture(), eq("text/csv"), eq(7L), eq(null));
            assertThat(uploadedBytes.getValue()).isEqualTo(plainBytes);
            assertThat(adapter.capturedMessage).isNotNull();
            assertThat(adapter.capturedMessage.getFileIds()).containsExactly("file-3");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileCallbackRecoversXlsxExtensionWhenWeComOmitsFileName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] xlsxBytes = minimalXlsxBytes();
        server.createContext("/wecom-file", exchange -> {
            exchange.sendResponseHeaders(200, xlsxBytes.length);
            exchange.getResponseBody().write(xlsxBytes);
            exchange.close();
        });
        server.start();
        try {
            String downloadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/wecom-file";
            ChatFileService chatFileService = mock(ChatFileService.class);
            when(chatFileService.uploadBytes(
                            eq("wecom-file.xlsx"),
                            any(byte[].class),
                            eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                            eq(7L),
                            eq(null)))
                    .thenReturn(new ChatFileService.UploadResponse(
                            "file-4",
                            "wecom-file.xlsx",
                            xlsxBytes.length,
                            null,
                            new MinioService.StoredFileDescriptor(
                                    "file-4",
                                    "wecom-file.xlsx",
                                    (long) xlsxBytes.length,
                                    "bucket",
                                    "chat/7/file-4/wecom-file.xlsx",
                                    "chat-upload://chat/7/file-4/wecom-file.xlsx",
                                    "/api/files/chat/file-4",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
            CapturingWeComChannelAdapter adapter =
                    new CapturingWeComChannelAdapter(channelConfig(), null, chatFileService);
            Object runtime = runtime(adapter, 7L);
            Method handleMessageCallback =
                    WeComChannelAdapter.class.getDeclaredMethod("handleMessageCallback", runtime.getClass(), Map.class);
            handleMessageCallback.setAccessible(true);

            handleMessageCallback.invoke(
                    adapter,
                    runtime,
                    Map.of(
                            "cmd",
                            "aibot_msg_callback",
                            "headers",
                            Map.of("req_id", "req-4"),
                            "body",
                            Map.of(
                                    "msgtype",
                                    "file",
                                    "msgid",
                                    "msg-4",
                                    "from",
                                    Map.of("userid", "user-a"),
                                    "file",
                                    Map.of("url", downloadUrl))));

            ArgumentCaptor<byte[]> uploadedBytes = ArgumentCaptor.forClass(byte[].class);
            verify(chatFileService)
                    .uploadBytes(
                            eq("wecom-file.xlsx"),
                            uploadedBytes.capture(),
                            eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                            eq(7L),
                            eq(null));
            assertThat(uploadedBytes.getValue()).isEqualTo(xlsxBytes);
            assertThat(adapter.capturedMessage).isNotNull();
            assertThat(adapter.capturedMessage.getFileIds()).containsExactly("file-4");
        } finally {
            server.stop(0);
        }
    }

    private byte[] minimalXlsxBytes() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zipOutputStream.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zipOutputStream.write("<workbook/>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }

    private byte[] encryptWeComAesCbc(byte[] plainBytes, byte[] aesKey) throws Exception {
        int padLength = 32 - plainBytes.length % 32;
        byte[] padded = Arrays.copyOf(plainBytes, plainBytes.length + padLength);
        Arrays.fill(padded, plainBytes.length, padded.length, (byte) padLength);
        byte[] iv = new byte[16];
        System.arraycopy(aesKey, 0, iv, 0, iv.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(padded);
    }

    private ChannelConfig channelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setId(1L);
        config.setChannelType("wecom");
        config.setRouteType("GENERAL_CHAT");
        config.setConfigJson("{}");
        return config;
    }

    private Object runtime(WeComChannelAdapter adapter, Long ownerUserId) throws Exception {
        Class<?> runtimeType = Class.forName(WeComChannelAdapter.class.getName() + "$RuntimeState");
        var constructor = runtimeType.getDeclaredConstructor(Long.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ownerUserId, "bot", "secret");
    }

    private static final class CapturingWeComChannelAdapter extends WeComChannelAdapter {

        private ChannelMessage capturedMessage;

        private CapturingWeComChannelAdapter(
                ChannelConfig channelConfig,
                ChannelUserBindingService channelUserBindingService,
                ChatFileService chatFileService) {
            super(channelConfig, null, channelUserBindingService, chatFileService);
        }

        @Override
        public void onMessage(ChannelMessage message) {
            this.capturedMessage = message;
        }
    }
}
