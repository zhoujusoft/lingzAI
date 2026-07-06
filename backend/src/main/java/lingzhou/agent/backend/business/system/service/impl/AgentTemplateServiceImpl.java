package lingzhou.agent.backend.business.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.business.system.dao.AgentTemplateMapper;
import lingzhou.agent.backend.business.system.dao.AgentTemplateSkillBindingMapper;
import lingzhou.agent.backend.business.system.dao.AgentTemplateToolBindingMapper;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.AgentListItemDto;
import lingzhou.agent.backend.business.system.model.AgentPageInput;
import lingzhou.agent.backend.business.system.model.AgentPageResult;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentTemplate;
import lingzhou.agent.backend.business.system.model.AgentTemplateSkillBinding;
import lingzhou.agent.backend.business.system.model.AgentTemplateToolBinding;
import lingzhou.agent.backend.business.system.model.CreateAgentInput;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.ToolSimpleDto;
import lingzhou.agent.backend.business.system.model.UpdateAgentInput;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.common.lzException.ExceptionCode;
import lingzhou.agent.backend.common.lzException.LZException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentTemplateServiceImpl implements AgentTemplateService {

    private static final List<SkillSimpleDto> EMPTY_SKILLS = List.of();
    private static final List<ToolSimpleDto> EMPTY_TOOLS = List.of();

    private final AgentTemplateSkillBindingMapper agentTemplateSkillBindingMapper;
    private final AgentTemplateToolBindingMapper agentTemplateToolBindingMapper;
    private final AgentTemplateMapper agentTemplateMapper;
    private final SkillCatalogMapper skillCatalogMapper;
    private final ToolCatalogMapper toolCatalogMapper;

    public AgentTemplateServiceImpl(
            AgentTemplateSkillBindingMapper agentTemplateSkillBindingMapper,
            AgentTemplateToolBindingMapper agentTemplateToolBindingMapper,
            AgentTemplateMapper agentTemplateMapper,
            SkillCatalogMapper skillCatalogMapper,
            ToolCatalogMapper toolCatalogMapper) {
        this.agentTemplateSkillBindingMapper = agentTemplateSkillBindingMapper;
        this.agentTemplateToolBindingMapper = agentTemplateToolBindingMapper;
        this.agentTemplateMapper = agentTemplateMapper;
        this.skillCatalogMapper = skillCatalogMapper;
        this.toolCatalogMapper = toolCatalogMapper;
    }

    @Override
    public List<AgentSimpleDto> listEnabledAgents() {
        LambdaQueryWrapper<AgentTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTemplate::getEnabled, 1).orderByAsc(AgentTemplate::getAgentCode);
        return agentTemplateMapper.selectList(wrapper).stream()
                .map(this::toSimpleDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentDetailDto> listEnabledAgentDetails() {
        LambdaQueryWrapper<AgentTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTemplate::getEnabled, 1).orderByAsc(AgentTemplate::getAgentCode);
        List<AgentTemplate> templates = agentTemplateMapper.selectList(wrapper);
        if (templates.isEmpty()) {
            return List.of();
        }
        List<Long> templateIds = templates.stream().map(AgentTemplate::getId).toList();
        Map<Long, List<SkillSimpleDto>> templateSkillMap = loadTemplateSkillsMap(templateIds);
        Map<Long, List<ToolSimpleDto>> templateToolMap = loadTemplateToolsMap(templateIds);
        return templates.stream()
                .map(template -> toDetailDto(
                        template,
                        0,
                        templateSkillMap.getOrDefault(template.getId(), EMPTY_SKILLS),
                        templateToolMap.getOrDefault(template.getId(), EMPTY_TOOLS)))
                .toList();
    }

    @Override
    public AgentSimpleDto getAgentById(Long agentId) {
        if (agentId == null) {
            return null;
        }
        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            return null;
        }
        return toSimpleDto(template);
    }

    @Override
    public AgentPageResult listAgents(AgentPageInput input) {
        AgentPageInput safeInput = input == null ? new AgentPageInput() : input;
        LambdaQueryWrapper<AgentTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(safeInput.getKeyword())) {
            String keyword = safeInput.getKeyword().trim();
            wrapper.and(w -> w.like(AgentTemplate::getAgentCode, keyword)
                    .or()
                    .like(AgentTemplate::getAgentName, keyword)
                    .or()
                    .like(AgentTemplate::getDescription, keyword));
        }
        wrapper.orderByDesc(AgentTemplate::getUpdatedAt);

        Page<AgentTemplate> page = new Page<>(safeInput.getPage(), safeInput.getPageSize());
        Page<AgentTemplate> result = agentTemplateMapper.selectPage(page, wrapper);
        List<AgentTemplate> templates = result.getRecords();
        List<Long> templateIds = templates.stream().map(AgentTemplate::getId).toList();
        Map<Long, List<SkillSimpleDto>> templateSkillMap = loadTemplateSkillsMap(templateIds);
        Map<Long, List<ToolSimpleDto>> templateToolMap = loadTemplateToolsMap(templateIds);

        AgentPageResult pageResult = new AgentPageResult();
        pageResult.setItems(templates.stream()
                .map(template -> toListItemDto(
                        template,
                        0,
                        templateSkillMap
                                .getOrDefault(template.getId(), EMPTY_SKILLS)
                                .size(),
                        templateToolMap
                                .getOrDefault(template.getId(), EMPTY_TOOLS)
                                .size()))
                .collect(Collectors.toList()));
        pageResult.setTotal(result.getTotal());
        pageResult.setPage((int) result.getCurrent());
        pageResult.setPageSize((int) result.getSize());
        return pageResult;
    }

    @Override
    public AgentDetailDto getAgentDetail(Long agentId) {
        if (agentId == null) {
            return null;
        }
        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            return null;
        }
        return toDetailDto(template, 0, loadTemplateSkills(agentId), loadTemplateTools(agentId));
    }

    @Override
    public AgentDetailDto getEnabledAgentDetail(Long agentId) {
        AgentDetailDto detail = getAgentDetail(agentId);
        if (detail == null || detail.getEnabled() == null || detail.getEnabled() != 1) {
            return null;
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailDto createAgent(CreateAgentInput input) {
        if (input == null || !StringUtils.hasText(input.getAgentCode())) {
            throw new LZException(ExceptionCode.Default, "专家技能包编码不能为空");
        }
        if (!StringUtils.hasText(input.getAgentName())) {
            throw new LZException(ExceptionCode.Default, "专家技能包名称不能为空");
        }

        String agentCode = input.getAgentCode().trim();
        LambdaQueryWrapper<AgentTemplate> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(AgentTemplate::getAgentCode, agentCode);
        if (agentTemplateMapper.selectCount(existWrapper) > 0) {
            throw new LZException(ExceptionCode.Default, "专家技能包编码已存在: " + agentCode);
        }

        AgentTemplate template = new AgentTemplate();
        template.setAgentCode(agentCode);
        template.setAgentName(input.getAgentName().trim());
        template.setDescription(trimToNull(input.getDescription()));
        template.setOpeningMessage(trimToNull(input.getOpeningMessage()));
        template.setIcon(trimToNull(input.getIcon()));
        template.setSoulTemplate(trimToNull(input.getSoulTemplate()));
        template.setProfileTemplate(trimToNull(input.getProfileTemplate()));
        template.setEnabled(input.getEnabled() != null ? input.getEnabled() : 1);
        agentTemplateMapper.insert(template);
        replaceTemplateSkillBindings(template.getId(), input.getSkillIds());
        replaceTemplateToolBindings(template.getId(), input.getToolIds());
        return getAgentDetail(template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailDto updateAgent(Long agentId, UpdateAgentInput input) {
        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            throw new LZException(ExceptionCode.Default, "专家技能包不存在: " + agentId);
        }
        if (input != null) {
            if (input.getAgentName() != null) {
                template.setAgentName(input.getAgentName().trim());
            }
            template.setDescription(trimToNull(input.getDescription()));
            template.setOpeningMessage(trimToNull(input.getOpeningMessage()));
            template.setIcon(trimToNull(input.getIcon()));
            template.setSoulTemplate(trimToNull(input.getSoulTemplate()));
            template.setProfileTemplate(trimToNull(input.getProfileTemplate()));
            if (input.getEnabled() != null) {
                template.setEnabled(input.getEnabled());
            }
            if (input.getSkillIds() != null) {
                replaceTemplateSkillBindings(agentId, input.getSkillIds());
            }
            if (input.getToolIds() != null) {
                replaceTemplateToolBindings(agentId, input.getToolIds());
            }
        }
        agentTemplateMapper.updateById(template);
        return getAgentDetail(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long agentId) {
        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            return;
        }
        agentTemplateSkillBindingMapper.delete(new LambdaQueryWrapper<AgentTemplateSkillBinding>()
                .eq(AgentTemplateSkillBinding::getTemplateId, agentId));
        agentTemplateToolBindingMapper.delete(new LambdaQueryWrapper<AgentTemplateToolBinding>()
                .eq(AgentTemplateToolBinding::getTemplateId, agentId));
        agentTemplateMapper.deleteById(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleAgentEnabled(Long agentId) {
        AgentTemplate template = agentTemplateMapper.selectById(agentId);
        if (template == null) {
            throw new LZException(ExceptionCode.Default, "专家技能包不存在: " + agentId);
        }
        template.setEnabled(template.getEnabled() != null && template.getEnabled() == 1 ? 0 : 1);
        agentTemplateMapper.updateById(template);
    }

    private AgentSimpleDto toSimpleDto(AgentTemplate template) {
        AgentSimpleDto dto = new AgentSimpleDto();
        dto.setId(template.getId());
        dto.setAgentCode(template.getAgentCode());
        dto.setAgentName(template.getAgentName());
        dto.setDescription(template.getDescription());
        dto.setOpeningMessage(template.getOpeningMessage());
        dto.setIcon(template.getIcon());
        return dto;
    }

    private AgentListItemDto toListItemDto(
            AgentTemplate template, Integer roleCount, Integer skillCount, Integer toolCount) {
        AgentListItemDto dto = new AgentListItemDto();
        dto.setId(template.getId());
        dto.setAgentCode(template.getAgentCode());
        dto.setAgentName(template.getAgentName());
        dto.setDescription(template.getDescription());
        dto.setIcon(template.getIcon());
        dto.setEnabled(template.getEnabled());
        dto.setRoleCount(roleCount);
        dto.setSkillCount(skillCount);
        dto.setToolCount(toolCount);
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }

    private AgentDetailDto toDetailDto(
            AgentTemplate template, Integer roleCount, List<SkillSimpleDto> skills, List<ToolSimpleDto> tools) {
        AgentDetailDto dto = new AgentDetailDto();
        dto.setId(template.getId());
        dto.setAgentCode(template.getAgentCode());
        dto.setAgentName(template.getAgentName());
        dto.setDescription(template.getDescription());
        dto.setOpeningMessage(template.getOpeningMessage());
        dto.setIcon(template.getIcon());
        dto.setSoulTemplate(template.getSoulTemplate());
        dto.setProfileTemplate(template.getProfileTemplate());
        dto.setEnabled(template.getEnabled());
        dto.setRoleCount(roleCount);
        dto.setSkillCount(skills.size());
        dto.setToolCount(tools.size());
        dto.setSkills(skills);
        dto.setTools(tools);
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }

    private List<SkillSimpleDto> loadTemplateSkills(Long templateId) {
        if (templateId == null) {
            return EMPTY_SKILLS;
        }
        return loadTemplateSkillsMap(List.of(templateId)).getOrDefault(templateId, EMPTY_SKILLS);
    }

    private List<ToolSimpleDto> loadTemplateTools(Long templateId) {
        if (templateId == null) {
            return EMPTY_TOOLS;
        }
        return loadTemplateToolsMap(List.of(templateId)).getOrDefault(templateId, EMPTY_TOOLS);
    }

    private Map<Long, List<SkillSimpleDto>> loadTemplateSkillsMap(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AgentTemplateSkillBinding> bindings =
                agentTemplateSkillBindingMapper.selectList(new LambdaQueryWrapper<AgentTemplateSkillBinding>()
                        .in(AgentTemplateSkillBinding::getTemplateId, templateIds)
                        .orderByAsc(AgentTemplateSkillBinding::getSortOrder)
                        .orderByAsc(AgentTemplateSkillBinding::getId));
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> skillIds = bindings.stream()
                .map(AgentTemplateSkillBinding::getSkillId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (skillIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, SkillSimpleDto> skillMap = skillCatalogMapper.selectByIds(skillIds).stream()
                .map(this::toSkillSimpleDto)
                .collect(Collectors.toMap(SkillSimpleDto::getId, item -> item));
        Map<Long, List<SkillSimpleDto>> result = new LinkedHashMap<>();
        for (AgentTemplateSkillBinding binding : bindings) {
            if (binding.getTemplateId() == null) {
                continue;
            }
            SkillSimpleDto skill = skillMap.get(binding.getSkillId());
            if (skill == null) {
                continue;
            }
            result.computeIfAbsent(binding.getTemplateId(), key -> new ArrayList<>())
                    .add(skill);
        }
        return result;
    }

    private void replaceTemplateSkillBindings(Long templateId, List<Long> skillIds) {
        if (templateId == null) {
            return;
        }
        agentTemplateSkillBindingMapper.delete(new LambdaQueryWrapper<AgentTemplateSkillBinding>()
                .eq(AgentTemplateSkillBinding::getTemplateId, templateId));
        List<Long> normalizedSkillIds = normalizeIds(skillIds);
        for (int i = 0; i < normalizedSkillIds.size(); i++) {
            AgentTemplateSkillBinding binding = new AgentTemplateSkillBinding();
            binding.setTemplateId(templateId);
            binding.setSkillId(normalizedSkillIds.get(i));
            binding.setSortOrder(i);
            agentTemplateSkillBindingMapper.insert(binding);
        }
    }

    private Map<Long, List<ToolSimpleDto>> loadTemplateToolsMap(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AgentTemplateToolBinding> bindings =
                agentTemplateToolBindingMapper.selectList(new LambdaQueryWrapper<AgentTemplateToolBinding>()
                        .in(AgentTemplateToolBinding::getTemplateId, templateIds)
                        .orderByAsc(AgentTemplateToolBinding::getSortOrder)
                        .orderByAsc(AgentTemplateToolBinding::getId));
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> toolIds = bindings.stream()
                .map(AgentTemplateToolBinding::getToolId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (toolIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ToolSimpleDto> toolMap = toolCatalogMapper.selectBatchIds(toolIds).stream()
                .map(this::toToolSimpleDto)
                .collect(Collectors.toMap(ToolSimpleDto::getId, item -> item));
        Map<Long, List<ToolSimpleDto>> result = new LinkedHashMap<>();
        for (AgentTemplateToolBinding binding : bindings) {
            if (binding.getTemplateId() == null) {
                continue;
            }
            ToolSimpleDto tool = toolMap.get(binding.getToolId());
            if (tool == null) {
                continue;
            }
            result.computeIfAbsent(binding.getTemplateId(), key -> new ArrayList<>())
                    .add(tool);
        }
        return result;
    }

    private void replaceTemplateToolBindings(Long templateId, List<Long> toolIds) {
        if (templateId == null) {
            return;
        }
        agentTemplateToolBindingMapper.delete(new LambdaQueryWrapper<AgentTemplateToolBinding>()
                .eq(AgentTemplateToolBinding::getTemplateId, templateId));
        List<Long> normalizedToolIds = normalizeIds(toolIds);
        for (int i = 0; i < normalizedToolIds.size(); i++) {
            AgentTemplateToolBinding binding = new AgentTemplateToolBinding();
            binding.setTemplateId(templateId);
            binding.setToolId(normalizedToolIds.get(i));
            binding.setSortOrder(i);
            agentTemplateToolBindingMapper.insert(binding);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private SkillSimpleDto toSkillSimpleDto(SkillCatalog skillCatalog) {
        SkillSimpleDto dto = new SkillSimpleDto();
        dto.setId(skillCatalog.getId());
        dto.setRuntimeSkillName(skillCatalog.getRuntimeSkillName());
        dto.setDisplayName(skillCatalog.getDisplayName());
        dto.setDescription(skillCatalog.getDescription());
        dto.setCategory(skillCatalog.getCategory());
        dto.setIcon(skillCatalog.getIcon());
        dto.setIconColor(skillCatalog.getIconColor());
        return dto;
    }

    private ToolSimpleDto toToolSimpleDto(ToolCatalog toolCatalog) {
        ToolSimpleDto dto = new ToolSimpleDto();
        dto.setId(toolCatalog.getId());
        dto.setToolName(toolCatalog.getToolName());
        dto.setDisplayName(toolCatalog.getDisplayName());
        dto.setDescription(toolCatalog.getDescription());
        dto.setToolType(toolCatalog.getToolType());
        dto.setSource(toolCatalog.getSource());
        return dto;
    }
}
