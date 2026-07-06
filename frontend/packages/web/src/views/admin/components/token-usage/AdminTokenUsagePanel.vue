<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import { getTokenUsageDashboard, listTokenUsageRuns } from '@/api/token-usage';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { USER_TYPES } from '@/model/enums/user-type';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';
import TokenUsageRunsTable from '@/views/admin/components/token-usage/TokenUsageRunsTable.vue';

const router = useRouter();

const activeTab = ref('overview');
const tabs = [
    { key: 'overview', label: '总览' },
    { key: 'agent', label: 'Agent' },
    { key: 'user', label: '用户' },
    { key: 'model', label: '模型' },
];

const dashboardLoading = ref(false);
const runsLoading = ref(false);
const loadError = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];
const activePreset = ref('14d');

const dashboard = ref(createEmptyDashboard());
const runs = ref([]);

const filters = reactive({
    startDate: '',
    endDate: '',
    sessionType: '',
    agentType: '',
    agentId: '',
    userId: '',
    modelProvider: '',
    modelName: '',
});

const presetButtons = [
    { key: '7d', label: '近 7 天', days: 7 },
    { key: '14d', label: '近 14 天', days: 14 },
    { key: '30d', label: '近 30 天', days: 30 },
    { key: '90d', label: '近 90 天', days: 90 },
];

const sessionTypeOptions = [
    { value: '', label: '全部会话类型' },
    { value: 'GENERAL_CHAT', label: '通用对话' },
    { value: 'SKILL_CHAT', label: '技能对话' },
    { value: 'PUBLISHED_SKILL_CHAT', label: '已发布技能' },
    { value: 'DATASET_CHAT', label: '数据集对话' },
    { value: 'KNOWLEDGE_QA', label: '知识库问答' },
    { value: 'SKILL_STUDIO_PROJECT_CHAT', label: '工坊项目对话' },
    { value: 'SKILL_STUDIO_PROJECT_PREVIEW_CHAT', label: '工坊项目试运行' },
];

const summaryCards = computed(() => {
    const summary = dashboard.value.summary;
    return [
        {
            id: 'total',
            label: '总 Token',
            value: formatCompact(summary.totalTokens),
            detail: `输入 ${formatCompact(summary.promptTokens)} / 输出 ${formatCompact(summary.completionTokens)}`,
        },
        {
            id: 'runs',
            label: '运行次数',
            value: formatCompact(summary.runCount),
            detail: `${formatCompact(summary.llmCallCount)} 次模型 / ${formatCompact(summary.toolCallCount)} 次工具`,
        },
        {
            id: 'coverage',
            label: 'Usage 覆盖',
            value: `${formatRate(summary.usageCoverageRate)}%`,
            detail: `${formatCompact(summary.usageRunCount)} / ${formatCompact(summary.runCount)} 条运行`,
        },
        {
            id: 'avg',
            label: '平均每次',
            value: formatCompact(summary.avgTokensPerRun),
            detail: `${formatDuration(summary.avgDurationMs)} 耗时 · ${formatCompact(summary.activeUserCount)} 用户`,
        },
    ];
});

const activeFocus = computed(() => {
    if (filters.userId) {
        const current = dashboard.value.userBreakdown.find(
            item => item.key === `user:${filters.userId}`
        );
        return current ? `当前聚焦用户：${current.label}` : `当前聚焦用户 ID ${filters.userId}`;
    }
    if (filters.modelProvider || filters.modelName) {
        const current = dashboard.value.modelBreakdown.find(item => isActiveModel(item));
        return current
            ? `当前聚焦模型：${current.label}`
            : `当前聚焦模型：${filters.modelName || filters.modelProvider}`;
    }
    if (filters.agentType) {
        const current = dashboard.value.agentBreakdown.find(item => isActiveAgent(item));
        return current
            ? `当前聚焦 Agent：${current.label}`
            : `当前聚焦 Agent：${filters.agentType}`;
    }
    return '';
});

