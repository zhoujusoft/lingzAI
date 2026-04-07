package lingzhou.agent.backend.capability.skillruntime.registry;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.app.SkillFilesystemSupport;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.skills.FashionGuideSkill;
import lingzhou.agent.backend.skills.InventorySkill;
import lingzhou.agent.backend.skills.PricingSkill;
import lingzhou.agent.backend.skills.PurchaseStrategySkill;
import lingzhou.agent.backend.skills.SupplierSkill;
import lingzhou.agent.backend.skills.TrendSkill;
import lingzhou.agent.backend.skills.WeatherSkill;
import lingzhou.agent.spring.ai.skill.capability.ReferencesLoader;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SkillRuntimeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SkillRuntimeRegistry.class);

    private static final String FILESYSTEM_SOURCE = "filesystem";

    private final GlobalToolRegistry globalToolRegistry;

    private final Object reloadMonitor = new Object();

    public SkillRuntimeRegistry(GlobalToolRegistry globalToolRegistry) {
        this.globalToolRegistry = globalToolRegistry;
    }

    public void registerAll(SkillKit skillKit) {
        registerBuiltinSkills(skillKit);
        registerFilesystemSkills(skillKit);
    }

    public void reload(SkillKit skillKit, SkillPoolManager poolManager, SimpleSkillBox skillBox) {
        synchronized (reloadMonitor) {
            skillKit.deactivateAllSkills();
            poolManager.clear();
            resetSkillBox(skillBox);
            registerAll(skillKit);
        }
    }

    public List<FilesystemSkillDescriptor> listFilesystemSkills() {
        Path root = SkillFilesystemSupport.resolveSkillRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .sorted()
                    .map(this::toDescriptor)
                    .filter(descriptor -> descriptor != null)
                    .toList();
        } catch (IOException ex) {
            logger.warn("扫描 filesystem skill 目录失败：root={}, error={}", root, ex.getMessage(), ex);
            return List.of();
        }
    }

    public FilesystemSkillDescriptor findFilesystemSkill(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return null;
        }
        return listFilesystemSkills().stream()
                .filter(item -> runtimeSkillName.trim().equals(item.runtimeSkillName()))
                .findFirst()
                .orElse(null);
    }

    private void registerBuiltinSkills(SkillKit skillKit) {
        skillKit.register(InventorySkill.create());
        skillKit.register(PricingSkill.create());
        skillKit.register(TrendSkill.create());
        skillKit.register(SupplierSkill.class);
        skillKit.register(PurchaseStrategySkill.class);
        skillKit.register(WeatherSkill.class);
        skillKit.register(FashionGuideSkill.class);
    }

    private void registerFilesystemSkills(SkillKit skillKit) {
        List<ToolCallback> baseTools = globalToolRegistry.getToolCallbacks();
        for (FilesystemSkillDescriptor descriptor : listFilesystemSkills()) {
            String content = readSkillFileAsString(descriptor.skillMarkdownPath());
            Map<String, String> references = loadReferences(descriptor.directoryPath());
            SkillMetadata metadata = SkillMetadata.builder(
                            descriptor.runtimeSkillName(),
                            descriptor.description(),
                            FILESYSTEM_SOURCE)
                    .build();
            skillKit.register(metadata, () -> new FilesystemSkill(metadata, content, baseTools, references));
        }
    }

    private FilesystemSkillDescriptor toDescriptor(Path skillDir) {
        Path skillMarkdownPath = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMarkdownPath)) {
            return null;
        }
        String content = readSkillFileAsString(skillMarkdownPath);
        Map<String, String> frontMatter = parseFrontMatter(content);
        String directoryName = skillDir.getFileName() == null ? "" : skillDir.getFileName().toString();
        String runtimeSkillName = normalize(frontMatter.get("name"), directoryName);
        String description = normalize(frontMatter.get("description"), "Filesystem skill: " + runtimeSkillName);
        return new FilesystemSkillDescriptor(runtimeSkillName, description, skillDir, skillMarkdownPath);
    }

    private static String readSkillFileAsString(Path skillPath) {
        try {
            return Files.readString(skillPath);
        } catch (IOException ex) {
            LoggerFactory.getLogger(SkillRuntimeRegistry.class)
                    .warn("读取 skill 文件失败：path={}, error={}", skillPath, ex.getMessage(), ex);
            return "Failed to read skill content: " + ex.getMessage();
        }
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
                String content = readSkillFileAsString(path);
                references.put(relativeKey, content);
                references.putIfAbsent("references/" + relativeKey, content);
                int lastSlash = relativeKey.lastIndexOf('/');
                String fileName = lastSlash >= 0 ? relativeKey.substring(lastSlash + 1) : relativeKey;
                references.putIfAbsent(fileName, content);
            });
        } catch (IOException ex) {
            logger.warn("读取 filesystem skill 参考资料失败：skillDir={}, error={}", skillDir, ex.getMessage(), ex);
        }
        return references;
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

    @SuppressWarnings("unchecked")
    private void resetSkillBox(SimpleSkillBox skillBox) {
        try {
            Field skillsField = SimpleSkillBox.class.getDeclaredField("skills");
            Field activatedField = SimpleSkillBox.class.getDeclaredField("activated");
            Field sourcesField = SimpleSkillBox.class.getDeclaredField("sources");
            skillsField.setAccessible(true);
            activatedField.setAccessible(true);
            sourcesField.setAccessible(true);
            ((Map<String, ?>) skillsField.get(skillBox)).clear();
            ((Map<String, ?>) activatedField.get(skillBox)).clear();
            List<String> sources = (List<String>) sourcesField.get(skillBox);
            sources.clear();
            sources.add("custom");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("重置 SkillBox 失败", ex);
        }
    }

    public record FilesystemSkillDescriptor(
            String runtimeSkillName, String description, Path directoryPath, Path skillMarkdownPath) {}

    private static final class FilesystemSkill implements Skill, ReferencesLoader {

        private final SkillMetadata metadata;
        private final String content;
        private final List<ToolCallback> tools;
        private final Map<String, String> references;

        private FilesystemSkill(
                SkillMetadata metadata,
                String content,
                List<ToolCallback> tools,
                Map<String, String> references) {
            this.metadata = metadata;
            this.content = content;
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            this.references = references == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(references));
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
