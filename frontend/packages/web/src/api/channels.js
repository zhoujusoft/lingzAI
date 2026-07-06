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

export function listChannelConfigs(onUnauthorized) {
    return authedJson('/api/channel/configs', { method: 'GET' }, onUnauthorized);
}

export function listMyChannelBindings(onUnauthorized) {
    return authedJson('/api/channel/configs/bindings/me', { method: 'GET' }, onUnauthorized);
}

export function createChannelConfig(payload, onUnauthorized) {
    return authedJson('/api/channel/configs', { method: 'POST', body: payload }, onUnauthorized);
}

export function updateChannelConfig(id, payload, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function startChannel(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/start`, { method: 'POST' }, onUnauthorized);
}

export function stopChannel(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/stop`, { method: 'POST' }, onUnauthorized);
}

export function restartChannel(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/restart`, { method: 'POST' }, onUnauthorized);
}

export function beginWechatLogin(id, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/weixin/login`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function getWechatLoginStatus(id, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/weixin/status`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function getWecomRuntimeStatus(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/wecom/status`, { method: 'GET' }, onUnauthorized);
}

export function beginDingtalkRegister(id, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/dingtalk/register/begin`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function getDingtalkRegisterStatus(id, sessionId, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/dingtalk/register/status?session=${encodeURIComponent(sessionId)}`,
        { method: 'GET' },
        onUnauthorized
    );
}

export function saveMyWecomBinding(id, payload, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/wecom/binding/me`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function getMyChannelBinding(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/binding/me`, { method: 'GET' }, onUnauthorized);
}

export function saveMyChannelBinding(id, payload, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/binding/me`,
        { method: 'PUT', body: payload },
        onUnauthorized
    );
}

export function closeMyChannelBinding(id, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/binding/me`,
        { method: 'DELETE' },
        onUnauthorized
    );
}

export function pollWechatChannel(id, onUnauthorized) {
    return authedJson(`/api/channel/configs/${id}/weixin/poll`, { method: 'POST' }, onUnauthorized);
}

export function sendChannelMessage(id, payload, onUnauthorized) {
    return authedJson(
        `/api/channel/configs/${id}/send`,
        { method: 'POST', body: payload },
        onUnauthorized
    );
}

export function listChannelSessions(params = {}, onUnauthorized) {
    return authedJson(
        `/api/channel/sessions${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    );
}
