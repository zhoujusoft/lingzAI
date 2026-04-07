package lingzhou.agent.backend.capability.rag.chunk.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ChunkedSection {

    private String id;

    /**
     * 原始章节路径（仅 HEADING_DIRECTORY 模式有效）。
     */
    private List<String> headings = new ArrayList<>();

    private String content;

    /**
     * 逻辑类型：TEXT / TABLE / WINDOW。
     */
    private String blockType;

    /**
     * 原始段序号（可选）。
     */
    private Integer orderNo;
}
