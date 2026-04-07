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
package lingzhou.agent.backend.app;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillIdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClothingSkillController {

    private final SkillKit skillKit;
    private final SkillPoolManager poolManager;
    private final SkillCatalogService skillCatalogService;
    private final DefaultSkillIdGenerator idGenerator = new DefaultSkillIdGenerator();

    public ClothingSkillController(
            SkillKit skillKit, SkillPoolManager poolManager, SkillCatalogService skillCatalogService) {
        this.skillKit = skillKit;
        this.poolManager = poolManager;
        this.skillCatalogService = skillCatalogService;
    }

    @GetMapping("/skills")
    public List<SkillInfo> listSkills() {
        return skillCatalogService.listRuntimeSkills().stream()
                .map(item -> new SkillInfo(
                        item.name(),
                        item.displayName(),
                        item.description(),
                        item.source(),
                        item.activated(),
                        item.extensions() == null ? Map.of() : item.extensions()))
                .toList();
    }

    @PostMapping("/skills/{name}/activate")
    public ResponseEntity<SkillActionResponse> activateSkill(@PathVariable("name") String name) {
        if (!skillKit.exists(name)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SkillActionResponse(name, false, "Skill not found"));
        }
        skillKit.activateSkill(name);
        return ResponseEntity.ok(new SkillActionResponse(name, true, null));
    }

    @PostMapping("/skills/{name}/deactivate")
    public ResponseEntity<SkillActionResponse> deactivateSkill(@PathVariable("name") String name) {
        if (!skillKit.exists(name)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SkillActionResponse(name, false, "Skill not found"));
        }
        skillKit.deactivateSkill(name);
        evictSkillInstance(name);
        return ResponseEntity.ok(new SkillActionResponse(name, true, null));
    }

    @PostMapping("/skills/deactivate-all")
    public ResponseEntity<SkillActionResponse> deactivateAll() {
        skillKit.deactivateAllSkills();
        poolManager.evictAll();
        return ResponseEntity.ok(new SkillActionResponse("all", true, null));
    }

    @PostMapping("/skills/{name}/evict")
    public ResponseEntity<SkillActionResponse> evictSkill(@PathVariable("name") String name) {
        if (!skillKit.exists(name)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SkillActionResponse(name, false, "Skill not found"));
        }
        SkillMetadata metadata = skillKit.getMetadata(name);
        if (metadata == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SkillActionResponse(name, false, "Skill metadata not found"));
        }
        poolManager.evict(idGenerator.generateId(metadata));
        return ResponseEntity.ok(new SkillActionResponse(name, true, null));
    }

    private void evictSkillInstance(String name) {
        SkillMetadata metadata = skillKit.getMetadata(name);
        if (metadata == null) {
            return;
        }
        poolManager.evict(idGenerator.generateId(metadata));
    }

    public record SkillInfo(
            String name,
            String displayName,
            String description,
            String source,
            boolean activated,
            Map<String, Object> extensions) {}

    public record SkillActionResponse(String name, boolean success, String error) {}
}
