<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getAdminDashboard } from '@/api/admin-dashboard';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();

const ACTIVE_STATUS_KEYS = [
    'published',
    'enabled',
    'active',
    'running',
    'connected',
    'visible',
    'registered',
    'initialized',
];
const DRAFT_STATUS_KEYS = ['draft', 'unpublished', 'pending', 'testing', 'hidden'];
const STATUS_LABELS = Object.freeze({
    published: '已发布',
    unpublished: '未发布',
    visible: '已上架',
    hidden: '已隐藏',
    enabled: '已启用',
    disabled: '未启用',
    registered: '已注册',
    unregistered: '未注册',
    active: '已启用',
    draft: '草稿',
    initialized: '已初始化',
    uninitialized: '未初始化',
});

const ACCENTS = Object.freeze({
    blue: {
        iconWrapClass: 'bg-blue-50 text-blue-600',
        valueClass: 'text-blue-600',
        ringColor: '#3B82F6',
    },
    emerald: {
        iconWrapClass: 'bg-emerald-50 text-emerald-600',
        valueClass: 'text-emerald-600',
        ringColor: '#22C55E',
    },
    amber: {
        iconWrapClass: 'bg-amber-50 text-amber-600',
        valueClass: 'text-amber-600',
        ringColor: '#F59E0B',
    },
    violet: {
        iconWrapClass: 'bg-violet-50 text-violet-600',
        valueClass: 'text-violet-600',
        ringColor: '#8B5CF6',
    },
    rose: {
        iconWrapClass: 'bg-rose-50 text-rose-500',
        valueClass: 'text-rose-500',
        ringColor: '#F43F5E',
    },
    cyan: {
        iconWrapClass: 'bg-cyan-50 text-cyan-600',
        valueClass: 'text-cyan-600',
        ringColor: '#0EA5E9',
    },
});

