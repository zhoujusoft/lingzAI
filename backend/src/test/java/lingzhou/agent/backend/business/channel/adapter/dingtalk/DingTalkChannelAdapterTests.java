package lingzhou.agent.backend.business.channel.adapter.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.domain.ChannelUserBinding;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.junit.jupiter.api.Test;

class DingTalkChannelAdapterTests {

    @Test
    void textMessageMapsToChannelMessage() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).hasSize(1);
        ChannelMessage message = adapter.messages.get(0);
        assertThat(message.getMessageId()).isEqualTo("msg-1");
        assertThat(message.getChannelType()).isEqualTo("dingtalk");
        assertThat(message.getSenderId()).isEqualTo("staff-1");
        assertThat(message.getSenderName()).isEqualTo("Alice");
        assertThat(message.getExternalSessionKey()).isEqualTo("dingtalk:conv-1");
        assertThat(message.getReplyTarget()).isEqualTo("https://oapi.dingtalk.com/robot/send?token=abc");
        assertThat(message.getContent()).isEqualTo("你好");
        assertThat(message.getContentType()).isEqualTo("text");
        assertThat(message.getInputMode()).isEqualTo("text");
        assertThat(message.getMetadata()).containsEntry("conversationId", "conv-1");
    }

    @Test
    void textMessageUsesOwnerUserBindingRouteWhenAvailable() {
        ChannelConfig config = channelConfig();
        config.setOwnerUserId(7L);
        config.setRouteType("GENERAL_CHAT");
        ChannelUserBinding binding = new ChannelUserBinding();
        binding.setRouteType("SKILL_CHAT");
        binding.setRouteTargetId(123L);
        ChannelUserBindingService bindingService = mock(ChannelUserBindingService.class);
        when(bindingService.findByChannelAndUser(1L, 7L)).thenReturn(binding);
        CapturingDingTalkChannelAdapter adapter =
                new CapturingDingTalkChannelAdapter(config, null, bindingService);
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).hasSize(1);
        ChannelMessage message = adapter.messages.get(0);
        assertThat(message.getOwnerUserId()).isEqualTo(7L);
        assertThat(message.getRouteType()).isEqualTo("SKILL_CHAT");
        assertThat(message.getRouteTargetId()).isEqualTo(123L);
    }

    @Test
    void duplicateMsgIdIsIgnoredWithinTtl() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");

        adapter.processChatbotMessage(payload);
        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).hasSize(1);
    }

    @Test
    void nonTextMessageIsIgnoredByDefault() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");
        payload.setMsgtype("picture");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).isEmpty();
        assertThat(adapter.unsupportedReplies).isEmpty();
    }

    @Test
    void unsupportedMessageCanReplyTipWhenConfigured() {
        ChannelConfig config = channelConfig();
        config.setConfigJson("{\"replyUnsupported\":true}");
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(config);
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");
        payload.setMsgtype("picture");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).isEmpty();
        assertThat(adapter.unsupportedReplies).containsExactly("暂不支持处理该类型消息，请发送文本内容。");
    }

    @Test
    void startWithoutCredentialLeavesAdapterPendingWithoutThrowing() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());

        adapter.start();

        assertThat(adapter.isRunning()).isFalse();
        assertThat(adapter.getRuntimeStatus())
                .containsEntry("status", "PENDING_CREDENTIAL")
                .containsEntry("lastError", null);
    }

    @Test
    void supportsOutboundFileMessage() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());

        assertThat(adapter.supportsFileMessage()).isTrue();
    }

    @Test
    void fileMessageUsesCachedConversationContext() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());
        ChatbotMessage payload = textPayload("msg-1", "conv-1", "user-1", "Alice", "你好");
        adapter.processChatbotMessage(payload);

        adapter.sendFileMessage(1L, payload.getSessionWebhook(), "hello".getBytes(), "report.xlsx", null);

        assertThat(adapter.uploadedFileName).isEqualTo("report.xlsx");
        assertThat(adapter.uploadedBytes).isEqualTo("hello".getBytes());
        assertThat(adapter.sentContext.conversationId()).isEqualTo("conv-1");
        assertThat(adapter.sentContext.senderId()).isEqualTo("staff-1");
        assertThat(adapter.sentContext.conversationType()).isEqualTo("1");
        assertThat(adapter.sentMediaId).isEqualTo("media-1");
        assertThat(adapter.sentFileName).isEqualTo("report.xlsx");
        assertThat(adapter.outboundFileMessageEndpoint(adapter.sentContext))
                .endsWith("/v1.0/robot/oToMessages/batchSend");
