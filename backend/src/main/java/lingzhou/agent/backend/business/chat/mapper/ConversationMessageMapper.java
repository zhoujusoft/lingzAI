package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationMessage;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    default int countBySessionId(Long sessionId) {
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return Math.toIntExact(this.selectCount(wrapper));
    }

    default List<ConversationMessage> selectUserMessagesBySessionId(Long sessionId, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePageNo - 1) * safePageSize;

        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("role", "USER")
                .eq("message_kind", "INPUT")
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("limit " + offset + "," + safePageSize);
        return this.selectList(wrapper);
    }

    default List<ConversationMessage> selectTimelineBySessionId(Long sessionId, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 500));

        // Reverse pagination: Page 1 returns the latest messages, then rows are restored
        // to chronological order inside the page.
        int total = countBySessionId(sessionId);

        // If no messages, return empty list
        if (total == 0) {
            return List.of();
        }

        int endExclusive = Math.max(0, total - (safePageNo - 1) * safePageSize);
        if (endExclusive <= 0) {
            return List.of();
        }
        int offset = Math.max(0, endExclusive - safePageSize);
        int limit = endExclusive - offset;

        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        // Column projection: only select fields needed for chat history display
        // Excluded fields: usage_summary_json, prompt_tokens, completion_tokens, total_tokens,
        //                 llm_call_count, tool_call_count, model_id, model_provider, model_name, adapter_type
        wrapper.select(
                        "id",
                        "message_code",
                        "parent_message_id",
                        "role",
                        "message_kind",
                        "content",
                        "content_format",
                        "status",
                        "error_code",
                        "error_message",
                        "segments_json",
                        "attachments_json",
                        "artifact_summary_json",
                        "params_json",
                        "sequence_no",
                        "created_at",
                        "updated_at",
                        "completed_at")
                .eq("session_id", sessionId)
                .orderByAsc("sequence_no") // Messages within page remain in chronological order
                .orderByAsc("id")
                .last("limit " + offset + "," + limit);
        return this.selectList(wrapper);
    }

    default List<ConversationMessage> selectByParentMessageIds(List<Long> parentMessageIds) {
        if (parentMessageIds == null || parentMessageIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.in("parent_message_id", parentMessageIds)
                .orderByAsc("created_at")
                .orderByAsc("id");
        return this.selectList(wrapper);
    }

    default List<ConversationMessage> selectRecentMessagesAfterSequence(
            Long sessionId, Integer minSequenceExclusive, int limit, Long excludedMessageId) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.select("id", "sequence_no", "role", "content", "status", "created_at")
                .eq("session_id", sessionId)
                .in("role", List.of("USER", "ASSISTANT", "SYSTEM"));
        if (minSequenceExclusive != null && minSequenceExclusive > 0) {
            wrapper.gt("sequence_no", minSequenceExclusive);
        }
        if (excludedMessageId != null && excludedMessageId > 0) {
            wrapper.ne("id", excludedMessageId);
        }
        wrapper.orderByDesc("sequence_no").orderByDesc("id").last("limit " + Math.max(1, limit));
        List<ConversationMessage> rows = this.selectList(wrapper);
        java.util.Collections.reverse(rows);
        return rows;
    }

    default List<ConversationMessage> selectMessagesAfterSequence(Long sessionId, Integer minSequenceExclusive) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.select("id", "sequence_no", "role", "content", "status", "created_at")
                .eq("session_id", sessionId)
                .in("role", List.of("USER", "ASSISTANT", "SYSTEM"));
        if (minSequenceExclusive != null && minSequenceExclusive > 0) {
            wrapper.gt("sequence_no", minSequenceExclusive);
        }
        wrapper.orderByAsc("sequence_no").orderByAsc("id");
        return this.selectList(wrapper);
    }

    default ConversationMessage selectByIdForUpdate(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationMessage selectLatestAssistantMessage(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return null;
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("role", "ASSISTANT")
                .orderByDesc("sequence_no")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationMessage selectLatestAssistantMessageBeforeSequence(Long sessionId, Integer maxSequenceExclusive) {
        if (sessionId == null || sessionId <= 0 || maxSequenceExclusive == null || maxSequenceExclusive <= 0) {
            return null;
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("role", "ASSISTANT")
                .lt("sequence_no", maxSequenceExclusive)
                .orderByDesc("sequence_no")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default ConversationMessage selectLatestUserMessage(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return null;
        }
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("role", "USER")
                .eq("message_kind", "INPUT")
                .orderByDesc("sequence_no")
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default int deleteBySessionId(Long sessionId) {
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return this.delete(wrapper);
    }

    default List<String> selectFileListsBySessionId(Long sessionId) {
        QueryWrapper<ConversationMessage> wrapper = new QueryWrapper<>();
        wrapper.select("attachments_json").eq("session_id", sessionId).eq("role", "USER");
        return this.selectObjs(wrapper).stream()
                .map(value -> value == null ? null : String.valueOf(value))
                .toList();
    }

    default int updateAssistantMessage(
            Long id,
            String content,
            String artifactSummaryJson,
            String paramsJson,
            String status,
            String errorCode,
            String errorMessage,
            ConversationMessageUsagePayload usagePayload) {
        return updateAssistantMessage(
                id, content, null, artifactSummaryJson, paramsJson, status, errorCode, errorMessage, usagePayload);
    }

    default int updateAssistantMessage(
            Long id,
            String content,
            String segmentsJson,
            String artifactSummaryJson,
            String paramsJson,
            String status,
            String errorCode,
            String errorMessage) {
        return updateAssistantMessage(
                id, content, segmentsJson, artifactSummaryJson, paramsJson, status, errorCode, errorMessage, null);
    }

    default int updateAssistantMessage(
            Long id,
            String content,
            String segmentsJson,
            String artifactSummaryJson,
            String paramsJson,
            String status,
            String errorCode,
            String errorMessage,
            ConversationMessageUsagePayload usagePayload) {
        ConversationMessage update = new ConversationMessage();
        update.setId(id);
        update.setContent(content);
        update.setSegmentsJson(segmentsJson);
        update.setArtifactSummaryJson(artifactSummaryJson);
        update.setParamsJson(paramsJson);
        update.setStatus(status);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setCompletedAt(new java.util.Date());
        if (usagePayload != null) {
            update.setPromptTokens(usagePayload.promptTokens());
            update.setCompletionTokens(usagePayload.completionTokens());
            update.setTotalTokens(usagePayload.totalTokens());
            update.setUsageAvailable(usagePayload.usageAvailable());
            update.setLlmCallCount(usagePayload.llmCallCount());
            update.setToolCallCount(usagePayload.toolCallCount());
            update.setModelId(usagePayload.modelId());
            update.setModelProvider(usagePayload.modelProvider());
            update.setModelName(usagePayload.modelName());
            update.setAdapterType(usagePayload.adapterType());
            update.setUsageSummaryJson(usagePayload.usageSummaryJson());
        }
        return this.updateById(update);
    }
}
