package lingzhou.agent.backend.business.system.service;

import java.util.List;
import lingzhou.agent.backend.common.lzException.TaskException;

public interface RegisterUserSkillService {

    PreviewResult preview(PreviewCommand command) throws TaskException;

    ConfirmResult confirm(Long operatorUserId, ConfirmCommand command) throws TaskException;

    record PreviewCommand(String username, String name, String password, String mobile, String email) {}

    record ConfirmCommand(
            String username, String name, String password, String mobile, String email, Boolean confirm) {}

    record PreviewResult(
            String username,
            String name,
            String password,
            boolean defaultPassword,
            String mobile,
            String email,
            boolean generatedUsername,
            List<String> notices) {}

    record ConfirmResult(
            Long userId,
            String username,
            String name,
            String roleCode,
            boolean defaultPassword,
            String mobile,
            String email,
            String platformUrl,
            String message) {}
}
