package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationSession;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSession> {

    default ConversationSession selectBySessionCodeGlobal(String sessionCode) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("session_code", sessionCode).last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationSession selectBySessionCode(Long userId, String sessionType, String sessionCode) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .eq("session_type", sessionType)
                .eq("session_code", sessionCode)
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationSession selectBySessionCode(Long userId, String sessionType, String sessionCode, Long scopeId) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).eq("session_type", sessionType).eq("session_code", sessionCode);
        if (scopeId == null) {
            wrapper.isNull("scope_id");
        } else {
            wrapper.eq("scope_id", scopeId);
        }
        wrapper.isNull("archived_at");
        wrapper.last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<ConversationSession> selectRecentSessions(Long userId, String sessionType, Long scopeId, int limit) {
        return selectRecentSessions(userId, sessionType, scopeId, 1, limit);
    }

    default List<ConversationSession> selectRecentSessions(
            Long userId, String sessionType, Long scopeId, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePageNo - 1) * safePageSize;
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).isNull("archived_at");
        if (StringUtils.hasText(sessionType)) {
            wrapper.eq("session_type", sessionType);
            if (scopeId == null) {
                wrapper.isNull("scope_id");
            } else {
                wrapper.eq("scope_id", scopeId);
            }
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id").last("limit " + offset + "," + safePageSize);
        return this.selectList(wrapper);
    }

    default List<ConversationSession> selectRecentSessionsByTypes(Long userId, List<String> sessionTypes, int limit) {
        return selectRecentSessionsByTypes(userId, sessionTypes, 1, limit);
    }

    default List<ConversationSession> selectRecentSessionsByTypes(
            Long userId, List<String> sessionTypes, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePageNo - 1) * safePageSize;
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).isNull("archived_at");
        if (sessionTypes != null && !sessionTypes.isEmpty()) {
            wrapper.in("session_type", sessionTypes);
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id").last("limit " + offset + "," + safePageSize);
        return this.selectList(wrapper);
    }

    default int countRecentSessions(Long userId, String sessionType, Long scopeId) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).isNull("archived_at");
        if (StringUtils.hasText(sessionType)) {
            wrapper.eq("session_type", sessionType);
            if (scopeId == null) {
                wrapper.isNull("scope_id");
            } else {
                wrapper.eq("scope_id", scopeId);
            }
        }
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default int countRecentSessionsByTypes(Long userId, List<String> sessionTypes) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).isNull("archived_at");
        if (sessionTypes != null && !sessionTypes.isEmpty()) {
            wrapper.in("session_type", sessionTypes);
        }
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default List<ConversationSession> selectRecentSkillSessions(Long userId, Collection<Long> scopeIds, int limit) {
        if (userId == null || userId <= 0 || scopeIds == null || scopeIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId)
                .in("session_type", List.of("SKILL_CHAT", "PUBLISHED_SKILL_CHAT"))
                .isNull("archived_at")
                .in("scope_id", scopeIds)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("limit " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    default int deleteBySessionCode(Long userId, String sessionType, String sessionCode) {
        QueryWrapper<ConversationSession> wrapper = new QueryWrapper<>();
        wrapper.eq("create_user_id", userId).eq("session_type", sessionType).eq("session_code", sessionCode);
        return this.delete(wrapper);
    }

    default int updateSessionSnapshot(Long id, Long lastMessageId, String lastMessage, String name, String status) {
        ConversationSession update = new ConversationSession();
        update.setId(id);
        update.setLastMessageId(lastMessageId);
        update.setLastMessage(lastMessage);
        update.setName(name);
        update.setStatus(status);
        return this.updateById(update);
    }

    default int updateSessionName(Long id, String name) {
        ConversationSession update = new ConversationSession();
        update.setId(id);
        update.setName(name);
        return this.updateById(update);
    }

    default int updateSessionChatModel(Long id, Long chatModelId) {
        ConversationSession update = new ConversationSession();
        update.setId(id);
        update.setChatModelId(chatModelId);
        return this.updateById(update);
    }
}
