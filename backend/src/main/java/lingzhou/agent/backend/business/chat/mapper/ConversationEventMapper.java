package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collections;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationEventMapper extends BaseMapper<ConversationEvent> {

    default int countBySessionId(Long sessionId) {
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default List<ConversationEvent> selectRecentBySessionId(
            Long sessionId, Integer minSequenceExclusive, int limit, List<String> eventTypes) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        if (minSequenceExclusive != null && minSequenceExclusive > 0) {
            wrapper.gt("sequence_no", minSequenceExclusive);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            wrapper.in("event_type", eventTypes);
        }
        wrapper.orderByDesc("sequence_no").orderByDesc("id").last("limit " + Math.max(1, limit));
        List<ConversationEvent> rows = this.selectList(wrapper);
        Collections.reverse(rows);
        return rows;
    }

    default List<ConversationEvent> selectAfterSequence(
            Long sessionId, Integer minSequenceExclusive, List<String> eventTypes) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        if (minSequenceExclusive != null && minSequenceExclusive > 0) {
            wrapper.gt("sequence_no", minSequenceExclusive);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            wrapper.in("event_type", eventTypes);
        }
        return this.selectList(wrapper);
    }

    default List<ConversationEvent> selectByRunId(Long runId) {
        if (runId == null || runId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("run_id", runId).orderByAsc("sequence_no").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<ConversationEvent> selectRecentByRunId(Long runId, int limit) {
        if (runId == null || runId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("run_id", runId).orderByDesc("sequence_no").orderByDesc("id").last("limit " + Math.max(1, limit));
        List<ConversationEvent> rows = this.selectList(wrapper);
        Collections.reverse(rows);
        return rows;
    }

    default ConversationEvent selectLatestSummary(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return null;
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("event_type", "SUMMARY_SNAPSHOT")
                .orderByDesc("sequence_no")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationEvent selectLatestByMessageIdAndEventType(Long messageId, String eventType) {
        if (messageId == null || messageId <= 0 || eventType == null || eventType.isBlank()) {
            return null;
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("message_id", messageId)
                .eq("event_type", eventType.trim())
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationEvent selectLatestByMessageIdAndEventTypeAndSubtype(
            Long messageId, String eventType, String eventSubtype) {
        if (messageId == null || messageId <= 0 || eventType == null || eventType.isBlank()) {
            return null;
        }
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("message_id", messageId).eq("event_type", eventType.trim());
        if (eventSubtype == null || eventSubtype.isBlank()) {
            wrapper.isNull("event_subtype");
        } else {
            wrapper.eq("event_subtype", eventSubtype.trim());
        }
        wrapper.orderByDesc("id").last("limit 1");
        return this.selectOne(wrapper);
    }

    default int deleteBySessionId(Long sessionId) {
        QueryWrapper<ConversationEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return this.delete(wrapper);
    }
}
