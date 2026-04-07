<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    createModelLibraryModel,
    createModelLibraryVendor,
    listModelLibraryDefaults,
    listModelLibraryModels,
    listModelLibraryVendors,
    saveModelLibraryDefaultBinding,
    updateModelLibraryModel,
    updateModelLibraryVendor,
} from '@/api/model-library';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const activeTab = ref('vendors');

const vendorModalOpen = ref(false);
const vendorSaving = ref(false);
const vendorSaveError = ref('');
const vendorEditingId = ref(null);

const modelModalOpen = ref(false);
const modelSaving = ref(false);
const modelSaveError = ref('');
const modelEditingId = ref(null);

const defaultSavingMap = reactive({
    CHAT: false,
    EMBEDDING: false,
    RERANK: false,
});

const filters = reactive({
    keyword: '',
    capabilityType: '',
    vendorId: '',
    status: '',
});

const vendorForm = reactive({
    vendorCode: '',
    vendorName: '',
    description: '',
    status: 'ACTIVE',
});

const modelForm = reactive({
    modelCode: '',
    displayName: '',
    capabilityType: 'CHAT',
    vendorId: '',
    adapterType: 'QWEN_ONLINE',
    protocol: '',
    baseUrl: '',
    apiKey: '',
    path: '',
    modelName: '',
    temperature: '',
    maxTokens: '',
    systemPrompt: '',
    enableThinking: '',
    dimensions: '',
    timeoutMs: '',
    fallbackRrf: '',
    extraConfigJson: '',
    status: 'ACTIVE',
    apiKeyConfigured: false,
});

const vendors = ref([]);
const models = ref([]);
const allModels = ref([]);
const defaultBindings = ref([]);
const defaultSelections = reactive({
    CHAT: '',
    EMBEDDING: '',
    RERANK: '',
});

const libraryTabs = [
    { value: 'vendors', label: '厂商' },
    { value: 'models', label: '模型' },
    { value: 'defaults', label: '默认模型绑定' },
];

const capabilityOptions = [
    { value: 'CHAT', label: '对话模型' },
    { value: 'EMBEDDING', label: '向量模型' },
    { value: 'RERANK', label: 'Rerank 模型' },
];

const statusOptions = [
    { value: 'ACTIVE', label: '启用' },
    { value: 'DRAFT', label: '草稿' },
];

const adapterOptions = [
    { value: 'QWEN_ONLINE', label: 'Qwen Online' },
    { value: 'VLLM', label: 'vLLM' },
];

const booleanOptions = [
    { value: '', label: '跟随默认' },
    { value: true, label: '开启' },
    { value: false, label: '关闭' },
];

const vendorFilterOptions = computed(() => [
    { value: '', label: '全部厂商' },
    ...vendors.value.map(item => ({
        value: item.id,
        label: item.vendorName,
    })),
]);

const modelStats = computed(() => ({
    vendorCount: vendors.value.length,
    modelCount: models.value.length,
    activeCount: models.value.filter(item => item.status === 'ACTIVE').length,
}));

const defaultBindingMap = computed(() => {
    const map = {};
    defaultBindings.value.forEach(item => {
        map[item.capabilityType] = item;
    });
    return map;
});

const filteredModelsByCapability = computed(() =>
    capabilityOptions.reduce((accumulator, item) => {
        accumulator[item.value] = allModels.value.filter(
            model => model.capabilityType === item.value && model.status === 'ACTIVE'
        );
        return accumulator;
    }, {})
);

const modalTitle = computed(() => (modelEditingId.value ? '编辑模型' : '新增模型'));
const vendorModalTitle = computed(() => (vendorEditingId.value ? '编辑厂商' : '新增厂商'));

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function capabilityLabel(value) {
    return capabilityOptions.find(item => item.value === value)?.label || value || '未设置';
}

function adapterLabel(value) {
    return adapterOptions.find(item => item.value === value)?.label || value || '未设置';
}

function statusMeta(status) {
    if (status === 'ACTIVE') {
        return {
            label: '启用',
            badgeClass: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
            dotClass: 'bg-emerald-500',
        };
    }
    return {
        label: '草稿',
        badgeClass: 'bg-slate-100 text-slate-600 ring-1 ring-slate-200',
        dotClass: 'bg-slate-400',
    };
}

