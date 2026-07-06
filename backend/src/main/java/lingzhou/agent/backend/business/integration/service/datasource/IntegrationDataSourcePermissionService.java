package lingzhou.agent.backend.business.integration.service.datasource;

import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.ResourcePermissionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDataSourcePermissionService {

    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final SysUserMapper sysUserMapper;

    public IntegrationDataSourcePermissionService(
            IntegrationDataSourceMapper integrationDataSourceMapper, SysUserMapper sysUserMapper) {
        this.integrationDataSourceMapper = integrationDataSourceMapper;
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
        return StringUtils.hasText(operator.getCode())
                && "admin".equalsIgnoreCase(operator.getCode().trim());
    }

    public int normalizePermissionScope(Integer permissionScope) {
        return ResourcePermissionSupport.normalizeScope(permissionScope);
    }

    public boolean canViewDataSource(IntegrationDataSource dataSource, SysUserModel operator) {
        if (dataSource == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canView(
                dataSource.getPermissionScope(), dataSource.getOwnerUserId(), operatorUserId);
    }

    public boolean canOperateDataSource(IntegrationDataSource dataSource, SysUserModel operator) {
        if (dataSource == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canOperate(
                dataSource.getPermissionScope(), dataSource.getOwnerUserId(), operatorUserId);
    }

    public boolean canChangePermissionScope(IntegrationDataSource dataSource, SysUserModel operator) {
        if (dataSource == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.isOwner(dataSource.getOwnerUserId(), operatorUserId);
    }

    public IntegrationDataSource requireDataSource(Long id) throws TaskException {
        if (id == null) {
            throw new TaskException("数据源 id 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationDataSource entity = integrationDataSourceMapper.selectById(id);
        if (entity == null) {
            throw new TaskException("数据源不存在：" + id, TaskException.Code.UNKNOWN);
        }
        return entity;
    }

    public void assertCanViewDataSource(IntegrationDataSource dataSource, SysUserModel operator) throws TaskException {
        if (!canViewDataSource(dataSource, operator)) {
            throw new TaskException("无权限查看该数据库", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanOperateDataSource(IntegrationDataSource dataSource, SysUserModel operator)
            throws TaskException {
        if (!canOperateDataSource(dataSource, operator)) {
            throw new TaskException("无权限操作该数据库", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanChangePermissionScope(IntegrationDataSource dataSource, SysUserModel operator)
            throws TaskException {
        if (!canChangePermissionScope(dataSource, operator)) {
            throw new TaskException("仅创建人或系统管理员可修改资源权限。", TaskException.Code.UNKNOWN);
        }
    }
}
