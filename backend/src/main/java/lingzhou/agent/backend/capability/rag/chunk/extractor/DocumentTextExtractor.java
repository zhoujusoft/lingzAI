package lingzhou.agent.backend.capability.rag.chunk.extractor;

import java.io.InputStream;

public interface DocumentTextExtractor {

    String extractText(InputStream inputStream, String fileName) throws Exception;
}
