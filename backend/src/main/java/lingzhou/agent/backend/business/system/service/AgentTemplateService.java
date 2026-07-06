package lingzhou.agent.backend.business.system.service;

import java.util.List;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.AgentPageInput;
import lingzhou.agent.backend.business.system.model.AgentPageResult;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.CreateAgentInput;
import lingzhou.agent.backend.business.system.model.UpdateAgentInput;

public interface AgentTemplateService {

    List<AgentSimpleDto> listEnabledAgents();

    List<AgentDetailDto> listEnabledAgentDetails();

    AgentSimpleDto getAgentById(Long agentId);

    AgentPageResult listAgents(AgentPageInput input);

    AgentDetailDto getAgentDetail(Long agentId);

    AgentDetailDto getEnabledAgentDetail(Long agentId);

    AgentDetailDto createAgent(CreateAgentInput input);

    AgentDetailDto updateAgent(Long agentId, UpdateAgentInput input);

    void deleteAgent(Long agentId);

    void toggleAgentEnabled(Long agentId);
}
