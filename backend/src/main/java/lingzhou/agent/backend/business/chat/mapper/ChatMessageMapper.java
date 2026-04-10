package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    default int countBySessionId(Long sessionId) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default List<ChatMessage> selectBySessionId(Long sessionId, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePageNo - 1) * safePageSize;

        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("limit " + offset + "," + safePageSize);
        return this.selectList(wrapper);
    }

    default int deleteBySessionId(Long sessionId) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return this.delete(wrapper);
    }

    default List<String> selectFileListsBySessionId(Long sessionId) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.select("file_list").eq("session_id", sessionId);
        return this.selectObjs(wrapper).stream()
                .map(value -> value == null ? null : String.valueOf(value))
                .toList();
    }

    default int updateSucceeded(
            Long id,
            String answer,
            String documentList,
            String fileList,
            String paramsJson,
            String consumesTime,
            String status) {
        ChatMessage update = new ChatMessage();
        update.setId(id);
        update.setAnswer(answer);
        update.setDocumentList(documentList);
        update.setFileList(fileList);
        update.setParamsJson(paramsJson);
        update.setConsumesTime(consumesTime);
        update.setStatus(status);
        update.setError(null);
        return this.updateById(update);
    }

    default int updateFailed(Long id, String error, String consumesTime, String status, String answer, String paramsJson) {
        ChatMessage update = new ChatMessage();
        update.setId(id);
        update.setError(error);
        update.setConsumesTime(consumesTime);
        update.setStatus(status);
        update.setAnswer(answer);
        update.setParamsJson(paramsJson);
        return this.updateById(update);
    }

}
