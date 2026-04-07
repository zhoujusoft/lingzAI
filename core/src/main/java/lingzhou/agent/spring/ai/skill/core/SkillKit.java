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
package lingzhou.agent.spring.ai.skill.core;

import java.util.List;
import java.util.function.Supplier;
import lingzhou.agent.spring.ai.skill.exception.SkillRegistrationException;
import lingzhou.agent.spring.ai.skill.exception.SkillValidationException;
import org.springframework.ai.tool.ToolCallback;

/**
 * Unified coordination interface for skill management.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 * @see DefaultSkillKit
 */
public interface SkillKit {

    // ==================== Skill Registration ====================

    /**
     * Registers skill with metadata and lazy loader.
     *
     * @param metadata skill metadata
     * @param loader skill instance loader
     * @throws SkillValidationException if validation fails
     * @throws SkillRegistrationException if registration fails
     */
    void register(SkillMetadata metadata, Supplier<Skill> loader);

    /**
     * Registers skill from instance.
     *
     * @param instance skill instance with @Skill annotation
     * @throws IllegalArgumentException if instance class lacks @Skill annotation
     */
    void register(Object instance);

    /**
     * Registers skill from class with lazy loading.
     *
     * @param skillClass skill class with @Skill annotation
     * @throws IllegalArgumentException if class lacks @Skill annotation
     */
    void register(Class<?> skillClass);

    // ==================== Skill Access ====================

    /**
     * Checks if skill exists by name.
     *
     * @param name skill name
     * @return true if exists
     */
    boolean exists(String name);

    /**
     * Gets skill instance by name.
     *
     * @param name skill name
     * @return skill instance or null if not found
     */
    Skill getSkill(String name);

    /**
     * Gets skill metadata by name.
     *
     * @param name skill name
     * @return skill metadata or null if not found
     */
    SkillMetadata getMetadata(String name);

    // ==================== Skill Activation ====================

    /**
     * Activates skill by name.
     *
     * @param name skill name
     * @throws IllegalArgumentException if skill not found
     */
    void activateSkill(String name);

    /**
     * Deactivates skill by name.
     *
     * @param name skill name
     * @throws IllegalArgumentException if skill not found
     */
    void deactivateSkill(String name);

    /**
     * Deactivates all skills.
     */
    void deactivateAllSkills();

    /**
     * Checks if skill is activated.
     *
     * @param name skill name
     * @return true if activated
     */
    boolean isActivated(String name);

    // ==================== Tools ====================

    /**
     * Gets framework skill tools for progressive loading.
     *
     * @return skill tool list
     */
    List<ToolCallback> getSkillLoaderTools();

    /**
     * Gets all active skill tools.
     *
     * @return active tool list (never null)
     * @throws IllegalStateException if data inconsistency detected
     */
    List<ToolCallback> getAllActiveTools();

    /**
     * Gets system prompt describing available skills.
     *
     * @return system prompt (never null)
     */
    String getSkillSystemPrompt();

    /**
     * Gets activated skill names.
     *
     * @return activated skill names (never null)
     */
    java.util.Set<String> getActivatedSkillNames();
}