//        assertThat(adapter.outboundFileMessageRequestBody(adapter.sentContext, adapter.sentMediaId, adapter.sentFileName))
//                .containsEntry("robotCode", "ding-client-1")
//                .containsEntry("msgKey", "sampleFile")
//                .containsEntry("userIds", List.of("staff-1"))
//                .doesNotContainKey("openConversationId");
    }

    @Test
    void groupFileMessageUsesRobotGroupEndpoint() throws Exception {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());
        ChatbotMessage payload = textPayload("msg-1", "conv-group-1", "user-1", "Alice", "你好");
        payload.setConversationType("2");
        adapter.processChatbotMessage(payload);

        adapter.sendFileMessage(1L, payload.getSessionWebhook(), "hello".getBytes(), "report.xlsx", null);

        assertThat(adapter.outboundFileMessageEndpoint(adapter.sentContext))
                .endsWith("/v1.0/robot/groupMessages/send");
        assertThat(adapter.outboundFileMessageRequestBody(adapter.sentContext, adapter.sentMediaId, adapter.sentFileName))
                .containsEntry("robotCode", "ding-client-1")
                .containsEntry("msgKey", "sampleFile")
                .containsEntry("openConversationId", "conv-group-1")
                .doesNotContainKey("userIds");
    }

    @Test
    void startTypingDoesNotSendPlaceholderMessage() {
        CapturingDingTalkChannelAdapter adapter = new CapturingDingTalkChannelAdapter(channelConfig());

        adapter.startTyping(1L, "https://oapi.dingtalk.com/robot/send?token=abc");

        assertThat(adapter.markdownReplies).isEmpty();
        assertThat(adapter.unsupportedReplies).isEmpty();
    }

    @Test
    void fileMessageRegistersChatAttachmentAndPropagatesFileIds() throws Exception {
        byte[] fileBytes = "name,amount\nfoo,1\n".getBytes(StandardCharsets.UTF_8);
        ChatFileService chatFileService = mock(ChatFileService.class);
        when(chatFileService.uploadBytes(eq("report.csv"), any(byte[].class), eq("text/csv"), eq(1L), eq(null)))
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
                                "chat/1/file-1/report.csv",
                                "chat-upload://chat/1/file-1/report.csv",
                                "/api/files/chat/file-1",
                                "text/csv")));
        CapturingDingTalkChannelAdapter adapter =
                new CapturingDingTalkChannelAdapter(channelConfig(), chatFileService);
        adapter.downloadUrl = "https://download.example/report.csv";
        adapter.downloadBytes = fileBytes;
        ChatbotMessage payload = filePayload("msg-file", "conv-1", "user-1", "Alice", "report.csv", "download-code-1");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).hasSize(1);
        ChannelMessage message = adapter.messages.get(0);
        assertThat(message.getInputMode()).isEqualTo("file");
        assertThat(message.getContentType()).isEqualTo("file");
        assertThat(message.getFileIds()).containsExactly("file-1");
        assertThat(message.getMetadata()).containsEntry("uploadStatus", "UPLOADED");
        assertThat(adapter.downloadRequestBody)
                .containsEntry("downloadCode", "download-code-1")
                .containsEntry("robotCode", "ding-client-1");
        assertThat(adapter.downloadRequestBody).doesNotContainKey("download_code");
    }

    @Test
    void pictureMessageWithDownloadCodeRegistersAsImageAttachment() throws Exception {
        byte[] imageBytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        ChatFileService chatFileService = mock(ChatFileService.class);
        when(chatFileService.uploadBytes(eq("dingtalk-image.jpg"), any(byte[].class), eq("image/jpeg"), eq(1L), eq(null)))
                .thenReturn(new ChatFileService.UploadResponse(
                        "file-image-1",
                        "dingtalk-image.jpg",
                        imageBytes.length,
                        null,
                        new MinioService.StoredFileDescriptor(
                                "file-image-1",
                                "dingtalk-image.jpg",
                                (long) imageBytes.length,
                                "bucket",
                                "chat/1/file-image-1/dingtalk-image.jpg",
                                "chat-upload://chat/1/file-image-1/dingtalk-image.jpg",
                                "/api/files/chat/file-image-1",
                                "image/jpeg")));
        CapturingDingTalkChannelAdapter adapter =
                new CapturingDingTalkChannelAdapter(channelConfig(), chatFileService);
        adapter.downloadUrl = "https://download.example/image.jpg";
        adapter.downloadBytes = imageBytes;
        ChatbotMessage payload = mediaPayload("msg-picture", "picture", "conv-1", "user-1", "Alice", "pic-code-1");

        adapter.processChatbotMessage(payload);

        assertThat(adapter.messages).hasSize(1);
        ChannelMessage message = adapter.messages.get(0);
        assertThat(message.getInputMode()).isEqualTo("image");
        assertThat(message.getContentType()).isEqualTo("file");
        assertThat(message.getContent()).isEqualTo("[图片] dingtalk-image.jpg");
        assertThat(message.getFileIds()).containsExactly("file-image-1");
        assertThat(message.getMetadata()).containsEntry("msgType", "picture").containsEntry("mediaType", "image");
    }

    private ChatbotMessage textPayload(
            String msgId, String conversationId, String senderId, String senderNick, String content) {
        ChatbotMessage payload = new ChatbotMessage();
        payload.setMsgId(msgId);
        payload.setConversationId(conversationId);
        payload.setSenderId(senderId);
        payload.setSenderStaffId("staff-1");
        payload.setSenderNick(senderNick);
        payload.setConversationType("1");
        payload.setConversationTitle("会话");
        payload.setSessionWebhook("https://oapi.dingtalk.com/robot/send?token=abc");
        payload.setSessionWebhookExpiredTime(1_999_999_999_000L);
        payload.setMsgtype("text");
        MessageContent text = new MessageContent();
        text.setContent(content);
        payload.setText(text);
        return payload;
    }

    private ChatbotMessage filePayload(
            String msgId,
            String conversationId,
            String senderId,
            String senderNick,
            String fileName,
            String downloadCode) {
        ChatbotMessage payload = textPayload(msgId, conversationId, senderId, senderNick, "");
        payload.setMsgtype("file");
        MessageContent file = new MessageContent();
        file.setFileName(fileName);
        file.setDownloadCode(downloadCode);
        payload.setContent(file);
        payload.setText(null);
        return payload;
    }

    private ChatbotMessage mediaPayload(
            String msgId,
            String msgType,
            String conversationId,
            String senderId,
            String senderNick,
            String downloadCode) {
        ChatbotMessage payload = textPayload(msgId, conversationId, senderId, senderNick, "");
        payload.setMsgtype(msgType);
        MessageContent content = new MessageContent();
        content.setPictureDownloadCode(downloadCode);
        payload.setContent(content);
        payload.setText(null);
        return payload;
    }

    private ChannelConfig channelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setId(1L);
        config.setChannelType("dingtalk");
        config.setRouteType("GENERAL_CHAT");
        config.setConfigJson("{\"clientId\":\"ding-client-1\",\"clientSecret\":\"secret-1\"}");
        return config;
    }

    private static final class CapturingDingTalkChannelAdapter extends DingTalkChannelAdapter {

        private final List<ChannelMessage> messages = new ArrayList<>();
        private final List<String> unsupportedReplies = new ArrayList<>();
        private final List<String> markdownReplies = new ArrayList<>();
        private byte[] uploadedBytes;
        private String uploadedFileName;
        private ReplyContext sentContext;
        private String sentMediaId;
        private String sentFileName;

        private String downloadUrl;
        private byte[] downloadBytes;
        private Map<String, Object> downloadRequestBody;

        private CapturingDingTalkChannelAdapter(ChannelConfig channelConfig) {
            this(channelConfig, null);
        }

        private CapturingDingTalkChannelAdapter(ChannelConfig channelConfig, ChatFileService chatFileService) {
            this(channelConfig, chatFileService, null);
        }

        private CapturingDingTalkChannelAdapter(
                ChannelConfig channelConfig,
                ChatFileService chatFileService,
                ChannelUserBindingService channelUserBindingService) {
            super(channelConfig, null, chatFileService, channelUserBindingService);
        }

        @Override
        public void onMessage(ChannelMessage message) {
            messages.add(message);
        }

        @Override
        protected void replyText(String webhook, String content) {
            unsupportedReplies.add(content);
        }

        @Override
        protected void replyMarkdown(String webhook, String title, String content) {
            markdownReplies.add(content);
        }

        @Override
        protected String uploadMedia(byte[] fileBytes, String fileName) {
            this.uploadedBytes = fileBytes;
            this.uploadedFileName = fileName;
            return "media-1";
        }

        @Override
        protected void sendConversationFileMessage(ReplyContext replyContext, String mediaId, String fileName) {
            this.sentContext = replyContext;
            this.sentMediaId = mediaId;
            this.sentFileName = fileName;
        }

        @Override
        protected String resolveInboundFileDownloadUrl(String downloadCode) {
            this.downloadRequestBody = inboundFileDownloadRequestBody(downloadCode);
            return downloadUrl;
        }

        @Override
        protected byte[] downloadInboundFile(String downloadUrl) {
            return downloadBytes;
        }
    }
}
