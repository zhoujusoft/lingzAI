<script setup>
import { computed, onMounted, ref } from 'vue';
import MiniPagination from '@/components/MiniPagination.vue';
import { alert, confirm } from '@/composables/useModal';
import { useRouter } from 'vue-router';
import { ROUTE_PATHS } from '@/router/routePaths';
import { clearUserSession } from '@/composables/useCurrentUser';
import { listAgents as fetchAgents, deleteAgent, toggleAgentEnabled } from '@/api/agents';

const router = useRouter();
const agents = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const keyword = ref('');
const pageSizeOptions = [10, 20, 50];
const loading = ref(false);
const loadError = ref('');

const enabledAgentCount = computed(() => agents.value.filter(agent => agent.enabled === 1).length);
const currentPageCapabilityCount = computed(() =>
    agents.value.reduce(
        (count, agent) => count + Number(agent.skillCount ?? 0) + Number(agent.toolCount ?? 0),
        0
    )
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function formatDateTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '-';
    }
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

function resolveAgentIcon(agent) {
    const icon = String(agent?.icon || '').trim();
    return icon || 'smart_toy';
}

function isMaterialIcon(icon) {
    return /^[a-z][a-z0-9_]*$/.test(String(icon || '').trim());
}

function agentStatusClass(agent) {
    return agent?.enabled === 1
        ? 'border border-emerald-100 bg-emerald-50 text-emerald-700'
        : 'border border-slate-200 bg-slate-50 text-slate-500';
}

function openEditAgentDialog(row) {
    if (!row?.id) {
        return;
    }
    router.push(ROUTE_PATHS.adminSystemExpertPackageEdit(row.id));
}

function openAddAgentDialog() {
    router.push(ROUTE_PATHS.adminSystemExpertPackageCreate);
}

