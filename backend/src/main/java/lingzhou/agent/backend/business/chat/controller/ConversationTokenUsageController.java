package lingzhou.agent.backend.business.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.chat.service.ConversationTokenUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat/token-usage")
public class ConversationTokenUsageController {

    private final ConversationTokenUsageService conversationTokenUsageService;

    public ConversationTokenUsageController(ConversationTokenUsageService conversationTokenUsageService) {
        this.conversationTokenUsageService = conversationTokenUsageService;
    }

    @GetMapping("/dashboard")
    public ConversationTokenUsageApiModels.DashboardResponse getDashboard(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "agentType", required = false) String agentType,
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "modelProvider", required = false) String modelProvider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "topN", required = false) Integer topN,
            HttpServletRequest request) {
        Long operatorUserId = requireUserId(request);
        return conversationTokenUsageService.getDashboard(
                operatorUserId,
                startDate,
                endDate,
                agentType,
                agentId,
                userId,
                sessionType,
                modelProvider,
                modelName,
                topN);
    }

    @GetMapping("/runs")
    public ConversationTokenUsageApiModels.RunListResponse listRuns(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "agentType", required = false) String agentType,
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "modelProvider", required = false) String modelProvider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        Long operatorUserId = requireUserId(request);
        return conversationTokenUsageService.listRuns(
                operatorUserId,
                startDate,
                endDate,
                agentType,
                agentId,
                userId,
                sessionType,
                modelProvider,
                modelName,
                pageNo,
                pageSize);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
