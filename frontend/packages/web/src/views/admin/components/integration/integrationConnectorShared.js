import { RESOURCE_PERMISSION_UI_OPTIONS } from '@/model/resource-permissions';

export const INTEGRATION_SELECT_BUTTON_CLASS =
    'bg-white shadow-none hover:bg-white focus-visible:bg-white';

export const INTEGRATION_FILTER_SELECT_BUTTON_CLASS =
    'bg-slate-50 shadow-none hover:bg-white focus-visible:bg-white';

export const INTEGRATION_STATUS_OPTIONS = [
    { value: 'ACTIVE', label: '已启用' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'DISABLED', label: '已停用' },
];

export const INTEGRATION_STATUS_FILTER_OPTIONS = [
    { value: '', label: '全部状态' },
    ...INTEGRATION_STATUS_OPTIONS,
];

export const INTEGRATION_PERMISSION_OPTIONS = RESOURCE_PERMISSION_UI_OPTIONS.map(item => ({
    value: item.value,
    label: item.label,
    description: item.description,
}));

export const INTEGRATION_METHOD_OPTIONS = [
    { value: 'GET', label: 'GET' },
    { value: 'POST', label: 'POST' },
    { value: 'PUT', label: 'PUT' },
    { value: 'PATCH', label: 'PATCH' },
    { value: 'DELETE', label: 'DELETE' },
];

export const INTEGRATION_AUTH_METHOD_OPTIONS = [
    { value: 'GET', label: 'GET' },
    { value: 'POST', label: 'POST' },
];

export const INTEGRATION_CONTENT_TYPE_OPTIONS = [
    { value: 'application/json', label: 'JSON' },
    { value: 'application/x-www-form-urlencoded', label: 'x-www-form-urlencoded' },
];

export const INTEGRATION_REQUEST_TABS = [
    { value: 'headers', label: 'header' },
    { value: 'forms', label: 'form' },
    { value: 'body', label: 'body' },
];

export const INTEGRATION_AUTH_STATE_OPTIONS = [
    { value: 1, label: '启用' },
    { value: 0, label: '停用' },
];

export function getIntegrationStatusMeta(status) {
    if (status === 'ACTIVE') {
        return {
            label: '已启用',
            badgeClass: 'bg-emerald-50 text-emerald-600',
        };
    }
    if (status === 'DISABLED') {
        return {
            label: '已停用',
            badgeClass: 'bg-slate-100 text-slate-500',
        };
    }
    return {
        label: '草稿',
        badgeClass: 'bg-amber-50 text-amber-600',
    };
}

export function getIntegrationPublishStatusMeta(status) {
    if (status === 'PUBLISHED') {
        return {
            label: '已发布',
            badgeClass: 'bg-blue-50 text-blue-600',
        };
    }
    return {
        label: '未发布',
        badgeClass: 'bg-slate-100 text-slate-500',
    };
}

export function buildIntegrationInputClass(invalid = false, disabled = false) {
    return [
        'w-full rounded-xl border px-4 py-2.5 text-sm outline-none transition',
        invalid
            ? 'border-rose-300 bg-rose-50 focus:border-rose-400 focus:ring-2 focus:ring-rose-100'
            : 'border-slate-200 bg-white focus:border-primary focus:ring-2 focus:ring-primary/10',
        disabled ? 'cursor-not-allowed bg-slate-100 text-slate-500' : '',
    ];
}
