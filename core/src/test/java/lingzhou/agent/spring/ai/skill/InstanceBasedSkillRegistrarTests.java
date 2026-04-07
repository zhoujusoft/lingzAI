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
import lingzhou.agent.spring.ai.skill.fixtures.CalculatorSkill;
import lingzhou.agent.spring.ai.skill.registration.InstanceBasedSkillRegistrar;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InstanceBasedSkillRegistrar}.
 *
 * @author LinPeng Zhang
 */
class InstanceBasedSkillRegistrarTests {

    private InstanceBasedSkillRegistrar registrar;

    private SkillPoolManager poolManager;

    @BeforeEach
    void setUp() {
        registrar = InstanceBasedSkillRegistrar.builder().build();
        poolManager = new DefaultSkillPoolManager();
    }

    @Test
    void registerShouldRegisterValidSkillInstance() {
        CalculatorSkill skill = new CalculatorSkill();

        SkillDefinition definition = registrar.register(poolManager, skill);

        assertThat(definition).isNotNull();
        assertThat(definition.getMetadata().getName()).isEqualTo("calculator");
        assertThat(poolManager.hasDefinition(definition.getSkillId())).isTrue();
    }

    @Test
    void registerShouldValidateInputs() {
        assertThatThrownBy(() -> registrar.register(null, new CalculatorSkill()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registrar.register(poolManager, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registrar.register(poolManager, "not a skill"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Skill");
    }

    @Test
    void supportsShouldIdentifyValidInstances() {
        assertThat(registrar.supports(new CalculatorSkill())).isTrue();
        assertThat(registrar.supports("not a skill")).isFalse();
        assertThat(registrar.supports(null)).isFalse();
    }
}
