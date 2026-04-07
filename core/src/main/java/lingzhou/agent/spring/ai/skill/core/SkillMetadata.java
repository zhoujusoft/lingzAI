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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Skill metadata.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SkillMetadata {

    private final String name;
    private final String description;
    private final String source;
    private final Map<String, Object> extensions;

    public SkillMetadata(String name, String description, String source) {
        this(name, description, source, new HashMap<>());
    }

    public SkillMetadata(String name, String description, String source, Map<String, Object> extensions) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("description is required");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("source is required");
        }

        this.name = name;
        this.description = description;
        this.source = source;
        this.extensions = new HashMap<>(extensions);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSource() {
        return source;
    }

    public Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

    public Object getExtension(String key) {
        return extensions.get(key);
    }

    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException("Extension '" + key + "' is not of type " + type.getName());
        }
        return type.cast(value);
    }

    public boolean hasExtension(String key) {
        return extensions.containsKey(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkillMetadata that = (SkillMetadata) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(source, that.source)
                && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, source, extensions);
    }

    @Override
    public String toString() {
        return "SkillMetadata{" + "name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", source='"
                + source + '\'' + ", extensions="
                + extensions + '}';
    }

    // Builder
    public static Builder builder(String name, String description, String source) {
        return new Builder(name, description, source);
    }

    public static class Builder {
        private final String name;
        private final String description;
        private final String source;
        private final Map<String, Object> extensions = new HashMap<>();

        private Builder(String name, String description, String source) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is required");
            }
            if (description == null || description.isEmpty()) {
                throw new IllegalArgumentException("description is required");
            }
            if (source == null || source.isEmpty()) {
                throw new IllegalArgumentException("source is required");
            }
            this.name = name;
            this.description = description;
            this.source = source;
        }

        public Builder extension(String key, Object value) {
            extensions.put(key, value);
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions.putAll(extensions);
            return this;
        }

        public SkillMetadata build() {
            return new SkillMetadata(name, description, source, extensions);
        }
    }
}
