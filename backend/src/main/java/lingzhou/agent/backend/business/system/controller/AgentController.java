package lingzhou.agent.backend.business.system.controller;

import java.util.List;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.AgentPageInput;
import lingzhou.agent.backend.business.system.model.AgentPageResult;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.CreateAgentInput;
import lingzhou.agent.backend.business.system.model.UpdateAgentInput;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/agents")
public class AgentController extends BaseController {

    private final AgentTemplateService agentTemplateService;

    public AgentController(AgentTemplateService agentTemplateService) {
        this.agentTemplateService = agentTemplateService;
    }

    /**
     * 分页查询Agent列表
     */
    @GetMapping
    public AgentPageResult listAgents(AgentPageInput input) {
        return agentTemplateService.listAgents(input);
    }

    /**
     * 获取Agent详情
     */
    @GetMapping("/{id}")
    public AgentDetailDto getAgentDetail(@PathVariable("id") Long id) {
        return agentTemplateService.getAgentDetail(id);
    }

    /**
     * 创建Agent模板
     */
    @PostMapping
    public ApiResponse<AgentDetailDto> createAgent(@RequestBody CreateAgentInput input) {
        try {
            return ApiResponse.success(agentTemplateService.createAgent(input));
        } catch (Exception e) {
            return ApiResponse.fail(400001, e.getMessage());
        }
    }

    /**
     * 更新Agent模板
     */
    @PutMapping("/{id}")
    public ApiResponse<AgentDetailDto> updateAgent(@PathVariable("id") Long id, @RequestBody UpdateAgentInput input) {
        try {
            return ApiResponse.success(agentTemplateService.updateAgent(id, input));
        } catch (Exception e) {
            return ApiResponse.fail(400001, e.getMessage());
        }
    }

    /**
     * 删除Agent模板
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAgent(@PathVariable("id") Long id) {
        try {
            agentTemplateService.deleteAgent(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.fail(400001, e.getMessage());
        }
    }

    /**
     * 切换Agent启用状态
     */
    @PostMapping("/{id}/toggle-enabled")
    public ApiResponse<Void> toggleAgentEnabled(@PathVariable("id") Long id) {
        try {
            agentTemplateService.toggleAgentEnabled(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.fail(400001, e.getMessage());
        }
    }

    /**
     * 获取所有启用的Agent（用于下拉选择）
     */
    @GetMapping("/enabled")
    public List<AgentSimpleDto> listEnabledAgents() {
        return agentTemplateService.listEnabledAgents();
    }

    /**
     * 获取所有启用的专家技能包详情
     */
    @GetMapping("/enabled-details")
    public List<AgentDetailDto> listEnabledAgentDetails() {
        return agentTemplateService.listEnabledAgentDetails();
    }

    /**
     * 获取启用的专家技能包详情
     */
    @GetMapping("/enabled-details/{id}")
    public AgentDetailDto getEnabledAgentDetail(@PathVariable("id") Long id) {
        return agentTemplateService.getEnabledAgentDetail(id);
    }
}
