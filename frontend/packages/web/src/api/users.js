import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function listUsers({ page = 1, pageSize = 10 } = {}, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/list', {
        method: 'POST',
        body: {
            page,
            pageSize,
        },
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function createUser(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/create', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateUserProfile(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/updateProfile', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function resetUserPassword(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/resetPassword', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateUserState(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/updateState', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function deleteUser(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/delete', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}
