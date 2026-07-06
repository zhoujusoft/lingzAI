package lingzhou.agent.backend.skillstudio.project.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService.ProjectSettingsState;
import lingzhou.agent.backend.skillstudio.project.service.SkillStudioProjectSettingsService.ProjectToolBindingState;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioToolResolutionService {

    public SkillStudioContextInput.ToolResolution resolve(ProjectSettingsState settingsState, String userGoal) {
        if (settingsState == null
                || settingsState.bindings() == null
                || settingsState.bindings().isEmpty()) {
            return new SkillStudioContextInput.ToolResolution(
                    List.of(),
                    List.of(),
                    List.of(new SkillStudioContextInput.MissingCapability("tool-binding", "当前项目尚未绑定任何工具")));
        }
        String normalizedGoal = normalize(userGoal);
        List<ScoredBinding> scored = new ArrayList<>();
        for (ProjectToolBindingState binding : settingsState.bindings()) {
            if (binding == null || !binding.enabled()) {
                continue;
            }
            scored.add(scoreBinding(
                    binding, normalizedGoal, settingsState.bindings().size()));
        }
        if (scored.isEmpty()) {
            return new SkillStudioContextInput.ToolResolution(
                    List.of(),
                    List.of(),
                    List.of(new SkillStudioContextInput.MissingCapability("tool-binding", "当前项目尚未启用任何工具")));
        }
        scored.sort(Comparator.comparingDouble(ScoredBinding::score).reversed().thenComparingInt(item -> item.binding()
                .priority()));

        List<SkillStudioContextInput.ResolvedTool> primaryTools = new ArrayList<>();
        List<SkillStudioContextInput.ResolvedTool> secondaryTools = new ArrayList<>();
        double topScore = scored.get(0).score();
        for (int index = 0; index < scored.size(); index++) {
            ScoredBinding item = scored.get(index);
            SkillStudioContextInput.ResolvedTool resolvedTool = new SkillStudioContextInput.ResolvedTool(
                    item.binding().toolName(), item.score(), item.matchMode(), item.reason());
            boolean primary =
                    index == 0 || (item.score() > 0 && topScore - item.score() <= 1.5d && primaryTools.size() < 3);
            if (primary) {
                primaryTools.add(resolvedTool);
            } else {
                secondaryTools.add(resolvedTool);
            }
        }
        if (primaryTools.isEmpty()) {
            ProjectToolBindingState fallback = scored.get(0).binding();
            primaryTools.add(new SkillStudioContextInput.ResolvedTool(
                    fallback.toolName(), 0.5d, "fallback", "未识别到明确命中，按项目绑定优先级回退"));
        }
        return new SkillStudioContextInput.ToolResolution(primaryTools, secondaryTools, List.of());
    }

    private ScoredBinding scoreBinding(ProjectToolBindingState binding, String normalizedGoal, int bindingCount) {
        double score = 0d;
        List<String> reasons = new ArrayList<>();
        if (!StringUtils.hasText(normalizedGoal)) {
            score = Math.max(score, bindingCount == 1 ? 0.8d : 0.2d);
            reasons.add(bindingCount == 1 ? "当前项目仅绑定该工具" : "按项目默认绑定顺序提供候选");
            return buildResult(binding, score, "fallback", reasons);
        }
        String toolName = normalize(binding.toolName());
        if (contains(normalizedGoal, toolName)) {
            score += 8d;
            reasons.add("命中工具名");
        }
        String tail = toolName.contains(".") ? toolName.substring(toolName.lastIndexOf('.') + 1) : toolName;
        if (StringUtils.hasText(tail) && contains(normalizedGoal, tail)) {
            score += 4d;
            reasons.add("命中工具短名");
        }
        if (StringUtils.hasText(binding.businessPurpose())
                && contains(normalizedGoal, normalize(binding.businessPurpose()))) {
            score += 5d;
            reasons.add("命中业务用途");
        }
        for (String triggerHint : binding.triggerHints()) {
            if (StringUtils.hasText(triggerHint) && contains(normalizedGoal, normalize(triggerHint))) {
                score += 4d;
                reasons.add("命中触发提示：" + triggerHint);
            }
        }
        score += Math.max(0.1d, 2.5d - Math.min(binding.priority(), 200) / 80.0d);
        String matchMode = score >= 8d ? "explicit" : score >= 4d ? "semantic" : "fallback";
        if (reasons.isEmpty()) {
            reasons.add("未识别到明确命中，按绑定优先级回退");
        }
        return buildResult(binding, score, matchMode, reasons);
    }

    private ScoredBinding buildResult(
            ProjectToolBindingState binding, double score, String matchMode, List<String> reasons) {
        return new ScoredBinding(binding, score, matchMode, String.join("；", reasons));
    }

    private boolean contains(String normalizedGoal, String normalizedSegment) {
        return StringUtils.hasText(normalizedGoal)
                && StringUtils.hasText(normalizedSegment)
                && normalizedGoal.contains(normalizedSegment);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredBinding(ProjectToolBindingState binding, double score, String matchMode, String reason) {}
}
