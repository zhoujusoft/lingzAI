import { ROUTE_PATHS } from '@/router/routePaths';

export const ADMIN_HOME_VIEWS = Object.freeze({
    KNOWLEDGE: 'knowledge',
    SKILL_MANAGEMENT: 'skill-management',
    MCP_MANAGEMENT: 'mcp-management',
    MODEL_LIBRARY: 'model-library',
    DATABASE: 'database',
    API_LIBRARY: 'api-library',
    TOOL_LIBRARY: 'tool-library',
    SYSTEM: 'system',
});

export const ADMIN_SYSTEM_SUB_VIEWS = Object.freeze({
    USER_MANAGEMENT: 'user-management',
    CONFIG_MANAGEMENT: 'config-management',
});

export const ADMIN_SIDEBAR_ITEMS = Object.freeze([
    {
        id: ADMIN_HOME_VIEWS.KNOWLEDGE,
        label: '知识库',
        icon: 'inventory_2',
        view: ADMIN_HOME_VIEWS.KNOWLEDGE,
        path: ROUTE_PATHS.adminKnowledge,
    },
    {
        id: ADMIN_HOME_VIEWS.SKILL_MANAGEMENT,
        label: '技能管理',
        icon: 'smart_toy',
        view: ADMIN_HOME_VIEWS.SKILL_MANAGEMENT,
        path: ROUTE_PATHS.adminSkillManagement,
    },
    {
        id: ADMIN_HOME_VIEWS.MCP_MANAGEMENT,
        label: 'MCP 服务',
        icon: 'hub',
        view: ADMIN_HOME_VIEWS.MCP_MANAGEMENT,
        path: ROUTE_PATHS.adminMcpManagement,
    },
    {
        id: ADMIN_HOME_VIEWS.MODEL_LIBRARY,
        label: '模型库',
        icon: 'deployed_code',
        view: ADMIN_HOME_VIEWS.MODEL_LIBRARY,
        path: ROUTE_PATHS.adminModelLibrary,
    },
    {
        id: ADMIN_HOME_VIEWS.DATABASE,
        label: '数据库',
        icon: 'database',
        view: ADMIN_HOME_VIEWS.DATABASE,
        path: ROUTE_PATHS.adminIntegrationDataSources,
    },
    {
        id: ADMIN_HOME_VIEWS.API_LIBRARY,
        label: 'API 库',
        icon: 'api',
        view: ADMIN_HOME_VIEWS.API_LIBRARY,
        path: ROUTE_PATHS.adminApiLibrary,
    },
    {
        id: ADMIN_HOME_VIEWS.TOOL_LIBRARY,
        label: '工具库',
        icon: 'build_circle',
        view: ADMIN_HOME_VIEWS.TOOL_LIBRARY,
        path: ROUTE_PATHS.adminToolLibrary,
    },
    {
        id: ADMIN_HOME_VIEWS.SYSTEM,
        label: '系统管理',
        icon: 'settings',
        view: ADMIN_HOME_VIEWS.SYSTEM,
        path: ROUTE_PATHS.adminSystemHome,
        children: [
            {
                id: ADMIN_SYSTEM_SUB_VIEWS.USER_MANAGEMENT,
                label: '用户管理',
                subView: ADMIN_SYSTEM_SUB_VIEWS.USER_MANAGEMENT,
                path: ROUTE_PATHS.adminSystemUserManagement,
            },
            {
                id: ADMIN_SYSTEM_SUB_VIEWS.CONFIG_MANAGEMENT,
                label: '配置管理',
                subView: ADMIN_SYSTEM_SUB_VIEWS.CONFIG_MANAGEMENT,
                path: ROUTE_PATHS.adminSystemConfigs,
            },
        ],
    },
]);
