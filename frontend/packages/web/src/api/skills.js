import {
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
    RequestError,
} from '@lingzhou/core/http/request';

export async function listSkillCatalogs(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.visibleOnly) {
        search.set('visibleOnly', 'true');
    }
    const query = search.toString();
    const { data } = await doRequestJson(`/api/skills/catalog${query ? `?${query}` : ''}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function listSkillTools(onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/tools', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function listMcpServers(onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/mcp/servers', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getMcpServerDetail(serverId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/mcp/servers/${serverId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function createMcpServer(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/mcp/servers', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateMcpServer(serverId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/mcp/servers/${serverId}`, {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function refreshMcpServer(serverId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/mcp/servers/${serverId}/refresh`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteMcpServer(serverId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/mcp/servers/${serverId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function listLowcodePlatforms(onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/lowcode/catalog/platforms', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function listLowcodeApps(platformKey, onUnauthorized) {
    const { data } = await doRequestJson(
        `/api/skills/lowcode/catalog/platforms/${platformKey}/apps`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return Array.isArray(data) ? data : [];
}

export async function listLowcodeApis(platformKey, appId, onUnauthorized) {
    const search = new URLSearchParams();
    search.set('appId', appId);
    const { data } = await doRequestJson(
        `/api/skills/lowcode/catalog/platforms/${platformKey}/apis?${search.toString()}`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
        }
    );
    return Array.isArray(data) ? data : [];
}

export async function registerLowcodeApi(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/lowcode/catalog/register', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function unregisterLowcodeApi(platformKey, apiCode, onUnauthorized) {
    const search = new URLSearchParams();
    search.set('platformKey', platformKey);
    search.set('apiCode', apiCode);
    const { data } = await doRequestJson(
        `/api/skills/lowcode/catalog/register?${search.toString()}`,
        {
            method: 'DELETE',
            auth: true,
            onUnauthorized,
        }
    );
    return data;
}

export async function testExecuteLowcodeApi(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/lowcode/catalog/test-execute', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateSkillCatalog(skillId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/catalog/${skillId}`, {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateSkillBindings(skillId, toolNames, onUnauthorized) {
    const { data } = await doRequestJson(`/api/skills/catalog/${skillId}/bindings`, {
        method: 'PUT',
        body: {
            toolNames,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

function parseFilenameFromDisposition(disposition = '') {
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
        return decodeURIComponent(utf8Match[1]);
    }
    const plainMatch = disposition.match(/filename=\"?([^\";]+)\"?/i);
    if (plainMatch?.[1]) {
        return plainMatch[1];
    }
    return '';
}

export async function exportSkillPackage(skillId, onUnauthorized) {
    const response = await doRequestRaw(`/api/skills/catalog/${skillId}/package/export`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
        responseType: 'blob',
    });
    if (response.status < 200 || response.status >= 300) {
        throw new RequestError('导出技能包失败', {
            status: response.status,
        });
    }
    const disposition =
        response.headers?.['content-disposition'] ||
        response.headers?.get?.('content-disposition') ||
        '';
    return {
        filename: parseFilenameFromDisposition(disposition) || `skill-package-${skillId}.zip`,
        blob: response.data,
    };
}

export async function previewSkillPackageImport(file, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await doRequestJson('/api/skills/packages/preview', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function confirmSkillPackageImport(file, confirmDowngrade = false, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('confirmDowngrade', confirmDowngrade ? 'true' : 'false');
    const { data } = await doRequestJson('/api/skills/packages/confirm', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function refreshSkillPackages(onUnauthorized) {
    const { data } = await doRequestJson('/api/skills/packages/refresh', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}
