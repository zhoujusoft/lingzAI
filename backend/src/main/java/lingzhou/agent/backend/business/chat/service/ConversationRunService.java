package lingzhou.agent.backend.business.chat.service;

import java.util.Date;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.ConversationRun;
import lingzhou.agent.backend.business.chat.mapper.ConversationRunMapper;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationRunService {

    private final ConversationRunMapper conversationRunMapper;

    public ConversationRunService(ConversationRunMapper conversationRunMapper) {
        this.conversationRunMapper = conversationRunMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationRun startRun(
            Long sessionId,
            Long triggerMessageId,
            Long userId,
            String runType,
            String status,
            String phase,
            String subStage,
            String currentTask,
            String currentRuntimeSkillName,
            String contextJson) {
        ConversationRun run = new ConversationRun();
        run.setRunCode(UlidGenerator.next());
        run.setSessionId(sessionId);
        run.setTriggerMessageId(triggerMessageId);
        run.setRunType(normalize(runType, ConversationRunConstants.RUN_TYPE_CHAT));
        run.setStatus(normalize(status, ConversationRunConstants.STATUS_PENDING));
        run.setPhase(normalize(phase, null));
        run.setSubStage(normalize(subStage, null));
        run.setCurrentTask(normalize(currentTask, null));
        run.setCurrentRuntimeSkillName(normalize(currentRuntimeSkillName, null));
        run.setContextJson(contextJson);
        run.setCreateUserId(userId);
        run.setStartedAt(new Date());
        conversationRunMapper.insert(run);
        return run;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRunningState(
            Long runId,
            String status,
            String phase,
            String subStage,
            String currentTask,
            String currentRuntimeSkillName,
            String contextJson) {
        if (runId == null || runId <= 0) {
            return;
        }
        conversationRunMapper.updatePhase(
                runId, status, phase, subStage, currentTask, currentRuntimeSkillName, contextJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void succeedRun(Long runId, Long finalMessageId, String contextJson) {
        if (runId == null || runId <= 0) {
            return;
        }
        conversationRunMapper.finishRun(
                runId, finalMessageId, ConversationRunConstants.STATUS_SUCCEEDED, null, null, contextJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failRun(Long runId, Long finalMessageId, String errorCode, String errorMessage, String contextJson) {
        if (runId == null || runId <= 0) {
            return;
        }
        conversationRunMapper.finishRun(
                runId, finalMessageId, ConversationRunConstants.STATUS_FAILED, errorCode, errorMessage, contextJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelRun(Long runId, Long finalMessageId, String contextJson) {
        if (runId == null || runId <= 0) {
            return;
        }
        conversationRunMapper.finishRun(
                runId, finalMessageId, ConversationRunConstants.STATUS_CANCELLED, null, null, contextJson);
    }

    public ConversationRun findLatestByTriggerMessageId(Long triggerMessageId) {
        return conversationRunMapper.selectLatestByTriggerMessageId(triggerMessageId);
    }

    public ConversationRun findByRunCode(String runCode) {
        return conversationRunMapper.selectByRunCode(runCode);
    }

    public List<ConversationRun> listBySessionId(Long sessionId, int limit) {
        return conversationRunMapper.selectBySessionId(sessionId, limit);
    }

    private String normalize(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return fallback;
    }
}
