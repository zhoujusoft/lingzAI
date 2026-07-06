package lingzhou.agent.backend.skillstudio.context;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.draft.SkillStudioDraftFileService;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultSkillStudioContextSummaryService implements SkillStudioContextSummaryService {

    private final SkillStudioDraftFileService draftFileService;
    private final SkillStudioMemoryService memoryService;

    public DefaultSkillStudioContextSummaryService(
            SkillStudioDraftFileService draftFileService, SkillStudioMemoryService memoryService) {
        this.draftFileService = draftFileService;
        this.memoryService = memoryService;
    }

    @Override
    public SkillStudioContextInput.DraftSummary buildDraftSummary(String skillName) {
        List<String> files = new ArrayList<>();
        draftFileService
                .listAllFiles(skillName)
                .forEach(file -> files.add(SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + skillName + "/" + file));
        List<String> references = draftFileService.listReferenceFiles(skillName);
        String skillSummary = draftFileService
                .readSkillMd(skillName)
                .map(this::summarizeSkillMd)
                .orElse("");
        return new SkillStudioContextInput.DraftSummary(List.copyOf(files), List.copyOf(references), skillSummary);
    }

    @Override
    public SkillStudioContextInput.MemorySummary buildMemorySummary(String skillName) {
        String memory = memoryService.readMemory(skillName).orElse("");
        return new SkillStudioContextInput.MemorySummary(
                extractInlineValue(memory, "Base Template:"),
                extractInlineValue(memory, "Capability Template:"),
                extractBulletList(memory, "Stable Constraints"),
                extractBulletList(memory, "### References"),
                extractBulletList(memory, "## Notes For Future Edits"));
    }

    private String summarizeSkillMd(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r", "").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) : normalized;
    }

    private String extractInlineValue(String markdown, String prefix) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- " + prefix)) {
                return trimmed.substring(("- " + prefix).length()).trim().replace("`", "");
            }
        }
        return "";
    }

    private List<String> extractBulletList(String markdown, String sectionTitle) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        String[] lines = markdown.split("\n");
        boolean inSection = false;
        List<String> items = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.equals(sectionTitle)) {
                inSection = true;
                continue;
            }
            if (inSection && line.startsWith("## ")) {
                break;
            }
            if (inSection && line.startsWith("### ") && !sectionTitle.startsWith("### ")) {
                break;
            }
            if (inSection && line.startsWith("- ")) {
                items.add(line.substring(2).trim().replace("`", ""));
            }
        }
        return List.copyOf(items);
    }
}
