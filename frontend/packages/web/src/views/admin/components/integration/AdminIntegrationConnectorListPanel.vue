<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
    createIntegrationConnector,
    deleteIntegrationConnector,
    listIntegrationConnectorPage,
    previewIntegrationConnectorCode,
    updateIntegrationConnector,
} from '@/api/integration';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { confirm } from '@/composables/useModal';
import {
    RESOURCE_PERMISSION_UI_OPTIONS,
    canChangeResourcePermission,
    getResourcePermissionBadgeClass,
    getResourcePermissionDescription,
    getResourcePermissionLabel,
    normalizeResourcePermissionScope,
} from '@/model/resource-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';
import MiniPagination from '@/components/MiniPagination.vue';

const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const saveError = ref('');
const message = ref('');
const editingId = ref(null);
const editingItem = ref(null);
const connectors = ref([]);
const showEditor = ref(false);
const validationTriggered = ref(false);
const connectorCodeEditedManually = ref(false);
const connectorCodeGenerating = ref(false);
const lastGeneratedConnectorCode = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];
let connectorCodeGenerateTimer = null;
let connectorCodeGenerateToken = 0;
let messageTimer = null;

const filters = reactive({
    keyword: '',
    status: '',
});

const form = reactive({
    name: '',
    alias: '',
    baseUrl: '',
    permissionScope: normalizeResourcePermissionScope(),
    status: 'ACTIVE',
});

const statusOptions = [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '已启用' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'DISABLED', label: '已停用' },
];

const connectorStatusOptions = statusOptions.filter(item => item.value);
const permissionOptions = RESOURCE_PERMISSION_UI_OPTIONS.map(item => ({
    value: item.value,
    label: item.label,
}));
const enabledConnectorCount = computed(
    () => connectors.value.filter(item => item.status === 'ACTIVE').length
);
const inactiveConnectorCount = computed(
    () => connectors.value.filter(item => item.status !== 'ACTIVE').length
);

const duplicateConnectorNameItem = computed(() => {
    const name = form.name.trim().toLowerCase();
    if (!name) {
        return null;
    }
    return (
        connectors.value.find(
            item =>
                item.id !== editingId.value &&
                String(item.name || '')
                    .trim()
                    .toLowerCase() === name
        ) || null
    );
});

const duplicateConnectorCodeItem = computed(() => {
    const code = form.alias.trim().toLowerCase();
    if (!code) {
        return null;
    }
    return (
        connectors.value.find(
            item =>
                item.id !== editingId.value &&
                String(item.alias || '')
                    .trim()
                    .toLowerCase() === code
        ) || null
    );
});

const formErrors = computed(() => ({
    name: !form.name.trim()
        ? '请输入连接器名称'
        : duplicateConnectorNameItem.value
          ? '连接器名称不能重复'
          : '',
    alias: !form.alias.trim()
        ? '请输入连接器编码'
        : duplicateConnectorCodeItem.value
          ? '连接器编码不能重复'
          : '',
}));

