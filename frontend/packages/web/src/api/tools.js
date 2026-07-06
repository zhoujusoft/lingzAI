import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

/**
 * 获取工具目录列表
 * @param {Object} params - 查询参数
 * @param {Function} onUnauthorized - 未授权回调
 * @returns {Promise<Array>} 工具目录列表
 */
export async function listToolCatalog(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.visibleOnly) {
        search.set('visibleOnly', 'true');
    }
    const query = search.toString();
    const { data } = await doRequestJson(`/api/skills/tools${query ? `?${query}` : ''}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}
