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
package lingzhou.agent.spring.ai.skill.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lingzhou.agent.spring.ai.skill.core.SkillBox;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of SkillBox with thread-safe metadata storage.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SimpleSkillBox implements SkillBox {
    private static final Logger logger = LoggerFactory.getLogger(SimpleSkillBox.class);

    private final ConcurrentHashMap<String, SkillMetadata> skills = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Boolean> activated = new ConcurrentHashMap<>();

    private final List<String> sources = new ArrayList<>();

    public SimpleSkillBox() {
        sources.add("custom");
    }

    @Override
    public void addSkill(String name, SkillMetadata metadata) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(metadata, "metadata cannot be null");

        if (skills.containsKey(name)) {
            throw new IllegalArgumentException("Skill with name '" + name + "' already exists in this SkillBox");
        }

        skills.put(name, metadata);
        addSource(metadata.getSource());
        activated.putIfAbsent(name, false);
    }

    @Override
    public SkillMetadata getMetadata(String name) {
        return skills.get(name);
    }

    @Override
    public Map<String, SkillMetadata> getAllMetadata() {
        return Collections.unmodifiableMap(skills);
    }

    @Override
    public boolean exists(String name) {
        return skills.containsKey(name);
    }

    @Override
    public int getSkillCount() {
        return skills.size();
    }

    @Override
    public void activateSkill(String name) {
        activated.put(name, true);
    }

    @Override
    public void deactivateSkill(String name) {
        activated.put(name, false);
    }

    @Override
    public void deactivateAllSkills() {
        activated.replaceAll((k, v) -> false);
    }

    @Override
    public boolean isActivated(String name) {
        return activated.getOrDefault(name, false);
    }

    @Override
    public Set<String> getActivatedSkillNames() {
        return activated.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public List<String> getSources() {
        return sources;
    }

    public void addSource(String source) {
        if (!sources.contains(source)) {
            sources.add(source);
        }
    }

    public void setSources(List<String> sourceList) {
        sources.clear();
        sources.addAll(sourceList);
    }

    public void clearSkills() {
        skills.clear();
        activated.clear();
        sources.clear();
        sources.add("custom");
    }
}
