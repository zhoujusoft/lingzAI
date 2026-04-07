package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    default ChatSession selectBySessionCodeGlobal(String sessionCode) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("session_code", sessionCode).last("limit 1");
        return this.selectOne(wrapper);
    }

    default ChatSession selectBySessionCode(Long userId, String sessionType, String sessionCode) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .eq("session_type", sessionType)
                .eq("session_code", sessionCode)
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ChatSession selectBySessionCode(
            Long userId, String sessionType, String sessionCode, Long scopeId) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .eq("session_type", sessionType)
                .eq("session_code", sessionCode);
        if (scopeId == null) {
            wrapper.isNull("scope_id");
        } else {
            wrapper.eq("scope_id", scopeId);
        }
        wrapper.last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ChatSession> selectRecentSessions(Long userId, String sessionType, Long scopeId, int limit) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId);
        if (StringUtils.hasText(sessionType)) {
            wrapper.eq("session_type", sessionType);
            if (scopeId == null) {
                wrapper.isNull("scope_id");
            } else {
                wrapper.eq("scope_id", scopeId);
            }
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id").last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default List<ChatSession> selectRecentSessionsByTypes(Long userId, List<String> sessionTypes, int limit) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId);
        if (sessionTypes != null && !sessionTypes.isEmpty()) {
            wrapper.in("session_type", sessionTypes);
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id").last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default List<ChatSession> selectRecentSkillSessions(Long userId, Collection<Long> scopeIds, int limit) {
        if (userId == null || userId <= 0 || scopeIds == null || scopeIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .eq("session_type", "SKILL_CHAT")
                .in("scope_id", scopeIds)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default int deleteBySessionCode(Long userId, String sessionType, String sessionCode) {
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .eq("session_type", sessionType)
                .eq("session_code", sessionCode);
        return this.delete(wrapper);
    }

    default int updateSessionSnapshot(
            Long id, String lastMessage, String name, String status) {
        ChatSession update = new ChatSession();
        update.setId(id);
        update.setLastMessage(lastMessage);
        update.setName(name);
        update.setStatus(status);
        return this.updateById(update);
    }

    default int updateSessionName(Long id, String name) {
        ChatSession update = new ChatSession();
        update.setId(id);
        update.setName(name);
        return this.updateById(update);
    }
}
