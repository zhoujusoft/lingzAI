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

export async function getTokenQuotaSettings(onUnauthorized) {
    const { data } = await doRequestJson('/api/systemConfig/tokenQuota/get', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function saveTokenQuotaSettings(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/systemConfig/tokenQuota/save', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getBrandingSettings() {
    const { data } = await doRequestJson('/api/systemConfig/branding/get', {
        method: 'POST',
    });
    return data;
}

export async function saveBrandingSettings(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/systemConfig/branding/save', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function uploadBrandingLogo(file, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await doRequestJson('/api/systemConfig/branding/uploadLogo', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data;
}
