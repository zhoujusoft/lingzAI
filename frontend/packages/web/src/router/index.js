import { createRouter, createWebHistory } from 'vue-router';
import LoginView from '../views/auth/LoginView.vue';
import RegisterView from '../views/auth/RegisterView.vue';
import FrontLandingLayout from '../views/front/FrontLandingLayout.vue';
import FrontChatPage from '../views/front/pages/FrontChatPage.vue';
import FrontFilesPage from '../views/front/pages/FrontFilesPage.vue';
import FrontAgentChatPage from '../views/front/pages/FrontAgentChatPage.vue';
import FrontAgentChatV2Page from '../views/front/pages/FrontAgentChatV2Page.vue';
import FrontAgentConfigPage from '../views/front/pages/FrontAgentConfigPage.vue';
import FrontSkillsPage from '../views/front/pages/FrontSkillsPage.vue';
import FrontExpertPackagesPage from '../views/front/pages/FrontExpertPackagesPage.vue';
import FrontIntelligentChatPage from '../views/front/pages/FrontIntelligentChatPage.vue';
import FrontIntelligentDatasetChatPage from '../views/front/pages/FrontIntelligentDatasetChatPage.vue';
import FrontResourcesPage from '../views/front/pages/FrontResourcesPage.vue';
import FrontChatbotPage from '../views/front/pages/FrontChatbotPage.vue';
import UserProfileCenterPage from '../views/shared/pages/UserProfileCenterPage.vue';
import AdminLayout from '../views/admin/AdminLayout.vue';
import AdminDashboardPage from '../views/admin/pages/AdminDashboardPage.vue';
import AdminKnowledgePage from '../views/admin/pages/AdminKnowledgePage.vue';
import AdminSkillStudioPage from '../views/admin/pages/AdminSkillStudioPage.vue';
import AdminSkillStudioVue3MirrorPage from '../views/admin/pages/AdminSkillStudioVue3MirrorPage.vue';
import AdminSandboxTestPage from '../views/admin/pages/AdminSandboxTestPage.vue';
import AdminMcpManagementPage from '../views/admin/pages/AdminMcpManagementPage.vue';
import AdminChannelManagementPage from '../views/admin/pages/AdminChannelManagementPage.vue';
import AdminMcpServerDetailPage from '../views/admin/pages/AdminMcpServerDetailPage.vue';
import AdminMcpServerFormPage from '../views/admin/pages/AdminMcpServerFormPage.vue';
import AdminModelLibraryPage from '../views/admin/pages/AdminModelLibraryPage.vue';
import AdminTokenUsagePage from '../views/admin/pages/AdminTokenUsagePage.vue';
import AdminSkillManagementPage from '../views/admin/pages/AdminSkillManagementPage.vue';
import AdminLowcodeApiLibraryPage from '../views/admin/pages/AdminLowcodeApiLibraryPage.vue';
import AdminIntegrationConnectorPage from '../views/admin/pages/AdminIntegrationConnectorPage.vue';
import AdminIntegrationConnectorDetailPage from '../views/admin/pages/AdminIntegrationConnectorDetailPage.vue';
import AdminIntegrationConnectorAuthPage from '../views/admin/pages/AdminIntegrationConnectorAuthPage.vue';
import AdminIntegrationDataSourcePage from '../views/admin/pages/AdminIntegrationDataSourcePage.vue';
import AdminIntegrationDatasetPage from '../views/admin/pages/AdminIntegrationDatasetPage.vue';
import AdminIntegrationDatasetFormPage from '../views/admin/pages/AdminIntegrationDatasetFormPage.vue';
import AdminSystemConfigPage from '../views/admin/pages/AdminSystemConfigPage.vue';
import AdminUserTokenQuotaPage from '../views/admin/pages/AdminUserTokenQuotaPage.vue';
import AdminToolLibraryPage from '../views/admin/pages/AdminToolLibraryPage.vue';
import AdminSystemUserPage from '../views/admin/pages/AdminSystemUserPage.vue';
import AdminRoleManagementPage from '../views/admin/pages/AdminRoleManagementPage.vue';
import AdminUserAgentConfigPage from '../views/admin/pages/AdminUserAgentConfigPage.vue';
import AdminAgentManagementPage from '../views/admin/pages/AdminAgentManagementPage.vue';
import AdminExpertPackageFormPage from '../views/admin/pages/AdminExpertPackageFormPage.vue';
import LicensePage from '../views/license/LicensePage.vue';
import { isAuthenticated } from '@lingzhou/core/auth';
import { clearUserSession, ensureCurrentUserLoaded } from '../composables/useCurrentUser';
import { ROUTE_PATHS } from './routePaths';
import { hasAnyAdminPermission, hasMenuPermission } from '@/model/admin-menu-permissions';
import { FRONT_HOME_VIEWS, getFrontSidebarItemByView } from '@/model/front-sidebar';

