package lingzhou.agent.spring.ai.wechat.ilink.service;

import java.io.IOException;
import java.util.Arrays;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.ContextPoolManager;
import lingzhou.agent.spring.ai.wechat.ilink.core.context.ConversationContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.exception.ILinkException;
import lingzhou.agent.spring.ai.wechat.ilink.core.http.BusinessApiClient;
import lingzhou.agent.spring.ai.wechat.ilink.core.login.LoginContext;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.ApiResponse;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.BaseInfo;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.FileItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.ImageItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.MessageItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.SendMessageRequest;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.UploadedMedia;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VideoItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.model.VoiceItem;
import lingzhou.agent.spring.ai.wechat.ilink.core.utils.RandomUtils;

public class MessageService {

    private final ILinkConfig config;
    private final BusinessApiClient apiClient;
    private final MediaService mediaService;
    private final ContextPoolManager contextPoolManager = ContextPoolManager.getInstance();

    public MessageService(ILinkConfig config, BusinessApiClient apiClient, MediaService mediaService) {
        this.config = config;
        this.apiClient = apiClient;
        this.mediaService = mediaService;
    }

    public void sendText(LoginContext loginContext, String toUserId, String text) throws IOException {
        ConversationContext ctx = requireContext(loginContext, toUserId);

        SendMessageRequest.Msg msg = new SendMessageRequest.Msg(
                toUserId,
                RandomUtils.clientId("ilink-sdk"),
                ctx.getLatestContextToken(),
                Arrays.asList(MessageItem.text(text)));

        apiClient.post(
                loginContext,
                "/ilink/bot/sendmessage",
                new SendMessageRequest(msg, new BaseInfo(config.getChannelVersion())),
                ApiResponse.class);
    }

    public void sendImage(
            LoginContext loginContext, String toUserId, byte[] imageBytes, String fileName, String caption)
            throws IOException {

        if (caption != null && !caption.isEmpty()) {
            sendText(loginContext, toUserId, caption);
        }

        ConversationContext ctx = requireContext(loginContext, toUserId);
        UploadedMedia uploaded = mediaService.uploadImage(loginContext, toUserId, imageBytes, fileName);

        ImageItem imageItem = new ImageItem();
        imageItem.setMedia(uploaded.getMedia());
        imageItem.setAeskey(uploaded.getAesKeyHex());
        imageItem.setMid_size(uploaded.getEncryptedSize());

        MessageItem item = new MessageItem();
        item.setType(2);
        item.setImage_item(imageItem);

        doSend(loginContext, toUserId, ctx.getLatestContextToken(), item);
    }

    public void sendFile(LoginContext loginContext, String toUserId, byte[] fileBytes, String fileName, String caption)
            throws IOException {

        if (caption != null && !caption.isEmpty()) {
            sendText(loginContext, toUserId, caption);
        }

        ConversationContext ctx = requireContext(loginContext, toUserId);
        UploadedMedia uploaded = mediaService.uploadFile(loginContext, toUserId, fileBytes, fileName);

        FileItem fileItem = new FileItem();
        fileItem.setMedia(uploaded.getMedia());
        fileItem.setFile_name(fileName);
        fileItem.setLen(String.valueOf(uploaded.getRawSize()));
        fileItem.setMd5(uploaded.getMd5());

        MessageItem item = new MessageItem();
        item.setType(4);
        item.setFile_item(fileItem);

        doSend(loginContext, toUserId, ctx.getLatestContextToken(), item);
    }

    public void sendVoice(
            LoginContext loginContext,
            String toUserId,
            byte[] voiceBytes,
            String fileName,
            Integer playTimeMs,
            Integer sampleRate)
            throws IOException {

        ConversationContext ctx = requireContext(loginContext, toUserId);
        UploadedMedia uploaded = mediaService.uploadVoice(loginContext, toUserId, voiceBytes, fileName);

        VoiceItem voiceItem = new VoiceItem();
        voiceItem.setMedia(uploaded.getMedia());
        voiceItem.setEncode_type(7);
        voiceItem.setPlaytime(playTimeMs);
        voiceItem.setSample_rate(sampleRate);

        MessageItem item = new MessageItem();
        item.setType(3);
        item.setVoice_item(voiceItem);

        doSend(loginContext, toUserId, ctx.getLatestContextToken(), item);
    }

    public void sendVideo(
            LoginContext loginContext,
            String toUserId,
            byte[] videoBytes,
            String fileName,
            Integer playLengthMs,
            String caption)
            throws IOException {

        if (caption != null && !caption.isEmpty()) {
            sendText(loginContext, toUserId, caption);
        }

        ConversationContext ctx = requireContext(loginContext, toUserId);
        UploadedMedia uploaded = mediaService.uploadVideo(loginContext, toUserId, videoBytes, fileName);

        VideoItem videoItem = new VideoItem();
        videoItem.setMedia(uploaded.getMedia());
        videoItem.setVideo_size(uploaded.getEncryptedSize());
        videoItem.setPlay_length(playLengthMs);
        videoItem.setVideo_md5(uploaded.getMd5());

        MessageItem item = new MessageItem();
        item.setType(5);
        item.setVideo_item(videoItem);

        doSend(loginContext, toUserId, ctx.getLatestContextToken(), item);
    }

    private ConversationContext requireContext(LoginContext loginContext, String toUserId) {
        ConversationContext ctx = contextPoolManager.get(loginContext.getBotId(), toUserId);
        if (ctx == null || !ctx.hasContextToken()) {
            throw new ILinkException("missing latest context token for userId=" + toUserId);
        }
        return ctx;
    }

    private void doSend(LoginContext loginContext, String toUserId, String contextToken, MessageItem item)
            throws IOException {

        SendMessageRequest.Msg msg = new SendMessageRequest.Msg(
                toUserId, RandomUtils.clientId("ilink-sdk"), contextToken, Arrays.asList(item));

        apiClient.post(
                loginContext,
                "/ilink/bot/sendmessage",
                new SendMessageRequest(msg, new BaseInfo(config.getChannelVersion())),
                ApiResponse.class);
    }
}
