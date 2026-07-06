import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';

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

export function listSkillStudioProjects(params = {}, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function createSkillStudioProject(payload, onUnauthorized) {
    return authedJson(
        '/api/skillstudio/projects',
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function getSkillStudioProject(projectId, onUnauthorized) {
    return authedJson(`/api/skillstudio/projects/${projectId}`, { method: 'GET' }, onUnauthorized);
}

export function updateSkillStudioProject(projectId, payload, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function getSkillStudioProjectSettings(projectId, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/settings`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function updateSkillStudioProjectSettings(projectId, payload, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/settings`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function publishSkillStudioProject(projectId, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/publish`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function deleteSkillStudioProject(projectId, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}`,
        { method: 'DELETE' },
        onUnauthorized
    );
}

export function listSkillStudioProjectSessions(projectId, params = {}, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/sessions${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listSkillStudioProjectMessages(projectId, sessionId, params = {}, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/sessions/${encodeURIComponent(String(sessionId || '').trim())}/messages${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function listSkillStudioProjectFiles(projectId, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/files`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function getSkillStudioProjectFileContent(projectId, path, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/files/content${buildQuery({ path })}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function sendSkillStudioProjectMessage(projectId, payload, onUnauthorized) {
    return authedJson(
        `/api/skillstudio/projects/${projectId}/chat`,
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function streamSkillStudioProjectMessage(projectId, payload, onUnauthorized) {
    return doRequestRaw(`/api/skillstudio/projects/${projectId}/chat/stream`, {
        method: 'POST',
        responseType: 'stream',
        auth: true,
        onUnauthorized,
        body: payload,
    });
}

export function streamSkillStudioProjectPreviewRun(projectId, payload, onUnauthorized) {
    return doRequestRaw(`/api/skillstudio/projects/${projectId}/preview/run/stream`, {
        method: 'POST',
        responseType: 'stream',
        auth: true,
        onUnauthorized,
        body: payload,
    });
}
