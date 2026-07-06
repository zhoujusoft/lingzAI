import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

export async function getLicenseStatus(onUnauthorized) {
    const { data } = await doRequestJson('/api/license/status', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function getLicenseRequest(onUnauthorized) {
    const { data } = await doRequestJson('/api/license/request', {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

export async function importLicenseFile(file, onUnauthorized) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await doRequestJson('/api/license/import', {
        method: 'POST',
        body: formData,
        auth: true,
        onUnauthorized,
    });
    return data;
}
