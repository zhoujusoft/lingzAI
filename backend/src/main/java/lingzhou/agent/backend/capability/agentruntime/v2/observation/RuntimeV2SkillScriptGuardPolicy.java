package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2SkillScriptGuardPolicy {

    private static final Pattern SKILL_SCRIPT_PATH_PATTERN = Pattern.compile("/skill/scripts/[A-Za-z0-9_./-]+\\.py");

    public String buildBlockedRewriteObservation(
            RuntimeV2State state, String toolName, Map<String, Object> arguments, int maxPromptLength) {
        if (state == null || !"file_write".equalsIgnoreCase(normalizeText(toolName))) {
            return "";
        }
        String attemptedPath = resolveToolPathArgument(arguments);
        if (!StringUtils.hasText(attemptedPath)
                || !attemptedPath.startsWith("/workspace/")
                || !attemptedPath.endsWith(".py")) {
            return "";
        }
        List<String> fixedScriptPaths = resolveDeclaredSkillScriptPaths(state.requestSkillKit());
        if (fixedScriptPaths.isEmpty() || !hasFailedSkillScriptRunObservation(state, fixedScriptPaths)) {
            return "";
        }
        return RuntimeV2GuardObservationFactory.buildFixedSkillScriptRewriteBlockedObservation(
                attemptedPath, fixedScriptPaths, maxPromptLength);
    }

    List<String> resolveDeclaredSkillScriptPaths(SkillKit requestSkillKit) {
        if (requestSkillKit == null) {
            return List.of();
        }
        Set<String> activeSkillNames = requestSkillKit.getActivatedSkillNames();
        if (activeSkillNames == null || activeSkillNames.isEmpty()) {
            return List.of();
        }
        List<String> scriptPaths = new ArrayList<>();
        for (String skillName : activeSkillNames) {
            if (!StringUtils.hasText(skillName)) {
                continue;
            }
            var skill = requestSkillKit.getSkill(skillName);
            if (skill == null || !StringUtils.hasText(skill.getContent())) {
                continue;
            }
            Matcher matcher = SKILL_SCRIPT_PATH_PATTERN.matcher(skill.getContent());
            while (matcher.find()) {
                String path = matcher.group();
                if (StringUtils.hasText(path) && !scriptPaths.contains(path)) {
                    scriptPaths.add(path);
                }
            }
        }
        return List.copyOf(scriptPaths);
    }

    boolean hasFailedSkillScriptRunObservation(RuntimeV2State state, List<String> fixedScriptPaths) {
        if (state == null || fixedScriptPaths == null || fixedScriptPaths.isEmpty()) {
            return false;
        }
        for (Map<String, Object> item : state.observationTrace()) {
            if (!"run_python".equalsIgnoreCase(normalizeText(item.get("toolName")))) {
                continue;
            }
            String observation = normalizeText(item.get("observation"));
            if (!StringUtils.hasText(observation)) {
                continue;
            }
            boolean mentionsFixedScript =
                    fixedScriptPaths.stream().anyMatch(path -> StringUtils.hasText(path) && observation.contains(path));
            if (!mentionsFixedScript) {
                continue;
            }
            if (observation.contains("\"success\":false")
                    || observation.contains("success: false")
                    || observation.contains("\"success\": false")) {
                return true;
            }
        }
        return false;
    }

    private String resolveToolPathArgument(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        String path = normalizeText(arguments.get("path"));
        if (!StringUtils.hasText(path)) {
            path = normalizeText(arguments.get("arg0"));
        }
        return path.replace('\\', '/');
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
