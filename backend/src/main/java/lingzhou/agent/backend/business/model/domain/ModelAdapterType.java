package lingzhou.agent.backend.business.model.domain;

import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.util.StringUtils;

public enum ModelAdapterType {
    QWEN_ONLINE,
    VLLM;

    public static String normalize(String value) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException("模型适配器不能为空", TaskException.Code.UNKNOWN);
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        for (ModelAdapterType adapterType : values()) {
            if (adapterType.name().equals(normalized)) {
                return adapterType.name();
            }
        }
        throw new TaskException("不支持的模型适配器：" + value, TaskException.Code.UNKNOWN);
    }

    public static String toChatProvider(String adapterType) {
        if (VLLM.name().equalsIgnoreCase(adapterType)) {
            return "vllm";
        }
        return "qwen-online";
    }
}
