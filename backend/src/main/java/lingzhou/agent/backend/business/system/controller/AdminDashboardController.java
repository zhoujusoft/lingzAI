package lingzhou.agent.backend.business.system.controller;

import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.service.AdminDashboardService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/dashboard")
public class AdminDashboardController extends BaseController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public AdminDashboardApiModels.DashboardResponse getDashboard() throws TaskException {
        return adminDashboardService.getDashboard(resolveUserId());
    }

    private Long resolveUserId() {
        String userId = getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new IllegalStateException("UserId missing");
        }
        return Long.valueOf(userId.trim());
    }
}
