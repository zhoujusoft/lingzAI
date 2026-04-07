package lingzhou.agent.backend.business.datasets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentMetadata;

/**
 * 存储元数据配置定义Service接口
 *
 * @author yanqy
 * @date 2025-05-23
 */
public interface IDocumentMetadataService {
    /**
     * 查询存储元数据配置定义
     *
     * @param metadataId 存储元数据配置定义主键
     * @return 存储元数据配置定义
     */
    public DocumentMetadata selectDocumentMetadataByMetadataId(Long metadataId);

    /**
     * 查询存储元数据配置定义列表
     *
     * @param documentMetadata 存储元数据配置定义
     * @return 存储元数据配置定义集合
     */
    public List<DocumentMetadata> selectDocumentMetadataList(DocumentMetadata documentMetadata);

    IPage<DocumentMetadata> selectDocumentMetadataPage(DocumentMetadata documentMetadata, long pageNum, long pageSize);

    /**
     * 新增存储元数据配置定义
     *
     * @param documentMetadata 存储元数据配置定义
     * @return 结果
     */
    public int insertDocumentMetadata(DocumentMetadata documentMetadata);

    /**
     * 修改存储元数据配置定义
     *
     * @param documentMetadata 存储元数据配置定义
     * @return 结果
     */
    public int updateDocumentMetadata(DocumentMetadata documentMetadata);

    /**
     * 批量删除存储元数据配置定义
     *
     * @param metadataIds 需要删除的存储元数据配置定义主键集合
     * @return 结果
     */
    public int deleteDocumentMetadataByMetadataIds(Long[] metadataIds);

    /**
     * 删除存储元数据配置定义信息
     *
     * @param metadataId 存储元数据配置定义主键
     * @return 结果
     */
    public int deleteDocumentMetadataByMetadataId(Long metadataId);

    public void insertDocumentMetadataBatch(Long kbId, String name);
}
