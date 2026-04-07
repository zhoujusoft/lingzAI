package lingzhou.agent.backend.business.chat.attachment;

import java.io.InputStream;

public interface AttachmentParser {

    boolean supports(String fileName);

    AttachmentParseResult parse(InputStream inputStream, String fileName) throws Exception;
}
