export const ADMIN_MENU_PERMISSION_GROUPS = Object.freeze([
    {
        id: 'content-skill',
        label: '内容与技能',
        options: Object.freeze([
            { key: 'admin.knowledge.view', label: '知识库' },
            { key: 'admin.skillstudio.view', label: '技能工坊' },
            { key: 'admin.sandbox-test.view', label: '云电脑测试' },
            { key: 'admin.skill-management.view', label: '技能管理' },
        ]),
    },
    {
        id: 'integration-model',
        label: '接入与模型',
        options: Object.freeze([
            { key: 'admin.mcp-management.view', label: 'MCP 服务' },
            { key: 'admin.channel-management.view', label: '渠道接入' },
            { key: 'admin.model-library.view', label: '模型库' },
            { key: 'admin.token-usage.view', label: 'Token 统计' },
        ]),
    },
    {
        id: 'catalog',
        label: '资源目录',
        options: Object.freeze([
            { key: 'admin.integration.connectors.view', label: '连接器' },
            { key: 'admin.integration.data-sources.view', label: '数据源' },
            { key: 'admin.integration.datasets.view', label: '数据集' },
            { key: 'admin.api-library.view', label: 'API 库' },
            { key: 'admin.tool-library.view', label: '工具库' },
        ]),
    },
    {
        id: 'system',
        label: '系统管理',
        options: Object.freeze([
            { key: 'admin.system.agents.view', label: '专家技能包' },
            { key: 'admin.system.roles.view', label: '角色管理' },
            { key: 'admin.system.users.view', label: '用户管理' },
            { key: 'admin.system.token-quota.view', label: '用户额度' },
            { key: 'admin.system.configs.view', label: '配置管理' },
            { key: 'admin.system.user-agent-config.view', label: '用户 Agent 配置' },
        ]),
    },
]);

export const ADMIN_MENU_PERMISSION_KEYS = Object.freeze(
    ADMIN_MENU_PERMISSION_GROUPS.flatMap(group => group.options.map(option => option.key))
);

export function normalizeMenuPermissions(menuPermissions) {
    if (!Array.isArray(menuPermissions)) {
        return [];
    }
    const normalized = [];
    const seen = new Set();
    for (const item of menuPermissions) {
        const key = typeof item === 'string' ? item.trim() : '';
        if (!key || seen.has(key)) {
            continue;
        }
        normalized.push(key);
        seen.add(key);
    }
    return normalized;
}

export function isSuperAdmin(profile) {
    const userCode = typeof profile?.code === 'string' ? profile.code.trim().toLowerCase() : '';
    if (userCode === 'admin') {
        return true;
    }
    const roleCode =
        typeof profile?.roleCode === 'string' ? profile.roleCode.trim().toLowerCase() : '';
    return roleCode === 'super-admin' || roleCode === 'super_admin' || roleCode === 'admin';
}

export function hasAnyAdminPermission(profile) {
    if (isSuperAdmin(profile)) {
        return true;
    }
    const menuPermissions = normalizeMenuPermissions(profile?.menuPermissions);
    if (menuPermissions.includes('*')) {
        return true;
    }
    return ADMIN_MENU_PERMISSION_KEYS.some(permissionKey =>
        menuPermissions.includes(permissionKey)
    );
}

export function hasMenuPermission(profile, permissionKey) {
    if (isSuperAdmin(profile)) {
        return true;
    }
    if (!permissionKey) {
        return true;
    }
    const menuPermissions = normalizeMenuPermissions(profile?.menuPermissions);
    if (menuPermissions.includes('*')) {
        return true;
    }
    return menuPermissions.includes(permissionKey);
}
