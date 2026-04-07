package lingzhou.agent.backend.business.datasets.domain.VO;

import java.io.Serializable;
import java.util.List;

public class AppendDocumentChunkRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String chunkContent;
    private String chunkType;
    private List<String> headings;
    private List<String> keywords;

    public String getChunkContent() {
        return chunkContent;
    }

    public void setChunkContent(String chunkContent) {
        this.chunkContent = chunkContent;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public List<String> getHeadings() {
        return headings;
    }

    public void setHeadings(List<String> headings) {
        this.headings = headings;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
