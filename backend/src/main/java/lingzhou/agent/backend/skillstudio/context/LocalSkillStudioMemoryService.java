package lingzhou.agent.backend.skillstudio.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lingzhou.agent.backend.skillstudio.draft.SkillStudioDraftPathResolver;
import org.springframework.stereotype.Service;

@Service
public class LocalSkillStudioMemoryService implements SkillStudioMemoryService {

    private final SkillStudioDraftPathResolver pathResolver;

    public LocalSkillStudioMemoryService(SkillStudioDraftPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @Override
    public Optional<String> readMemory(String skillName) {
        Path target = memoryPath(skillName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("读取 skill studio memory 失败: " + target, ex);
        }
    }

    @Override
    public void writeMemory(String skillName, String content) {
        Path target = memoryPath(skillName);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("写入 skill studio memory 失败: " + target, ex);
        }
    }

    @Override
    public boolean exists(String skillName) {
        return Files.isRegularFile(memoryPath(skillName));
    }

    private Path memoryPath(String skillName) {
        return pathResolver
                .resolveSkillDraftDir(skillName)
                .resolve(".studio")
                .resolve("memory.md")
                .normalize();
    }
}
