package lingzhou.agent.backend.business.system.service;

import lingzhou.agent.backend.business.system.model.ChangeCurrentUserPasswordInput;
import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.DeleteUserInput;
import lingzhou.agent.backend.business.system.model.GrantUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.ResetUserPasswordInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenResult;
import lingzhou.agent.backend.business.system.model.UpdateUserProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserStateInput;
import lingzhou.agent.backend.business.system.model.UpdateUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserInfoDto;
import lingzhou.agent.backend.business.system.model.UserPageInput;
import lingzhou.agent.backend.business.system.model.UserPageResult;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.login.DomainLoginDto;
import lingzhou.agent.backend.common.login.GetOrganizationListInput;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.AuthToken;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    DomainLoginDto login(GetOrganizationListInput input) throws Exception;

    AuthToken refreshToken(String refreshToken) throws Exception;

    ApiResponse<SsoExchangeTokenResult> exchangeToken(SsoExchangeTokenInput input) throws Exception;

    String createUser(Long operatorUserId, CreateUserInput input);

    String createUserWithoutPermissionCheck(CreateUserInput input);

    String updateUserProfile(Long operatorUserId, UpdateUserProfileInput input);

    String resetUserPassword(Long operatorUserId, ResetUserPasswordInput input);

    String changeCurrentUserPassword(Long userId, ChangeCurrentUserPasswordInput input);

    String updateUserState(Long operatorUserId, UpdateUserStateInput input);

    String deleteUser(Long operatorUserId, DeleteUserInput input);

    String grantUserTokenQuota(Long operatorUserId, GrantUserTokenQuotaInput input);

    String updateUserTokenQuota(Long operatorUserId, UpdateUserTokenQuotaInput input);

    UserAvatarUploadResult uploadCurrentUserAvatar(Long userId, MultipartFile file) throws TaskException;

    UserInfoDto getUserInfoById(Long userId);

    UserPageResult listUsers(Long operatorUserId, UserPageInput input);

    void logout(String token);
}
