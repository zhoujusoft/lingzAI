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
package lingzhou.agent.spring.ai.skill.registration;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lingzhou.agent.spring.ai.skill.adapter.SkillProxy;
import lingzhou.agent.spring.ai.skill.annotation.Skill;
import lingzhou.agent.spring.ai.skill.annotation.SkillContent;
import lingzhou.agent.spring.ai.skill.annotation.SkillTools;
import lingzhou.agent.spring.ai.skill.capability.SkillReferences;
import lingzhou.agent.spring.ai.skill.common.LoadStrategy;
import lingzhou.agent.spring.ai.skill.core.SkillDefinition;
import lingzhou.agent.spring.ai.skill.core.SkillIdGenerator;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.core.SkillRegistrar;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillIdGenerator;

/**
 * Instance-based skill registrar for pre-created POJO instances.
 *
 * <p>Supports both interface mode (Skill implementation) and annotation mode (@Skill POJO).
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class InstanceBasedSkillRegistrar implements SkillRegistrar<Object> {

    private final SkillIdGenerator idGenerator;

    private InstanceBasedSkillRegistrar(Builder builder) {
        this.idGenerator = (builder.idGenerator != null) ? builder.idGenerator : new DefaultSkillIdGenerator();
    }

    /**
     * Creates a new builder instance.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InstanceBasedSkillRegistrar.
     */
    public static class Builder {
        private SkillIdGenerator idGenerator;

        private Builder() {}

        /**
         * Sets custom ID generator.
         *
         * @param idGenerator ID generator instance
         * @return this builder
         */
        public Builder idGenerator(SkillIdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        /**
         * Builds the InstanceBasedSkillRegistrar instance.
         *
         * @return new InstanceBasedSkillRegistrar instance
         */
        public InstanceBasedSkillRegistrar build() {
            return new InstanceBasedSkillRegistrar(this);
        }
    }

    /**
     * Registers skill from instance.
     *
     * @param poolManager skill pool manager
     * @param instance skill instance annotated with @Skill
     * @return created skill definition
     * @throws IllegalArgumentException if instance class missing @Skill annotation
     */
    @Override
    public SkillDefinition register(SkillPoolManager poolManager, Object instance) {
        Objects.requireNonNull(poolManager, "poolManager cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");

        Class<?> skillClass = instance.getClass();
        Skill skillAnnotation = skillClass.getAnnotation(Skill.class);

        if (skillAnnotation == null) {
            throw new IllegalArgumentException(
                    "Instance class " + skillClass.getName() + " must be annotated with @Skill");
        }

        String source = skillAnnotation.source();

        SkillMetadata metadata = SkillMetadata.builder(skillAnnotation.name(), skillAnnotation.description(), source)
                .extensions(extractExtensions(skillAnnotation))
                .build();

        String skillId = idGenerator.generateId(metadata);

        Supplier<lingzhou.agent.spring.ai.skill.core.Skill> loader = () -> buildSkillFromInstance(instance, metadata);

        SkillDefinition definition = SkillDefinition.builder()
                .skillId(skillId)
                .source(source)
                .loader(loader)
                .metadata(metadata)
                .loadStrategy(LoadStrategy.LAZY)
                .build();

        poolManager.registerDefinition(definition);

        return definition;
    }

    /**
     * Builds Skill from instance.
     *
     * @param instance skill instance
     * @param metadata skill metadata
     * @return Skill instance (either direct or wrapped)
     */
    protected static lingzhou.agent.spring.ai.skill.core.Skill buildSkillFromInstance(
            Object instance, SkillMetadata metadata) {
        if (instance instanceof lingzhou.agent.spring.ai.skill.core.Skill) {
            return (lingzhou.agent.spring.ai.skill.core.Skill) instance;
        }

        Map<String, Method> extensionMethods = extractExtensionMethods(instance.getClass());
        return new SkillProxy(metadata, instance, extensionMethods);
    }

    /**
     * Extracts annotated extension methods from class.
     *
     * @param clazz class to scan
     * @return extension method map
     */
    protected static Map<String, Method> extractExtensionMethods(Class<?> clazz) {
        Map<String, Method> extensionMethods = new HashMap<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SkillContent.class)) {
                extensionMethods.put("content", method);
            }
            if (method.isAnnotationPresent(SkillTools.class)) {
                extensionMethods.put("tools", method);
            }
            if (method.isAnnotationPresent(SkillReferences.class)) {
                extensionMethods.put("references", method);
            }
        }

        return extensionMethods;
    }

    /**
     * Extracts extension properties from @Skill annotation.
     *
     * @param skillAnnotation @Skill annotation
     * @return extension properties map
     */
    protected static Map<String, Object> extractExtensions(Skill skillAnnotation) {
        Map<String, Object> extensions = new HashMap<>();

        for (String ext : skillAnnotation.extensions()) {
            String[] parts = ext.split("=", 2);
            if (parts.length == 2) {
                extensions.put(parts[0].trim(), parts[1].trim());
            }
        }

        return extensions;
    }

    // ==================== SkillRegistrar Interface ====================

    /**
     * Checks if source is supported.
     *
     * @param source source object to check
     * @return true if source is non-null object with @Skill annotation
     */
    @Override
    public boolean supports(Object source) {
        return source != null && source.getClass().isAnnotationPresent(Skill.class);
    }
}
