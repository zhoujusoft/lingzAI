package lingzhou.agent.backend.business.integration.service.connector;

import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.ResourcePermissionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationConnectorPermissionService {

    private final IntegrationConnectorMapper integrationConnectorMapper;
    private final SysUserMapper sysUserMapper;

    public IntegrationConnectorPermissionService(
            IntegrationConnectorMapper integrationConnectorMapper, SysUserMapper sysUserMapper) {
        this.integrationConnectorMapper = integrationConnectorMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public SysUserModel resolveOperator(Long operatorUserId) {
        if (operatorUserId == null) {
            return null;
        }
        return sysUserMapper.selectById(operatorUserId);
    }

    public boolean isAdmin(SysUserModel operator) {
        if (operator == null) {
            return false;
        }
        if (operator.getUserType() != null && operator.getUserType() == UserType.admin.getValue()) {
            return true;
        }
        return StringUtils.hasText(operator.getCode()) && "admin".equalsIgnoreCase(operator.getCode().trim());
    }

    public int normalizePermissionScope(Integer permissionScope) {
        return ResourcePermissionSupport.normalizeScope(permissionScope);
    }

    public boolean canViewConnector(IntegrationConnector connector, SysUserModel operator) {
        if (connector == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canView(
                connector.getPermissionScope(), connector.getOwnerUserId(), operatorUserId);
    }

    public boolean canOperateConnector(IntegrationConnector connector, SysUserModel operator) {
        if (connector == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canOperate(
                connector.getPermissionScope(), connector.getOwnerUserId(), operatorUserId);
    }

    public boolean canChangePermissionScope(IntegrationConnector connector, SysUserModel operator) {
        if (connector == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.isOwner(connector.getOwnerUserId(), operatorUserId);
    }

    public IntegrationConnector requireConnector(Long id) throws TaskException {
        if (id == null) {
            throw new TaskException("连接器 ID 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationConnector entity = integrationConnectorMapper.selectById(id);
        if (entity == null) {
            throw new TaskException("连接器不存在: " + id, TaskException.Code.UNKNOWN);
        }
        return entity;
    }

    public void assertCanViewConnector(IntegrationConnector connector, SysUserModel operator) throws TaskException {
        if (!canViewConnector(connector, operator)) {
            throw new TaskException("无权查看该连接器", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanOperateConnector(IntegrationConnector connector, SysUserModel operator) throws TaskException {
        if (!canOperateConnector(connector, operator)) {
            throw new TaskException("无权操作该连接器", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanChangePermissionScope(IntegrationConnector connector, SysUserModel operator)
            throws TaskException {
        if (!canChangePermissionScope(connector, operator)) {
            throw new TaskException(
                    "仅连接器所有者或系统管理员可以修改权限范围",
                    TaskException.Code.UNKNOWN);
        }
    }
}
