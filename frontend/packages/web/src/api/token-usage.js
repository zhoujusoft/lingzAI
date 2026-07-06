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

function normalizeBreakdown(item) {
    return {
        key: item?.key || '',
        label: item?.label || '',
        subtitle: item?.subtitle || '',
        runCount: Number(item?.runCount) || 0,
        totalTokens: Number(item?.totalTokens) || 0,
        promptTokens: Number(item?.promptTokens) || 0,
        completionTokens: Number(item?.completionTokens) || 0,
        llmCallCount: Number(item?.llmCallCount) || 0,
        toolCallCount: Number(item?.toolCallCount) || 0,
        avgTokensPerRun: Number(item?.avgTokensPerRun) || 0,
        shareRatio: typeof item?.shareRatio === 'number' ? item.shareRatio : 0,
    };
}

function resolveSessionTypeLabel(value) {
    const mapping = {
        GENERAL_CHAT: '通用对话',
        SKILL_CHAT: '技能对话',
        PUBLISHED_SKILL_CHAT: '已发布技能',
        DATASET_CHAT: '数据集对话',
        KNOWLEDGE_QA: '知识库问答',
        SKILL_STUDIO_PROJECT_CHAT: '工坊项目对话',
        SKILL_STUDIO_PROJECT_PREVIEW_CHAT: '工坊项目试运行',
        CHANNEL_CHAT: '渠道对话',
    };
    return mapping[value] || value || '--';
}

function resolveRunSceneLabel(item) {
    const sessionType = item?.sessionType || '';
    if (sessionType === 'SKILL_STUDIO_PROJECT_CHAT') {
        return '技能工坊创建';
    }
    if (sessionType === 'SKILL_STUDIO_PROJECT_PREVIEW_CHAT') {
        return '技能工坊试运行';
    }
    if (sessionType === 'SKILL_CHAT' || sessionType === 'PUBLISHED_SKILL_CHAT') {
        return '用户端技能调用';
    }
    if (sessionType === 'GENERAL_CHAT') {
        return '通用对话';
    }
    if (sessionType === 'DATASET_CHAT') {
        return '数据集对话';
    }
    if (sessionType === 'KNOWLEDGE_QA') {
        return '知识库问答';
    }
    if (sessionType === 'CHANNEL_CHAT') {
        return '渠道对话';
    }
    return sessionType || '未知来源';
}

function normalizeRun(item) {
    return {
        assistantMessageId: Number(item?.assistantMessageId) || null,
        userMessageId: Number(item?.userMessageId) || null,
        sessionId: Number(item?.sessionId) || null,
        sessionCode: item?.sessionCode || '',
        sessionType: item?.sessionType || '',
        scopeType: item?.scopeType || '',
        scopeId: Number(item?.scopeId) || null,
        userId: Number(item?.userId) || null,
        userName: item?.userName || '',
        userCode: item?.userCode || '',
        agentType: item?.agentType || '',
        agentId: Number(item?.agentId) || null,
        agentName: item?.agentName || '',
        runtimeSkillName: item?.runtimeSkillName || '',
        modelId: Number(item?.modelId) || null,
        modelProvider: item?.modelProvider || '',
        modelName: item?.modelName || '',
        adapterType: item?.adapterType || '',
        sessionTypeLabel: resolveSessionTypeLabel(item?.sessionType),
        runSceneLabel: resolveRunSceneLabel(item),
        runStatus: item?.runStatus || '',
        usageAvailable: Boolean(item?.usageAvailable),
        promptTokens: Number(item?.promptTokens) || 0,
        completionTokens: Number(item?.completionTokens) || 0,
        totalTokens: Number(item?.totalTokens) || 0,
        llmCallCount: Number(item?.llmCallCount) || 0,
        toolCallCount: Number(item?.toolCallCount) || 0,
        durationMs: Number(item?.durationMs) || 0,
        startedAt: item?.startedAt || '',
        completedAt: item?.completedAt || '',
    };
}

function normalizeDashboard(data) {
    return {
        range: {
            startDate: data?.range?.startDate || '',
            endDate: data?.range?.endDate || '',
        },
        summary: {
            runCount: Number(data?.summary?.runCount) || 0,
            usageRunCount: Number(data?.summary?.usageRunCount) || 0,
            totalTokens: Number(data?.summary?.totalTokens) || 0,
            promptTokens: Number(data?.summary?.promptTokens) || 0,
            completionTokens: Number(data?.summary?.completionTokens) || 0,
            llmCallCount: Number(data?.summary?.llmCallCount) || 0,
            toolCallCount: Number(data?.summary?.toolCallCount) || 0,
            avgDurationMs: Number(data?.summary?.avgDurationMs) || 0,
            avgTokensPerRun: Number(data?.summary?.avgTokensPerRun) || 0,
            activeUserCount: Number(data?.summary?.activeUserCount) || 0,
            activeAgentCount: Number(data?.summary?.activeAgentCount) || 0,
            usageCoverageRate:
                typeof data?.summary?.usageCoverageRate === 'number'
                    ? data.summary.usageCoverageRate
                    : 0,
        },
        dailyTrend: Array.isArray(data?.dailyTrend)
            ? data.dailyTrend.map(item => ({
                  date: item?.date || '',
                  totalTokens: Number(item?.totalTokens) || 0,
                  promptTokens: Number(item?.promptTokens) || 0,
                  completionTokens: Number(item?.completionTokens) || 0,
                  runCount: Number(item?.runCount) || 0,
              }))
            : [],
        agentBreakdown: Array.isArray(data?.agentBreakdown)
            ? data.agentBreakdown.map(normalizeBreakdown)
            : [],
        userBreakdown: Array.isArray(data?.userBreakdown)
            ? data.userBreakdown.map(normalizeBreakdown)
            : [],
        modelBreakdown: Array.isArray(data?.modelBreakdown)
            ? data.modelBreakdown.map(normalizeBreakdown)
            : [],
        spotlightRuns: Array.isArray(data?.spotlightRuns)
            ? data.spotlightRuns.map(normalizeRun)
            : [],
    };
}

export function getTokenUsageDashboard(params = {}, onUnauthorized) {
    return authedJson(
        `/api/chat/token-usage/dashboard${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    ).then(normalizeDashboard);
}

export function listTokenUsageRuns(params = {}, onUnauthorized) {
    return authedJson(
        `/api/chat/token-usage/runs${buildQuery(params)}`,
        { method: 'GET' },
        onUnauthorized
    ).then(result => ({
        items: Array.isArray(result?.items) ? result.items.map(normalizeRun) : [],
        pageNo: Number(result?.pageNo) || 1,
        pageSize: Number(result?.pageSize) || 10,
        total: Number(result?.total) || 0,
    }));
}
