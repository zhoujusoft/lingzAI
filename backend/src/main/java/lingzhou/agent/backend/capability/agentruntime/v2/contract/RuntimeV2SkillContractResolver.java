package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RuntimeV2SkillContractResolver {

    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final SkillCatalogService skillCatalogService;
    private final RuntimeV2ContractSupport contractSupport;
    private final RuntimeV2SkillContractBuilder contractBuilder;

    public RuntimeV2SkillContractResolver(
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            SkillCatalogService skillCatalogService,
            RuntimeV2ContractSupport contractSupport,
            RuntimeV2SkillContractBuilder contractBuilder) {
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.skillCatalogService = skillCatalogService;
        this.contractSupport = contractSupport;
        this.contractBuilder = contractBuilder;
    }

    public List<RuntimeV2SkillContract> resolveActiveContracts(RuntimeV2State state) {
        if (state == null || state.prepared() == null) {
            return List.of();
        }
        List<RuntimeLoadedSkill> loadedSkills = requestScopedSkillRuntimeService.extractLoadedSkills(
                state.requestSkillKit(), state.prepared().availableSkills());
        if (loadedSkills.isEmpty()) {
            return List.of();
        }
        List<RuntimeV2SkillContract> contracts = new ArrayList<>();
        for (RuntimeLoadedSkill loadedSkill : loadedSkills) {
            if (loadedSkill == null || loadedSkill.skillId() == null || loadedSkill.skillId() <= 0) {
                continue;
            }
            try {
                SkillCatalogService.SkillChatContext context =
                        skillCatalogService.resolveSkillChatContextForPublished(loadedSkill.skillId(), null, null);
                contracts.add(buildContract(state.requestSkillKit(), loadedSkill, context));
            } catch (Exception ex) {
                log.warn(
                        "Runtime V2 SkillContract 解析失败：skillName={}, skillId={}, error={}",
                        loadedSkill.runtimeSkillName(),
                        loadedSkill.skillId(),
                        ex.getMessage());
            }
        }
        return List.copyOf(contracts);
    }

    private RuntimeV2SkillContract buildContract(
            SkillKit requestSkillKit, RuntimeLoadedSkill loadedSkill, SkillCatalogService.SkillChatContext context) {
        RuntimeV2SkillContract metadataContract = resolveMetadataContract(requestSkillKit, loadedSkill);
        if (metadataContract != null) {
            return metadataContract;
        }
        RuntimeV2SkillContract sidecarContract =
                contractSupport.readSkillContract(context == null ? null : context.runtimeContract());
        if (sidecarContract != null) {
            return contractSupport.normalize(sidecarContract);
        }
        List<String> toolNames = extractToolNames(context == null ? List.of() : context.toolCallbacks());
        return contractBuilder.build(
                firstNonBlank(loadedSkill.runtimeSkillName(), ""),
                firstNonBlank(loadedSkill.displayName(), loadedSkill.runtimeSkillName()),
                toolNames);
    }

    private RuntimeV2SkillContract resolveMetadataContract(SkillKit requestSkillKit, RuntimeLoadedSkill loadedSkill) {
        if (requestSkillKit == null || loadedSkill == null || !StringUtils.hasText(loadedSkill.runtimeSkillName())) {
            return null;
        }
        try {
            SkillMetadata metadata =
                    requestSkillKit.getMetadata(loadedSkill.runtimeSkillName().trim());
            if (metadata == null || !metadata.hasExtension(RuntimeV2ContractSupport.EXTENSION_KEY)) {
                return null;
            }
            RuntimeV2SkillContract contract =
                    contractSupport.readSkillContract(metadata.getExtension(RuntimeV2ContractSupport.EXTENSION_KEY));
            return contractSupport.normalize(contract);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> extractToolNames(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return List.of();
        }
        return toolCallbacks.stream()
                .filter(tool -> tool != null && tool.getToolDefinition() != null)
                .map(tool -> tool.getToolDefinition().name())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private String firstNonBlank(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left.trim();
        }
        return StringUtils.hasText(right) ? right.trim() : "";
    }
}
