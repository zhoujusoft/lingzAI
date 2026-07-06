<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import { createMcpServer, getMcpServerDetail, updateMcpServer } from '@/api/skills';
import { alert } from '@/composables/useModal';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { canChangeResourcePermission } from '@/model/resource-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    ADMIN_SELECT_BUTTON_CLASS,
    MCP_AUTH_OPTIONS,
    MCP_RESOURCE_PERMISSION_OPTIONS,
    MCP_SERVER_SCOPE_OPTIONS,
    MCP_TRANSPORT_OPTIONS,
    getMcpPermissionScopeDescription,
    normalizeMcpPermissionScope,
} from '@/views/admin/components/mcp-management/mcpManagementShared';

const props = defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    serverId: {
        type: Number,
        default: null,
    },
});

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const existingServer = ref(null);

const serverForm = reactive({
    serverKey: '',
    displayName: '',
    description: '',
    permissionScope: 3,
    serverScope: 'INTERNAL',
    transportType: 'STREAMABLE_HTTP',
    endpoint: '',
    authType: 'NONE',
    authToken: '',
    headers: [],
    enabled: true,
});

const isEditMode = computed(() => props.mode === 'edit');
const pageTitle = computed(() => (isEditMode.value ? '编辑 MCP 服务' : '新建 MCP 服务'));
const canOperateCurrent = computed(
    () => !isEditMode.value || existingServer.value?.canOperate !== false
);
const canEditPermissionScope = computed(
    () =>
        !isEditMode.value ||
        canChangeResourcePermission(existingServer.value, currentUserState.profile)
);
const pageDescription = computed(() =>
    isEditMode.value
        ? '调整现有 MCP 服务的连接配置。保存后直接返回列表，需要时再从列表进入详情页。'
        : '创建一个新的 MCP 服务连接，保存后进入详情页查看并刷新工具目录。'
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function resetForm() {
    serverForm.serverKey = '';
    serverForm.displayName = '';
    serverForm.description = '';
    serverForm.permissionScope = 3;
    serverForm.serverScope = 'INTERNAL';
    serverForm.transportType = 'STREAMABLE_HTTP';
    serverForm.endpoint = '';
    serverForm.authType = 'NONE';
    serverForm.authToken = '';
    serverForm.headers = [];
    serverForm.enabled = true;
}

function fillForm(server) {
    existingServer.value = server || null;
    serverForm.serverKey = server?.serverKey || '';
    serverForm.displayName = server?.displayName || '';
    serverForm.description = server?.description || '';
    serverForm.permissionScope = normalizeMcpPermissionScope(server?.permissionScope);
    serverForm.serverScope = server?.serverScope || 'INTERNAL';
    serverForm.transportType = server?.transportType || 'STREAMABLE_HTTP';
    serverForm.endpoint = server?.endpoint || '';
    serverForm.authType = server?.authType || 'NONE';
    serverForm.authToken = '';
    serverForm.enabled = server?.enabled !== false;

    // 解析 headersJson 为数组
    serverForm.headers = parseHeadersJson(server?.headersJson);
}

function parseHeadersJson(jsonStr) {
    if (!jsonStr) return [];
    try {
        const obj = JSON.parse(jsonStr);
        if (typeof obj !== 'object' || obj === null) return [];
        return Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }));
    } catch {
        return [];
    }
}

function serializeHeaders() {
    if (!serverForm.headers || serverForm.headers.length === 0) return '';
    const obj = {};
    serverForm.headers.forEach(h => {
        if (h.key && h.value) {
            obj[h.key] = h.value;
        }
    });
    return Object.keys(obj).length > 0 ? JSON.stringify(obj) : '';
}

function addHeader() {
    serverForm.headers.push({ key: '', value: '' });
}

function removeHeader(index) {
    serverForm.headers.splice(index, 1);
}

function goBack() {
    router.push(ROUTE_PATHS.adminMcpManagement);
}

function openDetailPage() {
    if (!props.serverId) {
        return;
    }
    router.push(ROUTE_PATHS.adminMcpManagementDetail(props.serverId));
}

function serializeAuthConfig() {
    if (serverForm.authType !== 'BEARER_TOKEN') {
        return '';
    }
    const token = serverForm.authToken.trim();
    return token ? JSON.stringify({ token }) : '';
}

