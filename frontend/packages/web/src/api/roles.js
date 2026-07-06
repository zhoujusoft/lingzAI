import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function listRoles({ page = 1, pageSize = 10, keyword = '' } = {}, onUnauthorized) {
    const { data } = await doRequestJson('/api/system/roles', {
        method: 'GET',
        query: {
            page,
            pageSize,
            keyword: keyword || undefined,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getRoleDetail(roleId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function createRole(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/system/roles', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateRole(roleId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}`, {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteRole(roleId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function batchBindRoleUsers(roleId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}/users/batch-bind`, {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function listEnabledAgents(onUnauthorized) {
    const { data } = await doRequestJson('/api/system/roles/agents/enabled', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getRoleResources(roleId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}/resources`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateRoleResources(roleId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/roles/${roleId}/resources`, {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}
