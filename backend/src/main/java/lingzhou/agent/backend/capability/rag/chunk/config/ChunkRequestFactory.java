package lingzhou.agent.backend.capability.rag.chunk.config;

import com.alibaba.fastjson.JSONObject;
import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import org.apache.commons.lang3.StringUtils;

public final class ChunkRequestFactory {

    private static final int DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE = 500;
    private static final int DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE = 3000;
    private static final int DEFAULT_OVERLAP_SIZE = 50;
    private static final String DEFAULT_DELIMITER = "\n\n";
    private static final int DEFAULT_TABLE_MAX_CHARS = 1000;
    private static final int DEFAULT_LAW_ARTICLE_MAX_CHARS = 1800;
    private static final int DEFAULT_LAW_CLAUSE_SPLIT_THRESHOLD = 2200;

    private ChunkRequestFactory() {}

    public static ChunkRequest build(String fileType, String chunkStrategy, String chunkConfig) {
        ChunkRequest request = new ChunkRequest();
        ChunkStrategy strategy = determineStrategy(fileType, chunkStrategy);
        request.setStrategy(strategy);
        request.setChunkSize(defaultChunkSize(strategy));
        request.setOverlapSize(DEFAULT_OVERLAP_SIZE);
        request.setDelimiter(DEFAULT_DELIMITER);
        request.setDropEmpty(true);
        request.setIncludeTables(true);
        request.setTableKeepCaption(true);
        request.setTableMaxChars(DEFAULT_TABLE_MAX_CHARS);
        request.setArticleMaxChars(DEFAULT_LAW_ARTICLE_MAX_CHARS);
        request.setClauseSplitThreshold(DEFAULT_LAW_CLAUSE_SPLIT_THRESHOLD);

        if (StringUtils.isBlank(chunkConfig)) {
            return request;
        }

        try {
            JSONObject config = JSONObject.parseObject(chunkConfig);
            if (config.containsKey("documentDomain")) {
                request.setDocumentDomain(config.getString("documentDomain"));
            }
            if (config.containsKey("chunkSize")) {
                request.setChunkSize(config.getIntValue("chunkSize"));
            }
            if (config.containsKey("overlapSize")) {
                request.setOverlapSize(config.getIntValue("overlapSize"));
            }
            if (config.containsKey("delimiter")) {
                request.setDelimiter(normalizeDelimiter(config.getString("delimiter")));
            }
            if (config.containsKey("preserveWholeArticle")) {
                request.setPreserveWholeArticle(config.getBooleanValue("preserveWholeArticle"));
            }
            if (config.containsKey("articleMaxChars")) {
                request.setArticleMaxChars(config.getIntValue("articleMaxChars"));
            }
            if (config.containsKey("clauseSplitThreshold")) {
                request.setClauseSplitThreshold(config.getIntValue("clauseSplitThreshold"));
            }
        } catch (Exception ignored) {
        }

        if (request.isLawDocument() && !configuresLawChunkBehavior(request)) {
            request.setPreserveWholeArticle(true);
            request.setChunkSize(Math.max(request.getChunkSize(), request.getArticleMaxChars()));
        }

        return request;
    }

    private static boolean configuresLawChunkBehavior(ChunkRequest request) {
        return request.isPreserveWholeArticle()
                || request.getArticleMaxChars() != DEFAULT_LAW_ARTICLE_MAX_CHARS
                || request.getClauseSplitThreshold() != DEFAULT_LAW_CLAUSE_SPLIT_THRESHOLD;
    }

    public static ChunkStrategy determineStrategy(String fileType, String configStrategy) {
        if (ChunkStrategy.DELIMITER_WINDOW.name().equals(configStrategy)) {
            return ChunkStrategy.DELIMITER_WINDOW;
        }
        if (ChunkStrategy.HEADING_DIRECTORY.name().equals(configStrategy)) {
            return ChunkStrategy.HEADING_DIRECTORY;
        }
        if (FileType.supportsHeadingDirectory(fileType)) {
            return ChunkStrategy.HEADING_DIRECTORY;
        }
        return ChunkStrategy.DELIMITER_WINDOW;
    }

    private static int defaultChunkSize(ChunkStrategy strategy) {
        if (strategy == ChunkStrategy.HEADING_DIRECTORY) {
            return DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE;
        }
        return DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE;
    }

    public static String normalizeDelimiter(String delimiter) {
        if (delimiter == null) {
            return null;
        }
        return delimiter
                .replace("\\r\\n", "\r\n")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("/r/n", "\r\n")
                .replace("/n", "\n")
                .replace("/t", "\t");
    }
}