async function openDeleteAgentDialog(row) {
    const confirmed = await confirm({
        title: '删除专家技能包',
        message: `确认删除专家技能包“${row.agentName}”吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    try {
        await deleteAgent(row.id, handleUnauthorized);
        await loadAgents(page.value);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除专家技能包失败',
        });
    }
}

async function openToggleAgentEnabledDialog(row) {
    const actionText = row.enabled === 1 ? '停用' : '启用';
    const confirmed = await confirm({
        title: `${actionText}专家技能包`,
        message:
            row.enabled === 1
                ? `确认停用专家技能包“${row.agentName}”吗？停用后前台用户将不能继续进入该包的新对话。`
                : `确认启用专家技能包“${row.agentName}”吗？启用后所有登录用户可进入并使用包内配置的技能与工具。`,
        confirmText: actionText,
        cancelText: '取消',
    });
    if (!confirmed) {
        return;
    }

    try {
        await toggleAgentEnabled(row.id, handleUnauthorized);
        await loadAgents(page.value);
    } catch (error) {
        await alert({
            title: '操作失败',
            message: error?.message || `${actionText}失败`,
        });
    }
}

async function loadAgents(targetPage = page.value) {
    loading.value = true;
    loadError.value = '';
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await fetchAgents(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: keyword.value,
            },
            handleUnauthorized
        );
        agents.value = Array.isArray(data?.items) ? data.items : [];
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;

        if (agents.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadAgents(lastPage);
            }
        }
    } catch (error) {
        agents.value = [];
        total.value = 0;
        loadError.value = error?.message || '获取专家技能包失败';
    } finally {
        loading.value = false;
    }
}

function handlePageChange(nextPage) {
    loadAgents(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadAgents(1);
}

function handleSearch() {
    loadAgents(1);
}

onMounted(() => {
    loadAgents();
});
</script>

<template>
    <section
        class="admin-page admin-page--agent-management flex h-full min-h-0 flex-col bg-slate-100"
        data-component="AdminAgentManagementPanel"
    >
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1 class="text-3xl font-bold tracking-tight text-slate-900">专家技能包</h1>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        为不同专家场景组合身份、技能与工具，并控制面向用户的发布状态。
                    </p>
                </div>
                <button
                    type="button"
                    class="inline-flex self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 active:translate-y-px xl:self-auto"
                    @click="openAddAgentDialog"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>新建专家包</span>
                </button>
            </div>

            <div class="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ total }}</p>
                        <p class="text-[11px] text-slate-500">专家包总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ enabledAgentCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页已发布</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">
                            {{ currentPageCapabilityCount }}
                        </p>
                        <p class="text-[11px] text-slate-500">当前页能力数</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    <div class="relative min-w-0 flex-1 sm:w-72 sm:flex-none">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >
                            search
                        </span>
                        <input
                            v-model.trim="keyword"
                            type="text"
                            placeholder="搜索名称或编码"
                            class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                            @keyup.enter="handleSearch"
                        />
                    </div>
                    <button
                        type="button"
                        class="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 active:translate-y-px"
                        @click="handleSearch"
                    >
                        <span class="material-symbols-outlined text-[18px]">search</span>
                        <span>搜索</span>
                    </button>
                </div>
            </div>
        </header>

        <div class="flex min-h-0 flex-1 flex-col p-6">
            <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
                <div class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4">
                    <template v-if="loading">
                        <article
                            v-for="index in 6"
                            :key="`agent-skeleton-${index}`"
                            class="flex min-h-[238px] animate-pulse flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
                        >
                            <div class="flex items-start gap-3">
                                <div class="h-10 w-10 rounded-xl bg-slate-100"></div>
                                <div class="min-w-0 flex-1 space-y-3">
                                    <div class="h-5 w-2/3 rounded bg-slate-100"></div>
                                    <div class="h-3 w-1/2 rounded bg-slate-100"></div>
                                    <div class="h-3 w-full rounded bg-slate-100"></div>
                                </div>
                            </div>
                            <div
                                class="mt-auto grid grid-cols-3 gap-1.5 border-t border-slate-100 pt-3"
                            >
                                <div class="h-8 rounded-lg bg-slate-100"></div>
                                <div class="h-8 rounded-lg bg-slate-100"></div>
                                <div class="h-8 rounded-lg bg-slate-100"></div>
                            </div>
                        </article>
                    </template>

                    <div
                        v-else-if="loadError"
                        class="rounded-2xl border border-dashed border-rose-200 bg-white px-6 py-14 text-center text-sm text-rose-500 lg:col-span-2 xl:col-span-3 min-[1680px]:col-span-4"
                    >
                        {{ loadError }}
                    </div>

                    <div
                        v-else-if="!agents.length"
                        class="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-14 text-center text-sm text-slate-400 lg:col-span-2 xl:col-span-3 min-[1680px]:col-span-4"
                    >
                        当前筛选条件下没有匹配的专家技能包
                    </div>

                    <template v-else>
                        <article
                            v-for="agent in agents"
                            :key="agent.id"
                            class="flex min-h-[238px] cursor-pointer flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                            @click="openEditAgentDialog(agent)"
                        >
                            <div class="flex items-start gap-3">
                                <div
                                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                                >
                                    <span
                                        v-if="isMaterialIcon(resolveAgentIcon(agent))"
                                        class="material-symbols-outlined text-[21px]"
                                    >
                                        {{ resolveAgentIcon(agent) }}
                                    </span>
                                    <span v-else class="text-xl">{{
                                        resolveAgentIcon(agent)
                                    }}</span>
                                </div>
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-start justify-between gap-3">
                                        <div class="min-w-0">
                                            <h2 class="truncate text-base font-bold text-slate-900">
                                                {{ agent.agentName || '-' }}
                                            </h2>
                                            <p class="mt-0.5 truncate text-[11px] text-slate-400">
                                                {{ agent.agentCode || '未设置编码' }}
                                            </p>
                                        </div>
                                        <span
                                            class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                            :class="agentStatusClass(agent)"
                                        >
                                            {{ agent.enabled === 1 ? '已发布' : '未发布' }}
                                        </span>
                                    </div>
                                    <p
                                        class="mt-2 line-clamp-2 min-h-10 text-xs leading-5 text-slate-500"
                                    >
                                        {{ agent.description || '暂无描述' }}
                                    </p>
                                </div>
                            </div>

                            <div class="mt-3 flex flex-wrap gap-1.5">
                                <span
                                    class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                                >
                                    {{ Number(agent.skillCount ?? 0) }} 技能
                                </span>
                                <span
                                    class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                                >
                                    {{ Number(agent.toolCount ?? 0) }} 工具
                                </span>
                                <span
                                    class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                                >
                                    {{
                                        Number(agent.skillCount ?? 0) + Number(agent.toolCount ?? 0)
                                    }}
                                    能力
                                </span>
                                <span
                                    class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                                >
                                    更新于 {{ formatDateTime(agent.updatedAt) }}
                                </span>
                            </div>

                            <div class="mt-auto border-t border-slate-100 pt-3">
                                <div class="grid grid-cols-3 gap-1.5">
                                    <button
                                        type="button"
                                        aria-label="编辑专家技能包"
                                        class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700 active:translate-y-px"
                                        @click.stop="openEditAgentDialog(agent)"
                                    >
                                        <span class="material-symbols-outlined text-[17px]"
                                            >edit</span
                                        >
                                        <span>编辑</span>
                                    </button>
                                    <button
                                        type="button"
                                        :aria-label="
                                            agent.enabled === 1
                                                ? '停用专家技能包'
                                                : '启用专家技能包'
                                        "
                                        class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-blue-100 bg-blue-50 px-2 text-xs font-semibold text-blue-700 transition hover:border-blue-200 hover:bg-blue-100 active:translate-y-px"
                                        @click.stop="openToggleAgentEnabledDialog(agent)"
                                    >
                                        <span class="material-symbols-outlined text-[17px]">
                                            {{
                                                agent.enabled === 1 ? 'pause_circle' : 'play_circle'
                                            }}
                                        </span>
                                        <span>{{ agent.enabled === 1 ? '停用' : '启用' }}</span>
                                    </button>
                                    <button
                                        type="button"
                                        aria-label="删除专家技能包"
                                        class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 active:translate-y-px"
                                        @click.stop="openDeleteAgentDialog(agent)"
                                    >
                                        <span class="material-symbols-outlined text-[17px]"
                                            >delete</span
                                        >
                                        <span>删除</span>
                                    </button>
                                </div>
                            </div>
                        </article>
                    </template>
                </div>
            </div>
            <div class="flex h-[54px] shrink-0 items-end justify-end">
                <MiniPagination
                    :page="page"
                    :page-size="pageSize"
                    :total="total"
                    :page-size-options="pageSizeOptions"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>
        </div>
    </section>
</template>