const canEditPermissionScope = computed(() => {
    if (!editingId.value) {
        return true;
    }
    if (!editingItem.value) {
        return false;
    }
    if (editingItem.value?.canChangePermission !== undefined) {
        return Boolean(editingItem.value.canChangePermission);
    }
    return canChangeResourcePermission(editingItem.value, currentUserState.profile);
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function showMessage(text) {
    message.value = text;
    if (messageTimer) {
        clearTimeout(messageTimer);
    }
    messageTimer = setTimeout(() => {
        message.value = '';
    }, 2200);
}

function statusClass(status) {
    if (status === 'ACTIVE') {
        return 'border border-emerald-100 bg-emerald-50 text-emerald-700';
    }
    if (status === 'DISABLED') {
        return 'border border-slate-200 bg-slate-50 text-slate-500';
    }
    return 'border border-amber-100 bg-amber-50 text-amber-700';
}

function statusLabel(status) {
    if (status === 'ACTIVE') {
        return '已启用';
    }
    if (status === 'DISABLED') {
        return '已停用';
    }
    return '草稿';
}

function authTypeLabel(item) {
    return item?.authCount > 0 ? `${item.authCount} 条鉴权` : '未配置鉴权';
}

function clearConnectorCodeTimer() {
    if (connectorCodeGenerateTimer) {
        clearTimeout(connectorCodeGenerateTimer);
        connectorCodeGenerateTimer = null;
    }
}

function extractConnectorCode(result) {
    if (result && typeof result === 'object') {
        return String(result.connectorCode || '').trim();
    }
    return String(result || '').trim();
}

async function generateConnectorCodePreview(connectorName) {
    const currentToken = ++connectorCodeGenerateToken;
    connectorCodeGenerating.value = true;
    try {
        const result = await previewIntegrationConnectorCode(connectorName, handleUnauthorized);
        if (currentToken !== connectorCodeGenerateToken) {
            return;
        }
        const nextCode = extractConnectorCode(result);
        if (!nextCode) {
            return;
        }
        const previousGeneratedCode = lastGeneratedConnectorCode.value;
        lastGeneratedConnectorCode.value = nextCode;
        if (
            !connectorCodeEditedManually.value ||
            !form.alias.trim() ||
            form.alias === previousGeneratedCode
        ) {
            form.alias = nextCode;
        }
    } catch (error) {
        if (!saveError.value) {
            saveError.value = error?.message || '连接器编码生成失败';
        }
    } finally {
        if (currentToken === connectorCodeGenerateToken) {
            connectorCodeGenerating.value = false;
        }
    }
}

function scheduleConnectorCodePreview() {
    clearConnectorCodeTimer();
    const connectorName = form.name.trim();
    if (!connectorName) {
        if (!connectorCodeEditedManually.value || form.alias === lastGeneratedConnectorCode.value) {
            form.alias = '';
            lastGeneratedConnectorCode.value = '';
        }
        connectorCodeGenerating.value = false;
        return;
    }
    connectorCodeGenerateTimer = setTimeout(() => {
        generateConnectorCodePreview(connectorName);
    }, 250);
}

function handleConnectorNameInput(value) {
    form.name = value;
    const currentCode = form.alias.trim();
    if (
        !connectorCodeEditedManually.value ||
        !currentCode ||
        currentCode === lastGeneratedConnectorCode.value
    ) {
        scheduleConnectorCodePreview();
    }
}

function handleConnectorCodeInput(value) {
    form.alias = value;
    connectorCodeEditedManually.value =
        Boolean(value.trim()) && value.trim() !== lastGeneratedConnectorCode.value;
}

function resetForm() {
    saveError.value = '';
    validationTriggered.value = false;
    editingId.value = null;
    editingItem.value = null;
    Object.assign(form, {
        name: '',
        alias: '',
        baseUrl: '',
        permissionScope: normalizeResourcePermissionScope(),
        status: 'ACTIVE',
    });
    connectorCodeEditedManually.value = false;
    connectorCodeGenerating.value = false;
    lastGeneratedConnectorCode.value = '';
    clearConnectorCodeTimer();
}

function openCreate() {
    resetForm();
    showEditor.value = true;
}

function openEdit(item) {
    resetForm();
    editingId.value = item.id;
    editingItem.value = item;
    Object.assign(form, {
        name: item.name || '',
        alias: item.alias || '',
        baseUrl: item.baseUrl || '',
        permissionScope: normalizeResourcePermissionScope(item.permissionScope),
        status: item.status || 'ACTIVE',
    });
    lastGeneratedConnectorCode.value = item.alias || '';
    showEditor.value = true;
}

function closeEditor() {
    showEditor.value = false;
    resetForm();
}

function validateForm() {
    validationTriggered.value = true;
    return !formErrors.value.name && !formErrors.value.alias;
}

async function loadConnectors(targetPage = page.value) {
    loading.value = true;
    loadError.value = '';
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await listIntegrationConnectorPage(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: filters.keyword,
                status: filters.status,
            },
            handleUnauthorized
        );
        connectors.value = Array.isArray(data?.list) ? data.list : [];
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;
        if (connectors.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadConnectors(lastPage);
            }
        }
    } catch (error) {
        loadError.value = error?.message || '连接器列表加载失败';
        connectors.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
}