const MODULE_GROUPS = Object.freeze([
    {
        key: 'content',
        label: '内容与技能',
        dotClass: 'bg-blue-500',
        items: [
            {
                id: 'knowledge',
                label: '知识库',
                icon: 'inventory_2',
                path: ROUTE_PATHS.adminKnowledge,
                description: '管理与运营知识资产',
                accent: ACCENTS.blue,
                statuses: [
                    { key: 'published', label: '已发布' },
                    { key: 'draft', label: '未发布' },
                ],
            },
            {
                id: 'skillstudio',
                label: '技能工坊',
                icon: 'design_services',
                path: ROUTE_PATHS.adminSkillStudio,
                description: '构建与编辑技能',
                accent: ACCENTS.emerald,
                statuses: [
                    { key: 'published', label: '已发布' },
                    { key: 'unpublished', label: '未发布' },
                ],
            },
            {
                id: 'skill-management',
                label: '技能管理',
                icon: 'deployed_code',
                path: ROUTE_PATHS.adminSkillManagement,
                description: '管理与发布技能',
                accent: ACCENTS.amber,
                statuses: [
                    { key: 'published', label: '已发布' },
                    { key: 'unpublished', label: '未发布' },
                ],
            },
            {
                id: 'tool-library',
                label: '工具库',
                icon: 'work',
                path: ROUTE_PATHS.adminToolLibrary,
                description: '执行与集成能力',
                accent: ACCENTS.violet,
                statuses: [
                    { key: 'enabled', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
        ],
    },
    {
        key: 'integration',
        label: '接入与编排',
        dotClass: 'bg-emerald-500',
        items: [
            {
                id: 'mcp',
                label: 'MCP 服务',
                icon: 'asterisk',
                path: ROUTE_PATHS.adminMcpManagement,
                description: '协议与服务接入',
                accent: ACCENTS.rose,
                statuses: [
                    { key: 'enabled', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
            {
                id: 'api-library',
                label: 'API 库',
                icon: 'diamond',
                path: ROUTE_PATHS.adminApiLibrary,
                description: '管理与调用能力',
                accent: ACCENTS.cyan,
                statuses: [
                    { key: 'enabled', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
            {
                id: 'connector',
                label: '连接器',
                icon: 'conversion_path',
                path: ROUTE_PATHS.adminIntegrationConnectors,
                description: '第三方系统连接',
                accent: ACCENTS.rose,
                statuses: [
                    { key: 'active', label: '已启用' },
                    { key: 'draft', label: '草稿' },
                    { key: 'disabled', label: '已停用' },
                ],
            },
            {
                id: 'channel',
                label: '渠道接入',
                icon: 'support_agent',
                path: ROUTE_PATHS.adminChannels,
                description: '多渠道接入能力',
                accent: ACCENTS.blue,
                statuses: [
                    { key: 'enabled', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
        ],
    },
    {
        key: 'resource',
        label: '数据与资源',
        dotClass: 'bg-rose-500',
        items: [
            {
                id: 'data-source',
                label: '数据源',
                icon: 'database',
                path: ROUTE_PATHS.adminIntegrationDataSources,
                description: '管理与连接数据源',
                accent: ACCENTS.amber,
                statuses: [
                    { key: 'active', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
            {
                id: 'dataset',
                label: '数据集',
                icon: 'dataset',
                path: ROUTE_PATHS.adminIntegrationDatasets,
                description: '管理与发布数据集',
                accent: ACCENTS.emerald,
                statuses: [
                    { key: 'published', label: '已发布' },
                    { key: 'draft', label: '未发布' },
                ],
            },
            {
                id: 'model-library',
                label: '模型库',
                icon: 'deployed_code_history',
                path: ROUTE_PATHS.adminModelLibrary,
                description: '模型与服务管理',
                accent: ACCENTS.rose,
                statuses: [
                    { key: 'active', label: '已启用' },
                    { key: 'draft', label: '未发布' },
                ],
            },
            {
                id: 'agent-template',
                label: 'Agent 模板',
                icon: 'widgets',
                path: ROUTE_PATHS.adminSystemAgentManagement,
                description: '配置与运营模板',
                accent: ACCENTS.violet,
                statuses: [
                    { key: 'enabled', label: '已启用' },
                    { key: 'disabled', label: '未启用' },
                ],
            },
        ],
    },
]);

const dashboardLoading = ref(false);
const loadError = ref('');
const dashboard = ref(createEmptyDashboard());

const moduleStatsMap = computed(
    () => new Map((dashboard.value.modules || []).map(item => [item.moduleId, item]))
);

const licenseOverviewItems = computed(() => {
    const license = dashboard.value.license;
    return [
        {
            id: 'licensed-users',
            label: '授权用户',
            icon: 'group',
            value: formatNumber(license.registeredUsers),
            suffix: license.userUnlimited ? '无限制' : `/ ${formatNumber(license.maxActiveUsers)}`,
            description: `当前活跃 ${formatNumber(license.activeUsers)} 人`,
            path: ROUTE_PATHS.adminSystemUserManagement,
            iconClass: 'bg-blue-50 text-blue-600',
            valueClass: 'text-blue-600',
        },
        {
            id: 'active-users',
            label: '当前活跃用户',
            icon: 'person_check',
            value: formatNumber(license.activeUsers),
            suffix: '人',
            description: license.userUnlimited
                ? '授权用户数量无限制'
                : `授权上限 ${formatNumber(license.maxActiveUsers)} 人`,
            path: ROUTE_PATHS.adminSystemUserManagement,
            iconClass: 'bg-emerald-50 text-emerald-600',
            valueClass: 'text-emerald-600',
        },
        {
            id: 'expiration',
            label: '授权到期时间',
            icon: 'event',
            value: license.expirationUnlimited ? '永久有效' : formatDate(license.expiresAt),
            suffix: '',
            description: license.expirationUnlimited
                ? '当前授权没有到期时间'
                : `剩余 ${formatNumber(license.remainingDays)} 天`,
            path: ROUTE_PATHS.license,
            iconClass: resolveExpirationIconClass(license),
            valueClass: resolveExpirationValueClass(license),
        },
        {
            id: 'tokens',
            label: 'Token 使用量',
            icon: 'data_usage',
            value: formatCompactNumber(license.consumedTokens),
            suffix: license.tokenUnlimited
                ? '无限制'
                : `/ ${formatCompactNumber(license.maxTotalTokens)}`,
            description: license.tokenUnlimited
                ? '当前 Token 总额度无限制'
                : `剩余 ${formatCompactNumber(license.remainingTokens)} · 已使用 ${getTokenUsagePercentage(license)}%`,
            path: ROUTE_PATHS.adminTokenUsage,
            iconClass: 'bg-violet-50 text-violet-600',
            valueClass: 'text-violet-600',
            progress: getTokenUsagePercentage(license),
        },
    ];
});

const groupedSections = computed(() =>
    MODULE_GROUPS.map(group => ({
        ...group,
        items: group.items.map(item => {
            const stats = moduleStatsMap.value.get(item.id);
            const backendStatuses = Array.isArray(stats?.statuses) ? stats.statuses : [];
            const statuses = backendStatuses.length
                ? backendStatuses.map(status => ({
                      key: status.key,
                      label: STATUS_LABELS[status.key] || status.label || status.key,
                      count: Number(status.count) || 0,
                  }))
                : item.statuses.map(status => ({
                      ...status,
                      count: 0,
                  }));
            const total = Number(stats?.total);

            return {
                ...item,
                count: Number.isFinite(total)
                    ? total
                    : statuses.reduce((sum, status) => sum + Number(status.count || 0), 0),
                statuses,
            };
        }),
    }))
);

function createEmptyDashboard() {
    return {
        summary: {
            moduleCount: 0,
            resourceCount: 0,
            activeCount: 0,
            largestModuleId: '',
            largestModuleLabel: '',
            largestModuleCount: 0,
        },
        license: {
            enabled: false,
            status: '',
            customerName: '',
            edition: '',
            expiresAt: '',
            expirationUnlimited: false,
            remainingDays: 0,
            registeredUsers: 0,
            activeUsers: 0,
            maxActiveUsers: null,
            userUnlimited: false,
            consumedTokens: 0,
            maxTotalTokens: null,
            remainingTokens: null,
            tokenUnlimited: false,
        },
        modules: [],
    };
}

function formatNumber(value) {
    return new Intl.NumberFormat('zh-CN').format(Number(value) || 0);
}

function formatCompactNumber(value) {
    return new Intl.NumberFormat('zh-CN', {
        notation: 'compact',
        maximumFractionDigits: 1,
    }).format(Number(value) || 0);
}

function formatDate(value) {
    const text = String(value || '').trim();
    if (!text) {
        return '未设置';
    }
    return text.slice(0, 10);
}

function getTokenUsagePercentage(license) {
    if (license?.tokenUnlimited || Number(license?.maxTotalTokens) <= 0) {
        return 0;
    }
    return Math.max(
        0,
        Math.min(
            100,
            Math.round(
                (Number(license?.consumedTokens || 0) / Number(license.maxTotalTokens)) * 100
            )
        )
    );
}

function resolveLicenseStatusLabel(license) {
    if (!license?.enabled) {
        return '授权未启用';
    }
    const status = String(license?.status || '')
        .trim()
        .toUpperCase();
    if (status === 'VALID' || status === 'ACTIVE') {
        return '授权有效';
    }
    if (status === 'EXPIRED') {
        return '授权已过期';
    }
    return license?.status || '授权已启用';
}

function resolveLicenseStatusClass(license) {
    if (!license?.enabled) {
        return 'bg-slate-100 text-slate-600';
    }
    if (!license.expirationUnlimited && Number(license.remainingDays) <= 30) {
        return 'bg-amber-50 text-amber-700';
    }
    return 'bg-emerald-50 text-emerald-700';
}

function resolveExpirationIconClass(license) {
    if (!license?.expirationUnlimited && Number(license?.remainingDays) <= 30) {
        return 'bg-amber-50 text-amber-600';
    }
    return 'bg-cyan-50 text-cyan-600';
}

function resolveExpirationValueClass(license) {
    if (!license?.expirationUnlimited && Number(license?.remainingDays) <= 30) {
        return 'text-amber-600';
    }
    return 'text-cyan-600';
}

function resolvePrimaryStatus(statuses = []) {
    if (!statuses.length) {
        return { key: 'empty', label: '暂无', count: 0 };
    }

    return statuses.reduce((best, status) =>
        Number(status.count || 0) > Number(best.count || 0) ? status : best
    );
}

function resolveStatusPillClass(key) {
    if (ACTIVE_STATUS_KEYS.includes(key)) {
        return 'border-emerald-100 bg-emerald-50 text-emerald-700';
    }
    if (DRAFT_STATUS_KEYS.includes(key)) {
        return 'border-amber-100 bg-amber-50 text-amber-700';
    }
    return 'border-slate-200 bg-slate-100 text-slate-600';
}

function resolveStatusDotClass(key) {
    if (ACTIVE_STATUS_KEYS.includes(key)) {
        return 'bg-emerald-500';
    }
    if (DRAFT_STATUS_KEYS.includes(key)) {
        return 'bg-amber-500';
    }
    return 'bg-slate-500';
}

function getRingPercentage(item) {
    const total = Number(item.count) || 0;
    const primaryCount = Number(resolvePrimaryStatus(item.statuses).count) || 0;
    if (total <= 0 || primaryCount <= 0) {
        return 0;
    }
    return Math.max(0, Math.min(100, Math.round((primaryCount / total) * 100)));
}

function getRingStrokeDasharray(item) {
    const radius = 28;
    const circumference = 2 * Math.PI * radius;
    return `${circumference} ${circumference}`;
}

function getRingStrokeDashoffset(item) {
    const radius = 28;
    const circumference = 2 * Math.PI * radius;
    const progress = getRingPercentage(item);
    return circumference - (progress / 100) * circumference;
}

function getRingColor(item) {
    return getRingPercentage(item) > 0 ? item.accent.ringColor : '#E5E7EB';
}

function navigateTo(path) {
    if (!path) {
        return;
    }
    router.push({ path });
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadDashboard() {
    dashboardLoading.value = true;
    loadError.value = '';
    try {
        dashboard.value = await getAdminDashboard(handleUnauthorized);
    } catch (error) {
        dashboard.value = createEmptyDashboard();
        loadError.value = error?.message || '统计加载失败';
    } finally {
        dashboardLoading.value = false;
    }
}

onMounted(() => {
    loadDashboard();
});
</script>

<template>
    <section class="dashboard-page flex h-full min-h-0 flex-col bg-[#F7F8FA]">
        <div class="flex-1 overflow-y-auto px-8 py-6">
            <div class="w-full space-y-6">
                <header>
                    <div class="flex flex-wrap items-end justify-between gap-4">
                        <div>
                            <h2 class="text-[36px] font-semibold leading-none text-[#333333]">
                                首页看板
                            </h2>
                            <p class="mt-3 text-sm text-[#888888]">后台模块概览与状态分布</p>
                        </div>
                        <div class="flex items-center gap-2">
                            <span
                                class="inline-flex h-8 items-center rounded-full px-3 text-[12px] font-semibold"
                                :class="resolveLicenseStatusClass(dashboard.license)"
                            >
                                {{ resolveLicenseStatusLabel(dashboard.license) }}
                            </span>
                            <span
                                v-if="dashboard.license.customerName || dashboard.license.edition"
                                class="text-[12px] text-[#888888]"
                            >
                                {{
                                    [dashboard.license.customerName, dashboard.license.edition]
                                        .filter(Boolean)
                                        .join(' · ')
                                }}
                            </span>
                        </div>
                    </div>
                </header>

                <section>
                    <div class="mb-4 flex items-center gap-3">
                        <span class="h-3.5 w-3.5 rounded-full bg-violet-500" />
                        <h3 class="text-[20px] font-semibold leading-none text-[#333333]">
                            授权与用量
                        </h3>
                    </div>
                    <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                        <button
                            v-for="item in licenseOverviewItems"
                            :key="item.id"
                            type="button"
                            class="group flex min-h-[138px] flex-col rounded-2xl border border-[#EEF2F7] bg-white p-5 text-left shadow-[0_2px_8px_rgba(15,23,42,0.04)] transition-all hover:-translate-y-0.5 hover:shadow-[0_8px_18px_rgba(15,23,42,0.08)]"
                            @click="navigateTo(item.path)"
                        >
                            <div class="flex items-start justify-between gap-3">
                                <div>
                                    <p class="text-[13px] font-medium text-[#888888]">
                                        {{ item.label }}
                                    </p>
                                    <div class="mt-3 flex items-end gap-1.5 leading-none">
                                        <span
                                            class="text-[26px] font-semibold tracking-[-0.02em]"
                                            :class="item.valueClass"
                                        >
                                            {{ item.value }}
                                        </span>
                                        <span
                                            v-if="item.suffix"
                                            class="pb-0.5 text-[12px] font-medium text-[#888888]"
                                        >
                                            {{ item.suffix }}
                                        </span>
                                    </div>
                                </div>
                                <span
                                    class="material-symbols-outlined flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[20px]"
                                    :class="item.iconClass"
                                >
                                    {{ item.icon }}
                                </span>
                            </div>
                            <div class="mt-auto pt-4">
                                <div
                                    v-if="item.id === 'tokens' && !dashboard.license.tokenUnlimited"
                                    class="mb-2 h-1.5 overflow-hidden rounded-full bg-slate-100"
                                >
                                    <div
                                        class="h-full rounded-full bg-violet-500 transition-[width]"
                                        :style="{ width: `${item.progress}%` }"
                                    />
                                </div>
                                <p class="text-[12px] text-[#888888]">
                                    {{ item.description }}
                                </p>
                            </div>
                        </button>
                    </div>
                </section>

                <section
                    v-if="dashboardLoading || loadError"
                    class="flex min-h-10 items-center rounded-xl border border-[#EEF2F7] bg-white px-4 text-[12px]"
                    :class="loadError ? 'text-rose-600' : 'text-[#888888]'"
                >
                    {{ loadError || '正在加载看板统计...' }}
                </section>

                <section
                    v-for="group in groupedSections"
                    :key="group.key"
                    class="rounded-2xl bg-[#F7F8FA] py-2"
                >
                    <div class="mb-5 flex items-center gap-3">
                        <span class="h-3.5 w-3.5 rounded-full" :class="group.dotClass" />
                        <h3 class="text-[24px] font-semibold leading-none text-[#333333]">
                            {{ group.label }}
                        </h3>
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
                        <button
                            v-for="item in group.items"
                            :key="item.id"
                            type="button"
                            class="flex min-h-[248px] flex-col rounded-2xl border border-[#EEF2F7] bg-white p-6 text-left shadow-[0_2px_8px_rgba(15,23,42,0.04)] transition-all hover:-translate-y-0.5 hover:shadow-[0_8px_18px_rgba(15,23,42,0.08)]"
                            @click="navigateTo(item.path)"
                        >
                            <div class="flex min-w-0 items-start gap-4">
                                <div
                                    class="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl"
                                    :class="item.accent.iconWrapClass"
                                >
                                    <span class="material-symbols-outlined text-[30px]">
                                        {{ item.icon }}
                                    </span>
                                </div>
                                <div class="min-w-0 pt-1">
                                    <h4 class="truncate text-[22px] font-semibold text-[#333333]">
                                        {{ item.label }}
                                    </h4>
                                    <p class="mt-2 truncate text-[15px] text-[#888888]">
                                        {{ item.description }}
                                    </p>
                                </div>
                            </div>

                            <div class="mt-auto">
                                <div class="flex items-end justify-between gap-4">
                                    <div class="min-w-0">
                                        <div class="flex items-end gap-1 leading-none">
                                            <span
                                                class="text-[36px] font-semibold"
                                                :class="item.accent.valueClass"
                                            >
                                                {{ formatNumber(item.count) }}
                                            </span>
                                            <span class="pb-0.5 text-[12px] text-[#333333]"
                                                >项</span
                                            >
                                        </div>
                                    </div>

                                    <div class="shrink-0">
                                        <svg class="h-[84px] w-[84px]" viewBox="0 0 72 72">
                                            <g transform="rotate(-90 36 36)">
                                                <circle
                                                    cx="36"
                                                    cy="36"
                                                    r="28"
                                                    fill="none"
                                                    stroke="#E5E7EB"
                                                    stroke-width="4.5"
                                                />
                                                <circle
                                                    cx="36"
                                                    cy="36"
                                                    r="28"
                                                    fill="none"
                                                    :stroke="getRingColor(item)"
                                                    stroke-width="4.5"
                                                    stroke-linecap="round"
                                                    :stroke-dasharray="getRingStrokeDasharray(item)"
                                                    :stroke-dashoffset="
                                                        getRingStrokeDashoffset(item)
                                                    "
                                                />
                                            </g>
                                            <text
                                                x="36"
                                                y="40"
                                                text-anchor="middle"
                                                class="fill-[#333333] text-[12px] font-semibold"
                                            >
                                                {{ getRingPercentage(item) }}%
                                            </text>
                                        </svg>
                                    </div>
                                </div>

                                <div class="mt-6 flex flex-nowrap gap-2 overflow-hidden">
                                    <span
                                        v-for="status in item.statuses"
                                        :key="`${item.id}-${status.key}`"
                                        class="inline-flex h-9 min-w-0 items-center gap-1.5 rounded-full border px-3 text-[14px] font-medium whitespace-nowrap"
                                        :class="resolveStatusPillClass(status.key)"
                                    >
                                        <span
                                            class="h-2.5 w-2.5 rounded-full"
                                            :class="resolveStatusDotClass(status.key)"
                                        />
                                        <span class="truncate"
                                            >{{ status.label }}
                                            {{ formatNumber(status.count) }}</span
                                        >
                                    </span>
                                </div>
                            </div>
                        </button>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>

<style scoped>
.dashboard-page {
    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}
</style>
