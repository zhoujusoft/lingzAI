import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

function buildQuery(params = {}) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value == null || value === '') {
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

function normalizeVendor(item) {
    return {
        id: Number(item?.id) || null,
        vendorCode: item?.vendorCode || '',
        vendorName: item?.vendorName || '',
        description: item?.description || '',
        status: item?.status || 'ACTIVE',
        modelCount: Number(item?.modelCount) || 0,
        defaultBaseUrl: item?.defaultBaseUrl || '',
        apiKeyConfigured: Boolean(item?.apiKeyConfigured),
        mode: item?.mode || 'CUSTOMIZABLE',
        createdAt: item?.createdAt || '',
        updatedAt: item?.updatedAt || '',
    };
}

function normalizeModel(item) {
    return {
        id: Number(item?.id) || null,
        modelCode: item?.modelCode || '',
        displayName: item?.displayName || '',
        capabilityType: item?.capabilityType || 'CHAT',
        vendorId: Number(item?.vendorId) || null,
        vendorCode: item?.vendorCode || '',
        vendorName: item?.vendorName || '',
        baseUrl: item?.baseUrl || '',
        effectiveBaseUrl: item?.effectiveBaseUrl || '',
        baseUrlInherited: Boolean(item?.baseUrlInherited),
        modelName: item?.modelName || '',
        status: item?.status || 'ACTIVE',
        apiKeyConfigured: Boolean(item?.apiKeyConfigured),
        apiKeyInherited: Boolean(item?.apiKeyInherited),
        path: item?.path || '',
        protocol: item?.protocol || '',
        temperature: typeof item?.temperature === 'number' ? item.temperature : null,
        maxTokens: Number.isInteger(item?.maxTokens) ? item.maxTokens : null,
        systemPrompt: item?.systemPrompt || '',
        enableThinking:
            typeof item?.enableThinking === 'boolean' ? item.enableThinking : null,
        dimensions: Number.isInteger(item?.dimensions) ? item.dimensions : null,
        timeoutMs: Number.isInteger(item?.timeoutMs) ? item.timeoutMs : null,
        fallbackRrf:
            typeof item?.fallbackRrf === 'boolean' ? item.fallbackRrf : null,
        defaultModel: Boolean(item?.defaultModel),
        builtin: Boolean(item?.builtin),
        createdAt: item?.createdAt || '',
        updatedAt: item?.updatedAt || '',
    };
}

function normalizeDefaultBinding(item) {
    return {
        capabilityType: item?.capabilityType || '',
        modelId: item?.modelId ? Number(item.modelId) : null,
        modelDisplayName: item?.modelDisplayName || '',
        vendorId: item?.vendorId ? Number(item.vendorId) : null,
        vendorName: item?.vendorName || '',
        modelStatus: item?.modelStatus || '',
    };
}

export function listModelLibraryVendors(onUnauthorized) {
    return authedJson('/api/model-library/vendors', { method: 'GET' }, onUnauthorized).then(
        result => (Array.isArray(result) ? result.map(normalizeVendor) : [])
    );
}

export function updateModelLibraryVendor(id, payload, onUnauthorized) {
    return authedJson(
        `/api/model-library/vendors/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    ).then(normalizeVendor);
}

export function validateModelLibraryVendor(id, payload, onUnauthorized) {
    return authedJson(
        `/api/model-library/vendors/${id}/validate`,
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function listModelLibraryModels(params = {}, onUnauthorized) {
    return authedJson(
        `/api/model-library/models${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    ).then(result => (Array.isArray(result) ? result.map(normalizeModel) : []));
}

export function createModelLibraryModel(payload, onUnauthorized) {
    return authedJson(
        '/api/model-library/models',
        { method: 'POST', body: payload },
        onUnauthorized
    ).then(normalizeModel);
}

export function updateModelLibraryModel(id, payload, onUnauthorized) {
    return authedJson(
        `/api/model-library/models/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    ).then(normalizeModel);
}

export function listModelLibraryDefaults(onUnauthorized) {
    return authedJson('/api/model-library/defaults', { method: 'GET' }, onUnauthorized).then(
        result => (Array.isArray(result) ? result.map(normalizeDefaultBinding) : [])
    );
}

export function saveModelLibraryDefaultBinding(capabilityType, payload, onUnauthorized) {
    return authedJson(
        `/api/model-library/defaults/${capabilityType}`,
        { method: 'POST', body: payload },
        onUnauthorized
    ).then(normalizeDefaultBinding);
}
