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
package lingzhou.agent.spring.ai.skill.tool;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import lingzhou.agent.spring.ai.skill.capability.ReferencesLoader;
import lingzhou.agent.spring.ai.skill.core.Skill;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for progressive skill loading via Spring AI @Tool annotations.
 *
 * <p>Provides loadSkillContent and loadSkillReference methods for LLM to activate and access skills.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SimpleSkillLoaderTools {

    private static final Logger logger = LoggerFactory.getLogger(SimpleSkillLoaderTools.class);

    private final SkillKit skillKit;

    private SimpleSkillLoaderTools(Builder builder) {
        this.skillKit = Objects.requireNonNull(builder.skillKit, "skillKit cannot be null");
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
     * Builder for SimpleSkillLoaderTool.
     */
    public static class Builder {
        private SkillKit skillKit;

        private Builder() {}

        /**
         * Sets the skill kit.
         *
         * @param skillKit skill kit instance
         * @return this builder
         */
        public Builder skillKit(SkillKit skillKit) {
            this.skillKit = skillKit;
            return this;
        }

        /**
         * Builds the SimpleSkillLoaderTool instance.
         *
         * @return new SimpleSkillLoaderTool instance
         */
        public SimpleSkillLoaderTools build() {
            return new SimpleSkillLoaderTools(this);
        }
    }

    /**
     * Loads skill content by name, activates skill, and returns its documentation.
     *
     * @param skillName skill name
     * @return skill content or error message
     */
    @Tool(
            description = "Load the content of a skill by its name. "
                    + "This activates the skill and returns its documentation. "
                    + "Use this when you need to understand what a skill does and how to use it.")
    public String loadSkillContent(@ToolParam(description = "The name of the skill to load") String skillName) {
        try {
            if (skillName == null || skillName.trim().isEmpty()) {
                return "Error: Missing or empty skill name";
            }

            if (!skillKit.exists(skillName)) {
                return "Error: Skill not found in SkillBox: " + skillName
                        + ". Please use a skill that has been added to the SkillBox.";
            }

            skillKit.activateSkill(skillName);

            Skill skill = skillKit.getSkill(skillName);
            if (skill == null) {
                return "Error: Skill not found: " + skillName;
            }

            return skill.getContent();

        } catch (Exception e) {
            logger.error("Error loading skill: {}", skillName, e);
            return "Error loading skill: " + e.getMessage();
        }
    }

    /**
     * Loads specific reference from skill using reference key.
     *
     * @param skillName skill name with references
     * @param referenceKey reference key from skill content
     * @return reference content (URL, file path, or text) or error message
     */
    @Tool(
            description = "ONLY use this tool when a skill's content explicitly mentions it has reference materials. "
                    + "Load a specific reference from a skill using the reference key mentioned in the skill's content. "
                    + "Returns reference content (URL, file path, or text string). "
                    + "Do NOT use this for regular skill operations - use skill's own tools instead.")
    public String loadSkillReference(
            @ToolParam(description = "The skill name that has references") String skillName,
            @ToolParam(description = "The exact reference key mentioned in the skill's content") String referenceKey) {
        try {
            if (skillName == null || skillName.trim().isEmpty()) {
                return "Error: Missing or empty skill name";
            }

            if (referenceKey == null || referenceKey.trim().isEmpty()) {
                return "Error: Missing or empty reference key";
            }

            if (!skillKit.exists(skillName)) {
                return "Error: Skill not found in SkillBox: " + skillName
                        + ". Please use a skill that has been added to the SkillBox.";
            }

            Skill skill = skillKit.getSkill(skillName);
            if (skill == null) {
                return "Error: Skill not found: " + skillName;
            }

            if (!skill.supports(ReferencesLoader.class)) {
                return "Error: Skill '" + skillName + "' does not have references. ";
            }

            ReferencesLoader loader = skill.as(ReferencesLoader.class);
            Map<String, String> references = loader.getReferences();

            if (!references.containsKey(referenceKey)) {
                return "Error: Reference key '" + referenceKey + "' not found in skill '" + skillName + "'. "
                        + "Available keys: " + references.keySet();
            }

            String referenceUrl = references.get(referenceKey);
            return referenceUrl;

        } catch (Exception e) {
            logger.error("Error loading skill reference: skill={}, key={}", skillName, referenceKey, e);
            return "Error loading skill reference: " + e.getMessage();
        }
    }

    @Tool(description = "List activated skills with their descriptions.")
    public String listActiveSkills() {
        Set<String> active = skillKit.getActivatedSkillNames();
        if (active == null || active.isEmpty()) {
            return "[]";
        }

        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String name : active) {
            SkillMetadata metadata = skillKit.getMetadata(name);
            String description = metadata != null ? metadata.getDescription() : "";
            String source = metadata != null ? metadata.getSource() : "";
            joiner.add("{\"name\":\""
                    + escapeJson(name)
                    + "\",\"description\":\""
                    + escapeJson(description)
                    + "\",\"source\":\""
                    + escapeJson(source)
                    + "\"}");
        }
        return joiner.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
