<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { deleteMcpServer, listMcpServers, refreshMcpServer } from '@/api/skills';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    formatMcpTime,
    getMcpAuthLabel,
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

const filteredServers = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    if (!keyword) {
        return mcpServers.value;
    }
    return mcpServers.value.filter(server => {
        const fields = [
            server.displayName,
            server.serverKey,
            server.endpoint,
            server.description,
            ...(Array.isArray(server.tools)
                ? server.tools.flatMap(tool => [tool.displayName, tool.remoteToolName, tool.toolName])
                : []),
        ];
        return fields.some(field => (field || '').toLowerCase().includes(keyword));
    });
});

const enabledCount = computed(() => mcpServers.value.filter(server => server.enabled).length);
const totalToolCount = computed(() =>
    mcpServers.value.reduce((total, server) => total + Number(server.toolCount || 0), 0)
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadServers() {
    loading.value = true;
    loadError.value = '';
    try {
        const serverData = await listMcpServers(handleUnauthorized);
        mcpServers.value = Array.isArray(serverData) ? serverData : [];
    } catch (error) {
        loadError.value = error?.message || 'MCP 服务加载失败';
        mcpServers.value = [];
    } finally {
        loading.value = false;
    }
}

function openCreatePage() {
    router.push(ROUTE_PATHS.adminMcpManagementCreate);
}

function openDetailPage(server) {
    if (!server?.id) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementDetail(server.id));
}

