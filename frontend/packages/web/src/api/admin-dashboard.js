import { requestJson as doRequestJson } from '@lingzhou/core/http/request';

function normalizeStatus(item) {
    return {
        key: item?.key || '',
        label: item?.label || '',
        count: Number(item?.count) || 0,
    };
}

function normalizeModule(item) {
    return {
        moduleId: item?.moduleId || '',
        label: item?.label || '',
        total: Number(item?.total) || 0,
        statuses: Array.isArray(item?.statuses) ? item.statuses.map(normalizeStatus) : [],
    };
}

function normalizeNullableNumber(value) {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

function normalizeLicense(item) {
    return {
        enabled: item?.enabled === true,
        status: item?.status || '',
        customerName: item?.customerName || '',
        edition: item?.edition || '',
        expiresAt: item?.expiresAt || '',
        expirationUnlimited: item?.expirationUnlimited === true,
        remainingDays: Number(item?.remainingDays) || 0,
        registeredUsers: Number(item?.registeredUsers) || 0,
        activeUsers: Number(item?.activeUsers) || 0,
        maxActiveUsers: normalizeNullableNumber(item?.maxActiveUsers),
        userUnlimited: item?.userUnlimited === true,
        consumedTokens: Number(item?.consumedTokens) || 0,
        maxTotalTokens: normalizeNullableNumber(item?.maxTotalTokens),
        remainingTokens: normalizeNullableNumber(item?.remainingTokens),
        tokenUnlimited: item?.tokenUnlimited === true,
    };
}

export async function getAdminDashboard(onUnauthorized) {
    const { data } = await doRequestJson('/api/system/dashboard', {
        method: 'GET',
        auth: true,
        onUnauthorized,
    });
    return {
        summary: {
            moduleCount: Number(data?.summary?.moduleCount) || 0,
            resourceCount: Number(data?.summary?.resourceCount) || 0,
            activeCount: Number(data?.summary?.activeCount) || 0,
            largestModuleId: data?.summary?.largestModuleId || '',
            largestModuleLabel: data?.summary?.largestModuleLabel || '',
            largestModuleCount: Number(data?.summary?.largestModuleCount) || 0,
        },
        license: normalizeLicense(data?.license),
        modules: Array.isArray(data?.modules) ? data.modules.map(normalizeModule) : [],
    };
}