function resolveFrontHomePath(profile) {
    return ROUTE_PATHS.frontChat;
}

function buildFrontRouteMeta(view, extra = {}) {
    const item = getFrontSidebarItemByView(view);
    return {
        requiresAuth: true,
        area: 'front',
        shell: 'front-workbench',
        pageTitle: item?.pageTitle || item?.label || '',
        pageDescription: item?.pageDescription || '',
        railLabel: item?.shortLabel || '',
        showPageHeader: Boolean(item?.showPageHeader),
        ...extra,
    };
}

const ADMIN_PERMISSION_FALLBACK_ROUTES = Object.freeze([
    { path: ROUTE_PATHS.adminHomeDashboard, permissionKey: null },
    { path: ROUTE_PATHS.adminKnowledge, permissionKey: 'admin.knowledge.view' },
    { path: ROUTE_PATHS.adminSkillStudio, permissionKey: 'admin.skillstudio.view' },
    { path: ROUTE_PATHS.adminSandboxTest, permissionKey: 'admin.sandbox-test.view' },
    { path: ROUTE_PATHS.adminSkillManagement, permissionKey: 'admin.skill-management.view' },
    { path: ROUTE_PATHS.adminMcpManagement, permissionKey: 'admin.mcp-management.view' },
    { path: ROUTE_PATHS.adminChannels, permissionKey: 'admin.channel-management.view' },
    { path: ROUTE_PATHS.adminModelLibrary, permissionKey: 'admin.model-library.view' },
    { path: ROUTE_PATHS.adminTokenUsage, permissionKey: 'admin.token-usage.view' },
    {
        path: ROUTE_PATHS.adminIntegrationConnectors,
        permissionKey: 'admin.integration.connectors.view',
    },
    {
        path: ROUTE_PATHS.adminIntegrationDataSources,
        permissionKey: 'admin.integration.data-sources.view',
    },
    {
        path: ROUTE_PATHS.adminIntegrationDatasets,
        permissionKey: 'admin.integration.datasets.view',
    },
    { path: ROUTE_PATHS.adminApiLibrary, permissionKey: 'admin.api-library.view' },
    { path: ROUTE_PATHS.adminToolLibrary, permissionKey: 'admin.tool-library.view' },
    { path: ROUTE_PATHS.adminSystemAgentManagement, permissionKey: 'admin.system.agents.view' },
    { path: ROUTE_PATHS.adminSystemRoleManagement, permissionKey: 'admin.system.roles.view' },
    { path: ROUTE_PATHS.adminSystemUserManagement, permissionKey: 'admin.system.users.view' },
    { path: ROUTE_PATHS.adminSystemTokenQuota, permissionKey: 'admin.system.token-quota.view' },
    { path: ROUTE_PATHS.adminSystemConfigs, permissionKey: 'admin.system.configs.view' },
    {
        path: ROUTE_PATHS.adminSystemUserAgentConfig,
        permissionKey: 'admin.system.user-agent-config.view',
    },
]);