function openEditPage(server) {
    if (!server?.id) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementEdit(server.id));
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
        mcpServers.value = mcpServers.value.filter(item => item.id !== server.id);
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
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        MCP Services
                    </p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">MCP 服务管理</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        在这里管理远程 MCP server、查看已同步工具，并进入独立详情页处理刷新、编辑和删除操作。
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <label class="relative block">
                        <span
                            class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-lg text-slate-400"
                        >search</span>
                        <input
                            v-model.trim="searchKeyword"
                            type="text"
                            placeholder="搜索 server、endpoint 或 tool"
                            class="w-full rounded-2xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15 sm:w-72"
                        >
                    </label>
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="loadServers"
                    >
                        重新加载
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-hover"
                        @click="openCreatePage"
                    >
                        新建 MCP 服务
                    </button>
                </div>
            </div>

            <div class="mt-6 grid gap-3 md:grid-cols-3">
                <article class="rounded-3xl border border-slate-200 bg-slate-50 px-5 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Servers
                    </p>
                    <p class="mt-2 text-3xl font-bold text-slate-900">{{ mcpServers.length }}</p>
                    <p class="mt-1 text-xs text-slate-500">已配置的 MCP 服务</p>
                </article>
                <article class="rounded-3xl border border-slate-200 bg-slate-50 px-5 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Enabled
                    </p>
                    <p class="mt-2 text-3xl font-bold text-emerald-600">{{ enabledCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">当前启用中的服务</p>
                </article>
                <article class="rounded-3xl border border-slate-200 bg-slate-50 px-5 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Tools
                    </p>
                    <p class="mt-2 text-3xl font-bold text-blue-600">{{ totalToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">已同步到平台的 MCP 工具</p>
                </article>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-8 pb-10 pt-6">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                MCP 服务加载中...
            </div>

            <div
                v-else-if="!filteredServers.length"
                class="flex min-h-[340px] items-center justify-center"
            >
                <div
                    class="w-full max-w-xl rounded-[32px] border border-dashed border-slate-300 bg-white px-10 py-14 text-center shadow-sm"
                >
                    <div
                        class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-100 text-slate-400"
                    >
                        <span class="material-symbols-outlined text-3xl">hub_off</span>
                    </div>
                    <h3 class="mt-5 text-xl font-semibold text-slate-900">暂无匹配的 MCP 服务</h3>
                    <p class="mt-2 text-sm leading-6 text-slate-500">
                        先新建一个 MCP 服务，或者换个关键词继续查找。
                    </p>
                    <button
                        type="button"
                        class="mt-6 rounded-2xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-hover"
                        @click="openCreatePage"
                    >
                        去新建
                    </button>
                </div>
            </div>

            <div v-else class="grid gap-5 xl:grid-cols-2 2xl:grid-cols-3">
                <article
                    v-for="server in filteredServers"
                    :key="server.id"
                    class="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div class="min-w-0">
                            <div class="flex flex-wrap items-center gap-2">
                                <h3 class="truncate text-xl font-bold text-slate-900">
                                    {{ server.displayName }}
                                </h3>
                                <span
                                    class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600"
                                >
                                    {{ server.serverKey }}
                                </span>
                                <span
                                    class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-semibold"
                                    :class="getMcpRefreshStatusMeta(server.lastRefreshStatus).badgeClass"
                                >
                                    <span
                                        class="h-1.5 w-1.5 rounded-full"
                                        :class="getMcpRefreshStatusMeta(server.lastRefreshStatus).dotClass"
                                    />
                                    {{ getMcpRefreshStatusMeta(server.lastRefreshStatus).label }}
                                </span>
                            </div>
                            <p class="mt-3 break-all text-sm leading-6 text-slate-500">
                                {{ server.endpoint }}
                            </p>
                            <p
                                v-if="server.description"
                                class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500"
                            >
                                {{ server.description }}
                            </p>
                        </div>
                        <span
                            class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-primary"
                        >
                            <span class="material-symbols-outlined">hub</span>
                        </span>
                    </div>

                    <div class="mt-4 flex flex-wrap gap-2">
                        <span
                            class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                            {{ getMcpServerScopeLabel(server.serverScope) }}
                        </span>
                        <span
                            class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                            {{ getMcpTransportLabel(server.transportType) }}
                        </span>
                        <span
                            class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                            {{ getMcpAuthLabel(server.authType, server.hasAuthConfig) }}
                        </span>
                        <span
                            class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                            {{ server.enabled ? '启用中' : '已停用' }}
                        </span>
                        <span
                            class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                            {{ server.toolCount }} 个工具
                        </span>
                    </div>

                    <div class="mt-5 rounded-3xl border border-slate-200 bg-slate-50 p-4">
                        <div class="flex items-center justify-between gap-3">
                            <div>
                                <p class="text-xs font-semibold uppercase tracking-[0.22em] text-slate-400">
                                    Tools Preview
                                </p>
                                <p class="mt-1 text-sm font-medium text-slate-700">
                                    {{ server.toolCount ? '已同步工具预览' : '尚未同步到工具目录' }}
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                                @click="openDetailPage(server)"
                            >
                                查看详情
                            </button>
                        </div>

                        <div
                            v-if="getToolSummary(server).preview.length"
                            class="mt-4 flex flex-wrap gap-2"
                        >
                            <span
                                v-for="tool in getToolSummary(server).preview"
                                :key="tool.id || tool.toolName"
                                class="inline-flex items-center rounded-full bg-white px-3 py-1.5 text-xs font-medium text-slate-700 ring-1 ring-slate-200"
                            >
                                {{ tool.displayName || tool.remoteToolName || tool.toolName }}
                            </span>
                            <span
                                v-if="getToolSummary(server).remainingCount"
                                class="inline-flex items-center rounded-full bg-white px-3 py-1.5 text-xs font-medium text-slate-500 ring-1 ring-slate-200"
                            >
                                +{{ getToolSummary(server).remainingCount }} more
                            </span>
                        </div>
                        <p v-else class="mt-4 text-sm text-slate-400">
                            还没有同步到平台工具库，先刷新一次工具目录。
                        </p>
                    </div>

                    <div class="mt-4 grid gap-2 text-xs text-slate-400 sm:grid-cols-2">
                        <div class="rounded-2xl bg-slate-50 px-3 py-2">
                            <p class="font-semibold uppercase tracking-[0.18em] text-slate-400">
                                Last Refresh
                            </p>
                            <p class="mt-1 text-sm text-slate-600">
                                {{ formatMcpTime(server.lastRefreshedAt) }}
                            </p>
                        </div>
                        <div class="rounded-2xl bg-slate-50 px-3 py-2">
                            <p class="font-semibold uppercase tracking-[0.18em] text-slate-400">
                                Message
                            </p>
                            <p class="mt-1 line-clamp-2 text-sm text-slate-600">
                                {{ server.lastRefreshMessage || '暂无刷新摘要' }}
                            </p>
                        </div>
                    </div>

                    <div class="mt-5 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
                        <button
                            type="button"
                            class="rounded-2xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                            @click="openDetailPage(server)"
                        >
                            详情
                        </button>
                        <button
                            type="button"
                            class="rounded-2xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                            @click="openEditPage(server)"
                        >
                            编辑
                        </button>
                        <button
                            type="button"
                            class="rounded-2xl bg-blue-600 px-3 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="refreshingServerId === server.id"
                            @click="handleRefreshServer(server)"
                        >
                            {{ refreshingServerId === server.id ? '刷新中...' : '刷新' }}
                        </button>
                        <button
                            type="button"
                            class="rounded-2xl bg-rose-600 px-3 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="deletingServerId === server.id"
                            @click="handleDeleteServer(server)"
                        >
                            {{ deletingServerId === server.id ? '删除中...' : '删除' }}
                        </button>
                    </div>
                </article>
            </div>
        </div>
    </section>
</template>
