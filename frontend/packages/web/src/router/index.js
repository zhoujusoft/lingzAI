import { createRouter, createWebHistory } from 'vue-router';
import LoginView from '../views/auth/LoginView.vue';
import FrontLandingLayout from '../views/front/FrontLandingLayout.vue';
import FrontChatPage from '../views/front/pages/FrontChatPage.vue';
import FrontSkillsPage from '../views/front/pages/FrontSkillsPage.vue';
import FrontIntelligentChatPage from '../views/front/pages/FrontIntelligentChatPage.vue';
import FrontIntelligentDatasetChatPage from '../views/front/pages/FrontIntelligentDatasetChatPage.vue';
import AdminLayout from '../views/admin/AdminLayout.vue';
import AdminKnowledgePage from '../views/admin/pages/AdminKnowledgePage.vue';
import AdminMcpManagementPage from '../views/admin/pages/AdminMcpManagementPage.vue';
import AdminMcpServerDetailPage from '../views/admin/pages/AdminMcpServerDetailPage.vue';
import AdminMcpServerFormPage from '../views/admin/pages/AdminMcpServerFormPage.vue';
import AdminModelLibraryPage from '../views/admin/pages/AdminModelLibraryPage.vue';
import AdminSkillManagementPage from '../views/admin/pages/AdminSkillManagementPage.vue';
import AdminLowcodeApiLibraryPage from '../views/admin/pages/AdminLowcodeApiLibraryPage.vue';
import AdminIntegrationDataSourcePage from '../views/admin/pages/AdminIntegrationDataSourcePage.vue';
import AdminIntegrationDatasetPage from '../views/admin/pages/AdminIntegrationDatasetPage.vue';
import AdminIntegrationDatasetFormPage from '../views/admin/pages/AdminIntegrationDatasetFormPage.vue';
import AdminSystemConfigPage from '../views/admin/pages/AdminSystemConfigPage.vue';
import AdminToolLibraryPage from '../views/admin/pages/AdminToolLibraryPage.vue';
import AdminSystemUserPage from '../views/admin/pages/AdminSystemUserPage.vue';
import { isAuthenticated } from '@lingzhou/core/auth';
import { clearUserSession, ensureCurrentUserLoaded } from '../composables/useCurrentUser';
import { ROUTE_PATHS } from './routePaths';
import { USER_TYPES } from '@/model/enums/user-type';

const frontChildren = [
    {
        path: '',
        redirect: { name: 'front-chat' },
    },
    {
        path: 'chat',
        name: 'front-chat',
        component: FrontChatPage,
        meta: { requiresAuth: true, area: 'front' },
    },
    {
        path: 'skills',
        name: 'front-skills',
        component: FrontSkillsPage,
        meta: { requiresAuth: true, area: 'front' },
    },
    {
        path: 'intelligent-chat',
        name: 'front-intelligent-chat',
        component: FrontIntelligentChatPage,
        meta: { requiresAuth: true, area: 'front' },
    },
    {
        path: 'dataset-chat',
        name: 'front-dataset-chat',
        component: FrontIntelligentDatasetChatPage,
        meta: { requiresAuth: true, area: 'front' },
    },
];

const adminChildren = [
    {
        path: '',
        redirect: { name: 'admin-knowledge' },
    },
    {
        path: 'knowledge',
        name: 'admin-knowledge',
        component: AdminKnowledgePage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'skills',
        name: 'admin-skill-management',
        component: AdminSkillManagementPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'mcp',
        name: 'admin-mcp-management',
        component: AdminMcpManagementPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'model-library',
        name: 'admin-model-library',
        component: AdminModelLibraryPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'mcp/create',
        name: 'admin-mcp-create',
        component: AdminMcpServerFormPage,
        props: {
            mode: 'create',
            serverId: null,
        },
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'mcp/:serverId/edit',
        name: 'admin-mcp-edit',
        component: AdminMcpServerFormPage,
        props: route => ({
            mode: 'edit',
            serverId: Number(route.params.serverId) || null,
        }),
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'mcp/:serverId',
        name: 'admin-mcp-detail',
        component: AdminMcpServerDetailPage,
        props: route => ({
            serverId: Number(route.params.serverId) || null,
        }),
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration',
        redirect: ROUTE_PATHS.adminIntegrationDatasets,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration/data-sources',
        name: 'admin-integration-data-sources',
        component: AdminIntegrationDataSourcePage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration/datasets',
        name: 'admin-integration-datasets',
        component: AdminIntegrationDatasetPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration/datasets/create',
        name: 'admin-integration-dataset-create',
        component: AdminIntegrationDatasetFormPage,
        props: {
            mode: 'create',
            datasetId: null,
        },
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration/datasets/:datasetId/edit',
        name: 'admin-integration-dataset-edit',
        component: AdminIntegrationDatasetFormPage,
        props: route => ({
            mode: 'edit',
            datasetId: Number(route.params.datasetId) || null,
        }),
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'integration/datasets/:datasetId/view',
        name: 'admin-integration-dataset-view',
        component: AdminIntegrationDatasetFormPage,
        props: route => ({
            mode: 'view',
            datasetId: Number(route.params.datasetId) || null,
        }),
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'apis',
        name: 'admin-api-library',
        component: AdminLowcodeApiLibraryPage,
        meta: { requiresAuth: true, area: 'admin' },
    },
    {
        path: 'tools',
        name: 'admin-tool-library',
        component: AdminToolLibraryPage,
        meta: { requiresAuth: true, area: 'admin' },
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
        meta: { requiresAuth: true, area: 'admin', requiresAdmin: true },
    },
    {
        path: 'system/configs',
        name: 'admin-system-configs',
        component: AdminSystemConfigPage,
        meta: { requiresAuth: true, area: 'admin', requiresAdmin: true },
    },
];

const routes = [
    {
        path: ROUTE_PATHS.login,
        name: 'login',
        component: LoginView,
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
    { path: '/:pathMatch(.*)*', redirect: ROUTE_PATHS.frontHome },
];

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes,
});

router.beforeEach(async to => {
    const requiresAuth = to.matched.some(record => record.meta?.requiresAuth);
    const authed = isAuthenticated();

    if (to.path === ROUTE_PATHS.login && authed) {
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

        const requiresAdmin = to.matched.some(record => record.meta?.requiresAdmin);
        if (requiresAdmin && profile?.userType !== USER_TYPES.ADMIN) {
            return { path: ROUTE_PATHS.frontHome };
        }

        const isAdminArea = to.matched.some(record => record.meta?.area === 'admin');
        if (isAdminArea && profile?.userType !== USER_TYPES.ADMIN) {
            return { path: ROUTE_PATHS.frontHome };
        }
    }

    return true;
});

export default router;
