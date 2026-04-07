package lingzhou.agent.backend.business.system.service;

import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.DeleteUserInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenResult;
import lingzhou.agent.backend.business.system.model.ResetUserPasswordInput;
import lingzhou.agent.backend.business.system.model.UpdateUserProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserStateInput;
import lingzhou.agent.backend.business.system.model.UserInfoDto;
import lingzhou.agent.backend.business.system.model.UserPageInput;
import lingzhou.agent.backend.business.system.model.UserPageResult;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.login.DomainLoginDto;
import lingzhou.agent.backend.common.login.GetOrganizationListInput;
import lingzhou.agent.backend.common.permission.AuthToken;

public interface UserService {

    DomainLoginDto login(GetOrganizationListInput input) throws Exception;

    AuthToken refreshToken(String refreshToken) throws Exception;

    ApiResponse<SsoExchangeTokenResult> exchangeToken(SsoExchangeTokenInput input) throws Exception;

    String createUser(Long operatorUserId, CreateUserInput input);

    String updateUserProfile(Long operatorUserId, UpdateUserProfileInput input);

    String resetUserPassword(Long operatorUserId, ResetUserPasswordInput input);

    String updateUserState(Long operatorUserId, UpdateUserStateInput input);

    String deleteUser(Long operatorUserId, DeleteUserInput input);

    UserInfoDto getUserInfoById(Long userId);

    UserPageResult listUsers(Long operatorUserId, UserPageInput input);

    void logout(String token);
}
