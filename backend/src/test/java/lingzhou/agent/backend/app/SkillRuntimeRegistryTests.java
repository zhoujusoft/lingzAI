package lingzhou.agent.backend.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.spring.ai.skill.capability.ReferencesLoader;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillRuntimeRegistryTests {

    @TempDir
    Path tempDir;

    @Test
    void listFilesystemSkillsScansSkillDirectoriesAndUsesFrontMatterName() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Path skillsRoot = repoRoot.resolve("skills");
        Path contractSkillDir = skillsRoot.resolve("contract-review-pro");
        Path fallbackSkillDir = skillsRoot.resolve("fallback-skill");
        Files.createDirectories(contractSkillDir);
        Files.createDirectories(fallbackSkillDir);
        Files.writeString(
                contractSkillDir.resolve("SKILL.md"),
                """
                ---
                name: contract-review-pro
                description: 合同审核技能
                ---
                # Contract Review
                """);
        Files.writeString(
                fallbackSkillDir.resolve("SKILL.md"),
                """
                # Fallback Skill
                """);

        String previousSkillRoot = System.getProperty("skill.root");
        try {
            System.setProperty("skill.root", repoRoot.toString());
            SkillRuntimeRegistry registry = new SkillRuntimeRegistry(new GlobalToolRegistry(List.of()));

            List<SkillRuntimeRegistry.FilesystemSkillDescriptor> descriptors = registry.listFilesystemSkills();

            assertThat(descriptors).extracting(SkillRuntimeRegistry.FilesystemSkillDescriptor::runtimeSkillName)
                    .containsExactly("contract-review-pro", "fallback-skill");
            assertThat(descriptors).extracting(SkillRuntimeRegistry.FilesystemSkillDescriptor::description)
                    .contains("合同审核技能", "Filesystem skill: fallback-skill");
        } finally {
            restoreSystemProperty("skill.root", previousSkillRoot);
        }
    }

    @Test
    void registerFilesystemSkillsLoadsReferencesDirectoryIntoReferencesLoader() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Path skillDir = repoRoot.resolve("skills/legal-consultation");
        Path referencesDir = skillDir.resolve("references");
        Files.createDirectories(referencesDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                name: legal-consultation
                description: 法律咨询技能
                ---
                # Legal Consultation
                参考 [references/law-categories.md](references/law-categories.md)
                """);
        Files.writeString(referencesDir.resolve("law-categories.md"), "法条分类内容");

        String previousSkillRoot = System.getProperty("skill.root");
        try {
            System.setProperty("skill.root", repoRoot.toString());
            SkillRuntimeRegistry registry = new SkillRuntimeRegistry(new GlobalToolRegistry(List.of()));
            SimpleSkillBox skillBox = new SimpleSkillBox();
            DefaultSkillKit skillKit = DefaultSkillKit.builder()
                    .skillBox(skillBox)
                    .poolManager(new DefaultSkillPoolManager())
                    .build();

            registry.registerAll(skillKit);
            Skill skill = skillKit.getSkill("legal-consultation");

            assertThat(skill).isNotNull();
            assertThat(skill.supports(ReferencesLoader.class)).isTrue();
            ReferencesLoader loader = skill.as(ReferencesLoader.class);
            assertThat(loader.getReferences())
                    .containsEntry("law-categories.md", "法条分类内容")
                    .containsEntry("references/law-categories.md", "法条分类内容");
        } finally {
            restoreSystemProperty("skill.root", previousSkillRoot);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