const trendGeometry = computed(() => buildTrendGeometry(dashboard.value.dailyTrend));
const trendPeak = computed(() =>
    dashboard.value.dailyTrend.reduce(
        (best, item) => (item.totalTokens > best.totalTokens ? item : best),
        { date: '', totalTokens: 0 }
    )
);

const heroKpi = computed(() => {
    const summary = dashboard.value.summary;
    const range = dashboard.value.range;
    return {
        windowText:
            range.startDate && range.endDate
                ? `${range.startDate} 至 ${range.endDate}`
                : '最近时间窗口',
        description:
            activeFocus.value ||
            `当前窗口覆盖 ${formatCompact(summary.activeAgentCount)} 个 agent，${formatCompact(summary.activeUserCount)} 位用户。`,
    };
});

const spotlightRuns = computed(() => dashboard.value.spotlightRuns || []);
const modelBreakdown = computed(() => dashboard.value.modelBreakdown || []);
const currentUserId = computed(() => Number(currentUserState.profile?.id) || null);
const isAdminViewer = computed(
    () => Number(currentUserState.profile?.userType) === Number(USER_TYPES.ADMIN)
);
const showUserScopeHint = computed(() => currentUserState.initialized && !isAdminViewer.value);

function createEmptyDashboard() {
    return {
        range: {
            startDate: '',
            endDate: '',
        },
        summary: {
            runCount: 0,
            usageRunCount: 0,
            totalTokens: 0,
            promptTokens: 0,
            completionTokens: 0,
            llmCallCount: 0,
            toolCallCount: 0,
            avgDurationMs: 0,
            avgTokensPerRun: 0,
            activeUserCount: 0,
            activeAgentCount: 0,
            usageCoverageRate: 0,
        },
        dailyTrend: [],
        agentBreakdown: [],
        userBreakdown: [],
        modelBreakdown: [],
        spotlightRuns: [],
    };
}

function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function applyPreset(days, { reload = true, key = `${days}d` } = {}) {
    const end = new Date();
    const start = new Date();
    start.setDate(end.getDate() - days + 1);
    filters.startDate = formatDateInput(start);
    filters.endDate = formatDateInput(end);
    activePreset.value = key;
    if (reload) {
        page.value = 1;
        loadAll();
    }
}

