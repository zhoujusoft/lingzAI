package lingzhou.agent.backend.skillstudio.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.spring.ai.skill.capability.ReferencesLoader;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioFilesystemSkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillStudioFilesystemSkillLoader.class);
    private static final String SKILLSTUDIO_SOURCE = "skillstudio";

    public LoadedSkill load(Path workspaceRoot, String skillName, List<ToolCallback> tools) {
        Path skillsRoot =
                workspaceRoot.resolve(SkillStudioWorkspacePaths.SKILLS_ROOT).normalize();
        Path skillDir = skillsRoot.resolve(skillName).normalize();
        Path skillMarkdownPath = skillDir.resolve("SKILL.md");
        if (!skillDir.startsWith(skillsRoot) || !Files.isRegularFile(skillMarkdownPath)) {
            throw new IllegalArgumentException("未找到 skillstudio 技能: " + skillName + " at " + skillMarkdownPath);
        }
        String content = readString(skillMarkdownPath);
        Map<String, String> frontMatter = parseFrontMatter(content);
        String runtimeSkillName = normalize(frontMatter.get("name"), skillName);
        String description = normalize(frontMatter.get("description"), "SkillStudio skill: " + runtimeSkillName);
        Map<String, String> references = loadReferences(skillDir);
        SkillMetadata metadata = SkillMetadata.builder(runtimeSkillName, description, SKILLSTUDIO_SOURCE)
                .build();
        SkillKit skillKit = DefaultSkillKit.builder()
                .skillBox(new SimpleSkillBox())
                .poolManager(new DefaultSkillPoolManager())
                .tools(tools)
                .build();
        skillKit.register(metadata, () -> new FilesystemSkill(metadata, content, tools, references));
        skillKit.activateSkill(runtimeSkillName);
        log.info(
                "加载 skillstudio 技能完成：skillName={}, path={}, tools={}",
                runtimeSkillName,
                skillMarkdownPath,
                tools.size());
        return new LoadedSkill(runtimeSkillName, skillMarkdownPath, skillKit, List.copyOf(tools));
    }

    private Map<String, String> loadReferences(Path skillDir) {
        Path referencesDir = skillDir.resolve("references");
        if (!Files.isDirectory(referencesDir)) {
            return Map.of();
        }
        Map<String, String> references = new LinkedHashMap<>();
        try (var stream = Files.walk(referencesDir)) {
            stream.filter(Files::isRegularFile).sorted().forEach(path -> {
                String relativeKey = referencesDir.relativize(path).toString().replace('\\', '/');
                String content = readString(path);
                references.put(relativeKey, content);
                references.putIfAbsent("references/" + relativeKey, content);
                int lastSlash = relativeKey.lastIndexOf('/');
                String fileName = lastSlash >= 0 ? relativeKey.substring(lastSlash + 1) : relativeKey;
                references.putIfAbsent(fileName, content);
            });
        } catch (IOException ex) {
            log.warn("读取 skillstudio 参考资料失败：skillDir={}, error={}", skillDir, ex.getMessage(), ex);
        }
        return references;
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 skillstudio 文件失败: " + path, ex);
        }
    }

    private static Map<String, String> parseFrontMatter(String content) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!StringUtils.hasText(content) || !content.startsWith("---")) {
            return fields;
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return fields;
        }
        String header = content.substring(3, end);
        for (String line : header.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            if (parts.length == 2) {
                fields.put(parts[0].trim(), parts[1].trim());
            }
        }
        return fields;
    }

    private static String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public record LoadedSkill(
            String runtimeSkillName, Path skillMarkdownPath, SkillKit skillKit, List<ToolCallback> tools) {}

    private static final class FilesystemSkill implements Skill, ReferencesLoader {

        private final SkillMetadata metadata;
        private final String content;
        private final List<ToolCallback> tools;
        private final Map<String, String> references;

        private FilesystemSkill(
                SkillMetadata metadata, String content, List<ToolCallback> tools, Map<String, String> references) {
            this.metadata = metadata;
            this.content = content;
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            this.references =
                    references == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(references));
        }

        @Override
        public SkillMetadata getMetadata() {
            return metadata;
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public List<ToolCallback> getTools() {
            return tools;
        }

        @Override
        public Map<String, String> getReferences() {
            return references;
        }
    }
}
