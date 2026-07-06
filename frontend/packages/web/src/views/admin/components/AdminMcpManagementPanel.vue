<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { deleteMcpServer, listMcpServerPage, refreshMcpServer } from '@/api/skills';
import MiniPagination from '@/components/MiniPagination.vue';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    formatMcpTime,
    getMcpAuthLabel,
    getMcpPermissionScopeLabel,
    getMcpRefreshStatusMeta,
    getMcpServerScopeLabel,
    getMcpTransportLabel,
    summarizeMcpTools,
} from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const searchKeyword = ref('');
const mcpServers = ref([]);
const refreshingServerId = ref(null);
const deletingServerId = ref(null);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];

const enabledCount = computed(() => mcpServers.value.filter(server => server.enabled).length);
const totalToolCount = computed(() =>
    mcpServers.value.reduce((total, server) => total + Number(server.toolCount || 0), 0)
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadServers(targetPage = page.value) {
    loading.value = true;
    loadError.value = '';
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await listMcpServerPage(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: searchKeyword.value,
            },
            handleUnauthorized
        );
        mcpServers.value = Array.isArray(data?.list) ? data.list : [];
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;
        if (mcpServers.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadServers(lastPage);
            }
        }
    } catch (error) {
        loadError.value = error?.message || 'MCP 服务加载失败';
        mcpServers.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
}

function handleSearch() {
    loadServers(1);
}

function handlePageChange(nextPage) {
    loadServers(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadServers(1);
}

function openCreatePage() {
    router.push(ROUTE_PATHS.adminMcpManagementCreate);
}

function openDetailPage(server) {
    if (!server?.id || !canViewDetail(server)) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementDetail(server.id));
}

function openEditPage(server) {
    if (!server?.id || !server?.canOperate) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementEdit(server.id));
}

function canOperate(server) {
    return server?.canOperate !== false;
}

function canViewDetail(server) {
    return server?.canViewDetail !== false;
}

function getToolSummary(server) {
    return summarizeMcpTools(server?.tools || []);
}

async function handleRefreshServer(server) {
    if (!server?.id || refreshingServerId.value) {
        return;
    }
    refreshingServerId.value = server.id;
    try {
        const result = await refreshMcpServer(server.id, handleUnauthorized);
        await loadServers();
        await alert({
            title: '刷新完成',
            message: `MCP 服务 ${result?.serverKey || server.serverKey} 已同步 ${result?.toolCount ?? 0} 个工具。`,
        });
    } catch (error) {
        await alert({
            title: '刷新失败',
            message: error?.message || 'MCP 工具目录刷新失败',
        });
    } finally {
        refreshingServerId.value = null;
    }
}

