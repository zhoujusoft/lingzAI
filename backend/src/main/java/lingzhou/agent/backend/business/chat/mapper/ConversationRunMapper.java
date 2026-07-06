package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationRun;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationRunMapper extends BaseMapper<ConversationRun> {

    default ConversationRun selectByRunCode(String runCode) {
        if (runCode == null || runCode.isBlank()) {
            return null;
        }
        QueryWrapper<ConversationRun> wrapper = new QueryWrapper<>();
        wrapper.eq("run_code", runCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ConversationRun> selectBySessionId(Long sessionId, int limit) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationRun> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId).orderByDesc("id").last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default ConversationRun selectLatestByTriggerMessageId(Long triggerMessageId) {
        if (triggerMessageId == null || triggerMessageId <= 0) {
            return null;
        }
        QueryWrapper<ConversationRun> wrapper = new QueryWrapper<>();
        wrapper.eq("trigger_message_id", triggerMessageId).orderByDesc("id").last("limit 1");
        return this.selectOne(wrapper);
    }

    default int updatePhase(
            Long id,
            String status,
            String phase,
            String subStage,
            String currentTask,
            String currentRuntimeSkillName,
            String contextJson) {
        ConversationRun update = new ConversationRun();
        update.setId(id);
        update.setStatus(status);
        update.setPhase(phase);
        update.setSubStage(subStage);
        update.setCurrentTask(currentTask);
        update.setCurrentRuntimeSkillName(currentRuntimeSkillName);
        update.setContextJson(contextJson);
        return this.updateById(update);
    }

    default int finishRun(
            Long id, Long finalMessageId, String status, String errorCode, String errorMessage, String contextJson) {
        ConversationRun update = new ConversationRun();
        update.setId(id);
        update.setFinalMessageId(finalMessageId);
        update.setStatus(status);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setContextJson(contextJson);
        update.setFinishedAt(new java.util.Date());
        return this.updateById(update);
    }
}
