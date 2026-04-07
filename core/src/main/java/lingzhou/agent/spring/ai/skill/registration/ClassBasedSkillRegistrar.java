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
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lingzhou.agent.spring.ai.skill.adapter.SkillProxy;
import lingzhou.agent.spring.ai.skill.annotation.Skill;
import lingzhou.agent.spring.ai.skill.annotation.SkillContent;
import lingzhou.agent.spring.ai.skill.annotation.SkillInit;
import lingzhou.agent.spring.ai.skill.annotation.SkillTools;
import lingzhou.agent.spring.ai.skill.capability.SkillReferences;
import lingzhou.agent.spring.ai.skill.common.LoadStrategy;
import lingzhou.agent.spring.ai.skill.core.SkillDefinition;
import lingzhou.agent.spring.ai.skill.core.SkillIdGenerator;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.core.SkillRegistrar;
import lingzhou.agent.spring.ai.skill.exception.SkillRegistrationException;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillIdGenerator;

/**
 * Class-based skill registrar for lazy-loaded POJO skills.
 *
 * <p>Requirements: Class must be annotated with @Skill and have a @SkillInit static factory method.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class ClassBasedSkillRegistrar implements SkillRegistrar<Class<?>> {

    private final SkillIdGenerator idGenerator;

    private ClassBasedSkillRegistrar(Builder builder) {
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
     * Builder for ClassBasedSkillRegistrar.
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
         * Builds the ClassBasedSkillRegistrar instance.
         *
         * @return new ClassBasedSkillRegistrar instance
         */
        public ClassBasedSkillRegistrar build() {
            return new ClassBasedSkillRegistrar(this);
        }
    }

    /**
     * Registers skill from class definition.
     *
     * @param poolManager skill pool manager
     * @param skillClass skill class annotated with @Skill and @SkillInit
     * @return created skill definition
     * @throws IllegalArgumentException if class missing required annotations
     * @throws SkillRegistrationException if registration fails
     */
    @Override
    public SkillDefinition register(SkillPoolManager poolManager, Class<?> skillClass) {
        Objects.requireNonNull(poolManager, "poolManager cannot be null");
        Objects.requireNonNull(skillClass, "skillClass cannot be null");

        Skill skillAnnotation = skillClass.getAnnotation(Skill.class);

        if (skillAnnotation == null) {
            throw new IllegalArgumentException("Class " + skillClass.getName() + " must be annotated with @Skill");
        }

        Method initMethod = findAndValidateSkillInitMethod(skillClass);

        String source = skillAnnotation.source();

        SkillMetadata metadata = SkillMetadata.builder(skillAnnotation.name(), skillAnnotation.description(), source)
                .extensions(extractExtensions(skillAnnotation))
                .build();

        String skillId = idGenerator.generateId(metadata);

        Supplier<lingzhou.agent.spring.ai.skill.core.Skill> loader = () -> {
            try {
                Object instance = initMethod.invoke(null);
                return buildSkillFromInstance(instance, metadata);
            } catch (Exception e) {
                throw new SkillRegistrationException(
                        metadata.getName(),
                        "Failed to instantiate skill class via @SkillInit method: " + skillClass.getName(),
                        e);
            }
        };

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
     * Finds and validates @SkillInit method.
     *
     * @param skillClass skill class
     * @return validated @SkillInit method
     * @throws IllegalArgumentException if method not found or invalid
     */
    private static Method findAndValidateSkillInitMethod(Class<?> skillClass) {
        Method initMethod = null;
        int count = 0;

        for (Method method : skillClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SkillInit.class)) {
                initMethod = method;
                count++;
            }
        }

        if (count == 0) {
            throw new IllegalArgumentException(
                    "Class " + skillClass.getName() + " must have a @SkillInit annotated method. "
                            + "Example: @SkillInit public static MySkill create() { return new MySkill(); }");
        }

        if (count > 1) {
            throw new IllegalArgumentException(
                    "Class " + skillClass.getName() + " must have exactly one @SkillInit method, but found " + count);
        }

        if (!Modifier.isStatic(initMethod.getModifiers())) {
            throw new IllegalArgumentException("@SkillInit method '" + initMethod.getName() + "' in class "
                    + skillClass.getName() + " must be static. Example: public static MySkill create() { ... }");
        }

        Class<?> returnType = initMethod.getReturnType();
        if (!returnType.isAssignableFrom(skillClass)) {
            throw new IllegalArgumentException("@SkillInit method '" + initMethod.getName() + "' in class "
                    + skillClass.getName() + " must return type " + skillClass.getSimpleName()
                    + " (or its superclass), but returns " + returnType.getSimpleName());
        }

        initMethod.setAccessible(true);

        return initMethod;
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
     * @return true if source is a Class with @Skill and @SkillInit
     */
    @Override
    public boolean supports(Object source) {
        if (!(source instanceof Class)) {
            return false;
        }

        Class<?> clazz = (Class<?>) source;

        if (!clazz.isAnnotationPresent(Skill.class)) {
            return false;
        }

        return hasSkillInitMethod(clazz);
    }

    /**
     * Checks if class has @SkillInit method.
     *
     * @param clazz class to check
     * @return true if at least one @SkillInit method found
     */
    private static boolean hasSkillInitMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SkillInit.class)) {
                return true;
            }
        }
        return false;
    }
}
