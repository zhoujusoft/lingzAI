import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

/**
 * 获取运行时文件资产列表
 * @param {Object} params
 * @param {string} [params.sessionId] - 会话ID
 * @param {string} [params.fileRole] - 文件角色: UPLOAD, ARTIFACT, TEMP
 * @param {number} [params.pageNo] - 页码
 * @param {number} [params.pageSize] - 每页大小
 * @returns {Promise<{items: Array, current: number, size: number, total: number, pages: number}>}
 */
export async function listRuntimeFileAssets(params = {}) {
    const search = new URLSearchParams();
    if (params.sessionId) {
        search.set('sessionId', params.sessionId);
    }
    if (params.fileRole) {
        search.set('fileRole', params.fileRole);
    }
    if (params.pageNo) {
        search.set('pageNo', String(params.pageNo));
    }
    if (params.pageSize) {
        search.set('pageSize', String(params.pageSize));
    }

    const query = search.toString();
    const { data } = await doRequestJson(`/api/files/assets${query ? `?${query}` : ''}`, {
        method: 'GET',
        auth: true,
    });
    return data;
}

/**
 * 获取文件预览URL
 * @param {string} fileCode - 文件编码
 * @returns {string} 预览URL
 */
export function getFilePreviewUrl(fileCode) {
    return `/api/files/assets/${fileCode}/preview`;
}

/**
 * 获取文件下载URL
 * @param {string} fileCode - 文件编码
 * @returns {string} 下载URL
 */
export function getFileDownloadUrl(fileCode) {
    return `/api/files/assets/${fileCode}/download`;
}
