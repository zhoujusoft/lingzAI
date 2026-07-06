package lingzhou.agent.backend.business.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lingzhou.agent.backend.business.chat.attachment.FileParseMode;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.junit.jupiter.api.Test;

class AttachmentIntentHeuristicsTest {

    @Test
    void shouldPreferTextModeForPdfContentReadingRequest() {
        List<ChatFileService.UploadedFile> files =
                List.of(new ChatFileService.UploadedFile("1", "invoice.pdf", "/uploads/invoice.pdf", 1024, "obj-1"));

        FileParseMode mode = AttachmentIntentHeuristics.resolveDefaultParseMode(files, "提取这个发票内容");

        assertThat(mode).isEqualTo(FileParseMode.TEXT);
    }

    @Test
    void shouldPreferStructuredModeForZipArchiveRequest() {
        List<ChatFileService.UploadedFile> files =
                List.of(new ChatFileService.UploadedFile("1", "invoices.zip", "/uploads/invoices.zip", 1024, "obj-1"));

        FileParseMode mode = AttachmentIntentHeuristics.resolveDefaultParseMode(files, "帮我提取其中 PDF 发票文件然后压缩一下");

        assertThat(mode).isEqualTo(FileParseMode.STRUCTURED);
    }

    @Test
    void shouldOnlyEscalateZipWhenIntentRequiresExtractionOrRepack() {
        assertThat(AttachmentIntentHeuristics.shouldZipEscalateToCode("帮我提取里面的 PDF 发票并重新打包"))
                .isTrue();
        assertThat(AttachmentIntentHeuristics.shouldZipEscalateToCode("帮我看看这个压缩包里都有什么"))
                .isFalse();
    }

    @Test
    void shouldTreatRefilterAsAttachmentFollowUpInGeneralChatV2() {
        assertThat(ChatRuntimePreparedRequestAssembler.supportsAttachmentInheritance(ConversationSessionType.GENERAL_CHAT_V2))
                .isTrue();
        assertThat(ChatRuntimePreparedRequestAssembler.isAttachmentFollowUpMessage("重新筛选"))
                .isTrue();
    }
}