function defaultCardDescription(capabilityType) {
    if (capabilityType === 'CHAT') {
        return '对话链路和通用问答默认使用的模型。';
    }
    if (capabilityType === 'EMBEDDING') {
        return '向量化和检索索引默认使用的模型。';
    }
    return '混合检索中的重排序默认使用的模型。';
}

function createDefaultOptions(capabilityType) {
    return [
        { value: '', label: '暂不设置默认模型' },
        ...(filteredModelsByCapability.value[capabilityType] || []).map(item => ({
            value: item.id,
            label: item.displayName,
            description: `${item.vendorName || '未绑定厂商'} · ${adapterLabel(item.adapterType)}`,
        })),
    ];
}

function createEmptyVendorForm() {
    return {
        vendorCode: '',
        vendorName: '',
        description: '',
        status: 'ACTIVE',
    };
}

function createEmptyModelForm() {
    return {
        modelCode: '',
        displayName: '',
        capabilityType: 'CHAT',
        vendorId: vendors.value[0]?.id || '',
        adapterType: 'QWEN_ONLINE',
        protocol: '',
        baseUrl: '',
        apiKey: '',
        path: suggestedPath('CHAT', 'QWEN_ONLINE'),
        modelName: '',
        temperature: '',
        maxTokens: '',
        systemPrompt: '',
        enableThinking: '',
        dimensions: '',
        timeoutMs: '',
        fallbackRrf: '',
        extraConfigJson: '',
        status: 'ACTIVE',
        apiKeyConfigured: false,
    };
}

function fillDefaultSelections(items) {
    const nextMap = {
        CHAT: '',
        EMBEDDING: '',
        RERANK: '',
    };
    (Array.isArray(items) ? items : []).forEach(item => {
        nextMap[item.capabilityType] = item.modelId || '';
    });
    Object.assign(defaultSelections, nextMap);
}

async function loadVendors() {
    vendors.value = await listModelLibraryVendors(handleUnauthorized);
}

async function loadModels() {
    models.value = await listModelLibraryModels(
        {
            keyword: filters.keyword,
            capabilityType: filters.capabilityType,
            vendorId: filters.vendorId,
            status: filters.status,
        },
        handleUnauthorized
    );
}

async function loadAllModels() {
    allModels.value = await listModelLibraryModels({}, handleUnauthorized);
}

async function loadDefaults() {
    defaultBindings.value = await listModelLibraryDefaults(handleUnauthorized);
    fillDefaultSelections(defaultBindings.value);
}

async function loadPage() {
    loading.value = true;
    loadError.value = '';
    try {
        await Promise.all([loadVendors(), loadModels(), loadAllModels(), loadDefaults()]);
    } catch (error) {
        loadError.value = error?.message || '模型库加载失败';
        vendors.value = [];
        models.value = [];
        allModels.value = [];
        defaultBindings.value = [];
        fillDefaultSelections([]);
    } finally {
        loading.value = false;
    }
}

function openCreateVendorModal() {
    vendorEditingId.value = null;
    vendorSaveError.value = '';
    Object.assign(vendorForm, createEmptyVendorForm());
    vendorModalOpen.value = true;
}

function openEditVendorModal(item) {
    vendorEditingId.value = item?.id || null;
    vendorSaveError.value = '';
    Object.assign(vendorForm, {
        vendorCode: item?.vendorCode || '',
        vendorName: item?.vendorName || '',
        description: item?.description || '',
        status: item?.status || 'ACTIVE',
    });
    vendorModalOpen.value = true;
}

function openCreateModelModal() {
    modelEditingId.value = null;
    modelSaveError.value = '';
    Object.assign(modelForm, createEmptyModelForm());
    modelModalOpen.value = true;
}

