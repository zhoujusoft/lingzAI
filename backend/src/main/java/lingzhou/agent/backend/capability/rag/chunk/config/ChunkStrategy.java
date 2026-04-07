package lingzhou.agent.backend.capability.rag.chunk.config;

public enum ChunkStrategy {
    /**
     * 分块标识符 + chunk size + overlap size。
     */
    DELIMITER_WINDOW,

    /**
     * 按章节目录分块（Word 标题）。
     */
    HEADING_DIRECTORY
}
