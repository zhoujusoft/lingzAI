package lingzhou.agent.backend.capability.rag.chunk.extractor;

import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfTextExtractor implements DocumentTextExtractor {

    @Override
    public String extractText(InputStream inputStream, String fileName) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
