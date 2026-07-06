package lingzhou.agent.backend.common.permission;

import lingzhou.agent.backend.common.enums.ResourcePermissionScope;

public final class ResourcePermissionSupport {

    private ResourcePermissionSupport() {}

    public static int normalizeScope(Integer scope) {
        return ResourcePermissionScope.normalizeCode(scope);
    }

    public static boolean isOwner(Long ownerUserId, Long operatorUserId) {
        if (ownerUserId == null || operatorUserId == null) {
            return false;
        }
        return ownerUserId.equals(operatorUserId);
    }

    public static boolean canView(Integer scope, Long ownerUserId, Long operatorUserId) {
        int normalized = normalizeScope(scope);
        if (normalized == ResourcePermissionScope.OWNER_ONLY.code()) {
            return isOwner(ownerUserId, operatorUserId);
        }
        return true;
    }

    public static boolean canOperate(Integer scope, Long ownerUserId, Long operatorUserId) {
        int normalized = normalizeScope(scope);
        if (normalized == ResourcePermissionScope.PUBLIC_FULL_ACCESS.code()) {
            return true;
        }
        return isOwner(ownerUserId, operatorUserId);
    }
}
