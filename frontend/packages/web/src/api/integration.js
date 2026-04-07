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
    return authedJson(`/api/integration/data-sources${buildQuery(params)}`, { method: 'GET' }, onUnauthorized);
}

export function getIntegrationDataSource(id, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}`, { method: 'GET' }, onUnauthorized);
}

export function createIntegrationDataSource(payload, onUnauthorized) {
    return authedJson('/api/integration/data-sources', { method: 'POST', body: payload }, onUnauthorized);
}

export function updateIntegrationDataSource(id, payload, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}`, { method: 'PUT', body: payload }, onUnauthorized);
}

export function deleteIntegrationDataSource(id, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}`, { method: 'DELETE' }, onUnauthorized);
}

export function testIntegrationDataSource(payload, onUnauthorized) {
    return authedJson('/api/integration/data-sources/test-connection', { method: 'POST', body: payload }, onUnauthorized);
}

export function listIntegrationDataSourceObjects(id, onUnauthorized) {
    return authedJson(`/api/integration/data-sources/${id}/objects`, { method: 'GET' }, onUnauthorized);
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
    return authedJson(`/api/integration/datasets${buildQuery(params)}`, { method: 'GET' }, onUnauthorized);
}

export function getIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}`, { method: 'GET' }, onUnauthorized);
}

export function createIntegrationDataset(payload, onUnauthorized) {
    return authedJson('/api/integration/datasets', { method: 'POST', body: payload }, onUnauthorized);
}

export function updateIntegrationDataset(id, payload, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}`, { method: 'PUT', body: payload }, onUnauthorized);
}

export function generateIntegrationDatasetDescription(payload, onUnauthorized) {
    return authedJson('/api/integration/datasets/generate-description', { method: 'POST', body: payload }, onUnauthorized);
}

export function getIntegrationDatasetPublishStatus(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}/publish-status`, { method: 'GET' }, onUnauthorized);
}

export function publishIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}/publish`, { method: 'POST' }, onUnauthorized);
}

export function disableIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}/disable`, { method: 'POST' }, onUnauthorized);
}

export function deleteIntegrationDataset(id, onUnauthorized) {
    return authedJson(`/api/integration/datasets/${id}`, { method: 'DELETE' }, onUnauthorized);
}

export function listLowcodeIntegrationPlatforms(onUnauthorized) {
    return authedJson('/api/integration/lowcode/platforms', { method: 'GET' }, onUnauthorized);
}

export function listLowcodeIntegrationApps(platformKey, onUnauthorized) {
    return authedJson(`/api/integration/lowcode/platforms/${platformKey}/apps`, { method: 'GET' }, onUnauthorized);
}

export function listLowcodeIntegrationObjects(platformKey, appId, onUnauthorized) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/objects${buildQuery({ appId })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listLowcodeIntegrationFields(platformKey, appId, objectCode, onUnauthorized) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/fields${buildQuery({ appId, objectCode })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listLowcodeIntegrationRelations(platformKey, appId, objectCodes = [], onUnauthorized) {
    return authedJson(
        `/api/integration/lowcode/platforms/${platformKey}/relations${buildQuery({ appId, objectCodes })}`,
        { method: 'GET' },
        onUnauthorized
    );
}
