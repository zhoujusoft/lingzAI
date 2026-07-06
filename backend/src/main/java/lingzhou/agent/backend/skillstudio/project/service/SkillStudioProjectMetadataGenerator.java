package lingzhou.agent.backend.skillstudio.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.capability.agentruntime.capabilities.TokenUsageCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeTokenUsageAccumulator;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillStudioProjectMetadataGenerator {

    private static final List<String> ICON_OPTIONS = List.of(
            "grid_view",
            "smart_toy",
            "rocket_launch",
            "inventory_2",
            "dataset",
            "hub",
            "description",
            "article",
            "table_chart",
            "design_services",
            "palette",
            "rule",
            "gavel",
            "policy",
            "account_balance",
            "analytics",
            "travel_explore",
            "fact_check",
            "checklist",
            "assignment",
            "psychology",
            "monitor_heart",
            "medical_services",
            "auto_awesome",
            "dashboard");

    private static final List<String> ICON_COLORS = List.of("blue", "indigo", "emerald", "amber", "slate", "violet");

    private static final List<String> PROJECT_TYPES = List.of("技能", "网页应用", "智能体", "低代码", "数据分析", "知识问答");

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final TokenUsageCapabilityAdapter tokenUsageCapability;
    private final ObjectMapper objectMapper;

    public SkillStudioProjectMetadataGenerator(
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            TokenUsageCapabilityAdapter tokenUsageCapability,
            ObjectMapper objectMapper) {
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.tokenUsageCapability = tokenUsageCapability;
        this.objectMapper = objectMapper;
    }

    public GeneratedMetadata generate(String description) {
        return generateWithUsage(description).metadata();
    }

    public GeneratedMetadataResult generateWithUsage(String description) {
        if (!StringUtils.hasText(description)) {
            return new GeneratedMetadataResult(fallback(description), null);
        }
        RuntimeRunUsageSnapshot usageSnapshot = null;
        try {
            var chatBundle = modelRuntimeClientFactory.createChatBundle();
            CallResult callResult = call(chatBundle.chatClient(), chatBundle.config(), description.trim());
            String raw = callResult.rawResponse();
            usageSnapshot = callResult.usageSnapshot();
            String json = extractJson(raw);
            GeneratedMetadata parsed = objectMapper.readValue(json, GeneratedMetadata.class);
            return new GeneratedMetadataResult(normalize(parsed, description), usageSnapshot);
        } catch (Exception ignored) {
            return new GeneratedMetadataResult(fallback(description), usageSnapshot);
        }
    }

    private CallResult call(
            ChatClient chatClient,
            lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver.ResolvedChatModelConfig
                    chatConfig,
            String description) {
        long startedAt = System.currentTimeMillis();
        RuntimeTokenUsageAccumulator accumulator = tokenUsageCapability.createAccumulator(chatConfig, startedAt);
        accumulator.ensureCurrentCall(startedAt);
        try {
            ChatResponse chatResponse =
                    chatClient.prompt().user(buildPrompt(description)).call().chatResponse();
            String rawResponse = extractResponseText(chatResponse);
            tokenUsageCapability.recordResponse(accumulator, chatResponse);
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(accumulator, "COMPLETED", completedAt, safeLength(rawResponse));
            return new CallResult(rawResponse, tokenUsageCapability.snapshot(accumulator, "COMPLETED", completedAt));
        } catch (Exception ex) {
            long completedAt = System.currentTimeMillis();
            tokenUsageCapability.completeCurrentCall(accumulator, "FAILED", completedAt, 0);
            return new CallResult("", tokenUsageCapability.snapshot(accumulator, "FAILED", completedAt));
        }
    }

    private String buildPrompt(String description) {
        return """
                你是技能工坊的项目元信息生成器。

                目标：
                根据用户输入的技能描述，生成技能工坊项目的初始化元信息。

                强制要求：
                1. 只输出一个 JSON 对象，不要输出 Markdown、解释或代码块围栏。
                2. `name` 使用中文，要求简洁、像真实项目名。
                3. `runtimeSkillName` 必须是小写 kebab-case，只能包含 a-z、0-9、-，长度不超过 48。
                4. `draftSkillName` 默认与 `runtimeSkillName` 保持一致。
                5. `icon` 必须从下面列表中选择一个最贴切的：
                   %s
                6. `iconColor` 必须从下面列表中选择一个：
                   %s
                7. `projectType` 必须从下面列表中选择一个最贴切的：
                   %s
                8. `category` 用中文短语概括项目分类，例如“文档处理”“知识问答”“数据分析”。
                9. `summary` 用一句中文简要描述项目目标，长度不超过 60。

                输出 JSON schema：
                {
                  "name": "string",
                  "runtimeSkillName": "string",
                  "draftSkillName": "string",
                  "icon": "string",
                  "iconColor": "string",
                  "projectType": "string",
                  "category": "string",
                  "summary": "string"
                }

                技能描述：
                %s
                """
                .formatted(
                        String.join(", ", ICON_OPTIONS),
                        String.join(", ", ICON_COLORS),
                        String.join(", ", PROJECT_TYPES),
                        description);
    }

    private String extractJson(String raw) throws JsonProcessingException {
        if (!StringUtils.hasText(raw)) {
            throw new JsonProcessingException("metadata raw empty") {};
        }
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group();
        }
        return raw.trim();
    }

    private GeneratedMetadata normalize(GeneratedMetadata parsed, String description) {
        if (parsed == null) {
            return fallback(description);
        }
        String runtimeSkillName = normalizeRuntimeSkillName(parsed.runtimeSkillName(), description);
        String icon = ICON_OPTIONS.contains(parsed.icon()) ? parsed.icon() : "design_services";
        String iconColor = ICON_COLORS.contains(parsed.iconColor()) ? parsed.iconColor() : "blue";
        String projectType = PROJECT_TYPES.contains(parsed.projectType()) ? parsed.projectType() : "技能";
        String name = StringUtils.hasText(parsed.name())
                ? parsed.name().trim()
                : fallback(description).name();
        String draftSkillName = StringUtils.hasText(parsed.draftSkillName())
                ? normalizeRuntimeSkillName(parsed.draftSkillName(), description)
                : runtimeSkillName;
        String category =
                StringUtils.hasText(parsed.category()) ? parsed.category().trim() : "技能工坊";
        String summary = StringUtils.hasText(parsed.summary())
                ? shrink(parsed.summary().trim(), 60)
                : shrink(description.trim(), 60);
        return new GeneratedMetadata(
                name, runtimeSkillName, draftSkillName, icon, iconColor, projectType, category, summary);
    }

    private GeneratedMetadata fallback(String description) {
        String base = StringUtils.hasText(description) ? description.trim() : "新技能项目";
        String normalized = normalizeRuntimeSkillName(null, base);
        return new GeneratedMetadata(
                shrink(base, 24), normalized, normalized, "design_services", "blue", "技能", "技能工坊", shrink(base, 60));
    }

    private String normalizeRuntimeSkillName(String runtimeSkillName, String description) {
        String raw = StringUtils.hasText(runtimeSkillName) ? runtimeSkillName.trim() : deriveAsciiName(description);
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "skill-studio-project";
        }
        return shrink(normalized, 48);
    }

    private String extractResponseText(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null
                || chatResponse.getResult().getOutput().getText() == null) {
            return "";
        }
        return chatResponse.getResult().getOutput().getText().trim();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String deriveAsciiName(String description) {
        String raw =
                StringUtils.hasText(description) ? description.trim().toLowerCase(Locale.ROOT) : "skill-studio-project";
        String normalized =
                raw.replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (!StringUtils.hasText(normalized)) {
            return "skill-studio-project";
        }
        return normalized;
    }

    private String shrink(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }

    public record GeneratedMetadata(
            String name,
            String runtimeSkillName,
            String draftSkillName,
            String icon,
            String iconColor,
            String projectType,
            String category,
            String summary) {}

    public record GeneratedMetadataResult(GeneratedMetadata metadata, RuntimeRunUsageSnapshot usageSnapshot) {}

    private record CallResult(String rawResponse, RuntimeRunUsageSnapshot usageSnapshot) {}
}
