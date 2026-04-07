package lingzhou.agent.backend.business.datasets.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.Embedding;

public class DocumentBlock {
    private String id;
    private List<String> headings; // 包含父级所有标题
    private String content;
    private List<String> keywords;
    private List<String> suggestedQuestions;
    private Embedding embedding;
    private String blockType;
    private String JsonStr;
    private String fileId;
    private String docId;
    private String reference;

    private String tableType;

    public DocumentBlock() {
        this.headings = new ArrayList<>();
        this.keywords = new ArrayList<>();
        this.suggestedQuestions = new ArrayList<>();
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getJsonStr() {
        return JsonStr;
    }

    public void setJsonStr(String jsonStr) {
        JsonStr = jsonStr;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getHeadings() {
        return headings;
    }

    public void setHeadings(List<String> headings) {
        this.headings = headings;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    public void setSuggestedQuestions(List<String> suggestedQuestions) {
        this.suggestedQuestions = suggestedQuestions;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    @Override
    public String toString() {
        return "DocumentBlock{" + "id='"
                + id + '\'' + ", headings="
                + headings + ", content='"
                + content + '\'' + ", keywords="
                + keywords + ", suggestedQuestions="
                + suggestedQuestions + '}';
    }
}
