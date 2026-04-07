package lingzhou.agent.backend.business.datasets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.common.lzException.TaskException;

/**
 * 存储文档分块Service接口
 *
 * @author yanqy
 * @date 2025-05-23
 */
public interface IDocumentChunkService {
    /**
     * 查询存储文档分块
     *
     * @param chunkId 存储文档分块主键
     * @return 存储文档分块
     */
    public DocumentChunk selectDocumentChunkByChunkId(Long chunkId);

    /**
     * 查询存储文档分块列表
     *
     * @param documentChunk 存储文档分块
     * @return 存储文档分块集合
     */
    public List<DocumentChunk> selectDocumentChunkList(DocumentChunk documentChunk);

    IPage<DocumentChunk> selectDocumentChunkPage(DocumentChunk documentChunk, long pageNum, long pageSize);

    /**
     * 新增存储文档分块
     *
     * @param documentChunk 存储文档分块
     * @return 结果
     */
    public int insertDocumentChunk(DocumentChunk documentChunk);

    /**
     * 修改存储文档分块
     *
     * @param documentChunk 存储文档分块
     * @return 结果
     */
    public int updateDocumentChunk(String kbId, DocumentChunk documentChunk) throws TaskException;

    /**
     * 批量删除存储文档分块
     *
     * @param chunkIds 需要删除的存储文档分块主键集合
     * @return 结果
     */
    public int deleteDocumentChunkByChunkIds(Long[] chunkIds);

    /**
     * 删除存储文档分块信息
     *
     * @param chunkId 存储文档分块主键
     * @return 结果
     */
    public int deleteDocumentChunkByChunkId(Long chunkId);

    /**
     * 通过索引ID查询存储文档分块
     *
     * @param indexId 索引ID
     * @return 存储文档分块
     */
    public DocumentChunk selectDocumentChunkByIndexId(String indexId);
}
