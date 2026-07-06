package lingzhou.agent.spring.ai.wechat.ilink.core.serializer;

public interface Serializer {
    String serialize(Object obj);

    <T> T deserialize(String text, Class<T> clazz);
}
