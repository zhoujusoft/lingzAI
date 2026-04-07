<script setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { deleteMcpServer, getMcpServerDetail, refreshMcpServer } from '@/api/skills';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    formatMcpTime,
    getMcpAuthLabel,
    getMcpServerScopeLabel,
    getMcpTransportLabel,
} from '@/views/admin/components/mcp-management/mcpManagementShared';

const props = defineProps({
    serverId: {
        type: Number,
        default: null,
    },
});

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const server = ref(null);
const refreshing = ref(false);
const deleting = ref(false);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadServerDetail() {
    if (!props.serverId) {
        server.value = null;
        loadError.value = 'MCP 服务 ID 无效';
        return;
    }
    loading.value = true;
    loadError.value = '';
    try {
        server.value = await getMcpServerDetail(props.serverId, handleUnauthorized);
    } catch (error) {
        server.value = null;
        loadError.value = error?.message || 'MCP 服务详情加载失败';
    } finally {
        loading.value = false;
    }
}

function backToList() {
    router.push(ROUTE_PATHS.adminMcpManagement);
}

function openEditPage() {
    if (!server.value?.id) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementEdit(server.value.id));
}

async function handleRefresh() {
    if (!server.value?.id || refreshing.value) {
        return;
    }
    refreshing.value = true;
    try {
        const result = await refreshMcpServer(server.value.id, handleUnauthorized);
        await loadServerDetail();
        await alert({
            title: '刷新完成',
            message: `MCP 服务 ${result?.serverKey || server.value?.serverKey} 已同步 ${result?.toolCount ?? 0} 个工具。`,
        });
    } catch (error) {
        await alert({
            title: '刷新失败',
            message: error?.message || 'MCP 工具目录刷新失败',
        });
    } finally {
        refreshing.value = false;
    }
}

