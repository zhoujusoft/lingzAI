package lingzhou.agent.backend.business.datasets.domain;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lingzhou.agent.backend.framework.web.BaseEntity;

/**
 * 存储文档分块对象 document_chunk
 */
@TableName("document_chunk")
public class DocumentChunk  {

    private static final long serialVersionUID = 1L;

    @TableId(value = "chunk_id", type = IdType.AUTO)
    private Long chunkId;

    private Long docId;

    private String chunkContent;

    private Integer chunkOrder;

    private String indexId;

    private Long charCount;

    private String keywords;

    private String headings;

    private String chunkType;

    private String metadataValues;

    private Object embedding;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date createdAt;

    public DocumentChunk() {}

    public DocumentChunk(DocumentBlock documentBlock) {
        this.chunkContent = documentBlock.getContent();
        this.charCount = (long) documentBlock.getContent().length();
        this.keywords = JSON.toJSONString(documentBlock.getKeywords());
        this.indexId = documentBlock.getId();
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getChunkContent() {
        return chunkContent;
    }

    public void setChunkContent(String chunkContent) {
        this.chunkContent = chunkContent;
    }

    public Integer getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public String getIndexId() {
        return indexId;
    }

    public void setIndexId(String indexId) {
        this.indexId = indexId;
    }

    public Long getCharCount() {
        return charCount;
    }

    public void setCharCount(Long charCount) {
        this.charCount = charCount;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getHeadings() {
        return headings;
    }

    public void setHeadings(String headings) {
        this.headings = headings;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public String getMetadataValues() {
        return metadataValues;
    }

    public void setMetadataValues(String metadataValues) {
        this.metadataValues = metadataValues;
    }

    public Object getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Object embedding) {
        this.embedding = embedding;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
