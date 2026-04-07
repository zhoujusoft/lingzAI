/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lingzhou.agent.spring.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import lingzhou.agent.spring.ai.skill.common.LoadStrategy;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillDefinition;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.exception.SkillLoadException;
import lingzhou.agent.spring.ai.skill.exception.SkillNotFoundException;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * Unit tests for {@link DefaultSkillPoolManager}.
 *
 * @author LinPeng Zhang
 */
class DefaultSkillPoolManagerTests {

    private DefaultSkillPoolManager poolManager;

    private SkillMetadata testMetadata;

    @BeforeEach
    void setUp() {
        poolManager = new DefaultSkillPoolManager();
        testMetadata = SkillMetadata.builder("test", "Test skill", "spring").build();
    }

    @Test
    void registerDefinitionShouldStoreDefinitionAndRejectDuplicates() {
        SkillDefinition definition = createDefinition("test_spring");

        poolManager.registerDefinition(definition);

        assertThat(poolManager.hasDefinition("test_spring")).isTrue();
        assertThat(poolManager.getDefinition("test_spring")).isNotNull();

        assertThatThrownBy(() -> poolManager.registerDefinition(definition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lazyLoadingShouldCacheInstancesOnFirstCall() {
        SkillDefinition definition = createDefinition("test_spring", LoadStrategy.LAZY);
        poolManager.registerDefinition(definition);

        Skill skill1 = poolManager.load("test_spring");
        Skill skill2 = poolManager.load("test_spring");

        assertThat(skill1).isNotNull();
        assertThat(skill1).isSameAs(skill2);
    }

    @Test
    void eagerLoadingShouldCacheInstancesImmediately() {
        SkillDefinition definition = createDefinition("test_spring", LoadStrategy.EAGER);

        poolManager.registerDefinition(definition);

        Skill skill1 = poolManager.load("test_spring");
        Skill skill2 = poolManager.load("test_spring");
        assertThat(skill1).isSameAs(skill2);
    }

    @Test
    void loadShouldThrowExceptionForNonexistentSkill() {
        assertThatThrownBy(() -> poolManager.load("nonexistent")).isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    void loadShouldWrapLoaderExceptions() {
        SkillDefinition definition = SkillDefinition.builder()
                .skillId("test_spring")
                .source("spring")
                .metadata(testMetadata)
                .loader(() -> {
                    throw new RuntimeException("Loader failed");
                })
                .build();
        poolManager.registerDefinition(definition);

        assertThatThrownBy(() -> poolManager.load("test_spring"))
                .isInstanceOf(SkillLoadException.class)
                .hasMessageContaining("Failed to load skill");
    }

    @Test
    void evictShouldRemoveInstancesButKeepDefinitions() {
        poolManager.registerDefinition(createDefinition("test1_spring"));
        poolManager.registerDefinition(createDefinition("test2_spring"));
        Skill skill1 = poolManager.load("test1_spring");

        poolManager.evict("test1_spring");
        Skill skill1Reloaded = poolManager.load("test1_spring");
        assertThat(skill1).isNotSameAs(skill1Reloaded);
        assertThat(poolManager.hasDefinition("test1_spring")).isTrue();

        poolManager.evictAll();
        skill1Reloaded = poolManager.load("test1_spring");
        assertThat(poolManager.hasDefinition("test1_spring")).isTrue();
    }

    @Test
    void unregisterAndClearShouldRemoveDefinitions() {
        poolManager.registerDefinition(createDefinition("test1_spring"));
        poolManager.registerDefinition(createDefinition("test2_spring"));
        poolManager.load("test1_spring");

        poolManager.unregister("test1_spring");
        assertThat(poolManager.hasDefinition("test1_spring")).isFalse();
        assertThatThrownBy(() -> poolManager.load("test1_spring")).isInstanceOf(SkillNotFoundException.class);

        poolManager.clear();
        assertThat(poolManager.getDefinitions()).isEmpty();
    }

    @Test
    void getDefinitionsBySourceShouldFilterBySource() {
        poolManager.registerDefinition(createDefinition("test1_spring", "spring"));
        poolManager.registerDefinition(createDefinition("test2_spring", "spring"));
        poolManager.registerDefinition(createDefinition("test3_official", "official"));

        List<SkillDefinition> springSkills = poolManager.getDefinitionsBySource("spring");
        List<SkillDefinition> officialSkills = poolManager.getDefinitionsBySource("official");

        assertThat(springSkills).hasSize(2);
        assertThat(officialSkills).hasSize(1);
    }

    private SkillDefinition createDefinition(String skillId) {
        return createDefinition(skillId, LoadStrategy.LAZY);
    }

    private SkillDefinition createDefinition(String skillId, LoadStrategy strategy) {
        return createDefinition(skillId, "spring", strategy);
    }

    private SkillDefinition createDefinition(String skillId, String source) {
        return createDefinition(skillId, source, LoadStrategy.LAZY);
    }

    private SkillDefinition createDefinition(String skillId, String source, LoadStrategy strategy) {
        return SkillDefinition.builder()
                .skillId(skillId)
                .source(source)
                .metadata(testMetadata)
                .loader(() -> new TestSkill(testMetadata))
                .loadStrategy(strategy)
                .build();
    }

    private static class TestSkill implements Skill {

        private final SkillMetadata metadata;

        TestSkill(SkillMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public SkillMetadata getMetadata() {
            return metadata;
        }

        @Override
        public String getContent() {
            return "Test content";
        }

        @Override
        public List<ToolCallback> getTools() {
            return Collections.emptyList();
        }
    }
}
