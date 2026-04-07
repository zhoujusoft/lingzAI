package lingzhou.agent.backend.capability.rag.chunk.extractor;

import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractorFactory {

    public DocumentTextExtractor create(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return new PlainTextExtractor();
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        if (FileType.isWord(ext)) {
            return new WordTextExtractor();
        }
        if (FileType.isPdf(ext)) {
            return new PdfTextExtractor();
        }
        if (FileType.isExcel(ext)) {
            return new ExcelTextExtractor();
        }
        if (FileType.isMdFile(ext) || FileType.isTextFile(ext) || FileType.isCodeFile(ext)) {
            return new PlainTextExtractor();
        }
        return new PlainTextExtractor();
    }
}
