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

import lingzhou.agent.spring.ai.skill.core.SkillDefinition;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.fixtures.WeatherSkill;
import lingzhou.agent.spring.ai.skill.registration.ClassBasedSkillRegistrar;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClassBasedSkillRegistrar}.
 *
 * @author LinPeng Zhang
 */
class ClassBasedSkillRegistrarTests {

    private ClassBasedSkillRegistrar registrar;

    private SkillPoolManager poolManager;

    @BeforeEach
    void setUp() {
        registrar = ClassBasedSkillRegistrar.builder().build();
        poolManager = new DefaultSkillPoolManager();
    }

    @Test
    void registerShouldRegisterValidSkillClass() {
        SkillDefinition definition = registrar.register(poolManager, WeatherSkill.class);

        assertThat(definition).isNotNull();
        assertThat(definition.getMetadata().getName()).isEqualTo("weather");
        assertThat(poolManager.hasDefinition(definition.getSkillId())).isTrue();
    }

    @Test
    void registerShouldValidateInputs() {
        assertThatThrownBy(() -> registrar.register(null, WeatherSkill.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registrar.register(poolManager, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registrar.register(poolManager, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Skill");
    }

    @Test
    void supportsShouldIdentifyValidClasses() {
        assertThat(registrar.supports(WeatherSkill.class)).isTrue();
        assertThat(registrar.supports(String.class)).isFalse();
        assertThat(registrar.supports("not a class")).isFalse();
        assertThat(registrar.supports(null)).isFalse();
    }
}
