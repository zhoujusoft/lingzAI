export const SKILL_ICON_OPTIONS = [
    'grid_view',
    'smart_toy',
    'rocket_launch',
    'inventory_2',
    'dataset',
    'hub',
    'business_center',
    'work',
    'description',
    'article',
    'table_chart',
    'design_services',
    'palette',
    'rule',
    'gavel',
    'policy',
    'account_balance',
    'analytics',
    'travel_explore',
    'fact_check',
    'checklist',
    'assignment',
    'psychology',
    'monitor_heart',
    'medical_services',
    'auto_awesome',
    'dashboard',
];

export const SKILL_ICON_COLOR_OPTIONS = [
    { key: 'blue', label: '深海蓝' },
    { key: 'indigo', label: '靛青' },
    { key: 'emerald', label: '翠绿' },
    { key: 'amber', label: '琥珀' },
    { key: 'slate', label: '石板灰' },
    { key: 'violet', label: '紫藤' },
];

const DEFAULT_SKILL_ICON = 'grid_view';
const DEFAULT_SKILL_ICON_COLOR = 'blue';
const SKILL_ICON_OPTION_SET = new Set(SKILL_ICON_OPTIONS);
const SKILL_ICON_ALIASES = {
    briefcase_business: 'business_center',
};

const SKILL_ICON_COLOR_STYLES = {
    blue: {
        gradientClass: 'bg-gradient-to-br from-blue-500 via-cyan-500 to-sky-400 shadow-blue-500/20',
        swatchClass: 'bg-gradient-to-br from-blue-500 via-cyan-500 to-sky-400',
        accentClass: 'border-blue-200 bg-blue-50 text-blue-700',
    },
    indigo: {
        gradientClass:
            'bg-gradient-to-br from-indigo-500 via-blue-500 to-sky-400 shadow-indigo-500/20',
        swatchClass: 'bg-gradient-to-br from-indigo-500 via-blue-500 to-sky-400',
        accentClass: 'border-indigo-200 bg-indigo-50 text-indigo-700',
    },
    emerald: {
        gradientClass:
            'bg-gradient-to-br from-emerald-500 via-teal-500 to-cyan-400 shadow-emerald-500/20',
        swatchClass: 'bg-gradient-to-br from-emerald-500 via-teal-500 to-cyan-400',
        accentClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    },
    amber: {
        gradientClass:
            'bg-gradient-to-br from-amber-500 via-orange-500 to-rose-400 shadow-amber-500/20',
        swatchClass: 'bg-gradient-to-br from-amber-500 via-orange-500 to-rose-400',
        accentClass: 'border-amber-200 bg-amber-50 text-amber-700',
    },
    slate: {
        gradientClass:
            'bg-gradient-to-br from-slate-700 via-slate-500 to-slate-300 shadow-slate-500/20',
        swatchClass: 'bg-gradient-to-br from-slate-700 via-slate-500 to-slate-300',
        accentClass: 'border-slate-200 bg-slate-100 text-slate-700',
    },
    violet: {
        gradientClass:
            'bg-gradient-to-br from-violet-500 via-fuchsia-500 to-pink-400 shadow-violet-500/20',
        swatchClass: 'bg-gradient-to-br from-violet-500 via-fuchsia-500 to-pink-400',
        accentClass: 'border-violet-200 bg-violet-50 text-violet-700',
    },
};

export function resolveSkillIcon(icon) {
    const resolved = String(icon || '').trim();
    if (!resolved) {
        return DEFAULT_SKILL_ICON;
    }
    const normalized = SKILL_ICON_ALIASES[resolved] || resolved;
    if (SKILL_ICON_OPTION_SET.has(normalized)) {
        return normalized;
    }
    return DEFAULT_SKILL_ICON;
}

export function resolveSkillIconColor(iconColor) {
    const resolved = String(iconColor || '').trim();
    if (resolved && SKILL_ICON_COLOR_STYLES[resolved]) {
        return resolved;
    }
    return DEFAULT_SKILL_ICON_COLOR;
}

export function getSkillIconGradientClass(iconColor) {
    return SKILL_ICON_COLOR_STYLES[resolveSkillIconColor(iconColor)].gradientClass;
}

export function getSkillIconSwatchClass(iconColor) {
    return SKILL_ICON_COLOR_STYLES[resolveSkillIconColor(iconColor)].swatchClass;
}

export function getSkillIconAccentClass(iconColor) {
    return SKILL_ICON_COLOR_STYLES[resolveSkillIconColor(iconColor)].accentClass;
}

export function getSkillIconColorLabel(iconColor) {
    const resolved = resolveSkillIconColor(iconColor);
    return SKILL_ICON_COLOR_OPTIONS.find(item => item.key === resolved)?.label || resolved;
}
