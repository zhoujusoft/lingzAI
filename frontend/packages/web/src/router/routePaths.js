export const ROUTE_PATHS = {
    frontHome: '/',
    frontChat: '/chat',
    frontFiles: '/files',
    frontAgentChat: '/agent-chat',
    frontAgentChatV2: '/agent-chat-v2',
    frontAgentConfig: '/agent-config',
    frontProfile: '/profile',
    frontSkills: '/skills',
    frontExpertPackages: '/expert-packages',
    frontExpertPackageChat(packageId) {
        return `/chat?expertPackageId=${packageId}`;
    },
    frontIntelligentChat: '/intelligent-chat',
    frontDatasetChat: '/dataset-chat',
    frontResources: '/resources',
    frontChatbot(appCode) {
        return `/chatbot/${appCode}`;
    },
    adminHome: '/admin',
    adminHomeDashboard: '/admin/dashboard',
    adminKnowledge: '/admin/knowledge',
    adminSkillStudio: '/admin/skillstudio',
    adminSkillStudioProject(projectId, options = {}) {
        const base = `/admin/skillstudio/${projectId}`;
        const search = new URLSearchParams();
        if (options.bootstrap) {
            search.set('bootstrap', '1');
        }
        const query = search.toString();
        return query ? `${base}?${query}` : base;
    },
    adminSkillStudioMirror: '/admin/skillstudio-mirror',
    adminSandboxTest: '/admin/sandbox-test',
    adminSkillManagement: '/admin/skills',
    adminMcpManagement: '/admin/mcp',
    adminChannels: '/admin/channels',
    adminModelLibrary: '/admin/model-library',
    adminTokenUsage: '/admin/token-usage',
    adminApiLibrary: '/admin/apis',
    adminIntegrationHome: '/admin/integration',
    adminIntegrationConnectors: '/admin/integration/connectors',
    adminIntegrationConnectorDetail(connectorId) {
        return `/admin/integration/connectors/${connectorId}`;
    },
    adminIntegrationConnectorAuth(connectorId) {
        return `/admin/integration/connectors/${connectorId}/auth`;
    },
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
    adminSystemRoleManagement: '/admin/system/roles',
    adminSystemAgentManagement: '/admin/system/agents',
    adminSystemExpertPackageCreate: '/admin/system/agents/create',
    adminSystemExpertPackageEdit(packageId) {
        return `/admin/system/agents/${packageId}/edit`;
    },
    adminSystemUserAgentConfig: '/admin/system/user-agent-config',
    adminSystemConfigs: '/admin/system/configs',
    adminSystemTokenQuota: '/admin/system/token-quota',
    adminProfile: '/admin/profile',
    license: '/license',
    login: '/login',
    register: '/register',
};
