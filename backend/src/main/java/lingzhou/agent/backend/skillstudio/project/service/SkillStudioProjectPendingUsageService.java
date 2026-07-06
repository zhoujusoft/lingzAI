package lingzhou.agent.backend.skillstudio.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.skillstudio.draft.SkillStudioDraftPathResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioProjectPendingUsageService {

    private static final String PENDING_METADATA_USAGE_FILE = "pending-metadata-usage.json";

    private final SkillStudioDraftPathResolver draftPathResolver;
    private final ObjectMapper objectMapper;

    public SkillStudioProjectPendingUsageService(
            SkillStudioDraftPathResolver draftPathResolver, ObjectMapper objectMapper) {
        this.draftPathResolver = draftPathResolver;
        this.objectMapper = objectMapper;
    }

    public void savePendingMetadataUsage(String skillName, RuntimeRunUsageSnapshot snapshot) {
        if (!StringUtils.hasText(skillName) || snapshot == null) {
            return;
        }
        Path target = pendingUsagePath(skillName);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, objectMapper.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("写入技能工坊待合并 usage 失败: " + target, ex);
        }
    }

    public Optional<RuntimeRunUsageSnapshot> consumePendingMetadataUsage(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return Optional.empty();
        }
        Path target = pendingUsagePath(skillName);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            RuntimeRunUsageSnapshot snapshot = objectMapper.readValue(
                    Files.readString(target, StandardCharsets.UTF_8), RuntimeRunUsageSnapshot.class);
            Files.deleteIfExists(target);
            return Optional.ofNullable(snapshot);
        } catch (IOException ex) {
            throw new IllegalStateException("读取技能工坊待合并 usage 失败: " + target, ex);
        }
    }

    private Path pendingUsagePath(String skillName) {
        return draftPathResolver
                .resolveSkillDraftDir(skillName)
                .resolve(".studio")
                .resolve(PENDING_METADATA_USAGE_FILE)
                .normalize();
    }
}
