package lingzhou.agent.backend.business.datasets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 存储知识库基本信息Service接口
 *
 * @author yanqy
 * @date 2025-05-20
 */
public interface IKnowledgeBaseService {
    /**
     * 查询存储知识库基本信息
     *
     * @param kbId 存储知识库基本信息主键
     * @return 存储知识库基本信息
     */
    public KnowledgeBase selectKnowledgeBaseByKbId(Long kbId);

    /**
     * 查询存储知识库基本信息列表
     *
     * @param knowledgeBase 存储知识库基本信息
     * @return 存储知识库基本信息集合
     */
    public List<KnowledgeBase> selectKnowledgeBaseList(KnowledgeBase knowledgeBase);

    IPage<KnowledgeBase> selectKnowledgeBasePage(KnowledgeBase knowledgeBase, long pageNum, long pageSize);

    /**
     * 新增存储知识库基本信息
     *
     * @param knowledgeBase 存储知识库基本信息
     * @return 结果
     */
    public int insertKnowledgeBase(KnowledgeBase knowledgeBase);

    /**
     * 修改存储知识库基本信息
     *
     * @param knowledgeBase 存储知识库基本信息
     * @return 结果
     */
    public int updateKnowledgeBase(KnowledgeBase knowledgeBase);

    /**
     * 批量删除存储知识库基本信息
     *
     * @param kbIds 需要删除的存储知识库基本信息主键集合
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbIds(Long[] kbIds) throws Exception;

    /**
     * 删除存储知识库基本信息信息
     *
     * @param kbId 存储知识库基本信息主键
     * @return 结果
     */
    public int deleteKnowledgeBaseByKbId(Long kbId) throws Exception;

    public String selectKbName(String categoryName);

    /**
     * 创建知识库并同步上传首个文档
     *
     * @param knowledgeBase 知识库
     * @param file 文档文件
     * @param chunkStrategy 切片策略
     * @param chunkConfig 切片配置
     * @return 已创建的文档
     */
    public KnowledgeDocument createKnowledgeBaseWithDocument(
            KnowledgeBase knowledgeBase, MultipartFile file, String chunkStrategy, String chunkConfig) throws Exception;
}