function openEditModelModal(item) {
    modelEditingId.value = item?.id || null;
    modelSaveError.value = '';
    Object.assign(modelForm, {
        modelCode: item?.modelCode || '',
        displayName: item?.displayName || '',
        capabilityType: item?.capabilityType || 'CHAT',
        vendorId: item?.vendorId || '',
        adapterType: item?.adapterType || 'QWEN_ONLINE',
        protocol: item?.protocol || '',
        baseUrl: item?.baseUrl || '',
        apiKey: '',
        path:
            item?.path ||
            suggestedPath(item?.capabilityType || 'CHAT', item?.adapterType || 'QWEN_ONLINE'),
        modelName: item?.modelName || '',
        temperature: item?.temperature ?? '',
        maxTokens: item?.maxTokens ?? '',
        systemPrompt: item?.systemPrompt || '',
        enableThinking: item?.enableThinking ?? '',
        dimensions: item?.dimensions ?? '',
        timeoutMs: item?.timeoutMs ?? '',
        fallbackRrf: item?.fallbackRrf ?? '',
        extraConfigJson: item?.extraConfigJson || '',
        status: item?.status || 'ACTIVE',
        apiKeyConfigured: Boolean(item?.apiKeyConfigured),
    });
    modelModalOpen.value = true;
}

function suggestedPath(capabilityType, adapterType) {
    if (capabilityType === 'CHAT') {
        return adapterType === 'QWEN_ONLINE' ? '/v1/chat/completions' : '';
    }
    if (capabilityType === 'EMBEDDING') {
        return '/v1/embeddings';
    }
    return adapterType === 'VLLM'
        ? '/v1/rerank'
        : '/api/v1/services/rerank/text-rerank/text-rerank';
}

function applySuggestedPath() {
    modelForm.path = suggestedPath(modelForm.capabilityType, modelForm.adapterType);
}

function toOptionalNumber(value) {
    if (value === '' || value == null) {
        return null;
    }
    const result = Number(value);
    return Number.isFinite(result) ? result : null;
}

function toOptionalInteger(value) {
    if (value === '' || value == null) {
        return null;
    }
    const result = Number.parseInt(value, 10);
    return Number.isFinite(result) ? result : null;
}

function buildModelPayload() {
    return {
        modelCode: modelForm.modelCode,
        displayName: modelForm.displayName,
        capabilityType: modelForm.capabilityType,
        vendorId: modelForm.vendorId || null,
        adapterType: modelForm.adapterType,
        protocol: modelForm.capabilityType === 'RERANK' ? modelForm.protocol : '',
        baseUrl: modelForm.baseUrl,
        apiKey: modelForm.apiKey,
        path: modelForm.path,
        modelName: modelForm.modelName,
        temperature:
            modelForm.capabilityType === 'CHAT' ? toOptionalNumber(modelForm.temperature) : null,
        maxTokens:
            modelForm.capabilityType === 'CHAT' ? toOptionalInteger(modelForm.maxTokens) : null,
        systemPrompt: modelForm.capabilityType === 'CHAT' ? modelForm.systemPrompt : '',
        enableThinking: modelForm.capabilityType === 'CHAT' ? modelForm.enableThinking : null,
        dimensions:
            modelForm.capabilityType === 'EMBEDDING'
                ? toOptionalInteger(modelForm.dimensions)
                : null,
        timeoutMs:
            modelForm.capabilityType === 'RERANK' ? toOptionalInteger(modelForm.timeoutMs) : null,
        fallbackRrf: modelForm.capabilityType === 'RERANK' ? modelForm.fallbackRrf : null,
        extraConfigJson: modelForm.extraConfigJson,
        status: modelForm.status,
    };
}

async function handleSaveVendor() {
    vendorSaving.value = true;
    vendorSaveError.value = '';
    try {
        const payload = {
            vendorCode: vendorForm.vendorCode,
            vendorName: vendorForm.vendorName,
            description: vendorForm.description,
            status: vendorForm.status,
        };
        if (vendorEditingId.value) {
            await updateModelLibraryVendor(vendorEditingId.value, payload, handleUnauthorized);
        } else {
            await createModelLibraryVendor(payload, handleUnauthorized);
        }
        vendorModalOpen.value = false;
        await Promise.all([loadVendors(), loadModels(), loadAllModels(), loadDefaults()]);
    } catch (error) {
        vendorSaveError.value = error?.message || '厂商保存失败';
    } finally {
        vendorSaving.value = false;
    }
}

