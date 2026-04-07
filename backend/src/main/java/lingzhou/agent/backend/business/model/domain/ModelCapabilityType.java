package lingzhou.agent.backend.business.model.domain;

import java.util.List;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.util.StringUtils;

public enum ModelCapabilityType {
    CHAT,
    EMBEDDING,
    RERANK;

    public static List<ModelCapabilityType> orderedValues() {
        return List.of(CHAT, EMBEDDING, RERANK);
    }

    public static String normalize(String value) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException("模型能力类型不能为空", TaskException.Code.UNKNOWN);
        }
        String normalized = value.trim().toUpperCase();
        for (ModelCapabilityType capabilityType : values()) {
            if (capabilityType.name().equals(normalized)) {
                return capabilityType.name();
            }
        }
        throw new TaskException("不支持的模型能力类型：" + value, TaskException.Code.UNKNOWN);
    }
}
