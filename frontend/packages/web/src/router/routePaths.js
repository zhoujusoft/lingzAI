export const ROUTE_PATHS = {
    frontHome: '/',
    frontChat: '/chat',
    frontSkills: '/skills',
    frontIntelligentChat: '/intelligent-chat',
    frontDatasetChat: '/dataset-chat',
    adminHome: '/admin',
    adminKnowledge: '/admin/knowledge',
    adminSkillManagement: '/admin/skills',
    adminMcpManagement: '/admin/mcp',
    adminModelLibrary: '/admin/model-library',
    adminApiLibrary: '/admin/apis',
    adminIntegrationHome: '/admin/integration',
    adminIntegrationDataSources: '/admin/integration/data-sources',
    adminIntegrationDatasets: '/admin/integration/datasets',
    adminIntegrationDatasetCreate: '/admin/integration/datasets/create',
    adminIntegrationDatasetEdit(datasetId) {
        return `/admin/integration/datasets/${datasetId}/edit`;
    },
    adminIntegrationDatasetView(datasetId) {
        return `/admin/integration/datasets/${datasetId}/view`;
    },
    adminMcpManagementCreate: '/admin/mcp/create',
    adminMcpManagementDetail(serverId) {
        return `/admin/mcp/${serverId}`;
    },
    adminMcpManagementEdit(serverId) {
        return `/admin/mcp/${serverId}/edit`;
    },
    adminToolLibrary: '/admin/tools',
    adminSystemHome: '/admin/system',
    adminSystemUserManagement: '/admin/system/users',
    adminSystemConfigs: '/admin/system/configs',
    login: '/login',
};