function handleDateInput() {
    activePreset.value = 'custom';
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function buildQuery() {
    const effectiveUserId = isAdminViewer.value
        ? filters.userId === ''
            ? null
            : Number(filters.userId)
        : currentUserId.value;
    return {
        startDate: filters.startDate,
        endDate: filters.endDate,
        sessionType: filters.sessionType,
        agentType: filters.agentType,
        agentId: filters.agentId === '' ? null : Number(filters.agentId),
        userId: effectiveUserId,
        modelProvider: filters.modelProvider,
        modelName: filters.modelName,
    };
}

async function loadDashboard() {
    dashboardLoading.value = true;
    try {
        dashboard.value = await getTokenUsageDashboard(buildQuery(), handleUnauthorized);
    } finally {
        dashboardLoading.value = false;
    }
}

async function loadRuns() {
    runsLoading.value = true;
    try {
        const result = await listTokenUsageRuns(
            {
                ...buildQuery(),
                pageNo: page.value,
                pageSize: pageSize.value,
            },
            handleUnauthorized
        );
        runs.value = result.items;
        total.value = result.total;
        page.value = result.pageNo;
        pageSize.value = result.pageSize;
    } finally {
        runsLoading.value = false;
    }
}

async function loadAll() {
    loadError.value = '';
    try {
        await Promise.all([loadDashboard(), loadRuns()]);
    } catch (error) {
        loadError.value = error?.message || 'Token 统计加载失败';
        dashboard.value = createEmptyDashboard();
        runs.value = [];
        total.value = 0;
    }
}

function handleApplyFilters() {
    page.value = 1;
    loadAll();
}

function handleRefresh() {
    loadAll();
}

function handlePageChange(nextPage) {
    page.value = nextPage;
    loadRuns();
}

function handlePageSizeChange(nextSize) {
    pageSize.value = nextSize;
    page.value = 1;
    loadRuns();
}

function focusAgent(item) {
    const [agentType, rawAgentId] = String(item?.key || '').split(':');
    filters.agentType = agentType || '';
    filters.agentId = rawAgentId && rawAgentId !== 'NA' ? Number(rawAgentId) : '';
    filters.userId = '';
    filters.modelProvider = '';
    filters.modelName = '';
    page.value = 1;
    loadAll();
}

function focusUser(item) {
    if (!isAdminViewer.value) {
        return;
    }
    const rawUserId = String(item?.key || '').replace(/^user:/, '');
    filters.userId = rawUserId && rawUserId !== 'unknown' ? Number(rawUserId) : '';
    filters.agentType = '';
    filters.agentId = '';
    filters.modelProvider = '';
    filters.modelName = '';
    page.value = 1;
    loadAll();
}

function focusModel(item) {
    const key = String(item?.key || '');
    const separatorIndex = key.indexOf(':');
    filters.modelProvider = separatorIndex > -1 ? key.slice(0, separatorIndex) : '';
    filters.modelName = separatorIndex > -1 ? key.slice(separatorIndex + 1) : '';
    filters.agentType = '';
    filters.agentId = '';
    filters.userId = '';
    page.value = 1;
    loadAll();
}

function clearEntityFocus() {
    filters.agentType = '';
    filters.agentId = '';
    filters.userId = isAdminViewer.value ? '' : currentUserId.value || '';
    filters.modelProvider = '';
    filters.modelName = '';
    page.value = 1;
    loadAll();
}

function isActiveAgent(item) {
    if (!filters.agentType) {
        return false;
    }
    const [agentType, rawAgentId] = String(item?.key || '').split(':');
    const currentAgentId = filters.agentId === '' ? 'NA' : String(filters.agentId);
    return filters.agentType === agentType && currentAgentId === (rawAgentId || 'NA');
}

function isActiveUser(item) {
    return Boolean(filters.userId) && item?.key === `user:${filters.userId}`;
}

function isActiveModel(item) {
    if (!filters.modelProvider || !filters.modelName) {
        return false;
    }
    return item?.key === `${filters.modelProvider}:${filters.modelName}`;
}

function buildTrendGeometry(points) {
    if (!Array.isArray(points) || points.length === 0) {
        return null;
    }
    const width = 680;
    const height = 220;
    const paddingX = 28;
    const paddingY = 22;
    const baseline = height - paddingY;
    const maxValue = Math.max(...points.map(item => Number(item.totalTokens) || 0), 1);
    const stepX = points.length <= 1 ? 0 : (width - paddingX * 2) / (points.length - 1);
    const usableHeight = height - paddingY * 2;

    const coords = points.map((item, index) => {
        const total = Number(item.totalTokens) || 0;
        const x = paddingX + stepX * index;
        const y = baseline - (total / maxValue) * usableHeight;
        return {
            x,
            y,
            total,
            date: item.date,
        };
    });

    const linePath = coords
        .map(
            (point, index) =>
                `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
        )
        .join(' ');
    const areaPath = [
        `M ${coords[0].x.toFixed(2)} ${baseline.toFixed(2)}`,
        ...coords.map(point => `L ${point.x.toFixed(2)} ${point.y.toFixed(2)}`),
        `L ${coords[coords.length - 1].x.toFixed(2)} ${baseline.toFixed(2)}`,
        'Z',
    ].join(' ');

    return {
        width,
        height,
        baseline,
        maxValue,
        coords,
        linePath,
        areaPath,
    };
}

function formatCompact(value) {
    const numeric = Number(value) || 0;
    if (numeric >= 1000000) {
        return `${(numeric / 1000000).toFixed(numeric >= 10000000 ? 0 : 1)}M`;
    }
    if (numeric >= 10000) {
        return `${(numeric / 10000).toFixed(numeric >= 100000 ? 0 : 1)}万`;
    }
    if (numeric >= 1000) {
        return `${(numeric / 1000).toFixed(numeric >= 10000 ? 0 : 1)}k`;
    }
    return new Intl.NumberFormat('zh-CN').format(numeric);
}

function formatNumber(value) {
    return new Intl.NumberFormat('zh-CN').format(Number(value) || 0);
}

function formatRate(value) {
    const numeric = Number(value) || 0;
    return numeric.toFixed(numeric >= 10 ? 0 : 1);
}

function formatDuration(ms) {
    const numeric = Number(ms) || 0;
    if (numeric >= 60000) {
        return `${(numeric / 60000).toFixed(1)} min`;
    }
    if (numeric >= 1000) {
        return `${(numeric / 1000).toFixed(1)} s`;
    }
    return `${numeric} ms`;
}

function formatDateLabel(value) {
    if (!value) {
        return '--';
    }
    return value.slice(5);
}

function statusClass(status) {
    if (status === 'COMPLETED') {
        return 'bg-emerald-100 text-emerald-700';
    }
    if (status === 'FAILED') {
        return 'bg-red-100 text-red-700';
    }
    if (status === 'CANCELLED') {
        return 'bg-amber-100 text-amber-700';
    }
    return 'bg-slate-100 text-slate-500';
}

onMounted(() => {
    if (!isAdminViewer.value && currentUserId.value) {
        filters.userId = currentUserId.value;
    }
    applyPreset(14, { reload: false, key: '14d' });
    loadAll();
});
</script>

<template>
    <section class="token-usage-page flex h-full min-h-0 flex-col overflow-hidden">
        <header class="px-6 pb-4 pt-5">
            <div class="flex items-center justify-between">
                <div>
                    <h2 class="text-2xl font-semibold text-slate-800">Token 统计</h2>
                    <p class="mt-1 text-sm text-slate-500">
                        查看运行、Agent、用户的 Token 消耗分析
                    </p>
                </div>
                <div
                    class="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2"
                >
                    <span class="text-xs font-medium text-slate-500">窗口</span>
                    <span class="text-sm font-semibold text-slate-700">{{
                        heroKpi.windowText
                    }}</span>
                </div>
            </div>

            <!-- 标签页切换 -->
            <div class="mt-4 flex items-center gap-1 border-b border-slate-200">
                <button
                    v-for="tab in tabs"
                    :key="tab.key"
                    type="button"
                    class="px-4 py-2.5 text-sm font-medium transition border-b-2 -mb-px"
                    :class="
                        activeTab === tab.key
                            ? 'border-teal-500 text-teal-600'
                            : 'border-transparent text-slate-500 hover:text-slate-700'
                    "
                    @click="activeTab = tab.key"
                >
                    {{ tab.label }}
                </button>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-6 pb-6">
            <div
                class="filter-card mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3"
            >
                <div class="flex items-center gap-2">
                    <button
                        v-for="preset in presetButtons"
                        :key="preset.key"
                        type="button"
                        class="rounded-lg border px-3 py-1.5 text-sm font-medium transition"
                        :class="
                            activePreset === preset.key
                                ? 'border-slate-800 bg-slate-800 text-white'
                                : 'border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:bg-white'
                        "
                        @click="applyPreset(preset.days, { key: preset.key })"
                    >
                        {{ preset.label }}
                    </button>
                </div>

                <div class="flex flex-wrap items-center gap-3">
                    <label class="flex items-center gap-2">
                        <span class="text-xs text-slate-500">开始</span>
                        <input
                            v-model="filters.startDate"
                            type="date"
                            class="rounded-lg border border-slate-200 px-2 py-1.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
                            @change="handleDateInput"
                        />
                    </label>

                    <label class="flex items-center gap-2">
                        <span class="text-xs text-slate-500">结束</span>
                        <input
                            v-model="filters.endDate"
                            type="date"
                            class="rounded-lg border border-slate-200 px-2 py-1.5 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20"
                            @change="handleDateInput"
                        />
                    </label>

                    <div class="min-w-[160px]">
                        <AppSelect
                            v-model="filters.sessionType"
                            :options="sessionTypeOptions"
                            :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            placeholder="全部会话类型"
                        />
                    </div>

                    <button
                        type="button"
                        class="rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-teal-700"
                        @click="handleApplyFilters"
                    >
                        查询
                    </button>

                    <button
                        type="button"
                        class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
                        @click="handleRefresh"
                    >
                        刷新
                    </button>

                    <button
                        v-if="activeFocus"
                        type="button"
                        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-700 transition hover:bg-amber-100"
                        @click="clearEntityFocus"
                    >
                        清除筛选
                    </button>
                </div>
            </div>

            <p
                v-if="showUserScopeHint"
                class="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700"
            >
                当前账号为普通用户，仅展示本人 Token 数据。
            </p>

            <p
                v-if="loadError"
                class="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
            >
                {{ loadError }}
            </p>

            <!-- 总览标签页 -->
            <div v-show="activeTab === 'overview'" class="space-y-4">
                <!-- 关键指标 -->
                <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <article
                        v-for="card in summaryCards"
                        :key="card.id"
                        class="summary-card rounded-xl border border-slate-200 bg-white px-4 py-3"
                    >
                        <p class="text-xs font-medium text-slate-400">
                            {{ card.label }}
                        </p>
                        <p class="mt-2 font-mono text-2xl font-bold text-slate-800">
                            {{ card.value }}
                        </p>
                        <p class="mt-1 text-xs text-slate-500">{{ card.detail }}</p>
                    </article>
                </div>

                <!-- Token 趋势 + 高消耗运行 -->
                <div class="grid gap-4 xl:grid-cols-[1fr_400px]">
                    <section class="rounded-xl border border-slate-200 bg-white px-5 py-4">
                        <div class="flex items-center justify-between">
                            <div>
                                <h3 class="text-lg font-semibold text-slate-800">Token 趋势</h3>
                                <p class="mt-1 text-sm text-slate-500">每日累计 Token 变化</p>
                            </div>
                            <div
                                class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-right"
                            >
                                <p class="text-xs text-slate-400">总计</p>
                                <p class="font-mono text-lg font-bold text-slate-700">
                                    {{ formatCompact(dashboard.summary.totalTokens) }}
                                </p>
                            </div>
                        </div>

                        <div
                            class="mt-4 rounded-lg border border-slate-100 bg-slate-50/50 px-3 py-3"
                        >
                            <div
                                v-if="dashboardLoading && !dashboard.dailyTrend.length"
                                class="flex h-[180px] items-center justify-center text-sm text-slate-400"
                            >
                                加载中...
                            </div>
                            <div v-else-if="trendGeometry" class="space-y-3">
                                <svg
                                    :viewBox="`0 0 ${trendGeometry.width} ${trendGeometry.height}`"
                                    class="h-[180px] w-full"
                                    preserveAspectRatio="none"
                                >
                                    <defs>
                                        <linearGradient
                                            id="token-usage-area-gradient"
                                            x1="0%"
                                            y1="0%"
                                            x2="100%"
                                            y2="100%"
                                        >
                                            <stop
                                                offset="0%"
                                                stop-color="#0d9488"
                                                stop-opacity="0.2"
                                            />
                                            <stop
                                                offset="100%"
                                                stop-color="#0d9488"
                                                stop-opacity="0.05"
                                            />
                                        </linearGradient>
                                        <linearGradient
                                            id="token-usage-line-gradient"
                                            x1="0%"
                                            y1="0%"
                                            x2="100%"
                                            y2="0%"
                                        >
                                            <stop offset="0%" stop-color="#0f766e" />
                                            <stop offset="100%" stop-color="#14b8a6" />
                                        </linearGradient>
                                    </defs>
                                    <line
                                        v-for="step in 4"
                                        :key="step"
                                        x1="0"
                                        :x2="trendGeometry.width"
                                        :y1="
                                            trendGeometry.baseline -
                                            ((trendGeometry.baseline - 22) / 4) * step
                                        "
                                        :y2="
                                            trendGeometry.baseline -
                                            ((trendGeometry.baseline - 22) / 4) * step
                                        "
                                        stroke="#e2e8f0"
                                        stroke-dasharray="4 6"
                                    />
                                    <path
                                        :d="trendGeometry.areaPath"
                                        fill="url(#token-usage-area-gradient)"
                                    />
                                    <path
                                        :d="trendGeometry.linePath"
                                        fill="none"
                                        stroke="url(#token-usage-line-gradient)"
                                        stroke-width="3"
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                    />
                                    <g v-for="point in trendGeometry.coords" :key="point.date">
                                        <circle
                                            :cx="point.x"
                                            :cy="point.y"
                                            r="4"
                                            fill="#fff"
                                            stroke="#0f766e"
                                            stroke-width="2"
                                        />
                                    </g>
                                </svg>
                                <div class="grid gap-2 text-xs sm:grid-cols-3 lg:grid-cols-6">
                                    <div
                                        v-for="point in dashboard.dailyTrend.slice(-6)"
                                        :key="point.date"
                                        class="rounded-lg border border-slate-100 bg-white px-2 py-2"
                                    >
                                        <p class="font-medium text-slate-500">
                                            {{ formatDateLabel(point.date) }}
                                        </p>
                                        <p class="mt-1 font-semibold text-slate-700">
                                            {{ formatCompact(point.totalTokens) }}
                                        </p>
                                    </div>
                                </div>
                            </div>
                            <div
                                v-else
                                class="flex h-[180px] items-center justify-center text-sm text-slate-400"
                            >
                                暂无数据
                            </div>
                        </div>
                    </section>

                    <!-- 高消耗运行 -->
                    <section class="rounded-xl border border-slate-200 bg-white px-4 py-4">
                        <div class="flex items-center justify-between">
                            <h3 class="text-base font-semibold text-slate-800">高消耗运行</h3>
                            <span class="text-sm text-slate-400"
                                >{{ spotlightRuns.length }} 条</span
                            >
                        </div>
                        <div class="mt-3 space-y-2 max-h-[400px] overflow-y-auto">
                            <div
                                v-for="item in spotlightRuns"
                                :key="item.assistantMessageId"
                                class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2"
                            >
                                <div class="flex items-center justify-between">
                                    <div class="min-w-0">
                                        <p class="truncate text-sm font-medium text-slate-700">
                                            {{
                                                item.agentName || item.runtimeSkillName || '未命名'
                                            }}
                                        </p>
                                        <p class="mt-1 text-xs text-slate-400">
                                            {{
                                                item.runSceneLabel || item.sessionTypeLabel || '--'
                                            }}
                                        </p>
                                    </div>
                                    <span
                                        class="rounded-full px-2 py-0.5 text-xs font-medium"
                                        :class="statusClass(item.runStatus)"
                                    >
                                        {{ item.runStatus || 'UNKNOWN' }}
                                    </span>
                                </div>
                                <div
                                    class="mt-1 flex items-center justify-between text-xs text-slate-400"
                                >
                                    <span>{{ item.userName || '--' }}</span>
                                    <span class="font-mono font-semibold text-teal-600">{{
                                        formatCompact(item.totalTokens)
                                    }}</span>
                                </div>
                            </div>
                            <div
                                v-if="!spotlightRuns.length"
                                class="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400"
                            >
                                暂无数据
                            </div>
                        </div>
                    </section>
                </div>

                <!-- 运行明细 -->
                <TokenUsageRunsTable
                    title="运行明细"
                    subtitle="总览视角下的全部运行记录，已包含运行来源区分。"
                    :runs="runs"
                    :loading="runsLoading"
                    :total="total"
                    :page="page"
                    :page-size="pageSize"
                    :page-size-options="pageSizeOptions"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>

            <!-- Agent 标签页 -->
            <div v-show="activeTab === 'agent'" class="space-y-4">
                <section class="rounded-xl border border-slate-200 bg-white px-4 py-4">
                    <div class="flex items-center justify-between">
                        <h3 class="text-lg font-semibold text-slate-800">Agent Token 消耗排行</h3>
                        <button
                            v-if="activeFocus"
                            @click="clearEntityFocus"
                            class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-700 hover:bg-amber-100"
                        >
                            清除筛选
                        </button>
                    </div>
                    <div class="mt-4 space-y-2">
                        <button
                            v-for="item in dashboard.agentBreakdown"
                            :key="item.key"
                            type="button"
                            class="w-full rounded-lg border px-4 py-3 text-left transition"
                            :class="
                                isActiveAgent(item)
                                    ? 'border-teal-500 bg-teal-50'
                                    : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'
                            "
                            @click="focusAgent(item)"
                        >
                            <div class="flex items-center justify-between">
                                <div class="min-w-0">
                                    <p class="truncate text-sm font-semibold text-slate-700">
                                        {{ item.label }}
                                    </p>
                                    <p class="mt-1 text-xs text-slate-400">
                                        {{ formatCompact(item.runCount) }} 次运行
                                    </p>
                                </div>
                                <div class="text-right">
                                    <p class="font-mono text-lg font-bold text-teal-600">
                                        {{ formatCompact(item.totalTokens) }}
                                    </p>
                                    <p class="text-xs text-slate-400">
                                        {{ formatRate(item.shareRatio) }}%
                                    </p>
                                </div>
                            </div>
                            <div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-200">
                                <div
                                    class="h-full rounded-full bg-gradient-to-r from-teal-500 to-teal-400"
                                    :style="{
                                        width: `${Math.max(4, Math.min(item.shareRatio || 0, 100))}%`,
                                    }"
                                />
                            </div>
                        </button>
                        <div
                            v-if="!dashboard.agentBreakdown.length"
                            class="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400"
                        >
                            暂无数据
                        </div>
                    </div>
                </section>
                <TokenUsageRunsTable
                    title="Agent 运行明细"
                    subtitle="点击上方 Agent 榜单项后，当前 Agent 的运行记录会显示在这里。"
                    :runs="runs"
                    :loading="runsLoading"
                    :total="total"
                    :page="page"
                    :page-size="pageSize"
                    :page-size-options="pageSizeOptions"
                    empty-text="暂无 Agent 运行数据"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>

            <!-- 用户标签页 -->
            <div v-show="activeTab === 'user'" class="space-y-4">
                <section class="rounded-xl border border-slate-200 bg-white px-4 py-4">
                    <div class="flex items-center justify-between">
                        <h3 class="text-lg font-semibold text-slate-800">用户 Token 消耗排行</h3>
                        <button
                            v-if="activeFocus"
                            @click="clearEntityFocus"
                            class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-700 hover:bg-amber-100"
                        >
                            清除筛选
                        </button>
                    </div>
                    <div class="mt-4 space-y-2">
                        <button
                            v-for="item in dashboard.userBreakdown"
                            :key="item.key"
                            type="button"
                            class="w-full rounded-lg border px-4 py-3 text-left transition"
                            :class="
                                isActiveUser(item)
                                    ? 'border-teal-500 bg-teal-50'
                                    : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'
                            "
                            @click="focusUser(item)"
                        >
                            <div class="flex items-center justify-between">
                                <div class="min-w-0">
                                    <p class="truncate text-sm font-semibold text-slate-700">
                                        {{ item.label }}
                                    </p>
                                    <p class="mt-1 text-xs text-slate-400">
                                        {{ formatCompact(item.runCount) }} 次运行
                                    </p>
                                </div>
                                <div class="text-right">
                                    <p class="font-mono text-lg font-bold text-teal-600">
                                        {{ formatCompact(item.totalTokens) }}
                                    </p>
                                    <p class="text-xs text-slate-400">
                                        {{ formatCompact(item.avgTokensPerRun) }} 平均
                                    </p>
                                </div>
                            </div>
                        </button>
                        <div
                            v-if="!dashboard.userBreakdown.length"
                            class="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400"
                        >
                            暂无数据
                        </div>
                    </div>
                </section>
                <TokenUsageRunsTable
                    title="用户运行明细"
                    subtitle="点击上方用户榜单项后，当前用户的运行记录会显示在这里。"
                    :runs="runs"
                    :loading="runsLoading"
                    :total="total"
                    :page="page"
                    :page-size="pageSize"
                    :page-size-options="pageSizeOptions"
                    empty-text="暂无用户运行数据"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>

            <!-- 模型标签页 -->
            <div v-show="activeTab === 'model'" class="space-y-4">
                <section class="rounded-xl border border-slate-200 bg-white px-4 py-4">
                    <div class="flex items-center justify-between">
                        <h3 class="text-lg font-semibold text-slate-800">模型 Token 分布</h3>
                        <button
                            v-if="activeFocus"
                            @click="clearEntityFocus"
                            class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-700 hover:bg-amber-100"
                        >
                            清除筛选
                        </button>
                    </div>
                    <div class="mt-4 space-y-2">
                        <button
                            v-for="item in modelBreakdown"
                            :key="item.key"
                            type="button"
                            class="w-full rounded-lg border px-4 py-3 text-left transition"
                            :class="
                                isActiveModel(item)
                                    ? 'border-teal-500 bg-teal-50'
                                    : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'
                            "
                            @click="focusModel(item)"
                        >
                            <div class="flex items-center justify-between">
                                <div class="min-w-0">
                                    <p class="truncate text-sm font-semibold text-slate-700">
                                        {{ item.label }}
                                    </p>
                                    <p class="mt-1 text-xs text-slate-400">
                                        {{ item.subtitle || '模型' }}
                                    </p>
                                </div>
                                <div class="text-right">
                                    <p class="font-mono text-lg font-bold text-teal-600">
                                        {{ formatCompact(item.totalTokens) }}
                                    </p>
                                    <p class="text-xs text-slate-400">
                                        {{ formatRate(item.shareRatio) }}%
                                    </p>
                                </div>
                            </div>
                            <div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-200">
                                <div
                                    class="h-full rounded-full bg-gradient-to-r from-teal-500 to-teal-400"
                                    :style="{
                                        width: `${Math.max(4, Math.min(item.shareRatio || 0, 100))}%`,
                                    }"
                                />
                            </div>
                        </button>
                        <div
                            v-if="!modelBreakdown.length"
                            class="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400"
                        >
                            暂无数据
                        </div>
                    </div>
                </section>
                <TokenUsageRunsTable
                    title="模型运行明细"
                    subtitle="点击上方模型项后，当前模型的运行记录会显示在这里。"
                    :runs="runs"
                    :loading="runsLoading"
                    :total="total"
                    :page="page"
                    :page-size="pageSize"
                    :page-size-options="pageSizeOptions"
                    empty-text="暂无模型运行数据"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>
        </div>
    </section>
</template>

<style scoped>
.token-usage-page {
    background: #f8fafc;
}

.summary-card {
    min-height: 100px;
}

.filter-input-group {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    min-width: 140px;
}

.filter-input-group span {
    font-size: 11px;
    font-weight: 500;
    color: #64748b;
}

.spotlight-card {
    background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}
</style>
