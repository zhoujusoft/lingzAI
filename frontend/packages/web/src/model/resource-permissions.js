import { USER_TYPES } from '@/model/enums/user-type';

export const RESOURCE_PERMISSION_SCOPES = Object.freeze({
    OWNER_ONLY: 1,
    PUBLIC_VISIBLE_OWNER_OPERATE: 2,
    PUBLIC_FULL_ACCESS: 3,
});

export const RESOURCE_PERMISSION_UI_OPTIONS = Object.freeze([
    Object.freeze({
        value: RESOURCE_PERMISSION_SCOPES.OWNER_ONLY,
        label: '私人',
        description: '仅创建人可见可操作。',
        badgeClass: 'bg-slate-100 text-slate-600',
    }),
    Object.freeze({
        value: RESOURCE_PERMISSION_SCOPES.PUBLIC_VISIBLE_OWNER_OPERATE,
        label: '受限公开',
        description: '所有用户可见，仅创建人可操作。',
        badgeClass: 'bg-amber-50 text-amber-700',
    }),
    Object.freeze({
        value: RESOURCE_PERMISSION_SCOPES.PUBLIC_FULL_ACCESS,
        label: '公开',
        description: '所有用户可见可操作。',
        badgeClass: 'bg-emerald-50 text-emerald-700',
    }),
]);

export function normalizeResourcePermissionScope(scope) {
    const normalized = Number(scope);
    if (
        normalized === RESOURCE_PERMISSION_SCOPES.OWNER_ONLY ||
        normalized === RESOURCE_PERMISSION_SCOPES.PUBLIC_VISIBLE_OWNER_OPERATE ||
        normalized === RESOURCE_PERMISSION_SCOPES.PUBLIC_FULL_ACCESS
    ) {
        return normalized;
    }
    return RESOURCE_PERMISSION_SCOPES.PUBLIC_FULL_ACCESS;
}

export function getResourcePermissionOption(scope) {
    const normalizedScope = normalizeResourcePermissionScope(scope);
    return (
        RESOURCE_PERMISSION_UI_OPTIONS.find(item => item.value === normalizedScope) ||
        RESOURCE_PERMISSION_UI_OPTIONS[RESOURCE_PERMISSION_UI_OPTIONS.length - 1]
    );
}

export function getResourcePermissionLabel(scope) {
    return getResourcePermissionOption(scope).label;
}

export function getResourcePermissionDescription(scope) {
    return getResourcePermissionOption(scope).description;
}

export function getResourcePermissionBadgeClass(scope) {
    return getResourcePermissionOption(scope).badgeClass;
}

export function isResourceOwner(ownerUserId, currentUserId) {
    const owner = Number(ownerUserId);
    const current = Number(currentUserId);
    if (!Number.isFinite(owner) || owner <= 0) {
        return false;
    }
    if (!Number.isFinite(current) || current <= 0) {
        return false;
    }
    return owner === current;
}

export function isAdminUser(profile) {
    if (!profile || typeof profile !== 'object') {
        return false;
    }
    if (Number(profile.userType) === Number(USER_TYPES.ADMIN)) {
        return true;
    }
    return (
        String(profile.code || '')
            .trim()
            .toLowerCase() === 'admin'
    );
}

export function canViewResource(resource, profile) {
    if (!resource || typeof resource !== 'object') {
        return false;
    }
    if (isAdminUser(profile)) {
        return true;
    }
    const scope = normalizeResourcePermissionScope(resource.permissionScope);
    if (scope === RESOURCE_PERMISSION_SCOPES.OWNER_ONLY) {
        return isResourceOwner(resource.ownerUserId, profile?.id);
    }
    return true;
}

export function canOperateResource(resource, profile) {
    if (!resource || typeof resource !== 'object') {
        return false;
    }
    if (isAdminUser(profile)) {
        return true;
    }
    const scope = normalizeResourcePermissionScope(resource.permissionScope);
    if (scope === RESOURCE_PERMISSION_SCOPES.PUBLIC_FULL_ACCESS) {
        return true;
    }
    return isResourceOwner(resource.ownerUserId, profile?.id);
}

export function canChangeResourcePermission(resource, profile) {
    if (!resource || typeof resource !== 'object') {
        return false;
    }
    if (isAdminUser(profile)) {
        return true;
    }
    return isResourceOwner(resource.ownerUserId, profile?.id);
}
