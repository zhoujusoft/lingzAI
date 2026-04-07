package lingzhou.agent.backend.capability.rag.chunk.config;

import lombok.Data;

@Data
public class ChunkRequest {

    public static final String DOCUMENT_DOMAIN_LAW = "LAW";

    private ChunkStrategy strategy;

    /**
     * 分块标识符（例如："###" 或 "===="）。
     * DELIMITER_WINDOW 模式下可选；为空则整文滑窗切分。
     */
    private String delimiter;

    /**
     * 块大小（字符数）。
     */
    private int chunkSize = 2048;

    /**
     * 重叠大小（字符数）。
     */
    private int overlapSize = 200;

    /**
     * 是否过滤空块。
     */
    private boolean dropEmpty = true;

    /**
     * 是否在分块结果中包含表格内容。
     */
    private boolean includeTables = true;

    /**
     * 表格块中是否保留识别到的表题。
     */
    private boolean tableKeepCaption = true;

    /**
     * 表格块最大字符数；小于等于 0 表示不按大小拆分表格。
     * 拆分时按“行”聚合，不做纯字符硬切。
     */
    private int tableMaxChars = 0;

    /**
     * 文档领域；为空表示通用文档。
     */
    private String documentDomain;

    /**
     * 法律模式下是否优先保持整条法条为单个块。
     */
    private boolean preserveWholeArticle = false;

    /**
     * 法律模式下单条法条可保持不切分的最大字符数。
     */
    private int articleMaxChars = 1800;

    /**
     * 法律模式下超过该阈值时才允许按窗口继续拆分。
     */
    private int clauseSplitThreshold = 2200;

    public boolean isLawDocument() {
        return DOCUMENT_DOMAIN_LAW.equalsIgnoreCase(documentDomain);
    }
}
