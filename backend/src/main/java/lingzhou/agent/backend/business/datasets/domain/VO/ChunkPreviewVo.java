package lingzhou.agent.backend.business.datasets.domain.VO;

import java.io.Serializable;
import lingzhou.agent.backend.capability.rag.chunk.model.ChunkedSection;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;

public class ChunkPreviewVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String label;
    private int length;
    private String content;
    private String blockType;

    public ChunkPreviewVo() {}

    public ChunkPreviewVo(ChunkedSection section, int index) {
        this.id = section.getId();
        this.label = "Chunk-" + index;
        this.length = TableChunkContentSupport.resolveVisibleLength(section.getBlockType(), section.getContent());
        this.content = section.getContent();
        this.blockType = section.getBlockType();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
    }
}
