package lingzhou.agent.backend.business.datasets.service;

import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeDocument;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBaseMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeDocumentMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.ResourcePermissionScope;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.ResourcePermissionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBasePermissionService {

    public static final int SCOPE_OWNER_ONLY = ResourcePermissionScope.OWNER_ONLY.code();
    public static final int SCOPE_PUBLIC_VISIBLE_OWNER_OPERATE =
            ResourcePermissionScope.PUBLIC_VISIBLE_OWNER_OPERATE.code();
    public static final int SCOPE_PUBLIC_FULL_ACCESS = ResourcePermissionScope.PUBLIC_FULL_ACCESS.code();

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final SysUserMapper sysUserMapper;

    public KnowledgeBasePermissionService(
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            SysUserMapper sysUserMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public SysUserModel resolveOperator(Long operatorUserId) {
        if (operatorUserId == null) {
            return null;
        }
        return sysUserMapper.selectById(operatorUserId);
    }

    public boolean canViewKnowledgeBase(KnowledgeBase knowledgeBase, SysUserModel operator) {
        if (knowledgeBase == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canView(
                knowledgeBase.getPermissionScope(), knowledgeBase.getOwnerUserId(), operatorUserId);
    }

    public boolean canOperateKnowledgeBase(KnowledgeBase knowledgeBase, SysUserModel operator) {
        if (knowledgeBase == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canOperate(
                knowledgeBase.getPermissionScope(), knowledgeBase.getOwnerUserId(), operatorUserId);
    }

    public boolean canBindKnowledgeBaseTool(Long ownerUserId, Integer permissionScope, SysUserModel operator) {
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.canOperate(permissionScope, ownerUserId, operatorUserId);
    }

    public boolean canChangePermissionScope(KnowledgeBase knowledgeBase, SysUserModel operator) {
        if (knowledgeBase == null) {
            return false;
        }
        if (isAdmin(operator)) {
            return true;
        }
        Long operatorUserId = operator == null ? null : operator.getId();
        return ResourcePermissionSupport.isOwner(knowledgeBase.getOwnerUserId(), operatorUserId);
    }

    public int normalizePermissionScope(Integer permissionScope) {
        return ResourcePermissionSupport.normalizeScope(permissionScope);
    }

    public KnowledgeBase requireKnowledgeBase(Long kbId) throws TaskException {
        if (kbId == null) {
            throw new TaskException("知识库ID不能为空", TaskException.Code.UNKNOWN);
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbId(kbId);
        if (knowledgeBase == null) {
            throw new TaskException("知识库不存在: " + kbId, TaskException.Code.UNKNOWN);
        }
        return knowledgeBase;
    }

    public KnowledgeBase requireKnowledgeBaseByDocId(Long docId) throws TaskException {
        if (docId == null) {
            throw new TaskException("文档ID不能为空", TaskException.Code.UNKNOWN);
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectKnowledgeDocumentByDocId(docId);
        if (document == null || document.getKbId() == null) {
            throw new TaskException("文档不存在: " + docId, TaskException.Code.UNKNOWN);
        }
        return requireKnowledgeBase(document.getKbId());
    }

    public void assertCanViewKnowledgeBase(Long kbId, SysUserModel operator) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        assertCanViewKnowledgeBase(knowledgeBase, operator);
    }

    public void assertCanViewKnowledgeBase(KnowledgeBase knowledgeBase, SysUserModel operator) throws TaskException {
        if (!canViewKnowledgeBase(knowledgeBase, operator)) {
            throw new TaskException("无权限查看该知识库", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanOperateKnowledgeBase(Long kbId, SysUserModel operator) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(kbId);
        assertCanOperateKnowledgeBase(knowledgeBase, operator);
    }

    public void assertCanOperateKnowledgeBase(KnowledgeBase knowledgeBase, SysUserModel operator) throws TaskException {
        if (!canOperateKnowledgeBase(knowledgeBase, operator)) {
            throw new TaskException("无权限操作该知识库", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanChangePermissionScope(KnowledgeBase knowledgeBase, SysUserModel operator)
            throws TaskException {
        if (!canChangePermissionScope(knowledgeBase, operator)) {
            throw new TaskException("仅创建人或系统管理员可修改资源权限。", TaskException.Code.UNKNOWN);
        }
    }

    public void assertCanViewKnowledgeDocument(Long docId, SysUserModel operator) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBaseByDocId(docId);
        assertCanViewKnowledgeBase(knowledgeBase, operator);
    }

    public void assertCanOperateKnowledgeDocument(Long docId, SysUserModel operator) throws TaskException {
        KnowledgeBase knowledgeBase = requireKnowledgeBaseByDocId(docId);
        assertCanOperateKnowledgeBase(knowledgeBase, operator);
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
}
