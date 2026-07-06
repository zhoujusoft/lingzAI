package lingzhou.agent.backend.skillstudio.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ClasspathSkillStudioIntentMapLoader implements SkillStudioIntentMapLoader {

    private static final String RESOURCE_PATH = "skillstudio/intent-map.json";

    private final ObjectMapper objectMapper;
    private volatile SkillStudioIntentMap intentMap;

    public ClasspathSkillStudioIntentMapLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SkillStudioIntentMap load() {
        SkillStudioIntentMap snapshot = this.intentMap;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (this.intentMap != null) {
                return this.intentMap;
            }
            this.intentMap = readIntentMap();
            return this.intentMap;
        }
    }

    private SkillStudioIntentMap readIntentMap() {
        try {
            ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, SkillStudioIntentMap.class);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("加载 skill studio intent-map 失败", ex);
        }
    }
}
