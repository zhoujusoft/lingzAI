import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function listUsers({ page = 1, pageSize = 10, keyword = '' } = {}, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/list', {
        method: 'POST',
        body: {
            page,
            pageSize,
            keyword: keyword || undefined,
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

export async function uploadCurrentUserAvatar(file, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await doRequestJson('/api/user/profile/avatar/upload', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data || null;
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

export async function changeCurrentUserPassword(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/changePassword', {
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

export async function grantUserTokenQuota(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/tokenQuota/grant', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function updateUserTokenQuota(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/user/tokenQuota/update', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}
