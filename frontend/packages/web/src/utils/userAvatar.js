export function resolveUserAvatarUrl(user, fallbackUrl = '') {
    const avatarUrl = typeof user?.avatarUrl === 'string' ? user.avatarUrl.trim() : '';
    return avatarUrl || fallbackUrl;
}
