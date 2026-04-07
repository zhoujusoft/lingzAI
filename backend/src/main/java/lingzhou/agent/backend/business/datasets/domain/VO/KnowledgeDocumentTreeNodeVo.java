package lingzhou.agent.backend.business.datasets.domain.VO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;

public class KnowledgeDocumentTreeNodeVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long docId;
    private Long kbId;
    private Long parentId;
    private Long isFolder;
    private String name;
    private String path;
    private List<KnowledgeDocumentTreeNodeVo> children = new ArrayList<>();

    public KnowledgeDocumentTreeNodeVo() {}

    public KnowledgeDocumentTreeNodeVo(KnowledgeDocument document) {
        this.docId = document.getDocId();
        this.kbId = document.getKbId();
        this.parentId = document.getParentId();
        this.isFolder = document.getIsFolder();
        this.name = document.getName();
        this.path = document.getPath();
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Long isFolder) {
        this.isFolder = isFolder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<KnowledgeDocumentTreeNodeVo> getChildren() {
        return children;
    }

    public void setChildren(List<KnowledgeDocumentTreeNodeVo> children) {
        this.children = children;
    }
}
