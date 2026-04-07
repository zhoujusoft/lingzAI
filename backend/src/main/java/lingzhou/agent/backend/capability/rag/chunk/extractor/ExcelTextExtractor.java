package lingzhou.agent.backend.capability.rag.chunk.extractor;

import java.io.InputStream;

public class ExcelTextExtractor implements DocumentTextExtractor {

    @Override
    public String extractText(InputStream inputStream, String fileName) throws Exception {
        //        try (InputStream buffered = new BufferedInputStream(inputStream, 8192)) {
        //            Document document = new ApacheTikaDocumentParser().parse(buffered);
        //            return document.text();
        //        }
        return null;
    }
}
