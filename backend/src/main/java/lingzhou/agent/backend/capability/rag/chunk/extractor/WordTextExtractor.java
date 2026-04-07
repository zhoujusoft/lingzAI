package lingzhou.agent.backend.capability.rag.chunk.extractor;

import java.io.InputStream;
import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

public class WordTextExtractor implements DocumentTextExtractor {

    @Override
    public String extractText(InputStream inputStream, String fileName) throws Exception {
        if (isLegacyWord(fileName)) {
            try (HWPFDocument document = new HWPFDocument(inputStream);
                    WordExtractor extractor = new WordExtractor(document)) {
                return extractor.getText();
            }
        }

        StringBuilder builder = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        builder.append(text.trim()).append("\n");
                    }
                    continue;
                }
                if (element instanceof XWPFTable table) {
                    String tableText = WordTableTextRenderer.toText(table).trim();
                    if (!tableText.isEmpty()) {
                        builder.append(tableText).append("\n");
                    }
                }
            }
        }
        return builder.toString();
    }

    private boolean isLegacyWord(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        return FileType.DOC.equalsIgnoreCase(ext);
    }
}
