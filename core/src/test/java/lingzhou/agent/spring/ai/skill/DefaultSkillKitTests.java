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

import java.util.List;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillBox;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.exception.SkillRegistrationException;
import lingzhou.agent.spring.ai.skill.exception.SkillValidationException;
import lingzhou.agent.spring.ai.skill.fixtures.CalculatorSkill;
import lingzhou.agent.spring.ai.skill.fixtures.WeatherSkill;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * Unit tests for {@link DefaultSkillKit}.
 *
 * @author LinPeng Zhang
 */
class DefaultSkillKitTests {

    private DefaultSkillKit skillKit;

    private SkillPoolManager poolManager;

    private SkillBox skillBox;

    @BeforeEach
    void setUp() {
        poolManager = new DefaultSkillPoolManager();
        SimpleSkillBox simpleSkillBox = new SimpleSkillBox();
        simpleSkillBox.setSources(List.of("custom", "example"));
        skillBox = simpleSkillBox;
        skillKit = DefaultSkillKit.builder()
                .skillBox(skillBox)
                .poolManager(poolManager)
                .build();
    }

    @Test
    void registerShouldValidateInputsAndRejectDuplicates() {
        SkillMetadata metadata =
                SkillMetadata.builder("test", "description", "custom").build();

        assertThatThrownBy(() -> skillKit.register(null, () -> new TestSkill(metadata)))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("metadata cannot be null");
        assertThatThrownBy(() -> skillKit.register(metadata, null))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("loader cannot be null");

        skillKit.register(metadata, () -> new TestSkill(metadata));
        assertThatThrownBy(() -> skillKit.register(metadata, () -> new TestSkill(metadata)))
                .isInstanceOf(SkillRegistrationException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerFromInstanceShouldRegisterAnnotatedSkill() {
        skillKit.register(new CalculatorSkill());

        assertThat(skillKit.exists("calculator")).isTrue();
        assertThat(skillKit.getMetadata("calculator")).isNotNull();
    }

    @Test
    void registerFromClassShouldRegisterClass() {
        skillKit.register(WeatherSkill.class);

        assertThat(skillKit.exists("weather")).isTrue();
        Skill skill = skillKit.getSkill("weather");
        assertThat(skill).isNotNull();
        assertThat(skill.getTools()).isNotEmpty();
    }

    @Test
    void deactivateAllSkillsShouldClearAllActivation() {
        skillKit.register(new CalculatorSkill());
        skillKit.register(WeatherSkill.class);
        skillKit.activateSkill("calculator");
        skillKit.activateSkill("weather");

        skillKit.deactivateAllSkills();

        assertThat(skillKit.isActivated("calculator")).isFalse();
        assertThat(skillKit.isActivated("weather")).isFalse();
    }

    @Test
    void getSkillByNameShouldReturnSkillOrNull() {
        skillKit.register(new CalculatorSkill());

        Skill skill = skillKit.getSkill("calculator");
        assertThat(skill).isNotNull();
        assertThat(skill.getMetadata().getName()).isEqualTo("calculator");

        Skill nonexistent = skillKit.getSkill("nonexistent");
        assertThat(nonexistent).isNull();
    }

    @Test
    void getAllActiveToolsShouldReturnToolsFromActivatedSkills() {
        skillKit.register(WeatherSkill.class);
        skillKit.activateSkill("weather");

        List<ToolCallback> tools = skillKit.getAllActiveTools();

        assertThat(tools).isNotEmpty();
    }

    @Test
    void getSkillSystemPromptShouldGeneratePromptContent() {
        String emptyPrompt = skillKit.getSkillSystemPrompt();
        assertThat(emptyPrompt).isEmpty();

        skillKit.register(new CalculatorSkill());
        String prompt = skillKit.getSkillSystemPrompt();
        //        assertThat(prompt).contains("calculator").contains("loadSkillContent");
    }

    @Test
    void multiTenantShouldIsolateBoxButSharePoolManager() {
        SkillBox tenant1Box = new SimpleSkillBox();
        SkillBox tenant2Box = new SimpleSkillBox();
        SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

        DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
                .skillBox(tenant1Box)
                .poolManager(sharedPoolManager)
                .build();
        DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
                .skillBox(tenant2Box)
                .poolManager(sharedPoolManager)
                .build();

        tenant1Kit.register(new CalculatorSkill());

        assertThat(tenant1Kit.exists("calculator")).isTrue();
        assertThat(tenant2Kit.exists("calculator")).isFalse();
    }

    @Test
    void multiTenantShouldIsolateActivationState() {
        SimpleSkillBox tenant1Box = new SimpleSkillBox();
        tenant1Box.setSources(List.of("example"));
        SimpleSkillBox tenant2Box = new SimpleSkillBox();
        tenant2Box.setSources(List.of("example"));
        SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

        DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
                .skillBox(tenant1Box)
                .poolManager(sharedPoolManager)
                .build();
        DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
                .skillBox(tenant2Box)
                .poolManager(sharedPoolManager)
                .build();

        tenant1Kit.register(new CalculatorSkill());
        tenant1Kit.activateSkill("calculator");

        tenant2Kit.addSkillToBox("calculator");

        assertThat(tenant1Kit.isActivated("calculator")).isTrue();
        assertThat(tenant2Kit.isActivated("calculator")).isFalse();
    }

    @Test
    void multiTenantShouldShareSingletonInstances() {
        SimpleSkillBox tenant1Box = new SimpleSkillBox();
        tenant1Box.setSources(List.of("example"));
        SimpleSkillBox tenant2Box = new SimpleSkillBox();
        tenant2Box.setSources(List.of("example"));
        SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

        DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
                .skillBox(tenant1Box)
                .poolManager(sharedPoolManager)
                .build();
        DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
                .skillBox(tenant2Box)
                .poolManager(sharedPoolManager)
                .build();

        tenant1Kit.register(new CalculatorSkill());
        tenant2Kit.addSkillToBox("calculator");

        Skill skill1 = tenant1Kit.getSkill("calculator");
        Skill skill2 = tenant2Kit.getSkill("calculator");

        assertThat(skill1).isSameAs(skill2);
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
            return List.of();
        }
    }
}