const frontChildren = [
    {
        path: 'agent-config',
        name: 'front-agent-config',
        component: FrontAgentConfigPage,
        meta: {
            requiresAuth: true,
            area: 'front',
            shell: 'front-workbench',
            pageTitle: '个人助手',
            pageDescription: '维护你的个人助手配置、记忆与技能绑定',
            showPageHeader: false,
            contentInset: true,
        },
    },
    {
        path: '',
        name: 'front-home',
        component: FrontChatPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.CHAT, {
            fullBleedContent: true,
        }),
    },
    {
        path: 'chat',
        name: 'front-chat',
        component: FrontChatPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.CHAT, {
            fullBleedContent: true,
        }),
    },
    {
        path: 'files',
        name: 'front-files',
        component: FrontFilesPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.FILES, {
            contentInset: true,
        }),
    },
    {
        path: 'skills',
        name: 'front-skills',
        component: FrontSkillsPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.SKILLS, {
            contentInset: true,
        }),
    },
    {
        path: 'expert-packages',
        name: 'front-expert-packages',
        component: FrontExpertPackagesPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.EXPERT_PACKAGES, {
            contentInset: true,
        }),
    },
    {
        path: 'expert-packages/:packageId/chat',
        name: 'front-expert-package-chat',
        redirect: route => ({
            path: ROUTE_PATHS.frontChat,
            query: { expertPackageId: route.params.packageId },
        }),
    },
    {
        path: 'intelligent-chat',
        name: 'front-intelligent-chat',
        component: FrontIntelligentChatPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.INTELLIGENT_CHAT, {
            fullBleedContent: true,
        }),
    },
    {
        path: 'dataset-chat',
        name: 'front-dataset-chat',
        component: FrontIntelligentDatasetChatPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.DATASET_CHAT, {
            fullBleedContent: true,
        }),
    },
    {
        path: 'resources',
        name: 'front-resources',
        component: FrontResourcesPage,
        meta: buildFrontRouteMeta(FRONT_HOME_VIEWS.RESOURCES, {
            contentInset: true,
        }),
    },
    {
        path: 'profile',
        name: 'front-profile',
        component: UserProfileCenterPage,
        meta: {
            requiresAuth: true,
            area: 'front',
            shell: 'front-workbench',
            pageTitle: '个人中心',
            pageDescription: '查看个人资料、账户信息与账户偏好',
            showPageHeader: false,
            contentInset: true,
        },
    },
];

