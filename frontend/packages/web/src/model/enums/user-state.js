export const USER_STATES = Object.freeze({
    WAIT_ACTIVE: -2,
    UNSPECIFIED: -1,
    INACTIVE: 0,
    ACTIVE: 1,
    QUIT: -3,
});

export const USER_STATE_LABELS = Object.freeze({
    [USER_STATES.INACTIVE]: '已禁用',
    [USER_STATES.ACTIVE]: '已激活',
    [USER_STATES.UNSPECIFIED]: '未指定',
    [USER_STATES.WAIT_ACTIVE]: '待激活',
    [USER_STATES.QUIT]: '已离线',
});

const USER_STATE_STYLES = Object.freeze({
    [USER_STATES.INACTIVE]: {
        dotClass: 'bg-slate-400',
        badgeClass: 'bg-slate-100 text-slate-600',
    },
    [USER_STATES.ACTIVE]: {
        dotClass: 'bg-green-500',
        badgeClass: 'bg-green-50 text-green-600',
    },
    [USER_STATES.UNSPECIFIED]: {
        dotClass: 'bg-slate-300',
        badgeClass: 'bg-slate-100 text-slate-500',
    },
    [USER_STATES.WAIT_ACTIVE]: {
        dotClass: 'bg-amber-500',
        badgeClass: 'bg-amber-50 text-amber-600',
    },
    [USER_STATES.QUIT]: {
        dotClass: 'bg-rose-500',
        badgeClass: 'bg-rose-50 text-rose-600',
    },
});

export function resolveUserStateLabel(state, fallback = '未指定 / 未定义') {
    return USER_STATE_LABELS[state] || fallback;
}

export function resolveUserStateStyle(state) {
    return (
        USER_STATE_STYLES[state] || {
            dotClass: 'bg-slate-300',
            badgeClass: 'bg-slate-100 text-slate-500',
        }
    );
}
