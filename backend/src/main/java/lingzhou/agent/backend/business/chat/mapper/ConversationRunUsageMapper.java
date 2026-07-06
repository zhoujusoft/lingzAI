package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.ConversationRunUsage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationRunUsageMapper extends BaseMapper<ConversationRunUsage> {

    default ConversationRunUsage selectByAssistantMessageId(Long assistantMessageId) {
        if (assistantMessageId == null || assistantMessageId <= 0) {
            return null;
        }
        QueryWrapper<ConversationRunUsage> wrapper = new QueryWrapper<>();
        wrapper.eq("assistant_message_id", assistantMessageId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default int deleteBySessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return 0;
        }
        QueryWrapper<ConversationRunUsage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return this.delete(wrapper);
    }

    default long sumConsumedTokensSince(Date createdAtInclusive) {
        QueryWrapper<ConversationRunUsage> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(total_tokens), 0) AS total_tokens");
        wrapper.eq("usage_available", 1);
        if (createdAtInclusive != null) {
            wrapper.ge("created_at", createdAtInclusive);
        }
        List<Map<String, Object>> rows = this.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).get("total_tokens");
        if (total instanceof Number number) {
            return Math.max(number.longValue(), 0L);
        }
        if (total == null) {
            return 0L;
        }
        try {
            return Math.max(Long.parseLong(String.valueOf(total)), 0L);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