const adminChildren = [
    {
        path: '',
        redirect: { name: 'admin-dashboard' },
    },
    {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: AdminDashboardPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'knowledge',
        name: 'admin-knowledge',
        component: AdminKnowledgePage,
        meta: { requiresAuth: true, area: 'admin', requiredPermission: 'admin.knowledge.view' },
    },
    {
        path: 'skillstudio',
        name: 'admin-skill-studio',
        component: AdminSkillStudioPage,
        meta: { requiresAuth: true, area: 'admin', requiredPermission: 'admin.skillstudio.view' },
    },
    {
        path: 'skills',
        name: 'admin-skill-management',
        component: AdminSkillManagementPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.skill-management.view',
        },
    },
    {
        path: 'mcp',
        name: 'admin-mcp-management',
        component: AdminMcpManagementPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.mcp-management.view',
        },
    },
    {
        path: 'channels',
        name: 'admin-channel-management',
        component: AdminChannelManagementPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.channel-management.view',
        },
    },
    {
        path: 'model-library',
        name: 'admin-model-library',
        component: AdminModelLibraryPage,
        meta: { requiresAuth: true, area: 'admin', requiredPermission: 'admin.model-library.view' },
    },
    {
        path: 'token-usage',
        name: 'admin-token-usage',
        component: AdminTokenUsagePage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.token-usage.view',
        },
    },
    {
        path: 'mcp/create',
        name: 'admin-mcp-create',
        component: AdminMcpServerFormPage,
        props: {
            mode: 'create',
            serverId: null,
        },
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.mcp-management.view',
        },
    },
    {
        path: 'mcp/:serverId/edit',
        name: 'admin-mcp-edit',
        component: AdminMcpServerFormPage,
        props: route => ({
            mode: 'edit',
            serverId: Number(route.params.serverId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.mcp-management.view',
        },
    },
    {
        path: 'mcp/:serverId',
        name: 'admin-mcp-detail',
        component: AdminMcpServerDetailPage,
        props: route => ({
            serverId: Number(route.params.serverId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.mcp-management.view',
        },
    },
    {
        path: 'integration',
        redirect: ROUTE_PATHS.adminIntegrationConnectors,
        meta: {
            requiresAuth: true,
            area: 'admin',
        },
    },
    {
        path: 'integration/connectors',
        name: 'admin-integration-connectors',
        component: AdminIntegrationConnectorPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.connectors.view',
        },
    },
    {
        path: 'integration/connectors/:connectorId',
        name: 'admin-integration-connector-detail',
        component: AdminIntegrationConnectorDetailPage,
        props: route => ({
            connectorId: Number(route.params.connectorId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.connectors.view',
        },
    },
    {
        path: 'integration/connectors/:connectorId/auth',
        name: 'admin-integration-connector-auth',
        component: AdminIntegrationConnectorAuthPage,
        props: route => ({
            connectorId: Number(route.params.connectorId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.connectors.view',
        },
    },
    {
        path: 'integration/data-sources',
        name: 'admin-integration-data-sources',
        component: AdminIntegrationDataSourcePage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.data-sources.view',
        },
    },
    {
        path: 'integration/datasets',
        name: 'admin-integration-datasets',
        component: AdminIntegrationDatasetPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.datasets.view',
        },
    },
    {
        path: 'integration/datasets/create',
        name: 'admin-integration-dataset-create',
        component: AdminIntegrationDatasetFormPage,
        props: {
            mode: 'create',
            datasetId: null,
        },
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.datasets.view',
        },
    },
    {
        path: 'integration/datasets/:datasetId/edit',
        name: 'admin-integration-dataset-edit',
        component: AdminIntegrationDatasetFormPage,
        props: route => ({
            mode: 'edit',
            datasetId: Number(route.params.datasetId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.datasets.view',
        },
    },
    {
        path: 'integration/datasets/:datasetId/view',
        name: 'admin-integration-dataset-view',
        component: AdminIntegrationDatasetFormPage,
        props: route => ({
            mode: 'view',
            datasetId: Number(route.params.datasetId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiredPermission: 'admin.integration.datasets.view',
        },
    },
    {
        path: 'apis',
        name: 'admin-api-library',
        component: AdminLowcodeApiLibraryPage,
        meta: { requiresAuth: true, area: 'admin', requiredPermission: 'admin.api-library.view' },
    },
    {
        path: 'tools',
        name: 'admin-tool-library',
        component: AdminToolLibraryPage,
        meta: { requiresAuth: true, area: 'admin', requiredPermission: 'admin.tool-library.view' },
    },
    {
        path: 'system',
        redirect: ROUTE_PATHS.adminSystemUserManagement,
        meta: { requiresAuth: true, area: 'admin', requiresAdmin: true },
    },
    {
        path: 'system/users',
        name: 'admin-system-users',
        component: AdminSystemUserPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.users.view',
        },
    },
    {
        path: 'system/roles',
        name: 'admin-system-roles',
        component: AdminRoleManagementPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.roles.view',
        },
    },
    {
        path: 'system/user-agent-config',
        name: 'admin-system-user-agent-config',
        component: AdminUserAgentConfigPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.user-agent-config.view',
        },
    },
    {
        path: 'system/agents/create',
        name: 'admin-system-expert-package-create',
        component: AdminExpertPackageFormPage,
        props: {
            mode: 'create',
            packageId: null,
        },
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.agents.view',
        },
    },
    {
        path: 'system/agents/:packageId/edit',
        name: 'admin-system-expert-package-edit',
        component: AdminExpertPackageFormPage,
        props: route => ({
            mode: 'edit',
            packageId: Number(route.params.packageId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.agents.view',
        },
    },
    {
        path: 'system/agents',
        name: 'admin-system-agents',
        component: AdminAgentManagementPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.agents.view',
        },
    },
    {
        path: 'system/configs',
        name: 'admin-system-configs',
        component: AdminSystemConfigPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.configs.view',
        },
    },
    {
        path: 'system/token-quota',
        name: 'admin-system-token-quota',
        component: AdminUserTokenQuotaPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.system.token-quota.view',
        },
    },
    {
        path: 'profile',
        name: 'admin-profile',
        component: UserProfileCenterPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
];

const routes = [
    {
        path: ROUTE_PATHS.login,
        name: 'login',
        component: LoginView,
    },
    {
        path: ROUTE_PATHS.register,
        name: 'register',
        component: RegisterView,
    },
    {
        path: ROUTE_PATHS.license,
        name: 'license',
        component: LicensePage,
        meta: { requiresAuth: true },
    },
    {
        path: ROUTE_PATHS.frontAgentChat,
        name: 'front-agent-chat',
        component: FrontAgentChatPage,
        meta: { requiresAuth: true, area: 'front' },
    },
    {
        path: ROUTE_PATHS.frontAgentChatV2,
        name: 'front-agent-chat-v2',
        component: FrontAgentChatV2Page,
        meta: { requiresAuth: true, area: 'front' },
    },
    {
        path: ROUTE_PATHS.frontAgentConfig,
        redirect: { name: 'front-agent-config' },
    },
    {
        path: ROUTE_PATHS.frontHome,
        component: FrontLandingLayout,
        meta: { requiresAuth: true, area: 'front' },
        children: frontChildren,
    },
    {
        path: ROUTE_PATHS.adminHome,
        component: AdminLayout,
        meta: { requiresAuth: true, area: 'admin', requiresAdmin: true },
        children: adminChildren,
    },
    {
        path: '/admin/skillstudio/:projectId',
        name: 'admin-skill-studio-project',
        component: AdminSkillStudioVue3MirrorPage,
        props: route => ({
            projectId: Number(route.params.projectId) || null,
        }),
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.skillstudio.view',
        },
    },
    {
        path: ROUTE_PATHS.adminSkillStudioMirror,
        name: 'admin-skill-studio-mirror',
        component: AdminSkillStudioVue3MirrorPage,
        props: {
            projectId: 1,
        },
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.skillstudio.view',
        },
    },
    {
        path: ROUTE_PATHS.adminSandboxTest,
        name: 'admin-sandbox-test',
        component: AdminSandboxTestPage,
        meta: {
            requiresAuth: true,
            area: 'admin',
            requiresAdmin: true,
            requiredPermission: 'admin.sandbox-test.view',
        },
    },
    {
        path: '/chatbot/:appCode',
        name: 'front-chatbot',
        component: FrontChatbotPage,
    },
    { path: '/:pathMatch(.*)*', redirect: ROUTE_PATHS.frontHome },
];

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes,
});