async function loadServerDetail() {
    if (!isEditMode.value) {
        existingServer.value = null;
        loadError.value = '';
        resetForm();
        return;
    }
    if (!props.serverId) {
        existingServer.value = null;
        loadError.value = 'MCP 服务 ID 无效';
        return;
    }
    loading.value = true;
    loadError.value = '';
    try {
        const data = await getMcpServerDetail(props.serverId, handleUnauthorized);
        fillForm(data);
    } catch (error) {
        existingServer.value = null;
        loadError.value = error?.message || 'MCP 服务详情加载失败';
    } finally {
        loading.value = false;
    }
}

async function handleSubmit() {
    if (saving.value) {
        return;
    }
    saving.value = true;
    try {
        const permissionScopeForSubmit = canEditPermissionScope.value
            ? normalizeMcpPermissionScope(serverForm.permissionScope)
            : normalizeMcpPermissionScope(existingServer.value?.permissionScope);
        const payload = {
            displayName: serverForm.displayName,
            description: serverForm.description,
            permissionScope: permissionScopeForSubmit,
            serverScope: serverForm.serverScope,
            transportType: serverForm.transportType,
            endpoint: serverForm.endpoint,
            authType: serverForm.authType,
            authConfigJson: serializeAuthConfig(),
            headersJson: serializeHeaders() || null,
            enabled: serverForm.enabled,
        };
        let result = null;
        if (isEditMode.value) {
            await updateMcpServer(props.serverId, payload, handleUnauthorized);
            router.replace(ROUTE_PATHS.adminMcpManagement);
            return;
        } else {
            result = await createMcpServer(
                {
                    ...payload,
                    serverKey: serverForm.serverKey,
                },
                handleUnauthorized
            );
        }
        const nextServerId = result?.id || props.serverId;
        if (nextServerId) {
            router.replace(ROUTE_PATHS.adminMcpManagementDetail(nextServerId));
            return;
        }
        router.replace(ROUTE_PATHS.adminMcpManagement);
    } catch (error) {
        await alert({
            title: isEditMode.value ? '保存失败' : '创建失败',
            message: error?.message || 'MCP 服务保存失败',
        });
    } finally {
        saving.value = false;
    }
}

watch(
    () => serverForm.authType,
    authType => {
        if (authType === 'NONE') {
            serverForm.authToken = '';
        }
    }
);