async function handleDelete() {
    if (!server.value?.id || deleting.value) {
        return;
    }
    const confirmed = await confirm({
        title: '删除 MCP 服务',
        message: `确认删除 MCP 服务“${server.value.displayName || server.value.serverKey}”吗？其已同步工具和相关技能手动绑定会一起清理。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }
    deleting.value = true;
    try {
        await deleteMcpServer(server.value.id, handleUnauthorized);
        router.replace(ROUTE_PATHS.adminMcpManagement);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除 MCP 服务失败',
        });
    } finally {
        deleting.value = false;
    }
}

watch(
    () => props.serverId,
    () => {
        loadServerDetail();
    },
    { immediate: true }
);
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                <div class="min-w-0">
                    <button
                        type="button"
                        class="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="backToList"
                    >
                        <span class="material-symbols-outlined text-base">arrow_back</span>
                        返回列表
                    </button>
                    <p
                        class="mt-5 text-xs font-semibold uppercase tracking-[0.32em] text-slate-400"
                    >
                        MCP Detail
                    </p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">
                        {{ server?.displayName || 'MCP 服务详情' }}
                    </h2>
                    <p class="mt-2 text-sm leading-6 text-slate-500">
                        {{
                            server?.description || '查看当前 MCP 服务配置、连接状态和完整工具清单。'
                        }}
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="openEditPage"
                    >
                        编辑服务
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="refreshing || !server?.id"
                        @click="handleRefresh"
                    >
                        {{ refreshing ? '刷新中...' : '刷新工具目录' }}
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="deleting || !server?.id"
                        @click="handleDelete"
                    >
                        {{ deleting ? '删除中...' : '删除服务' }}
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-8 pb-10 pt-6">
            <div
                v-if="loading && !server"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                MCP 服务详情加载中...
            </div>

            <div
                v-else-if="loadError && !server"
                class="rounded-[30px] border border-rose-200 bg-white p-8 shadow-sm"
            >
                <h3 class="text-lg font-semibold text-rose-600">详情加载失败</h3>
                <p class="mt-2 text-sm leading-6 text-slate-500">{{ loadError }}</p>
                <button
                    type="button"
                    class="mt-5 rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                    @click="backToList"
                >
                    返回 MCP 列表
                </button>
            </div>

            <template v-else-if="server">
                <p
                    v-if="loadError"
                    class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ loadError }}
                </p>

                <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex items-center justify-between gap-4">
                        <div>
                            <h3 class="text-xl font-bold text-slate-900">服务配置</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                这里展示当前 MCP 服务的连接方式、鉴权和时间信息。
                            </p>
                        </div>
                    </div>

                    <div class="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Scope
                            </p>
                            <p class="mt-2 text-sm text-slate-700">
                                {{ getMcpServerScopeLabel(server.serverScope) }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Server Key
                            </p>
                            <p class="mt-2 break-all font-mono text-sm text-slate-700">
                                {{ server.serverKey }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Endpoint
                            </p>
                            <p class="mt-2 break-all text-sm text-slate-700">
                                {{ server.endpoint }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Transport
                            </p>
                            <p class="mt-2 text-sm text-slate-700">
                                {{ getMcpTransportLabel(server.transportType) }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Auth
                            </p>
                            <p class="mt-2 text-sm text-slate-700">
                                {{ getMcpAuthLabel(server.authType, server.hasAuthConfig) }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Last Refreshed
                            </p>
                            <p class="mt-2 text-sm text-slate-700">
                                {{ formatMcpTime(server.lastRefreshedAt) }}
                            </p>
                        </article>
                        <article class="rounded-3xl bg-slate-50 px-4 py-4">
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400"
                            >
                                Updated
                            </p>
                            <p class="mt-2 text-sm text-slate-700">
                                {{ formatMcpTime(server.updatedAt) }}
                            </p>
                        </article>
                    </div>
                </section>

                <section class="mt-6 rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <h3 class="text-xl font-bold text-slate-900">工具列表</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                当前 server 已同步的 MCP
                                tools。工具是否被技能使用，仍然在技能详情里按 tool 绑定。
                            </p>
                        </div>
                        <div
                            class="rounded-full bg-slate-100 px-3 py-1.5 text-sm font-semibold text-slate-600"
                        >
                            {{ server.toolCount }} 个工具
                        </div>
                    </div>

                    <div
                        v-if="!Array.isArray(server.tools) || !server.tools.length"
                        class="mt-6 rounded-3xl border border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center"
                    >
                        <div
                            class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-slate-400 ring-1 ring-slate-200"
                        >
                            <span class="material-symbols-outlined text-3xl">construction</span>
                        </div>
                        <h4 class="mt-4 text-lg font-semibold text-slate-800">还没有同步到工具</h4>
                        <p class="mt-2 text-sm leading-6 text-slate-500">
                            先刷新一次工具目录，把远程 MCP tools 拉到平台中。
                        </p>
                        <button
                            type="button"
                            class="mt-5 rounded-2xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700"
                            @click="handleRefresh"
                        >
                            立即刷新
                        </button>
                    </div>

                    <div v-else class="mt-6 grid gap-4 lg:grid-cols-2">
                        <article
                            v-for="tool in server.tools"
                            :key="tool.id || tool.toolName"
                            class="rounded-[28px] border border-slate-200 bg-slate-50 p-5"
                        >
                            <div class="flex items-start justify-between gap-3">
                                <div class="min-w-0">
                                    <h4 class="truncate text-lg font-semibold text-slate-900">
                                        {{
                                            tool.displayName || tool.remoteToolName || tool.toolName
                                        }}
                                    </h4>
                                    <p class="mt-2 font-mono text-xs text-slate-500">
                                        {{ tool.toolName }}
                                    </p>
                                </div>
                                <span
                                    class="rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200"
                                >
                                    {{ tool.bindable ? '可绑定' : '不可绑定' }}
                                </span>
                            </div>

                            <div class="mt-4 flex flex-wrap gap-2">
                                <span
                                    v-if="tool.remoteToolName"
                                    class="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-slate-600 ring-1 ring-slate-200"
                                >
                                    remote: {{ tool.remoteToolName }}
                                </span>
                                <span
                                    class="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-slate-600 ring-1 ring-slate-200"
                                >
                                    更新于 {{ formatMcpTime(tool.updatedAt) }}
                                </span>
                            </div>

                            <p class="mt-4 text-sm leading-6 text-slate-600">
                                {{ tool.description || '暂无工具描述' }}
                            </p>
                        </article>
                    </div>
                </section>
            </template>
        </div>
    </section>
</template>
