package lingzhou.agent.backend.business.datasets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.domain.MateDataParam;
import lingzhou.agent.backend.business.datasets.domain.VO.AppendDocumentChunkRequest;
import lingzhou.agent.backend.business.datasets.domain.VO.ChunkPreviewVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentDetailVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentTreeNodeVo;
import lingzhou.agent.backend.business.datasets.domain.VO.KnowledgeDocumentVo;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.quartz.SchedulerException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 存储文档信息Service接口
 *
 * @author yanqy
 * @date 2025-05-21
 */
public interface IKnowledgeDocumentService {
    /**
     * 查询存储文档信息
     *
     * @param docId 存储文档信息主键
     * @return 存储文档信息
     */
    public KnowledgeDocument selectKnowledgeDocumentByDocId(Long docId);

    /**
     * 查询存储文档信息列表
     *
     * @param knowledgeDocument 存储文档信息
     * @return 存储文档信息集合
     */
    public List<KnowledgeDocument> selectKnowledgeDocumentList(KnowledgeDocument knowledgeDocument);

    IPage<KnowledgeDocument> selectKnowledgeDocumentPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize);

    /**
     * 根据知识库ID查询存储文档信息列表
     *
     * @param knowledgeDocument 存储文档信息
     * @return 存储文档信息集合
     */
    public List<KnowledgeDocument> selectKnowledgeDocumentListByKbId(KnowledgeDocument knowledgeDocument);

    IPage<KnowledgeDocument> selectKnowledgeDocumentByKbIdPage(
            KnowledgeDocument knowledgeDocument, long pageNum, long pageSize);

    /**
     * 根据元数据查询存储文档信息列表
     *
     * @return 存储文档信息集合
     */
    public List<KnowledgeDocument> selectKnowledgeDocumentListByMetadata(MateDataParam mateDataParam);

    /**
     * 根据元数据查询存储文档id列表
     *
     * @return 存储文档信息集合
     */
    public List<String> selectKnowledgeDocumentIdsByMetadata(MateDataParam mateDataParam);

    /**
     * 新增存储文档信息
     *
     * @param knowledgeDocument 存储文档信息
     * @return 结果
     */
    public int insertKnowledgeDocument(KnowledgeDocument knowledgeDocument) throws SchedulerException, TaskException;

    /**
     * 修改存储文档信息
     *
     * @param knowledgeDocument 存储文档信息
     * @return 结果
     */
    public int updateKnowledgeDocument(KnowledgeDocument knowledgeDocument);

    /**
     * 批量删除存储文档信息
     *
     * @param docIds 需要删除的存储文档信息主键集合
     * @return 结果
     */
    public int deleteKnowledgeDocumentByDocIds(Long[] docIds) throws Exception;

    /**
     * 删除存储文档信息信息
     *
     * @param docId 存储文档信息主键
     * @return 结果
     */
    public int deleteKnowledgeDocumentByDocId(Long kbId, Long docId) throws Exception;

    /**
     * 查询存储文档信息及文档分块
     *
     * @param docId 存储文档信息主键
     * @return 存储文档信息
     */
    public KnowledgeDocumentVo selectKnowledgeDocumentVoByDocId(Long docId);

    public KnowledgeDocumentDetailVo selectKnowledgeDocumentDetailByDocId(Long docId) throws TaskException;

    /**
     * 根据前缀查询存储文档信息
     */
    public List<KnowledgeDocument> findByPrefixes(List<String> prefixes, Long kbId, String fileCode);

    /**
     * 查出所有的技术规范书文档id
     */
    public List<String> findDocIdsByTechSpec();

    public List<String> findDocIdsByTechSpecByKbId(String kbId);

    /**
     * 根据父文档id查询存储文档信息
     */
    public List<KnowledgeDocument> selectKnowledgeDocumentListByParentDocId(String parentDocId, String kbId);

    public List<KnowledgeDocumentTreeNodeVo> selectKnowledgeDocumentTree(Long kbId);

    public List<KnowledgeDocument> selectKnowledgeDocumentChildren(Long kbId, Long parentId);

    /**
     * 根据知识库id查询所有文档id
     */
    public List<String> selectDocIdByKbId(String kbId);

    // * 查询所有的教材id
    List<String> selectJiaoCaiIds(Long kbId);

    List<String> selectKnowledgeDocumentIds(KnowledgeDocument knowledgeDocument);

    /**
     * 更新文档状态
     *
     * @param docId 文档 ID
     * @param status 状态
     * @param errorMessage 错误信息
     * @return 影响行数
     */
    public int updateStatus(Long docId, Integer status, String errorMessage);

    public List<ChunkPreviewVo> previewChunks(Long docId, String chunkStrategy, String chunkConfig) throws Exception;

    public int submitChunkConfigAndParse(Long docId, String chunkStrategy, String chunkConfig)
            throws SchedulerException, TaskException;

    public KnowledgeDocument createKnowledgeDocumentWithFile(
            Long kbId, Long parentId, MultipartFile file, String chunkStrategy, String chunkConfig) throws Exception;

    public KnowledgeDocument createFolder(Long kbId, Long parentId, String name) throws Exception;

    public int renameFolder(Long kbId, Long docId, String name) throws TaskException;

    public int moveNode(Long kbId, Long docId, Long targetParentId) throws TaskException;

    /**
     * 重试解析文档
     *
     * @param docId 文档 ID
     * @return 结果
     */
    public int retryParseDocument(Long docId) throws SchedulerException, TaskException;

    /**
     * 启动文档解析
     *
     * @param docId 文档 ID
     * @return 结果
     */
    public int startParseDocument(Long docId) throws SchedulerException, TaskException;

    public DocumentChunk appendDocumentChunk(Long docId, AppendDocumentChunkRequest request) throws TaskException;
}
