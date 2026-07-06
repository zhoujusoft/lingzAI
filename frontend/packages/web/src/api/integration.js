import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

function buildQuery(params = {}) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value == null || value === '') {
            return;
        }
        if (Array.isArray(value)) {
            value.forEach(item => {
                if (item != null && item !== '') {
                    search.append(key, String(item));
                }
            });
            return;
        }
        search.set(key, String(value));
    });
    const query = search.toString();
    return query ? `?${query}` : '';
}

async function authedJson(path, options = {}, onUnauthorized) {
    const { data } = await doRequestJson(path, {
        auth: true,
        onUnauthorized,
        ...options,
    });
    return data;
}

export function listIntegrationDataSources(params = {}, onUnauthorized) {
    return authedJson(
        `/api/integration/data-sources${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listIntegrationConnectors(params = {}, onUnauthorized) {
    return listIntegrationConnectorPage(
        { page: 1, pageSize: 1000, ...params },
        onUnauthorized
    ).then(data => (Array.isArray(data?.list) ? data.list : []));
}

export function listIntegrationConnectorPage(params = {}, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function getIntegrationConnector(id, onUnauthorized) {
    return authedJson(`/api/integration/connectors/${id}`, { method: 'GET' }, onUnauthorized);
}

export function createIntegrationConnector(payload, onUnauthorized) {
    return authedJson(
        '/api/integration/connectors',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function updateIntegrationConnector(id, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function deleteIntegrationConnector(id, onUnauthorized) {
    return authedJson(`/api/integration/connectors/${id}`, { method: 'DELETE' }, onUnauthorized);
}

export function testIntegrationConnectorAuth(connectorId, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/${connectorId}/auth/test`,
        { method: 'POST', body: payload || {} },
        onUnauthorized
    );
}

export function listIntegrationConnectorApis(connectorId, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/${connectorId}/apis`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function previewIntegrationConnectorCode(connectorName, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/code-preview${buildQuery({ connectorName })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function previewIntegrationConnectorApiCode(connectorId, apiName, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/code-preview${buildQuery({ connectorId, apiName })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function getIntegrationConnectorApi(apiId, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function createIntegrationConnectorApi(connectorId, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/${connectorId}/apis`,
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function updateIntegrationConnectorApi(apiId, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function deleteIntegrationConnectorApi(apiId, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}`,
        { method: 'DELETE' },
        onUnauthorized
    );
}

export function publishIntegrationConnectorApi(apiId, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}/publish`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function disableIntegrationConnectorApi(apiId, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}/disable`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function testIntegrationConnectorApi(apiId, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/connectors/apis/${apiId}/test`,
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function getIntegrationDataSource(id, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}`, { method: 'GET' }, onUnauthorized);
}

export function createIntegrationDataSource(payload, onUnauthorized) {
    return authedJson(
        '/api/integration/data-sources',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function updateIntegrationDataSource(id, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/data-sources/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function deleteIntegrationDataSource(id, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}`, { method: 'DELETE' }, onUnauthorized);
}

export function testIntegrationDataSource(payload, onUnauthorized) {
    return authedJson(
        '/api/integration/data-sources/test-connection',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function listIntegrationDataSourceObjects(id, onUnauthorized) {
    return authedJson(
        `/api/integration/data-sources/${id}/objects`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listIntegrationDataSourceFields(id, objectCode, onUnauthorized) {
    return authedJson(
        `/api/integration/data-sources/${id}/fields${buildQuery({ objectCode })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listIntegrationDataSourceRelations(id, objectCodes = [], onUnauthorized) {
    return authedJson(
        `/api/integration/data-sources/${id}/relations${buildQuery({ objectCodes })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listIntegrationDatasets(params = {}, onUnauthorized) {
    return authedJson(
        `/api/integration/datasets${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function getIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}`, { method: 'GET' }, onUnauthorized);
}

export function createIntegrationDataset(payload, onUnauthorized) {
    return authedJson(
        '/api/integration/datasets',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function updateIntegrationDataset(id, payload, onUnauthorized) {
    return authedJson(
        `/api/integration/datasets/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function generateIntegrationDatasetDescription(payload, onUnauthorized) {
    return authedJson(
        '/api/integration/datasets/generate-description',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function getIntegrationDatasetPublishStatus(id, onUnauthorized) {
    return authedJson(
        `/api/integration/datasets/${id}/publish-status`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function publishIntegrationDataset(id, onUnauthorized) {
    return authedJson(
        `/api/integration/datasets/${id}/publish`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function disableIntegrationDataset(id, onUnauthorized) {
    return authedJson(
        `/api/integration/datasets/${id}/disable`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function deleteIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}`, { method: 'DELETE' }, onUnauthorized);
}

export function listLowcodeIntegrationPlatforms(onUnauthorized) {
    return authedJson('/api/integration/lowcode/platforms', { method: 'GET' }, onUnauthorized);
}

export function listLowcodeIntegrationApps(platformKey, onUnauthorized) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/apps`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listLowcodeIntegrationObjects(platformKey, appId, onUnauthorized) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/objects${buildQuery({ appId })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listLowcodeIntegrationFields(
    platformKey,
    appId,
    objectCode,
    formCode,
    onUnauthorized
) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/fields${buildQuery({ appId, objectCode, formCode })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listLowcodeIntegrationRelations(
    platformKey,
    appId,
    objectCodes = [],
    onUnauthorized
) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/relations${buildQuery({ appId, objectCodes })}`,
        { method: 'GET' },
        onUnauthorized
    );
}
