export const ADMIN_SELECT_BUTTON_CLASS =
    'bg-slate-50 shadow-none hover:bg-white focus-visible:bg-white';

export const MCP_SERVER_SCOPE_OPTIONS = [
    { value: 'INTERNAL', label: '内部 MCP' },
    { value: 'EXTERNAL', label: '外部 MCP' },
];

export const MCP_TRANSPORT_OPTIONS = [
    { value: 'STREAMABLE_HTTP', label: 'Streamable HTTP' },
    { value: 'SSE', label: 'SSE' },
];

export const MCP_AUTH_OPTIONS = [
    { value: 'NONE', label: '无鉴权' },
    { value: 'BEARER_TOKEN', label: 'Bearer Token' },
];

export function getMcpRefreshStatusMeta(status) {
    if (status === 'SUCCESS') {
        return {
            label: '已刷新',
            badgeClass: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
            dotClass: 'bg-emerald-500',
        };
    }
    if (status === 'FAILED') {
        return {
            label: '刷新失败',
            badgeClass: 'bg-rose-50 text-rose-700 ring-1 ring-rose-200',
            dotClass: 'bg-rose-500',
        };
    }
    return {
        label: '未刷新',
        badgeClass: 'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
        dotClass: 'bg-slate-400',
    };
}

export function getMcpTransportLabel(transportType) {
    if (transportType === 'SSE') {
        return 'SSE';
    }
    if (transportType === 'STREAMABLE_HTTP') {
        return 'Streamable HTTP';
    }
    return transportType || '未知传输';
}

export function getMcpServerScopeLabel(serverScope) {
    if (serverScope === 'EXTERNAL') {
        return '外部 MCP';
    }
    if (serverScope === 'INTERNAL') {
        return '内部 MCP';
    }
    return serverScope || '内部 MCP';
}

export function getMcpAuthLabel(authType, hasAuthConfig = false) {
    if (authType === 'BEARER_TOKEN') {
        return hasAuthConfig ? 'Bearer Token 已配置' : 'Bearer Token 未配置';
    }
    return '无鉴权';
}

export function formatMcpTime(value) {
    if (!value) {
        return '未记录';
    }
    return value;
}

export function summarizeMcpTools(tools, limit = 4) {
    const list = Array.isArray(tools) ? tools : [];
    return {
        preview: list.slice(0, limit),
        remainingCount: Math.max(0, list.length - limit),
    };
}
