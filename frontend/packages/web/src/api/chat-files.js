import {
    RequestError,
    requestJson as doRequestJson,
    requestRaw as doRequestRaw,
} from '@lingzhou/core/http/request';

export async function listCurrentUserFileAssets(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.sessionId) {
        search.set('sessionId', String(params.sessionId).trim());
    }
    if (params.fileRole) {
        search.set('fileRole', String(params.fileRole).trim());
    }
    if (params.pageNo != null) {
        search.set('pageNo', String(params.pageNo));
    }
    if (params.pageSize != null) {
        search.set('pageSize', String(params.pageSize));
    }
    const query = search.toString();
    const path = query ? `/api/files/assets?${query}` : '/api/files/assets';
    const { data } = await doRequestJson(path, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || null;
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

export async function downloadCurrentUserFileAsset(fileCode, onUnauthorized) {
    const response = await doRequestRaw(
        `/api/files/assets/${encodeURIComponent(fileCode)}/download`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
            responseType: 'blob',
        }
    );
    if (response.status < 200 || response.status >= 300) {
        throw new RequestError('下载文件失败', {
            status: response.status,
        });
    }
    const disposition =
        response.headers?.['content-disposition'] ||
        response.headers?.get?.('content-disposition') ||
        '';
    const contentType =
        response.headers?.['content-type'] || response.headers?.get?.('content-type') || '';
    return {
        filename: parseFilenameFromDisposition(disposition) || 'download',
        contentType,
        blob: response.data,
    };
}

export async function previewCurrentUserFileAsset(fileCode, onUnauthorized) {
    const response = await doRequestRaw(
        `/api/files/assets/${encodeURIComponent(fileCode)}/preview`,
        {
            method: 'GET',
            auth: true,
            onUnauthorized,
            responseType: 'blob',
        }
    );
    if (response.status < 200 || response.status >= 300) {
        throw new RequestError('预览文件失败', {
            status: response.status,
        });
    }
    const disposition =
        response.headers?.['content-disposition'] ||
        response.headers?.get?.('content-disposition') ||
        '';
    const contentType =
        response.headers?.['content-type'] || response.headers?.get?.('content-type') || '';
    return {
        filename: parseFilenameFromDisposition(disposition) || 'preview',
        contentType,
        blob: response.data,
    };
}