async function handleDeleteServer(server) {
    if (!server?.id || deletingServerId.value) {
        return;
    }
    const confirmed = await confirm({
        title: '删除 MCP 服务',
        message: `确认删除 MCP 服务“${server.displayName || server.serverKey}”吗？其已同步工具和相关技能手动绑定会一起清理。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }
    deletingServerId.value = server.id;
    try {
        await deleteMcpServer(server.id, handleUnauthorized);
        await loadServers(page.value);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除 MCP 服务失败',
        });
    } finally {
        deletingServerId.value = null;
    }
}

onMounted(() => {
    loadServers();
});
</script>

<template>
    <section class="admin-page flex h-full min-h-0 flex-col bg-slate-100">
        <header class="shrink-0 border-b border-slate-200 bg-white px-6 py-4">
            <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1
                        class="text-[28px] font-semibold leading-tight tracking-tight text-slate-900"
                    >
                        MCP 服务管理
                    </h1>
                    <p class="mt-1.5 max-w-3xl text-[13px] leading-5 text-slate-500">
                        在这里管理远程 MCP
                        server、查看已同步工具，并进入独立详情页处理刷新、编辑和删除操作。
                    </p>
                </div>
                <button
                    type="button"
                    class="inline-flex h-9 self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-3.5 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 xl:self-auto"
                    @click="openCreatePage"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>新建 MCP 服务</span>
                </button>
            </div>

            <div class="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">{{ total }}</p>
                        <p class="mt-0.5 text-xs text-slate-500">MCP 服务总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">{{ enabledCount }}</p>
                        <p class="mt-0.5 text-xs text-slate-500">当前页启用</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">{{ totalToolCount }}</p>
                        <p class="mt-0.5 text-xs text-slate-500">当前页工具数</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    <label class="relative min-w-0 flex-1 sm:w-72 sm:flex-none">
                        <span
                            class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-lg text-slate-400"
                            >search</span
                        >
                        <input
                            v-model.trim="searchKeyword"
                            type="text"
                            placeholder="搜索名称、编码或地址"
                            class="w-full h-9 rounded-xl border border-slate-200 bg-white pl-10 pr-3 text-[13px] transition focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500/10"
                            @keyup.enter="handleSearch"
                        />
                    </label>
                    <button
                        type="button"
                        class="inline-flex h-9 items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 text-[13px] font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="handleSearch"
                    >
                        <span class="material-symbols-outlined text-[18px]">search</span>
                        <span>搜索</span>
                    </button>
                </div>
            </div>
        </header>

        <div class="flex min-h-0 flex-1 flex-col p-5">
            <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
                <p
                    v-if="loadError"
                    class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ loadError }}
                </p>

                <div
                    v-if="loading"
                    class="rounded-2xl border border-slate-200 bg-white px-5 py-8 text-sm text-slate-400 shadow-sm"
                >
                    MCP 服务加载中...
                </div>

                <div
                    v-else-if="!mcpServers.length"
                    class="flex min-h-[340px] items-center justify-center"
                >
                    <div
                        class="w-full rounded-2xl border border-dashed border-slate-200 bg-white px-5 py-12 text-center text-sm text-slate-400"
                    >
                        当前筛选条件下没有匹配的 MCP 服务
                    </div>
                </div>

                <div
                    v-else
                    class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4"
                >
                    <article
                        v-for="server in mcpServers"
                        :key="server.id"
                        class="flex min-h-[212px] flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                    >
                        <div class="flex items-start gap-3">
                            <span
                                class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                            >
                                <span class="material-symbols-outlined text-[20px]">hub</span>
                            </span>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <h3
                                            class="truncate text-[15px] font-semibold text-slate-900"
                                        >
                                            {{ server.displayName }}
                                        </h3>
                                        <p class="mt-0.5 truncate text-xs text-slate-400">
                                            {{ server.serverKey }}
                                        </p>
                                    </div>
                                    <span
                                        class="inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold"
                                        :class="
                                            getMcpRefreshStatusMeta(server.lastRefreshStatus)
                                                .badgeClass
                                        "
                                    >
                                        {{
                                            getMcpRefreshStatusMeta(server.lastRefreshStatus).label
                                        }}
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 min-h-9 text-[13px] leading-[18px] text-slate-500"
                                >
                                    {{ server.description || server.endpoint || '暂无描述' }}
                                </p>
                            </div>
                        </div>

                        <div class="mt-3 flex flex-wrap gap-1.5">
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ getMcpServerScopeLabel(server.serverScope) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ getMcpTransportLabel(server.transportType) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ getMcpAuthLabel(server.authType, server.hasAuthConfig) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ getMcpPermissionScopeLabel(server.permissionScope) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ server.enabled ? '启用中' : '已停用' }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                            >
                                {{ server.toolCount }} 个工具
                            </span>
                        </div>

                        <div class="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                            <div class="flex items-center justify-between gap-3">
                                <div class="min-w-0">
                                    <p class="text-xs font-semibold text-slate-400">
                                        已同步工具预览
                                    </p>
                                    <p class="mt-0.5 truncate text-xs font-medium text-slate-600">
                                        {{
                                            server.toolCount
                                                ? '已同步工具预览'
                                                : '尚未同步到工具目录'
                                        }}
                                    </p>
                                </div>
                                <button
                                    v-if="canViewDetail(server)"
                                    type="button"
                                    class="inline-flex h-7 shrink-0 items-center justify-center gap-1 rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                                    @click="openDetailPage(server)"
                                >
                                    <span class="material-symbols-outlined text-[15px]"
                                        >visibility</span
                                    >
                                    <span>详情</span>
                                </button>
                            </div>

                            <div
                                v-if="getToolSummary(server).preview.length"
                                class="mt-2 flex flex-wrap gap-1.5"
                            >
                                <span
                                    v-for="tool in getToolSummary(server).preview"
                                    :key="tool.id || tool.toolName"
                                    class="inline-flex items-center rounded-full bg-white px-2 py-1 text-xs font-medium text-slate-600 ring-1 ring-slate-200"
                                >
                                    {{ tool.displayName || tool.remoteToolName || tool.toolName }}
                                </span>
                                <span
                                    v-if="getToolSummary(server).remainingCount"
                                    class="inline-flex items-center rounded-full bg-white px-2 py-1 text-xs font-medium text-slate-500 ring-1 ring-slate-200"
                                >
                                    +{{ getToolSummary(server).remainingCount }} more
                                </span>
                            </div>
                            <p v-else class="mt-2 text-xs text-slate-400">
                                还没有同步到平台工具库。
                            </p>
                        </div>

                        <div class="mt-3 grid gap-1.5 text-xs text-slate-400 sm:grid-cols-2">
                            <div class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                                <p class="text-xs font-semibold text-slate-400">最后刷新</p>
                                <p class="mt-0.5 truncate text-xs text-slate-600">
                                    {{ formatMcpTime(server.lastRefreshedAt) }}
                                </p>
                            </div>
                            <div class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                                <p class="text-xs font-semibold text-slate-400">刷新摘要</p>
                                <p class="mt-0.5 line-clamp-1 text-xs text-slate-600">
                                    {{ server.lastRefreshMessage || '暂无刷新摘要' }}
                                </p>
                            </div>
                        </div>

                        <div
                            class="mt-auto grid gap-1.5 border-t border-slate-100 pt-2.5"
                            :class="
                                canOperate(server)
                                    ? 'grid-flow-col auto-cols-fr'
                                    : canViewDetail(server)
                                      ? 'grid-cols-1'
                                      : ''
                            "
                        >
                            <button
                                v-if="canViewDetail(server)"
                                type="button"
                                aria-label="查看 MCP 服务详情"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                                @click="openDetailPage(server)"
                            >
                                <span class="material-symbols-outlined text-[17px]"
                                    >visibility</span
                                >
                                <span>详情</span>
                            </button>
                            <button
                                v-if="canOperate(server)"
                                type="button"
                                aria-label="编辑 MCP 服务"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                                @click="openEditPage(server)"
                            >
                                <span class="material-symbols-outlined text-[17px]">edit</span>
                                <span>编辑</span>
                            </button>
                            <button
                                v-if="canOperate(server)"
                                type="button"
                                aria-label="刷新 MCP 工具目录"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="refreshingServerId === server.id"
                                @click="handleRefreshServer(server)"
                            >
                                <span
                                    class="material-symbols-outlined text-[17px]"
                                    :class="{ 'animate-spin': refreshingServerId === server.id }"
                                >
                                    refresh
                                </span>
                                <span>
                                    {{ refreshingServerId === server.id ? '刷新中...' : '刷新' }}
                                </span>
                            </button>
                            <button
                                v-if="canOperate(server)"
                                type="button"
                                aria-label="删除 MCP 服务"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="deletingServerId === server.id"
                                @click="handleDeleteServer(server)"
                            >
                                <span class="material-symbols-outlined text-[17px]">delete</span>
                                <span>
                                    {{ deletingServerId === server.id ? '删除中...' : '删除' }}
                                </span>
                            </button>
                        </div>
                    </article>
                </div>
            </div>
            <div class="flex h-12 shrink-0 items-end justify-end">
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
