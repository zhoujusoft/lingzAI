export const USER_TYPES = Object.freeze({
    ADMIN: 0,
    NORMAL: 1,
});

export const USER_TYPE_LABELS = Object.freeze({
    [USER_TYPES.NORMAL]: '普通用户',
    [USER_TYPES.ADMIN]: '管理员',
});

export function resolveUserTypeLabel(userType, fallback = '普通用户') {
    return USER_TYPE_LABELS[userType] || fallback;
}
