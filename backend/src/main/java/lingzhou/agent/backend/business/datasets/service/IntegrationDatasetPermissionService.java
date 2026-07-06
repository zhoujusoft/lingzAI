package lingzhou.agent.backend.business.datasets.service;

import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.ResourcePermissionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDatasetPermissionService {

    private final IntegrationDatasetMapper integrationDatasetMapper;
    private final SysUserMapper sysUserMapper;

    public IntegrationDatasetPermissionService(
            IntegrationDatasetMapper integrationDatasetMapper, SysUserMapper sysUserMapper) {
        this.integrationDatasetMapper = integrationDatasetMapper;
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

    public boolean canViewDataset(IntegrationDataset dataset, SysUserModel operator) {
        if (dataset == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canView(
                dataset.getPermissionScope(), dataset.getOwnerUserId(), operatorUserId);
    }

    public boolean canOperateDataset(IntegrationDataset dataset, SysUserModel operator) {
        if (dataset == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canOperate(
                dataset.getPermissionScope(), dataset.getOwnerUserId(), operatorUserId);
    }

    public boolean canChangePermissionScope(IntegrationDataset dataset, SysUserModel operator) {
        if (dataset == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.isOwner(dataset.getOwnerUserId(), operatorUserId);
    }

    public IntegrationDataset requireDataset(Long datasetId) throws TaskException {
        if (datasetId == null) {
            throw new TaskException("数据集 id 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationDataset dataset = integrationDatasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new TaskException("数据集不存在：" + datasetId, TaskException.Code.UNKNOWN);
        }
        return dataset;
    }

    public void assertCanViewDataset(IntegrationDataset dataset, SysUserModel operator) throws TaskException {
        if (!canViewDataset(dataset, operator)) {
            throw new TaskException("无权限查看该数据集", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanOperateDataset(IntegrationDataset dataset, SysUserModel operator) throws TaskException {
        if (!canOperateDataset(dataset, operator)) {
            throw new TaskException("无权限操作该数据集", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanChangePermissionScope(IntegrationDataset dataset, SysUserModel operator) throws TaskException {
        if (!canChangePermissionScope(dataset, operator)) {
            throw new TaskException("仅创建人或系统管理员可修改资源权限。", TaskException.Code.UNKNOWN);
        }
    }
}
