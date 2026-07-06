package lingzhou.agent.backend.business.chat.attachment;

import com.alibaba.fastjson.JSON;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class FileParseToolProvider {

    private final FileParseService fileParseService;

    public FileParseToolProvider(FileParseService fileParseService) {
        this.fileParseService = fileParseService;
    }

    @Tool(
            description =
                    """
                    Parse an uploaded attachment into model-readable content on demand. Use this when you need to understand the content of a binary attachment such as .docx, .xlsx or other supported office files. Do not use direct `file_read` for binary attachments.

                    Recommended usage:
                    - Use parse_file when you need to read or analyze an uploaded file, or a runtime-generated file under logical paths like /uploads, /temp, /outputs or /workspace
                    - For PDF/doc content extraction, quote extraction, summaries, or field identification from readable document text, prefer mode=`text`
                    - Use mode=`structured` mainly for tabular schema, column structure, or archive entry structure such as ZIP contents
                    - If parse_file returns FAILED or UNSUPPORTED for a binary file or runtime artifact, treat that as parsing insufficiency and switch to CODE fallback instead of stopping at the parser error
                    - Use direct `run_python` when you need to process the original file directly

                    Input path should be the uploaded file name, or a runtime logical path, for example:
                    - 报销制度.docx
                    - /uploads/报销制度.docx
                    - /temp/pdf_invoices/pdf/invoice-1.pdf
                    - /outputs/translated-output.docx
                    """)
    public String parse_file(
            @ToolParam(
                            description =
                                    "Uploaded file name, or runtime logical path such as /uploads/... /temp/... /outputs/... /workspace/...")
                    String path,
            @ToolParam(description = "Optional parse mode: structured, markdown or text") String mode) {
        RuntimeToolContext context = RuntimeToolInvocationContextHolder.get();
        FileParseResult result = fileParseService.parseUploadedFile(context, path, FileParseMode.fromValue(mode));
        return JSON.toJSONString(toPayload(result));
    }

    Map<String, Object> toPayload(FileParseResult result) {
        return FileParsePayloadBuilder.build(result);
    }
}
