package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RagRetrievalProperties {

    private Integer bm25TopK = 40;
    private Integer vectorTopK = 40;
    private Integer rrfK = 60;
    private Integer rrfTopK = 30;
    private Integer maxResultWindow = 20;
    private Integer rerankInputTopK = 30;
    private Integer finalTopN = 8;

    public Integer getBm25TopK() {
        return bm25TopK;
    }

    public void setBm25TopK(Integer bm25TopK) {
        this.bm25TopK = bm25TopK;
    }

    public Integer getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(Integer vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public Integer getRrfK() {
        return rrfK;
    }

    public void setRrfK(Integer rrfK) {
        this.rrfK = rrfK;
    }

    public Integer getRrfTopK() {
        return rrfTopK;
    }

    public void setRrfTopK(Integer rrfTopK) {
        this.rrfTopK = rrfTopK;
    }

    public Integer getMaxResultWindow() {
        return maxResultWindow;
    }

    public void setMaxResultWindow(Integer maxResultWindow) {
        this.maxResultWindow = maxResultWindow;
    }

    public Integer getRerankInputTopK() {
        return rerankInputTopK;
    }

    public void setRerankInputTopK(Integer rerankInputTopK) {
        this.rerankInputTopK = rerankInputTopK;
    }

    public Integer getFinalTopN() {
        return finalTopN;
    }

    public void setFinalTopN(Integer finalTopN) {
        this.finalTopN = finalTopN;
    }
}
