package lingzhou.agent.backend.business.datasets.domain.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;

public class KnowledgeDocumentDetailVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long docId;
    private Long kbId;
    private Long parentId;
    private String name;
    private String fileType;
    private Long fileSize;
    private String path;
    private Integer status;
    private String errorMessage;
    private String chunkStrategy;
    private String chunkConfig;
    private Long chunkCount;
    private Long charCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date uploadTime;

    public KnowledgeDocumentDetailVo() {}

    public KnowledgeDocumentDetailVo(KnowledgeDocument document) {
        this.docId = document.getDocId();
        this.kbId = document.getKbId();
        this.parentId = document.getParentId();
        this.name = document.getName();
        this.fileType = document.getFileType();
        this.fileSize = document.getFileSize();
        this.path = document.getPath();
        this.status = document.getStatus();
        this.errorMessage = document.getErrorMessage();
        this.chunkStrategy = document.getChunkStrategy();
        this.chunkConfig = document.getChunkConfig();
        this.uploadTime = document.getUploadTime();
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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public String getChunkConfig() {
        return chunkConfig;
    }

    public void setChunkConfig(String chunkConfig) {
        this.chunkConfig = chunkConfig;
    }

    public Long getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Long chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Long getCharCount() {
        return charCount;
    }

    public void setCharCount(Long charCount) {
        this.charCount = charCount;
    }

    public Date getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Date uploadTime) {
        this.uploadTime = uploadTime;
    }
}
