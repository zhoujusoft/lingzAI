import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function getPlatformSettings(onUnauthorized) {
    const { data } = await doRequestJson('/api/systemConfig/platforms/get', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function savePlatformSettings(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/systemConfig/platforms/save', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}
