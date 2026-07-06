import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

/**
 * 分页查询专家技能包列表（兼容旧 Agent 模板接口）
 */
export async function listAgents(params = {}, onUnauthorized) {
    const search = new URLSearchParams();
    if (params.page) search.set('page', params.page);
    if (params.pageSize) search.set('pageSize', params.pageSize);
    if (params.keyword) search.set('keyword', params.keyword);
    const query = search.toString();

    const { data } = await doRequestJson(`/api/system/agents${query ? `?${query}` : ''}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data || { items: [], total: 0, page: 1, pageSize: 10 };
}

/**
 * 获取专家技能包详情
 */
export async function getAgentDetail(agentId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/agents/${agentId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 创建专家技能包
 */
export async function createAgent(payload, onUnauthorized) {
    const { data } = await doRequestJson('/api/system/agents', {
        method: 'POST',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 更新专家技能包
 */
export async function updateAgent(agentId, payload, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/agents/${agentId}`, {
        method: 'PUT',
        body: payload,
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 删除专家技能包
 */
export async function deleteAgent(agentId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/agents/${agentId}`, {
        method: 'DELETE',
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 切换专家技能包启用状态
 */
export async function toggleAgentEnabled(agentId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/agents/${agentId}/toggle-enabled`, {
        method: 'POST',
        auth: true,
        onUnauthorized,
    });
    return data;
}

/**
 * 获取所有启用的专家技能包（用于下拉选择）
 */
export async function listEnabledAgents(onUnauthorized) {
    const { data } = await doRequestJson('/api/system/agents/enabled', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function listEnabledExpertPackages(onUnauthorized) {
    const { data } = await doRequestJson('/api/system/agents/enabled-details', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return Array.isArray(data) ? data : [];
}

export async function getEnabledExpertPackage(packageId, onUnauthorized) {
    const { data } = await doRequestJson(`/api/system/agents/enabled-details/${packageId}`, {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return data;
}
