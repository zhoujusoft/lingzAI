package lingzhou.agent.backend.business.datasets.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lingzhou.agent.backend.business.datasets.domain.VO.FileListModel;
import lingzhou.agent.backend.framework.web.BaseEntity;

/**
 * 存储文档信息对象 knowledge_document
 */
@TableName("knowledge_document")
public class KnowledgeDocument {

    private static final long serialVersionUID = 1L;

    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    private Long kbId;

    private Long parentId;

    private Long isFolder;

    private String name;

    private String fileId;

    private String fileType;

    private Long fileSize;

    private Long totalTokens;

    private String metadataValues;

    private String path;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date uploadTime;

    private Integer status;

    private String errorMessage;

    private String chunkStrategy;

    private String chunkConfig;

    private String documentJson;

    @TableField(exist = false)
    private String parentName;

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

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
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

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getMetadataValues() {
        return metadataValues;
    }

    public void setMetadataValues(String metadataValues) {
        this.metadataValues = metadataValues;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Date getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Date uploadTime) {
        this.uploadTime = uploadTime;
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

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public FileListModel setFileListModel() {
        return new FileListModel(getDocId(), getName(), getFileId());
    }
}
