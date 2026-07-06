import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

async function authedJson(path, options = {}, onUnauthorized) {
    const { data } = await doRequestJson(path, {
        auth: true,
        onUnauthorized,
        ...options,
    });
    return data;
}

export function startSandboxTest(payload = {}, onUnauthorized) {
    return authedJson('/api/sandbox-test/start', { method: 'POST', body: payload }, onUnauthorized);
}

export function getSandboxTestInfo(sessionId, onUnauthorized) {
    return authedJson(`/api/sandbox-test/${sessionId}`, { method: 'GET' }, onUnauthorized);
}

export function openSandboxTestBaidu(sessionId, onUnauthorized) {
    return authedJson(
        `/api/sandbox-test/${sessionId}/open-baidu`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function navigateSandboxTest(sessionId, url, onUnauthorized) {
    return authedJson(
        `/api/sandbox-test/${sessionId}/navigate`,
        { method: 'POST', body: { url } },
        onUnauthorized
    );
}

export function takeSandboxTestScreenshot(sessionId, onUnauthorized) {
    return authedJson(
        `/api/sandbox-test/${sessionId}/screenshot`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function snapshotSandboxTest(sessionId, onUnauthorized) {
    return authedJson(
        `/api/sandbox-test/${sessionId}/snapshot`,
        { method: 'POST' },
        onUnauthorized
    );
}

export function stopSandboxTest(sessionId, onUnauthorized) {
    return authedJson(`/api/sandbox-test/${sessionId}/stop`, { method: 'POST' }, onUnauthorized);
}
