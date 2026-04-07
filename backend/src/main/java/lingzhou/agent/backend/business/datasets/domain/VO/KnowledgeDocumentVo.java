package lingzhou.agent.backend.business.datasets.domain.VO;

import java.io.Serializable;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;

public class KnowledgeDocumentVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long docId;
    private Long kbId;
    private String name;
    private String fileType;
    private String documentJson;
    private List<DocumentChunk> documentChunks;

    public KnowledgeDocumentVo() {}

    public KnowledgeDocumentVo(KnowledgeDocument knowledgeDocument) {
        this.docId = knowledgeDocument.getDocId();
        this.kbId = knowledgeDocument.getKbId();
        this.name = knowledgeDocument.getName();
        this.fileType = knowledgeDocument.getFileType();
        this.documentJson = knowledgeDocument.getDocumentJson();
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public List<DocumentChunk> getDocumentChunks() {
        return documentChunks;
    }

    public void setDocumentChunks(List<DocumentChunk> documentChunks) {
        this.documentChunks = documentChunks;
    }

    @Override
    public String toString() {
        return "KnowledgeDocumentVO{" + "docId="
                + docId + ", kbId="
                + kbId + ", name='"
                + name + '\'' + ", fileType='"
                + fileType + '\'' + ", documentJson='"
                + documentJson + '\'' + ", documentChunks="
                + documentChunks + '}';
    }
}
