package lingzhou.agent.backend.business.datasets.domain.VO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RecallChunkVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private Long docId;
    private Long chunkId;
    private Double score;
    private String chunkLabel;
    private String content;
    private List<String> tags = new ArrayList<>();
    private String fileName;
    private String lawTitle;
    private String articleCn;
    private Integer articleNo;
    private Integer bm25Rank;
    private Integer vectorRank;
    private Double rrfScore;
    private Double rerankScore;
    private Boolean rerankApplied;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getChunkLabel() {
        return chunkLabel;
    }

    public void setChunkLabel(String chunkLabel) {
        this.chunkLabel = chunkLabel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLawTitle() {
        return lawTitle;
    }

    public void setLawTitle(String lawTitle) {
        this.lawTitle = lawTitle;
    }

    public String getArticleCn() {
        return articleCn;
    }

    public void setArticleCn(String articleCn) {
        this.articleCn = articleCn;
    }

    public Integer getArticleNo() {
        return articleNo;
    }

    public void setArticleNo(Integer articleNo) {
        this.articleNo = articleNo;
    }

    public Integer getBm25Rank() {
        return bm25Rank;
    }

    public void setBm25Rank(Integer bm25Rank) {
        this.bm25Rank = bm25Rank;
    }

    public Integer getVectorRank() {
        return vectorRank;
    }

    public void setVectorRank(Integer vectorRank) {
        this.vectorRank = vectorRank;
    }

    public Double getRrfScore() {
        return rrfScore;
    }

    public void setRrfScore(Double rrfScore) {
        this.rrfScore = rrfScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }

    public Boolean getRerankApplied() {
        return rerankApplied;
    }

    public void setRerankApplied(Boolean rerankApplied) {
        this.rerankApplied = rerankApplied;
    }
}
