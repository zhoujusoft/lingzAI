package lingzhou.agent.backend.business.datasets.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 进度管理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProgressManager {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PROGRESS_KEY_PREFIX = "rag:progress:";
    private static final long TTL_HOURS = 24;

    /**
     * 更新进度
     *
     * @param docId 文档 ID
     * @param progress 进度百分比 (0-100)
     * @param stage 阶段
     * @param message 消息
     */
    public void updateProgress(Long docId, int progress, String stage, String message) {
        JSONObject progressData = new JSONObject();
        progressData.put("docId", docId);
        progressData.put("status", 1);
        progressData.put("progress", progress);
        progressData.put("stage", stage);
        progressData.put("message", message);
        progressData.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        String key = PROGRESS_KEY_PREFIX + docId;
        redisTemplate.opsForValue().set(key, progressData.toJSONString(), TTL_HOURS, TimeUnit.HOURS);
        log.debug("更新进度：docId={}, progress={}, stage={}, message={}", docId, progress, stage, message);
    }

    /**
     * 获取进度
     *
     * @param docId 文档 ID
     * @return 进度 JSON 字符串
     */
    public String getProgress(Long docId) {
        String key = PROGRESS_KEY_PREFIX + docId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 标记为完成
     *
     * @param docId 文档 ID
     */
    public void complete(Long docId) {
        updateProgress(docId, 100, "COMPLETED", "处理完成");
        log.info("文档处理完成：docId={}", docId);
    }

    /**
     * 标记为失败
     *
     * @param docId 文档 ID
     * @param errorMessage 错误信息
     */
    public void fail(Long docId, String errorMessage) {
        JSONObject progressData = new JSONObject();
        progressData.put("docId", docId);
        progressData.put("status", 3);
        progressData.put("progress", 0);
        progressData.put("stage", "FAILED");
        progressData.put("message", errorMessage);
        progressData.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        String key = PROGRESS_KEY_PREFIX + docId;
        redisTemplate.opsForValue().set(key, progressData.toJSONString(), TTL_HOURS, TimeUnit.HOURS);
        log.error("文档处理失败：docId={}, error={}", docId, errorMessage);
    }

    /**
     * 删除进度（清理）
     *
     * @param docId 文档 ID
     */
    public void removeProgress(Long docId) {
        String key = PROGRESS_KEY_PREFIX + docId;
        redisTemplate.delete(key);
        log.debug("删除进度：docId={}", docId);
    }

    /**
     * 进度阶段枚举
     */
    public enum ProgressStage {
        PARSING("解析中", 0, 30),
        CHUNKING("分块中", 30, 70),
        EMBEDDING("向量化中", 70, 90),
        INDEXING("入库中", 90, 100),
        COMPLETED("完成", 100, 100),
        FAILED("失败", 0, 0);

        private final String message;
        private final int minProgress;
        private final int maxProgress;

        ProgressStage(String message, int minProgress, int maxProgress) {
            this.message = message;
            this.minProgress = minProgress;
            this.maxProgress = maxProgress;
        }

        public String getMessage() {
            return message;
        }

        public int getMinProgress() {
            return minProgress;
        }

        public int getMaxProgress() {
            return maxProgress;
        }
    }
}