function resolveFirstAllowedAdminPath(profile) {
    for (const item of ADMIN_PERMISSION_FALLBACK_ROUTES) {
        if (hasMenuPermission(profile, item.permissionKey)) {
            return item.path;
        }
    }
    return ROUTE_PATHS.frontHome;
}

router.beforeEach(async to => {
    const requiresAuth = to.matched.some(record => record.meta?.requiresAuth);
    const authed = isAuthenticated();

    if ((to.path === ROUTE_PATHS.login || to.path === ROUTE_PATHS.register) && authed) {
        return { path: ROUTE_PATHS.frontHome };
    }

    if (requiresAuth && !authed) {
        return { path: ROUTE_PATHS.login, query: { redirect: to.fullPath } };
    }

    if (requiresAuth) {
        let profile = null;
        try {
            profile = await ensureCurrentUserLoaded({
                onUnauthorized: () => clearUserSession(),
            });
        } catch (error) {
            // keep route navigation stable; auth failures are handled below
        }

        if (!isAuthenticated()) {
            return { path: ROUTE_PATHS.login, query: { redirect: to.fullPath } };
        }

        const isAdminArea = to.matched.some(record => record.meta?.area === 'admin');
        if (isAdminArea) {
            if (!hasAnyAdminPermission(profile)) {
                return { path: ROUTE_PATHS.frontHome };
            }
            const requiredPermissions = to.matched
                .map(record => record.meta?.requiredPermission)
                .filter(Boolean);
            const hasAllPermissions = requiredPermissions.every(permissionKey =>
                hasMenuPermission(profile, permissionKey)
            );
            if (!hasAllPermissions) {
                const fallbackPath = resolveFirstAllowedAdminPath(profile);
                if (fallbackPath === to.path) {
                    return { path: ROUTE_PATHS.frontHome };
                }
                return { path: fallbackPath };
            }
        }

        if (to.name === 'front-home') {
            return { path: resolveFrontHomePath(profile) };
        }
    }

    return true;
});

export default router;