async function handleSaveModel() {
    modelSaving.value = true;
    modelSaveError.value = '';
    try {
        const payload = buildModelPayload();
        if (modelEditingId.value) {
            await updateModelLibraryModel(modelEditingId.value, payload, handleUnauthorized);
        } else {
            await createModelLibraryModel(payload, handleUnauthorized);
        }
        modelModalOpen.value = false;
        await Promise.all([loadModels(), loadAllModels(), loadDefaults()]);
    } catch (error) {
        modelSaveError.value = error?.message || '模型保存失败';
    } finally {
        modelSaving.value = false;
    }
}

async function handleSaveDefault(capabilityType) {
    defaultSavingMap[capabilityType] = true;
    try {
        await saveModelLibraryDefaultBinding(
            capabilityType,
            {
                modelId: defaultSelections[capabilityType] || null,
            },
            handleUnauthorized
        );
        await Promise.all([loadModels(), loadAllModels(), loadDefaults()]);
        await alert({
            title: '默认模型已更新',
            message: `${capabilityLabel(capabilityType)}默认模型已生效。`,
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '默认模型保存失败',
        });
    } finally {
        defaultSavingMap[capabilityType] = false;
    }
}

onMounted(loadPage);
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-6 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        Model Library
                    </p>
                    <h2 class="mt-2 text-[28px] font-bold tracking-tight text-slate-900">模型库</h2>
                    <p class="mt-1 max-w-3xl text-sm leading-6 text-slate-500">
                        统一管理模型厂商、模型配置与默认模型绑定。
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="loadPage"
                    >
                        重新加载
                    </button>
                </div>
            </div>
        </header>

        <div class="border-b border-slate-200 bg-white px-6 pt-2">
            <div class="flex items-center gap-8 overflow-x-auto">
                <button
                    v-for="tab in libraryTabs"
                    :key="tab.value"
                    type="button"
                    class="border-b-2 pb-4 text-sm transition"
                    :class="
                        activeTab === tab.value
                            ? 'border-primary font-semibold text-primary'
                            : 'border-transparent font-medium text-slate-400 hover:text-slate-600'
                    "
                    @click="activeTab = tab.value"
                >
                    {{ tab.label }}
                </button>
            </div>
        </div>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-6 pb-8 pt-5">
            <p
                v-if="loadError"
                class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <!-- 厂商标签页 -->
            <template v-if="activeTab === 'vendors'">
                <article class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="flex items-center justify-between gap-3">
                        <div>
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400"
                            >
                                Vendors
                            </p>
                            <h3 class="mt-2 text-xl font-bold text-slate-900">模型厂商</h3>
                        </div>
                        <button
                            type="button"
                            class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                            @click="openCreateVendorModal"
                        >
                            新增厂商
                        </button>
                    </div>

                    <div
                        v-if="loading"
                        class="mt-5 rounded-[24px] bg-slate-50 px-4 py-8 text-center text-sm text-slate-400"
                    >
                        加载中...
                    </div>

                    <div v-else class="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        <article
                            v-for="vendor in vendors"
                            :key="vendor.id"
                            class="rounded-[24px] border border-slate-200 bg-slate-50 p-4 transition hover:border-primary/30"
                        >
                            <div class="flex items-start justify-between gap-3">
                                <div class="min-w-0">
                                    <p class="truncate text-base font-semibold text-slate-900">
                                        {{ vendor.vendorName }}
                                    </p>
                                    <p
                                        class="mt-1 text-xs uppercase tracking-[0.22em] text-slate-400"
                                    >
                                        {{ vendor.vendorCode }}
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    class="rounded-full p-2 text-slate-400 transition hover:bg-white hover:text-primary"
                                    @click="openEditVendorModal(vendor)"
                                >
                                    <span class="material-symbols-outlined text-[18px]">edit</span>
                                </button>
                            </div>

                            <p class="mt-3 line-clamp-3 text-sm leading-6 text-slate-500">
                                {{ vendor.description || '暂无厂商描述。' }}
                            </p>

                            <div class="mt-4 flex items-center justify-between text-sm">
                                <span
                                    class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold"
                                    :class="statusMeta(vendor.status).badgeClass"
                                >
                                    <span
                                        class="inline-block h-2 w-2 rounded-full"
                                        :class="statusMeta(vendor.status).dotClass"
                                    />
                                    {{ statusMeta(vendor.status).label }}
                                </span>
                                <span class="text-slate-400">{{ vendor.modelCount }} 个模型</span>
                            </div>
                        </article>
                    </div>
                </article>
            </template>

            <!-- 模型标签页 -->
            <template v-else-if="activeTab === 'models'">
                <div class="grid gap-4 lg:grid-cols-2">
                    <article class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                            Models
                        </p>
                        <p class="mt-3 text-3xl font-bold text-slate-900">
                            {{ modelStats.modelCount }}
                        </p>
                        <p class="mt-2 text-sm text-slate-500">当前筛选条件下的模型数</p>
                    </article>
                    <article class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                            Active
                        </p>
                        <p class="mt-3 text-3xl font-bold text-slate-900">
                            {{ modelStats.activeCount }}
                        </p>
                        <p class="mt-2 text-sm text-slate-500">启用中的模型定义数</p>
                    </article>
                </div>

                <article class="mt-6 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div
                        class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"
                    >
                        <div>
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400"
                            >
                                Filters
                            </p>
                            <h3 class="mt-2 text-xl font-bold text-slate-900">模型筛选</h3>
                        </div>
                        <div class="flex flex-col gap-3 lg:flex-row lg:items-center">
                            <div class="w-full lg:w-44">
                                <AppSelect
                                    :model-value="filters.capabilityType"
                                    :options="[
                                        { value: '', label: '全部能力' },
                                        ...capabilityOptions,
                                    ]"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                    @update:modelValue="
                                        value => {
                                            filters.capabilityType = value;
                                            loadModels();
                                        }
                                    "
                                />
                            </div>
                            <div class="w-full lg:w-44">
                                <AppSelect
                                    :model-value="filters.vendorId"
                                    :options="vendorFilterOptions"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                    @update:modelValue="
                                        value => {
                                            filters.vendorId = value;
                                            loadModels();
                                        }
                                    "
                                />
                            </div>
                            <div class="w-full lg:w-36">
                                <AppSelect
                                    :model-value="filters.status"
                                    :options="[
                                        { value: '', label: '全部状态' },
                                        ...statusOptions,
                                    ]"
                                    :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                    @update:modelValue="
                                        value => {
                                            filters.status = value;
                                            loadModels();
                                        }
                                    "
                                />
                            </div>
                            <div class="relative w-full lg:w-72">
                                <span
                                    class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                                >
                                    search
                                </span>
                                <input
                                    v-model.trim="filters.keyword"
                                    type="text"
                                    class="w-full rounded-2xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    placeholder="搜索模型编码、展示名、模型名"
                                    @keyup.enter="loadModels"
                                />
                            </div>
                        </div>
                    </div>
                </article>

                <article class="mt-6 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="flex items-center justify-between gap-3">
                        <div>
                            <p
                                class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400"
                            >
                                Models
                            </p>
                            <h3 class="mt-2 text-xl font-bold text-slate-900">模型定义</h3>
                        </div>
                        <button
                            type="button"
                            class="rounded-2xl bg-primary px-4 py-2 text-sm font-semibold text-white transition hover:bg-primary-hover"
                            @click="openCreateModelModal"
                        >
                            新增模型
                        </button>
                    </div>

                    <div
                        v-if="loading"
                        class="mt-5 rounded-[24px] bg-slate-50 px-4 py-12 text-center text-sm text-slate-400"
                    >
                        模型加载中...
                    </div>

                    <div
                        v-else-if="!models.length"
                        class="mt-5 rounded-[24px] bg-slate-50 px-4 py-12 text-center text-sm text-slate-400"
                    >
                        当前筛选条件下暂无模型。
                    </div>

                    <div v-else class="mt-5 grid gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                        <article
                            v-for="item in models"
                            :key="item.id"
                            class="group rounded-[24px] border border-slate-200 bg-slate-50 p-4 transition hover:border-primary/30 hover:bg-white"
                        >
                            <div class="flex items-start justify-between gap-3">
                                <div class="min-w-0">
                                    <div class="flex flex-wrap items-center gap-2">
                                        <h4
                                            class="truncate text-base font-semibold text-slate-900"
                                        >
                                            {{ item.displayName }}
                                        </h4>
                                        <span
                                            v-if="item.defaultModel"
                                            class="rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-semibold text-primary ring-1 ring-blue-100"
                                        >
                                            默认
                                        </span>
                                    </div>
                                    <p
                                        class="mt-1 text-xs uppercase tracking-[0.22em] text-slate-400"
                                    >
                                        {{ item.modelCode }}
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    class="rounded-full p-2 text-slate-400 transition hover:bg-white hover:text-primary"
                                    @click.stop="openEditModelModal(item)"
                                >
                                    <span class="material-symbols-outlined text-[18px]"
                                        >edit</span
                                    >
                                </button>
                            </div>

                            <div class="mt-4 flex flex-wrap gap-2">
                                <span
                                    class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600"
                                >
                                    {{ capabilityLabel(item.capabilityType) }}
                                </span>
                                <span
                                    class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600"
                                >
                                    {{ adapterLabel(item.adapterType) }}
                                </span>
                                <span
                                    class="rounded-full px-2.5 py-1 text-[11px] font-semibold"
                                    :class="statusMeta(item.status).badgeClass"
                                >
                                    {{ statusMeta(item.status).label }}
                                </span>
                            </div>

                            <dl class="mt-4 space-y-2 text-sm">
                                <div class="flex justify-between gap-3">
                                    <dt class="text-slate-400">厂商</dt>
                                    <dd class="truncate text-right text-slate-700">
                                        {{ item.vendorName || '未绑定厂商' }}
                                    </dd>
                                </div>
                                <div class="flex justify-between gap-3">
                                    <dt class="text-slate-400">模型名</dt>
                                    <dd class="truncate text-right text-slate-700">
                                        {{ item.modelName || '未设置' }}
                                    </dd>
                                </div>
                                <div class="flex justify-between gap-3">
                                    <dt class="text-slate-400">Path</dt>
                                    <dd class="truncate text-right text-slate-700">
                                        {{ item.path || '默认路径' }}
                                    </dd>
                                </div>
                            </dl>

                            <p
                                class="mt-4 line-clamp-2 break-all rounded-2xl bg-white px-3 py-2 text-xs leading-5 text-slate-500 ring-1 ring-slate-200"
                            >
                                {{ item.baseUrl || '未配置 Base URL' }}
                            </p>

                            <div
                                class="mt-4 flex items-center justify-between text-xs text-slate-400"
                            >
                                <span>{{
                                    item.apiKeyConfigured ? 'API Key 已配置' : 'API Key 未配置'
                                }}</span>
                                <span>{{
                                    item.updatedAt || item.createdAt || '未记录时间'
                                }}</span>
                            </div>
                        </article>
                    </div>
                </article>
            </template>

            <!-- 默认模型绑定标签页 -->
            <template v-else-if="activeTab === 'defaults'">
                <section class="grid gap-4 xl:grid-cols-3">
                    <article
                        v-for="capability in capabilityOptions"
                        :key="capability.value"
                        class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm"
                    >
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <p
                                    class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400"
                                >
                                    Default
                                </p>
                                <h3 class="mt-2 text-xl font-bold text-slate-900">
                                    {{ capability.label }}
                                </h3>
                                <p class="mt-1 text-sm leading-6 text-slate-500">
                                    {{ defaultCardDescription(capability.value) }}
                                </p>
                            </div>
                            <span
                                class="rounded-full px-3 py-1 text-xs font-semibold"
                                :class="
                                    defaultBindingMap[capability.value]?.modelId
                                        ? 'bg-blue-50 text-primary ring-1 ring-blue-100'
                                        : 'bg-slate-100 text-slate-500 ring-1 ring-slate-200'
                                "
                            >
                                {{
                                    defaultBindingMap[capability.value]?.modelId
                                        ? '当前默认模型'
                                        : '未设置默认模型'
                                }}
                            </span>
                        </div>

                        <div class="mt-5 rounded-[24px] bg-slate-50 p-4">
                            <p class="text-sm font-semibold text-slate-900">
                                {{
                                    defaultBindingMap[capability.value]?.modelDisplayName ||
                                    '尚未设置默认模型'
                                }}
                            </p>
                            <p class="mt-1 text-sm text-slate-500">
                                {{
                                    defaultBindingMap[capability.value]?.vendorName ||
                                    '暂无绑定厂商'
                                }}
                            </p>
                            <p class="mt-2 text-xs text-slate-400">
                                {{
                                    defaultBindingMap[capability.value]?.adapterType
                                        ? adapterLabel(defaultBindingMap[capability.value]?.adapterType)
                                        : '未绑定'
                                }}
                            </p>
                        </div>

                        <div class="mt-4">
                            <AppSelect
                                v-model="defaultSelections[capability.value]"
                                :options="createDefaultOptions(capability.value)"
                                placeholder="请选择默认模型"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </div>

                        <div class="mt-4 flex justify-end">
                            <button
                                type="button"
                                class="rounded-2xl bg-primary px-4 py-2 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="defaultSavingMap[capability.value]"
                                @click="handleSaveDefault(capability.value)"
                            >
                                {{ defaultSavingMap[capability.value] ? '保存中...' : '保存默认模型' }}
                            </button>
                        </div>
                    </article>
                </section>
            </template>
        </div>

        <BaseModal :open="vendorModalOpen" panel-class="max-w-2xl" @close="vendorModalOpen = false">
            <template #header>
                <div class="border-b border-slate-200 px-6 py-5">
                    <div class="flex items-center justify-between">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">{{ vendorModalTitle }}</h3>
                            <p class="mt-1 text-sm text-slate-400">
                                厂商独立管理，模型会关联到具体厂商。
                            </p>
                        </div>
                        <button
                            type="button"
                            class="text-slate-400 transition hover:text-slate-700"
                            @click="vendorModalOpen = false"
                        >
                            <span class="material-symbols-outlined">close</span>
                        </button>
                    </div>
                </div>
            </template>

            <template #content>
                <div class="space-y-5 px-6 py-6">
                    <div
                        v-if="vendorSaveError"
                        class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                    >
                        {{ vendorSaveError }}
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">厂商编码</span>
                            <input
                                v-model.trim="vendorForm.vendorCode"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：aliyun"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">厂商名称</span>
                            <input
                                v-model.trim="vendorForm.vendorName"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：阿里云"
                            />
                        </label>
                    </div>

                    <label class="block space-y-2">
                        <span class="text-sm font-semibold text-slate-600">厂商描述</span>
                        <textarea
                            v-model="vendorForm.description"
                            rows="4"
                            class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                            placeholder="补充厂商定位、接入范围或注意事项。"
                        />
                    </label>

                    <label class="block max-w-xs space-y-2">
                        <span class="text-sm font-semibold text-slate-600">状态</span>
                        <AppSelect
                            v-model="vendorForm.status"
                            :options="statusOptions"
                            :button-class="ADMIN_SELECT_BUTTON_CLASS"
                        />
                    </label>
                </div>
            </template>

            <template #footer>
                <div
                    class="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4"
                >
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="vendorModalOpen = false"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="vendorSaving"
                        @click="handleSaveVendor"
                    >
                        {{ vendorSaving ? '保存中...' : '保存厂商' }}
                    </button>
                </div>
            </template>
        </BaseModal>

        <BaseModal :open="modelModalOpen" panel-class="max-w-5xl" @close="modelModalOpen = false">
            <template #header>
                <div class="border-b border-slate-200 px-6 py-5">
                    <div class="flex items-center justify-between">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">{{ modalTitle }}</h3>
                            <p class="mt-1 text-sm text-slate-400">
                                第一阶段支持对话、向量、Rerank 三类模型，并兼容 Qwen Online 与
                                vLLM。
                            </p>
                        </div>
                        <button
                            type="button"
                            class="text-slate-400 transition hover:text-slate-700"
                            @click="modelModalOpen = false"
                        >
                            <span class="material-symbols-outlined">close</span>
                        </button>
                    </div>
                </div>
            </template>

            <template #content>
                <div class="space-y-5 px-6 py-6">
                    <div
                        v-if="modelSaveError"
                        class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                    >
                        {{ modelSaveError }}
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-4">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">模型编码</span>
                            <input
                                v-model.trim="modelForm.modelCode"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：qwen-chat-main"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">展示名称</span>
                            <input
                                v-model.trim="modelForm.displayName"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：主对话模型"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">能力类型</span>
                            <AppSelect
                                v-model="modelForm.capabilityType"
                                :options="capabilityOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">状态</span>
                            <AppSelect
                                v-model="modelForm.status"
                                :options="statusOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-3">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">厂商</span>
                            <AppSelect
                                v-model="modelForm.vendorId"
                                :options="
                                    vendors.map(item => ({
                                        value: item.id,
                                        label: item.vendorName,
                                    }))
                                "
                                placeholder="请选择厂商"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">适配器</span>
                            <AppSelect
                                v-model="modelForm.adapterType"
                                :options="adapterOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">协议</span>
                            <input
                                v-model.trim="modelForm.protocol"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                :placeholder="
                                    modelForm.capabilityType === 'RERANK'
                                        ? '可留空，自动按适配器推断'
                                        : '非 Rerank 可留空'
                                "
                            />
                        </label>
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Base URL</span>
                            <input
                                v-model.trim="modelForm.baseUrl"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="https://dashscope.aliyuncs.com/compatible-mode"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">模型名</span>
                            <input
                                v-model.trim="modelForm.modelName"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：qwen3-max"
                            />
                        </label>
                    </div>

                    <div class="grid grid-cols-1 gap-5 md:grid-cols-[minmax(0,1fr)_160px]">
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Path</span>
                            <input
                                v-model.trim="modelForm.path"
                                type="text"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="/v1/chat/completions"
                            />
                        </label>
                        <div class="flex items-end">
                            <button
                                type="button"
                                class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                                @click="applySuggestedPath"
                            >
                                使用建议路径
                            </button>
                        </div>
                    </div>

                    <label class="block space-y-2">
                        <span class="text-sm font-semibold text-slate-600">API Key</span>
                        <input
                            v-model="modelForm.apiKey"
                            type="password"
                            class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                            :placeholder="
                                modelForm.apiKeyConfigured
                                    ? '已配置，留空则保持原值'
                                    : '请输入 API Key'
                            "
                        />
                        <p class="text-xs text-slate-400">列表与详情不会回显明文密钥。</p>
                    </label>

                    <div
                        v-if="modelForm.capabilityType === 'CHAT'"
                        class="grid grid-cols-1 gap-5 md:grid-cols-3"
                    >
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Temperature</span>
                            <input
                                v-model="modelForm.temperature"
                                type="number"
                                step="0.1"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="留空则沿用默认"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Max Tokens</span>
                            <input
                                v-model="modelForm.maxTokens"
                                type="number"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="留空则沿用默认"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Thinking</span>
                            <AppSelect
                                v-model="modelForm.enableThinking"
                                :options="booleanOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                    </div>

                    <label v-if="modelForm.capabilityType === 'CHAT'" class="block space-y-2">
                        <span class="text-sm font-semibold text-slate-600">系统提示词</span>
                        <textarea
                            v-model="modelForm.systemPrompt"
                            rows="4"
                            class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                            placeholder="留空则使用系统默认值。"
                        />
                    </label>

                    <div
                        v-if="modelForm.capabilityType === 'EMBEDDING'"
                        class="grid grid-cols-1 gap-5 md:grid-cols-2"
                    >
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">向量维度</span>
                            <input
                                v-model="modelForm.dimensions"
                                type="number"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：1024"
                            />
                        </label>
                    </div>

                    <div
                        v-if="modelForm.capabilityType === 'RERANK'"
                        class="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3"
                    >
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">超时时间（ms）</span>
                            <input
                                v-model="modelForm.timeoutMs"
                                type="number"
                                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="例如：1200"
                            />
                        </label>
                        <label class="space-y-2">
                            <span class="text-sm font-semibold text-slate-600">失败回退 RRF</span>
                            <AppSelect
                                v-model="modelForm.fallbackRrf"
                                :options="booleanOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>
                    </div>

                    <label class="block space-y-2">
                        <span class="text-sm font-semibold text-slate-600">额外配置 JSON</span>
                        <textarea
                            v-model="modelForm.extraConfigJson"
                            rows="4"
                            class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                            placeholder='例如：{"headers":{"x-trace":"demo"}}'
                        />
                    </label>
                </div>
            </template>

            <template #footer>
                <div
                    class="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4"
                >
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="modelModalOpen = false"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl bg-primary px-5 py-2 text-sm font-semibold text-white transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="modelSaving"
                        @click="handleSaveModel"
                    >
                        {{ modelSaving ? '保存中...' : '保存模型' }}
                    </button>
                </div>
            </template>
        </BaseModal>
    </section>
</template>