function handleSearch() {
    loadConnectors(1);
}

function handlePageChange(nextPage) {
    loadConnectors(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadConnectors(1);
}

async function saveConnector() {
    saveError.value = '';
    if (!validateForm()) {
        return;
    }
    const payload = {
        name: form.name.trim(),
        alias: form.alias.trim(),
        baseUrl: form.baseUrl.trim(),
        permissionScope: form.permissionScope,
        status: form.status,
    };
    try {
        if (editingId.value) {
            await updateIntegrationConnector(editingId.value, payload, handleUnauthorized);
            showMessage('保存成功');
        } else {
            await createIntegrationConnector(payload, handleUnauthorized);
            showMessage('创建成功');
        }
        closeEditor();
        await loadConnectors(page.value);
    } catch (error) {
        saveError.value = error?.message || '保存连接器失败';
    }
}

async function deleteConnectorItem(item) {
    const ok = await confirm({
        title: '删除连接器',
        message: `确认删除连接器“${item.name || item.alias}”吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!ok) {
        return;
    }
    try {
        await deleteIntegrationConnector(item.id, handleUnauthorized);
        showMessage('删除成功');
        await loadConnectors();
    } catch (error) {
        loadError.value = error?.message || '删除连接器失败';
    }
}

function openDetail(item) {
    router.push(ROUTE_PATHS.adminIntegrationConnectorDetail(item.id));
}

function inputClass(hasError, disabled = false) {
    return [
        'w-full rounded-xl border px-4 py-2.5 text-sm outline-none transition',
        hasError
            ? 'border-rose-300 bg-rose-50 focus:border-rose-400 focus:ring-2 focus:ring-rose-100'
            : 'border-slate-200 bg-white focus:border-primary focus:ring-2 focus:ring-primary/10',
        disabled ? 'cursor-not-allowed bg-slate-100 text-slate-500' : '',
    ];
}

onMounted(loadConnectors);
</script>

<template>
    <section
        :class="
            showEditor
                ? 'flex h-full min-h-0 flex-col gap-5 overflow-y-auto p-6'
                : 'admin-page flex h-full min-h-0 flex-col bg-slate-100'
        "
    >
        <div
            v-if="message"
            class="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700"
            :class="{ 'mx-8 mt-5': !showEditor }"
        >
            {{ message }}
        </div>

        <header v-if="!showEditor" class="shrink-0 border-b border-slate-200 bg-white px-6 py-4">
            <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1
                        class="text-[28px] font-semibold leading-tight tracking-tight text-slate-900"
                    >
                        连接器管理
                    </h1>
                    <p class="mt-1.5 max-w-3xl text-[13px] leading-5 text-slate-500">
                        管理自定义 API 连接器，进入详情后配置鉴权和 API。
                    </p>
                </div>
                <button
                    class="inline-flex h-9 self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-3.5 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 xl:self-auto"
                    type="button"
                    @click="openCreate"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>新建连接器</span>
                </button>
            </div>

            <div class="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">{{ total }}</p>
                        <p class="mt-0.5 text-xs text-slate-500">连接器总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">
                            {{ enabledConnectorCount }}
                        </p>
                        <p class="mt-0.5 text-xs text-slate-500">当前页启用</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-base font-semibold text-slate-900">
                            {{ inactiveConnectorCount }}
                        </p>
                        <p class="mt-0.5 text-xs text-slate-500">当前页未启用</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    <label class="relative min-w-0 flex-1 sm:w-72 sm:flex-none">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >
                            search
                        </span>
                        <input
                            v-model="filters.keyword"
                            class="w-full h-9 rounded-xl border border-slate-200 bg-white pl-10 pr-3 text-[13px] outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                            type="text"
                            placeholder="搜索连接器名称或编码"
                            @keyup.enter="handleSearch"
                        />
                    </label>
                    <select
                        v-model="filters.status"
                        class="h-9 rounded-xl border border-slate-200 bg-white px-3 text-[13px] outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                        @change="handleSearch"
                    >
                        <option v-for="item in statusOptions" :key="item.value" :value="item.value">
                            {{ item.label }}
                        </option>
                    </select>
                    <button
                        class="inline-flex h-9 items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 text-[13px] font-semibold text-slate-700 transition hover:bg-slate-50"
                        type="button"
                        @click="handleSearch"
                    >
                        <span class="material-symbols-outlined text-[18px]">search</span>
                        <span>查询</span>
                    </button>
                </div>
            </div>
        </header>

        <template v-if="showEditor">
            <header
                class="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4"
            >
                <div class="flex min-w-0 items-center gap-4">
                    <button
                        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-primary"
                        type="button"
                        @click="closeEditor"
                    >
                        <span class="material-symbols-outlined text-[20px]">arrow_back</span>
                    </button>
                    <div class="flex min-w-0 items-center gap-3">
                        <h1 class="truncate text-xl font-bold text-slate-900">
                            {{ editingId ? '编辑连接器' : '新建连接器' }}
                        </h1>
                        <span v-if="form.alias" class="truncate text-sm text-slate-500">
                            【{{ form.alias }}】
                        </span>
                    </div>
                </div>
            </header>

            <div class="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
                <p class="mb-5 text-sm text-slate-500">
                    连接器编码新增时可修改，编辑后不允许修改。
                </p>

                <div
                    v-if="saveError"
                    class="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ saveError }}
                </div>

                <div class="grid gap-4 lg:grid-cols-2">
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">
                            <span class="text-rose-500">*</span>
                            连接器名称
                        </span>
                        <input
                            :value="form.name"
                            :class="inputClass(validationTriggered && Boolean(formErrors.name))"
                            type="text"
                            placeholder="请输入连接器名称"
                            @input="event => handleConnectorNameInput(event.target.value)"
                        />
                        <span
                            v-if="validationTriggered && formErrors.name"
                            class="mt-1 block text-xs text-rose-500"
                        >
                            {{ formErrors.name }}
                        </span>
                    </label>

                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">
                            <span class="text-rose-500">*</span>
                            连接器编码
                        </span>
                        <input
                            :value="form.alias"
                            :disabled="Boolean(editingId)"
                            :class="
                                inputClass(
                                    validationTriggered && Boolean(formErrors.alias),
                                    Boolean(editingId)
                                )
                            "
                            type="text"
                            placeholder="根据连接器名称自动生成"
                            @input="event => handleConnectorCodeInput(event.target.value)"
                        />
                        <span class="mt-1 block text-xs text-slate-400">
                            {{
                                connectorCodeGenerating
                                    ? '正在生成编码...'
                                    : '编码用于 API 编码前缀和运行时标识'
                            }}
                        </span>
                        <span
                            v-if="validationTriggered && formErrors.alias"
                            class="mt-1 block text-xs text-rose-500"
                        >
                            {{ formErrors.alias }}
                        </span>
                    </label>

                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >接口基础地址</span
                        >
                        <input
                            v-model="form.baseUrl"
                            :class="inputClass(false)"
                            type="text"
                            placeholder="例如：https://api.example.com，可为空"
                        />
                    </label>

                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">状态</span>
                        <select v-model="form.status" :class="inputClass(false)">
                            <option
                                v-for="item in connectorStatusOptions"
                                :key="item.value"
                                :value="item.value"
                            >
                                {{ item.label }}
                            </option>
                        </select>
                    </label>

                    <label class="block lg:col-span-2">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >权限范围</span
                        >
                        <select
                            v-model="form.permissionScope"
                            :disabled="!canEditPermissionScope"
                            :class="inputClass(false, !canEditPermissionScope)"
                        >
                            <option
                                v-for="item in permissionOptions"
                                :key="item.value"
                                :value="item.value"
                            >
                                {{ item.label }}
                            </option>
                        </select>
                    </label>
                </div>

                <div class="mt-6 flex justify-end gap-3">
                    <button
                        class="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 transition hover:border-slate-300"
                        type="button"
                        @click="closeEditor"
                    >
                        取消
                    </button>
                    <button
                        class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90"
                        type="button"
                        @click="saveConnector"
                    >
                        保存
                    </button>
                </div>
            </div>
        </template>

        <div v-if="!showEditor" class="flex min-h-0 flex-1 flex-col p-5">
            <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
                <div
                    v-if="loadError"
                    class="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ loadError }}
                </div>
                <div v-if="loading" class="px-6 py-16 text-center text-sm text-slate-400">
                    正在加载连接器...
                </div>
                <div
                    v-else-if="!connectors.length"
                    class="px-6 py-16 text-center text-sm text-slate-400"
                >
                    暂无连接器
                </div>
                <div
                    v-else
                    class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4"
                >
                    <article
                        v-for="item in connectors"
                        :key="item.id"
                        class="flex min-h-[212px] flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                    >
                        <div class="min-w-0 flex-1">
                            <div class="flex items-start gap-3">
                                <span
                                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                                >
                                    <span class="material-symbols-outlined text-[20px]">hub</span>
                                </span>
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-start justify-between gap-3">
                                        <button
                                            class="min-w-0 truncate text-left text-base font-bold text-slate-900 transition hover:text-blue-700"
                                            type="button"
                                            @click="openDetail(item)"
                                        >
                                            {{ item.name || '未命名连接器' }}
                                        </button>
                                        <span
                                            class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold"
                                            :class="statusClass(item.status)"
                                        >
                                            {{ statusLabel(item.status) }}
                                        </span>
                                    </div>
                                    <p class="mt-1 truncate font-mono text-xs text-slate-400">
                                        {{ item.alias || '-' }}
                                    </p>
                                </div>
                            </div>

                            <p
                                class="mt-2 line-clamp-2 min-h-10 break-all text-xs leading-5 text-slate-500"
                            >
                                {{ item.baseUrl || '未配置接口基础地址' }}
                            </p>
                            <div class="mt-3 flex flex-wrap gap-1.5">
                                <span
                                    class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-500"
                                >
                                    {{ authTypeLabel(item) }}
                                </span>
                                <span
                                    class="rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ring-black/5"
                                    :class="getResourcePermissionBadgeClass(item.permissionScope)"
                                >
                                    {{ getResourcePermissionLabel(item.permissionScope) }}
                                </span>
                            </div>
                            <p class="mt-2 line-clamp-1 text-xs leading-5 text-slate-400">
                                {{ getResourcePermissionDescription(item.permissionScope) }}
                            </p>
                        </div>

                        <div
                            class="mt-auto grid grid-flow-col auto-cols-fr gap-1.5 border-t border-slate-100 pt-2.5"
                        >
                            <button
                                aria-label="配置连接器"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700"
                                type="button"
                                @click="openDetail(item)"
                            >
                                <span class="material-symbols-outlined text-[17px]">settings</span>
                                <span>配置</span>
                            </button>
                            <button
                                v-if="item.canOperate !== false"
                                aria-label="编辑连接器"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                                type="button"
                                @click="openEdit(item)"
                            >
                                <span class="material-symbols-outlined text-[17px]">edit</span>
                                <span>编辑</span>
                            </button>
                            <button
                                v-if="item.canOperate !== false"
                                aria-label="删除连接器"
                                class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50"
                                type="button"
                                @click="deleteConnectorItem(item)"
                            >
                                <span class="material-symbols-outlined text-[17px]">delete</span>
                                <span>删除</span>
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