watch(
    () => [props.mode, props.serverId],
    () => {
        loadServerDetail();
    },
    { immediate: true }
);
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                <div>
                    <button
                        type="button"
                        class="mb-4 inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="goBack"
                    >
                        <span class="material-symbols-outlined text-base">arrow_back</span>
                        返回列表
                    </button>
                    <h2 class="text-3xl font-bold tracking-tight text-slate-900">
                        {{ pageTitle }}
                    </h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        {{ pageDescription }}
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <button
                        v-if="isEditMode && props.serverId"
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="openDetailPage"
                    >
                        查看详情
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving || loading || !canOperateCurrent"
                        @click="handleSubmit"
                    >
                        {{ saving ? '保存中...' : isEditMode ? '保存修改' : '创建 MCP 服务' }}
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-8 pb-10 pt-6">
            <div
                v-if="loading"
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                MCP 服务信息加载中...
            </div>

            <div
                v-else-if="loadError && isEditMode && !existingServer"
                class="rounded-[30px] border border-rose-200 bg-white p-8 shadow-sm"
            >
                <h3 class="text-lg font-semibold text-rose-600">表单初始化失败</h3>
                <p class="mt-2 text-sm leading-6 text-slate-500">{{ loadError }}</p>
                <button
                    type="button"
                    class="mt-5 rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                    @click="goBack"
                >
                    返回上一页
                </button>
            </div>

            <div v-else class="grid gap-6 2xl:grid-cols-[minmax(0,1fr)_360px]">
                <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex items-center justify-between gap-3">
                        <div>
                            <h3 class="text-xl font-bold text-slate-900">连接配置</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                配置保存成功后，仍需要在详情页或列表页手动刷新一次工具目录。
                            </p>
                        </div>
                        <span
                            v-if="isEditMode && existingServer?.hasAuthConfig"
                            class="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200"
                        >
                            已存在凭证
                        </span>
                    </div>

                    <p
                        v-if="loadError"
                        class="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                    >
                        {{ loadError }}
                    </p>
                    <p
                        v-if="isEditMode && !canOperateCurrent"
                        class="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700"
                    >
                        当前仅有查看权限，不能修改该 MCP 服务。
                    </p>

                    <div class="mt-6 grid gap-5">
                        <label class="space-y-2 text-sm text-slate-600">
                            <span class="font-medium">Server Key</span>
                            <input
                                v-model.trim="serverForm.serverKey"
                                type="text"
                                :disabled="isEditMode"
                                placeholder="例如 weather.local"
                                class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15 disabled:bg-slate-100"
                            />
                            <p class="text-xs text-slate-400">
                                仅支持字母、数字、点、下划线和中划线，保存后会作为 MCP
                                工具名前缀的一部分。
                            </p>
                        </label>

                        <div class="grid gap-5 lg:grid-cols-2">
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">展示名称</span>
                                <input
                                    v-model.trim="serverForm.displayName"
                                    type="text"
                                    class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">Endpoint</span>
                                <input
                                    v-model.trim="serverForm.endpoint"
                                    type="text"
                                    placeholder="https://example.com"
                                    class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                />
                            </label>
                        </div>

                        <div class="grid gap-5 lg:grid-cols-2">
                            <div class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">权限范围</span>
                                <AppSelect
                                    v-model.number="serverForm.permissionScope"
                                    :options="MCP_RESOURCE_PERMISSION_OPTIONS"
                                    placeholder="请选择权限范围"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                    :disabled="!canEditPermissionScope"
                                />
                                <p class="text-xs text-slate-400">
                                    {{
                                        getMcpPermissionScopeDescription(serverForm.permissionScope)
                                    }}
                                </p>
                                <p v-if="!canEditPermissionScope" class="text-xs text-amber-700">
                                    仅创建人或系统管理员可修改资源权限。
                                </p>
                            </div>
                            <div class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">服务范围</span>
                                <AppSelect
                                    v-model="serverForm.serverScope"
                                    :options="MCP_SERVER_SCOPE_OPTIONS"
                                    placeholder="请选择服务范围"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                />
                                <p class="text-xs text-slate-400">
                                    内部 MCP 走标准 SDK，外部 MCP 走外部适配器。
                                </p>
                            </div>
                            <div class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">Transport</span>
                                <AppSelect
                                    v-model="serverForm.transportType"
                                    :options="MCP_TRANSPORT_OPTIONS"
                                    placeholder="请选择传输方式"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                />
                            </div>
                            <div class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">鉴权方式</span>
                                <AppSelect
                                    v-model="serverForm.authType"
                                    :options="MCP_AUTH_OPTIONS"
                                    placeholder="请选择鉴权方式"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                />
                            </div>
                        </div>

                        <label
                            v-if="serverForm.authType === 'BEARER_TOKEN'"
                            class="space-y-2 text-sm text-slate-600"
                        >
                            <span class="font-medium">Bearer Token</span>
                            <input
                                v-model.trim="serverForm.authToken"
                                type="password"
                                class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                            />
                            <p class="text-xs text-slate-400">
                                {{
                                    isEditMode
                                        ? '留空表示保留现有凭证。'
                                        : '创建时会将该 Token 保存为平台级凭证。'
                                }}
                            </p>
                        </label>

                        <label class="space-y-2 text-sm text-slate-600">
                            <span class="font-medium">自定义请求头 (Headers)</span>
                            <div class="space-y-2 rounded-2xl border border-slate-200 p-3">
                                <div
                                    v-for="(header, index) in serverForm.headers"
                                    :key="index"
                                    class="flex items-center gap-2"
                                >
                                    <input
                                        v-model="header.key"
                                        type="text"
                                        class="flex-1 rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        placeholder="Key"
                                    />
                                    <input
                                        v-model="header.value"
                                        type="text"
                                        class="flex-1 rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        placeholder="Value"
                                    />
                                    <button
                                        type="button"
                                        class="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-red-500"
                                        @click="removeHeader(index)"
                                    >
                                        <svg
                                            class="h-4 w-4"
                                            fill="none"
                                            stroke="currentColor"
                                            viewBox="0 0 24 24"
                                        >
                                            <path
                                                stroke-linecap="round"
                                                stroke-linejoin="round"
                                                stroke-width="2"
                                                d="M6 18L18 6M6 6l12 12"
                                            />
                                        </svg>
                                    </button>
                                </div>
                                <button
                                    type="button"
                                    class="flex items-center gap-1 rounded-lg border border-dashed border-slate-300 px-3 py-2 text-sm text-slate-500 hover:border-primary hover:text-primary"
                                    @click="addHeader"
                                >
                                    <svg
                                        class="h-4 w-4"
                                        fill="none"
                                        stroke="currentColor"
                                        viewBox="0 0 24 24"
                                    >
                                        <path
                                            stroke-linecap="round"
                                            stroke-linejoin="round"
                                            stroke-width="2"
                                            d="M12 4v16m8-8H4"
                                        />
                                    </svg>
                                    添加请求头
                                </button>
                            </div>
                            <p class="text-xs text-slate-400">
                                用于添加额外的请求头（如 API Key）。
                            </p>
                        </label>

                        <label class="space-y-2 text-sm text-slate-600">
                            <span class="font-medium">描述</span>
                            <textarea
                                v-model.trim="serverForm.description"
                                rows="5"
                                class="w-full rounded-[24px] border border-slate-200 px-4 py-3 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                            />
                        </label>

                        <label
                            class="flex items-center gap-3 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-700"
                        >
                            <input
                                v-model="serverForm.enabled"
                                type="checkbox"
                                class="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary"
                            />
                            <span class="font-medium">启用此 MCP 服务</span>
                        </label>
                    </div>
                </section>

                <aside class="space-y-6">
                    <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h3 class="text-lg font-bold text-slate-900">填写提示</h3>
                        <div class="mt-4 space-y-4 text-sm leading-6 text-slate-500">
                            <div class="rounded-3xl bg-slate-50 px-4 py-4">
                                <p class="font-semibold text-slate-700">SSE 服务</p>
                                <p class="mt-2">
                                    `endpoint` 只填 base URL，例如
                                    `http://127.0.0.1:8088`，不要手动追加 `/sse`。
                                </p>
                            </div>
                            <div class="rounded-3xl bg-slate-50 px-4 py-4">
                                <p class="font-semibold text-slate-700">Streamable HTTP</p>
                                <p class="mt-2">
                                    同样建议填写 base URL，不要把 `/mcp` 写死在配置里，统一交给
                                    client 补齐。
                                </p>
                            </div>
                            <div class="rounded-3xl bg-slate-50 px-4 py-4">
                                <p class="font-semibold text-slate-700">外部 MCP</p>
                                <p class="mt-2">
                                    如企查查这类平台型 MCP，请选择
                                    `EXTERNAL`，并直接填写平台提供的完整 stream endpoint。
                                </p>
                            </div>
                            <div class="rounded-3xl bg-slate-50 px-4 py-4">
                                <p class="font-semibold text-slate-700">保存后动作</p>
                                <p class="mt-2">
                                    新建或编辑只保存连接配置，不会自动拉工具。保存后请回到列表执行“刷新工具目录”；工具是否全局可用，请到“工具库”统一设置。
                                </p>
                            </div>
                        </div>
                    </section>

                    <section
                        v-if="isEditMode && existingServer"
                        class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm"
                    >
                        <h3 class="text-lg font-bold text-slate-900">当前状态</h3>
                        <div class="mt-4 space-y-3 text-sm text-slate-500">
                            <div
                                class="flex items-center justify-between gap-3 rounded-2xl bg-slate-50 px-4 py-3"
                            >
                                <span>已同步工具</span>
                                <span class="font-semibold text-slate-700">{{
                                    existingServer.toolCount || 0
                                }}</span>
                            </div>
                            <div
                                class="flex items-center justify-between gap-3 rounded-2xl bg-slate-50 px-4 py-3"
                            >
                                <span>最近刷新状态</span>
                                <span class="font-semibold text-slate-700">
                                    {{ existingServer.lastRefreshStatus || 'IDLE' }}
                                </span>
                            </div>
                            <div
                                class="flex items-center justify-between gap-3 rounded-2xl bg-slate-50 px-4 py-3"
                            >
                                <span>最近刷新时间</span>
                                <span class="font-semibold text-slate-700">
                                    {{ existingServer.lastRefreshedAt || '未记录' }}
                                </span>
                            </div>
                        </div>
                    </section>
                </aside>
            </div>
        </div>
    </section>
</template>
