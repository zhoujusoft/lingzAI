<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import BaseModal from '@/components/feedback/BaseModal.vue';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    createModelLibraryModel,
    listModelLibraryDefaults,
    listModelLibraryModels,
    listModelLibraryVendors,
    saveModelLibraryDefaultBinding,
    updateModelLibraryModel,
    validateModelLibraryVendor,
    updateModelLibraryVendor,
} from '@/api/model-library';
import { ROUTE_PATHS } from '@/router/routePaths';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const expandedVendorId = ref('');

const modelModalOpen = ref(false);
const modelSaving = ref(false);
const modelSaveError = ref('');
const modelEditingId = ref(null);
const modelAdvancedOpen = ref(false);
const defaultModalOpen = ref(false);
const defaultModalSaving = ref(false);
const suppressModelTemplateSync = ref(false);

const defaultSavingMap = reactive({
    CHAT: false,
    EMBEDDING: false,
    RERANK: false,
});

const vendorSavingMap = reactive({});
const vendorErrorMap = reactive({});
const vendorDrafts = reactive({});

const providerFilters = reactive({
    keyword: '',
    capabilityType: '',
    status: '',
});

const modelForm = reactive(createEmptyModelForm());

const vendors = ref([]);
const allModels = ref([]);
const defaultBindings = ref([]);
const defaultSelections = reactive({
    CHAT: '',
    EMBEDDING: '',
    RERANK: '',
});

const capabilityOptions = [
    { value: 'CHAT', label: 'LLM / 对话' },
    { value: 'EMBEDDING', label: 'TEXT EMBEDDING' },
    { value: 'RERANK', label: 'RERANK' },
];

const rerankProtocolOptions = [
    { value: 'dashscope', label: 'DashScope 兼容' },
    { value: 'vllm', label: 'vLLM / OpenAI 兼容' },
];

const modelModalTitle = computed(() => (modelEditingId.value ? '编辑模型' : '添加模型'));

const vendorSelectOptions = computed(() =>
    vendors.value.map(item => ({
        value: item.id,
        label: item.vendorName,
    }))
);

const providerStats = computed(() => ({
    vendorCount: vendors.value.length,
    modelCount: allModels.value.length,
    activeCount: allModels.value.filter(item => item.status === 'ACTIVE').length,
}));

const defaultBindingMap = computed(() => {
    const map = {};
    defaultBindings.value.forEach(item => {
        map[item.capabilityType] = item;
    });
    return map;
});

const filteredModels = computed(() =>
    allModels.value.filter(item => {
        if (
            providerFilters.capabilityType &&
            item.capabilityType !== providerFilters.capabilityType
        ) {
            return false;
        }
        if (providerFilters.status && item.status !== providerFilters.status) {
            return false;
        }
        if (!providerFilters.keyword) {
            return true;
        }
        const keyword = providerFilters.keyword.trim().toLowerCase();
        return [item.displayName, item.modelCode, item.modelName, item.vendorName]
            .filter(Boolean)
            .some(value => String(value).toLowerCase().includes(keyword));
    })
);

const modelsByVendor = computed(() => {
    const capabilityOrder = capabilityOptions.reduce((accumulator, item, index) => {
        accumulator[item.value] = index;
        return accumulator;
    }, {});
    const map = {};
    vendors.value.forEach(vendor => {
        map[vendor.id] = filteredModels.value
            .filter(item => item.vendorId === vendor.id)
            .slice()
            .sort((left, right) => {
                if (Boolean(left.defaultModel) !== Boolean(right.defaultModel)) {
                    return left.defaultModel ? -1 : 1;
                }
                const leftCapabilityOrder = capabilityOrder[left.capabilityType] ?? 99;
                const rightCapabilityOrder = capabilityOrder[right.capabilityType] ?? 99;
                if (leftCapabilityOrder !== rightCapabilityOrder) {
                    return leftCapabilityOrder - rightCapabilityOrder;
                }
                return String(left.displayName || left.modelName || left.modelCode).localeCompare(
                    String(right.displayName || right.modelName || right.modelCode),
                    'zh-CN'
                );
            });
    });
    return map;
});

const visibleVendors = computed(() => {
    const keyword = providerFilters.keyword.trim().toLowerCase();
    const filtersApplied = Boolean(
        keyword || providerFilters.capabilityType || providerFilters.status
    );
    return vendors.value.filter(vendor => {
        if (!filtersApplied) {
            return true;
        }
        const matchedModels = modelsByVendor.value[vendor.id] || [];
        if (matchedModels.length > 0) {
            return true;
        }
        if (!keyword) {
            return false;
        }
        return [vendor.vendorName, vendor.vendorCode, vendor.description]
            .filter(Boolean)
            .some(value => String(value).toLowerCase().includes(keyword));
    });
});

const filteredModelsByCapability = computed(() =>
    capabilityOptions.reduce((accumulator, item) => {
        accumulator[item.value] = allModels.value.filter(
            model => model.capabilityType === item.value && model.status === 'ACTIVE'
        );
        return accumulator;
    }, {})
);

function createEmptyModelForm() {
    return {
        modelCode: '',
        displayName: '',
        capabilityType: 'CHAT',
        vendorId: '',
        baseUrl: '',
        baseUrlDirty: false,
        apiKey: '',
        apiKeyDirty: false,
        modelName: '',
        path: '',
        protocol: '',
        temperature: '',
        maxTokens: '',
        systemPrompt: '',
        dimensions: '1024',
        timeoutMs: '1200',
        fallbackRrf: true,
        apiKeyConfigured: false,
    };
}

function findVendorById(vendorId) {
    const targetId = Number(vendorId);
    if (!targetId) {
        return null;
    }
    return vendors.value.find(item => item.id === targetId) || null;
}

function resolveModelVendorCode(vendorId) {
    return findVendorById(vendorId)?.vendorCode || 'QWEN_ONLINE';
}

function buildModelTemplate(vendorCode, capabilityType) {
    const normalizedVendorCode = vendorCode || 'QWEN_ONLINE';
    const normalizedCapabilityType = capabilityType || 'CHAT';
    const vendorBaseUrl = findVendorById(modelForm.vendorId)?.defaultBaseUrl || '';
    if (normalizedCapabilityType === 'EMBEDDING') {
        return {
            baseUrl:
                normalizedVendorCode === 'QWEN_ONLINE'
                    ? vendorBaseUrl || 'https://dashscope.aliyuncs.com/compatible-mode'
                    : vendorBaseUrl,
            path: '/v1/embeddings',
            protocol: '',
            temperature: '0.7',
            maxTokens: '4096',
            systemPrompt: '',
            dimensions: '1024',
            timeoutMs: '',
            fallbackRrf: true,
        };
    }
    if (normalizedCapabilityType === 'RERANK') {
        return {
            baseUrl:
                normalizedVendorCode === 'QWEN_ONLINE'
                    ? vendorBaseUrl || 'https://dashscope.aliyuncs.com'
                    : vendorBaseUrl,
            path:
                normalizedVendorCode === 'VLLM'
                    ? '/v1/rerank'
                    : '/api/v1/services/rerank/text-rerank/text-rerank',
            protocol: normalizedVendorCode === 'VLLM' ? 'vllm' : 'dashscope',
            temperature: '0.7',
            maxTokens: '4096',
            systemPrompt: '',
            dimensions: '',
            timeoutMs: '1200',
            fallbackRrf: true,
        };
    }
    return {
        baseUrl:
            normalizedVendorCode === 'QWEN_ONLINE'
                ? vendorBaseUrl || 'https://dashscope.aliyuncs.com/compatible-mode'
                : vendorBaseUrl,
        path: '/v1/chat/completions',
        protocol: '',
        temperature: '0.7',
        maxTokens: '4096',
        systemPrompt: '',
        dimensions: '',
        timeoutMs: '',
        fallbackRrf: true,
    };
}

function applyModelTemplateDefaults(force = false) {
    const template = buildModelTemplate(
        resolveModelVendorCode(modelForm.vendorId),
        modelForm.capabilityType
    );
    const assignIfNeeded = (key, nextValue) => {
        if (force || modelForm[key] === '' || modelForm[key] == null) {
            modelForm[key] = nextValue;
        }
    };
    if (force || !modelForm.baseUrlDirty) {
        assignIfNeeded('baseUrl', template.baseUrl);
    }
    assignIfNeeded('path', template.path);
    assignIfNeeded('protocol', template.protocol);
    assignIfNeeded('temperature', template.temperature);
    assignIfNeeded('maxTokens', template.maxTokens);
    assignIfNeeded('systemPrompt', template.systemPrompt);
    assignIfNeeded('dimensions', template.dimensions);
    assignIfNeeded('timeoutMs', template.timeoutMs);
    if (force || modelForm.fallbackRrf == null) {
        modelForm.fallbackRrf = template.fallbackRrf;
    }
}

function syncTemplateFieldsBySelection() {
    const template = buildModelTemplate(
        resolveModelVendorCode(modelForm.vendorId),
        modelForm.capabilityType
    );
    if (!modelForm.baseUrlDirty || !modelForm.baseUrl) {
        modelForm.baseUrl = template.baseUrl;
    }
    modelForm.path = template.path;
    modelForm.protocol = template.protocol;
    if (modelForm.capabilityType === 'CHAT') {
        modelForm.temperature = template.temperature;
        modelForm.maxTokens = template.maxTokens;
        modelForm.dimensions = '';
        modelForm.timeoutMs = '';
        modelForm.fallbackRrf = true;
    } else if (modelForm.capabilityType === 'EMBEDDING') {
        modelForm.temperature = template.temperature;
        modelForm.maxTokens = template.maxTokens;
        modelForm.systemPrompt = '';
        modelForm.dimensions = template.dimensions;
        modelForm.timeoutMs = '';
        modelForm.fallbackRrf = true;
    } else {
        modelForm.temperature = template.temperature;
        modelForm.maxTokens = template.maxTokens;
        modelForm.systemPrompt = '';
        modelForm.dimensions = '';
        modelForm.timeoutMs = template.timeoutMs;
        modelForm.fallbackRrf = template.fallbackRrf;
    }
}

function modelBaseUrlPlaceholder() {
    const template = buildModelTemplate(
        resolveModelVendorCode(modelForm.vendorId),
        modelForm.capabilityType
    );
    return template.baseUrl || '例如：http://127.0.0.1:8000';
}

function modelPathPlaceholder() {
    return buildModelTemplate(resolveModelVendorCode(modelForm.vendorId), modelForm.capabilityType)
        .path;
}

function modelPathHint() {
    const vendorCode = resolveModelVendorCode(modelForm.vendorId);
    if (vendorCode === 'VLLM') {
        return 'vLLM 与私有部署的端口、网关前缀、路由都可能不同，可按实际部署修改。';
    }
    if (modelForm.capabilityType === 'RERANK') {
        return '通义 Rerank 默认走 DashScope 重排序服务路径。';
    }
    return '已按当前模型模板填入默认路径，通常无需额外调整。';
}

function showChatAdvancedFields() {
    return modelForm.capabilityType === 'CHAT';
}

function showEmbeddingAdvancedFields() {
    return modelForm.capabilityType === 'EMBEDDING';
}

function showRerankAdvancedFields() {
    return modelForm.capabilityType === 'RERANK';
}

function showBasicConnectionFields() {
    return resolveModelVendorCode(modelForm.vendorId) === 'VLLM';
}

function showAdvancedConnectionFields() {
    return resolveModelVendorCode(modelForm.vendorId) === 'QWEN_ONLINE';
}

function showBasicPathField() {
    return resolveModelVendorCode(modelForm.vendorId) === 'VLLM';
}

function showAdvancedPathField() {
    return resolveModelVendorCode(modelForm.vendorId) !== 'VLLM';
}

function handleModelApiKeyFocus() {
    if (modelForm.apiKeyConfigured && !modelForm.apiKeyDirty) {
        modelForm.apiKey = '';
    }
}

function handleModelApiKeyInput(event) {
    modelForm.apiKeyDirty = true;
    modelForm.apiKey = event?.target?.value || '';
}

function createVendorDraft(item) {
    return {
        defaultBaseUrl: item?.defaultBaseUrl || '',
        apiKey: item?.apiKeyConfigured ? '••••••••••••••••' : '',
        apiKeyDirty: false,
        status: item?.status || 'ACTIVE',
        apiKeyConfigured: Boolean(item?.apiKeyConfigured),
    };
}

function syncVendorDrafts(list) {
    Object.keys(vendorDrafts).forEach(key => {
        delete vendorDrafts[key];
    });
    Object.keys(vendorSavingMap).forEach(key => {
        delete vendorSavingMap[key];
    });
    Object.keys(vendorErrorMap).forEach(key => {
        delete vendorErrorMap[key];
    });
    (Array.isArray(list) ? list : []).forEach(item => {
        vendorDrafts[item.id] = createVendorDraft(item);
        vendorSavingMap[item.id] = false;
        vendorErrorMap[item.id] = '';
    });
}

function buildVendorPayload(vendorId) {
    const draft = vendorDrafts[vendorId];
    if (!draft) {
        return {
            defaultBaseUrl: '',
            apiKey: '',
            status: 'ACTIVE',
        };
    }
    return {
        defaultBaseUrl: draft.defaultBaseUrl,
        apiKey: draft.apiKeyDirty ? draft.apiKey : '',
        status: draft.status,
    };
}

function handleVendorApiKeyFocus(vendorId) {
    const draft = vendorDrafts[vendorId];
    if (!draft) {
        return;
    }
    if (draft.apiKeyConfigured && !draft.apiKeyDirty) {
        draft.apiKey = '';
    }
}

function handleVendorApiKeyInput(vendorId, event) {
    const draft = vendorDrafts[vendorId];
    if (!draft) {
        return;
    }
    draft.apiKeyDirty = true;
    draft.apiKey = event?.target?.value || '';
}

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function capabilityLabel(value) {
    return capabilityOptions.find(item => item.value === value)?.label || value || '未设置';
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

function vendorModeLabel(mode) {
    return mode === 'PREDEFINED' ? '在线模型' : '部署模型';
}

function vendorModeClass(mode) {
    return mode === 'PREDEFINED'
        ? 'bg-blue-50 text-primary ring-1 ring-blue-100'
        : 'bg-amber-50 text-amber-700 ring-1 ring-amber-100';
}

function vendorSummary(item) {
    if (item.vendorCode === 'QWEN_ONLINE') {
        return '配置一次厂商默认 API Key 后，即可启用在线对话、向量与 Rerank 模型，也支持继续补充部署模型。';
    }
    return '适用于离线部署、自建服务或私有网关。可不配置 API Key，模型也可继承厂商默认连接信息或逐个覆盖。';
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

function baseUrlSourceLabel(item) {
    if (item.baseUrlInherited) {
        return '继承厂商';
    }
    if (item.baseUrl) {
        return '模型覆盖';
    }
    return '等待回退';
}

function apiKeySourceLabel(item) {
    if (!item.apiKeyConfigured) {
        return '未配置';
    }
    return item.apiKeyInherited ? '继承厂商' : '模型覆盖';
}

function createDefaultOptions(capabilityType) {
    return [
        { value: '', label: '暂不设置默认模型' },
        ...(filteredModelsByCapability.value[capabilityType] || []).map(item => ({
            value: item.id,
            label: item.displayName,
            description: `${item.vendorName || '未绑定厂商'} · ${item.modelName || '未设置模型名'}`,
        })),
    ];
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

function vendorModels(vendorId, capabilityType) {
    return (modelsByVendor.value[vendorId] || []).filter(
        item => item.capabilityType === capabilityType
    );
}

function vendorTotalModelCount(vendorId) {
    return allModels.value.filter(item => item.vendorId === vendorId).length;
}

function vendorActiveModelCount(vendorId) {
    return allModels.value.filter(item => item.vendorId === vendorId && item.status === 'ACTIVE')
        .length;
}

function toggleVendorExpanded(vendorId) {
    expandedVendorId.value = expandedVendorId.value === vendorId ? '' : vendorId;
}

async function loadVendors() {
    vendors.value = await listModelLibraryVendors(handleUnauthorized);
    syncVendorDrafts(vendors.value);
    if (!vendors.value.some(item => item.id === expandedVendorId.value)) {
        expandedVendorId.value = vendors.value[0]?.id || '';
    }
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
        await Promise.all([loadVendors(), loadAllModels(), loadDefaults()]);
    } catch (error) {
        loadError.value = error?.message || '模型库加载失败';
        vendors.value = [];
        allModels.value = [];
        defaultBindings.value = [];
        fillDefaultSelections([]);
        syncVendorDrafts([]);
    } finally {
        loading.value = false;
    }
}

function openCreateModelModal(vendorId = '') {
    modelEditingId.value = null;
    modelSaveError.value = '';
    modelAdvancedOpen.value = false;
    suppressModelTemplateSync.value = true;
    Object.assign(modelForm, createEmptyModelForm(), {
        vendorId: vendorId || vendors.value[0]?.id || '',
    });
    applyModelTemplateDefaults(true);
    suppressModelTemplateSync.value = false;
    modelModalOpen.value = true;
}

function openEditModelModal(item) {
    modelEditingId.value = item?.id || null;
    modelSaveError.value = '';
    modelAdvancedOpen.value = false;
    suppressModelTemplateSync.value = true;
    Object.assign(modelForm, createEmptyModelForm(), {
        modelCode: item?.modelCode || '',
        displayName: item?.displayName || '',
        capabilityType: item?.capabilityType || 'CHAT',
        vendorId: item?.vendorId || vendors.value[0]?.id || '',
        baseUrl: item?.baseUrl || '',
        baseUrlDirty: false,
        apiKey: '',
        apiKeyDirty: false,
        modelName: item?.modelName || '',
        path: item?.path || '',
        protocol: item?.protocol || '',
        temperature:
            item?.temperature === null || item?.temperature === undefined
                ? ''
                : String(item.temperature),
        maxTokens:
            item?.maxTokens === null || item?.maxTokens === undefined
                ? ''
                : String(item.maxTokens),
        systemPrompt: item?.systemPrompt || '',
        dimensions:
            item?.dimensions === null || item?.dimensions === undefined
                ? ''
                : String(item.dimensions),
        timeoutMs:
            item?.timeoutMs === null || item?.timeoutMs === undefined
                ? ''
                : String(item.timeoutMs),
        fallbackRrf:
            typeof item?.fallbackRrf === 'boolean' ? item.fallbackRrf : true,
        apiKeyConfigured: Boolean(item?.apiKeyConfigured),
    });
    applyModelTemplateDefaults(false);
    suppressModelTemplateSync.value = false;
    modelModalOpen.value = true;
}

function buildModelPayload() {
    const normalizeNumber = value => {
        if (value === '' || value == null) {
            return null;
        }
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    };
    return {
        modelCode: modelForm.modelCode || '',
        displayName: modelForm.displayName,
        capabilityType: modelForm.capabilityType,
        vendorId: modelForm.vendorId || null,
        baseUrl: modelForm.baseUrl,
        apiKey: modelForm.apiKeyDirty ? modelForm.apiKey : '',
        modelName: modelForm.modelName,
        status: 'ACTIVE',
        protocol: showRerankAdvancedFields() ? modelForm.protocol : '',
        path: modelForm.path,
        temperature: showChatAdvancedFields()
            ? normalizeNumber(modelForm.temperature)
            : null,
        maxTokens: showChatAdvancedFields()
            ? normalizeNumber(modelForm.maxTokens)
            : null,
        systemPrompt: showChatAdvancedFields() ? modelForm.systemPrompt : '',
        enableThinking: null,
        dimensions: showEmbeddingAdvancedFields()
            ? normalizeNumber(modelForm.dimensions)
            : null,
        timeoutMs: showRerankAdvancedFields()
            ? normalizeNumber(modelForm.timeoutMs)
            : null,
        fallbackRrf: showRerankAdvancedFields() ? Boolean(modelForm.fallbackRrf) : null,
    };
}

async function handleSaveVendor(vendor) {
    const draft = vendorDrafts[vendor.id];
    if (!draft) {
        return;
    }
    vendorSavingMap[vendor.id] = true;
    vendorErrorMap[vendor.id] = '';
    try {
        const payload = buildVendorPayload(vendor.id);
        await validateModelLibraryVendor(vendor.id, payload, handleUnauthorized);
        await updateModelLibraryVendor(vendor.id, payload, handleUnauthorized);
        await Promise.all([loadVendors(), loadAllModels()]);
        await alert({
            title: '厂商配置已保存',
            message:
                vendor.vendorCode === 'VLLM'
                    ? `${vendor.vendorName} 连接校验通过，默认连接信息已更新。`
                    : `${vendor.vendorName} 的 API Key 校验通过，默认连接信息已更新。`,
        });
    } catch (error) {
        vendorErrorMap[vendor.id] = error?.message || '厂商配置保存失败';
    } finally {
        vendorSavingMap[vendor.id] = false;
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
        await Promise.all([loadVendors(), loadAllModels(), loadDefaults()]);
    } catch (error) {
        modelSaveError.value = error?.message || '模型保存失败';
    } finally {
        modelSaving.value = false;
    }
}

function handleModelBaseUrlInput(event) {
    modelForm.baseUrl = event?.target?.value || '';
    modelForm.baseUrlDirty = true;
}

async function handleSetDefault(item) {
    if (!item?.id || !item?.capabilityType) {
        return;
    }
    try {
        await saveModelLibraryDefaultBinding(
            item.capabilityType,
            { modelId: item.id },
            handleUnauthorized
        );
        await Promise.all([loadAllModels(), loadDefaults()]);
    } catch (error) {
        await alert({
            title: '设置失败',
            message: error?.message || '默认模型设置失败',
        });
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
        await Promise.all([loadAllModels(), loadDefaults()]);
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

async function handleSaveAllDefaults() {
    defaultModalSaving.value = true;
    try {
        for (const capability of capabilityOptions) {
            await saveModelLibraryDefaultBinding(
                capability.value,
                {
                    modelId: defaultSelections[capability.value] || null,
                },
                handleUnauthorized
            );
        }
        await Promise.all([loadAllModels(), loadDefaults()]);
        defaultModalOpen.value = false;
        await alert({
            title: '默认模型已更新',
            message: '对话、向量和 Rerank 默认模型已更新。',
        });
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '默认模型保存失败',
        });
    } finally {
        defaultModalSaving.value = false;
    }
}

watch(
    () => [modelForm.vendorId, modelForm.capabilityType],
    () => {
        if (suppressModelTemplateSync.value || !modelModalOpen.value) {
            return;
        }
        syncTemplateFieldsBySelection();
    }
);

onMounted(loadPage);
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-50">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        Model Library
                    </p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">模型库</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        管理模型厂商、内置模型与自定义模型，默认模型通过右上角入口统一配置。
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="defaultModalOpen = true"
                    >
                        默认模型
                    </button>
                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="loadPage"
                    >
                        刷新模型库
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-5 pb-6 pt-4">
            <p
                v-if="loadError"
                class="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <section class="space-y-3">
                <article
                    v-for="vendor in visibleVendors"
                    :key="vendor.id"
                    class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
                >
                    <!-- Vendor Header (always visible, clickable to toggle) -->
                    <div
                        class="flex cursor-pointer select-none items-center justify-between px-4 py-3 transition-colors hover:bg-slate-50/80"
                        @click="toggleVendorExpanded(vendor.id)"
                    >
                        <div class="flex min-w-0 items-center gap-3">
                            <div
                                class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-blue-50 to-blue-100"
                            >
                                <span class="text-xs font-bold text-blue-600/70">{{
                                    (vendor.vendorName || '?')[0]
                                }}</span>
                            </div>
                            <div class="min-w-0">
                                <div class="flex items-center gap-2">
                                    <h3 class="truncate text-sm font-bold text-slate-900">
                                        {{ vendor.vendorName }}
                                    </h3>
                                    <span
                                        class="flex-shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                        :class="vendorModeClass(vendor.mode)"
                                    >
                                        {{ vendorModeLabel(vendor.mode) }}
                                    </span>
                                </div>
                                <p class="mt-0.5 text-xs text-slate-400">
                                    {{ vendorActiveModelCount(vendor.id) }} 个激活模型 · 共
                                    {{ vendorTotalModelCount(vendor.id) }} 个
                                </p>
                            </div>
                        </div>
                        <div class="flex flex-shrink-0 items-center gap-3">
                            <span
                                class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold"
                                :class="statusMeta(vendor.status).badgeClass"
                            >
                                <span
                                    class="inline-block h-1.5 w-1.5 rounded-full"
                                    :class="statusMeta(vendor.status).dotClass"
                                />
                                {{ statusMeta(vendor.status).label }}
                            </span>
                            <span
                                class="material-symbols-outlined text-[20px] text-slate-400 transition-transform duration-200"
                                :class="{
                                    'rotate-180': expandedVendorId === vendor.id,
                                }"
                            >
                                keyboard_arrow_down
                            </span>
                        </div>
                    </div>

                    <!-- Expanded Content: Left=Model List, Right=Vendor Config -->
                    <div
                        v-if="expandedVendorId === vendor.id"
                        class="flex border-t border-slate-100"
                    >
                        <!-- Left: Model List -->
                        <div class="w-1/2 border-r border-slate-100 p-4">
                            <div class="mb-3 flex items-center justify-between">
                                <span
                                    class="text-xs font-semibold uppercase tracking-wider text-slate-400"
                                    >可用模型</span
                                >
                                <button
                                    type="button"
                                    class="text-xs font-semibold text-primary transition-colors hover:text-primary-dim"
                                    @click.stop="openCreateModelModal(vendor.id)"
                                >
                                    + 添加模型
                                </button>
                            </div>

                            <div
                                v-if="!(modelsByVendor[vendor.id] || []).length"
                                class="py-8 text-center text-sm text-slate-400"
                            >
                                暂无模型
                            </div>

                            <div
                                v-else
                                class="custom-scrollbar max-h-[360px] space-y-1 overflow-y-auto"
                            >
                                <div
                                    v-for="item in modelsByVendor[vendor.id] || []"
                                    :key="item.id"
                                    class="group flex cursor-pointer items-center justify-between rounded-lg p-2 transition-colors hover:bg-slate-50"
                                >
                                    <div class="flex min-w-0 items-center gap-2.5">
                                        <div class="min-w-0">
                                            <div class="flex items-center gap-2">
                                                <span
                                                    class="truncate text-sm font-medium text-slate-800"
                                                    >{{ item.displayName }}</span
                                                >
                                                <span
                                                    v-if="item.defaultModel"
                                                    class="flex-shrink-0 rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-semibold text-primary ring-1 ring-blue-100"
                                                >
                                                    默认
                                                </span>
                                                <span
                                                    class="flex-shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-500"
                                                >
                                                    {{ capabilityLabel(item.capabilityType) }}
                                                </span>
                                                <span
                                                    v-if="item.status !== 'ACTIVE'"
                                                    class="flex-shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-600 ring-1 ring-amber-100"
                                                >
                                                    草稿
                                                </span>
                                            </div>
                                            <p class="mt-0.5 truncate text-[11px] text-slate-400">
                                                {{ item.modelName || item.modelCode }}
                                            </p>
                                        </div>
                                    </div>
                                    <div class="flex items-center gap-1">
                                        <button
                                            v-if="!item.defaultModel && item.status === 'ACTIVE'"
                                            type="button"
                                            class="whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-semibold text-slate-400 opacity-0 transition hover:bg-blue-50 hover:text-primary group-hover:opacity-100"
                                            @click.stop="handleSetDefault(item)"
                                        >
                                            设为默认
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-full p-1 text-slate-400 opacity-0 transition hover:bg-slate-200 hover:text-primary group-hover:opacity-100"
                                            @click.stop="openEditModelModal(item)"
                                        >
                                            <span class="material-symbols-outlined text-[16px]"
                                                >edit</span
                                            >
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Right: Vendor Config -->
                        <div class="w-1/2 bg-slate-50/50 p-4">
                            <span
                                class="mb-3 block text-xs font-semibold uppercase tracking-wider text-slate-400"
                                >厂商配置</span
                            >
                            <div class="space-y-3">
                                <label class="block space-y-1.5">
                                    <span class="text-xs font-semibold text-slate-600"
                                        >默认 Base URL</span
                                    >
                                    <input
                                        v-model.trim="vendorDrafts[vendor.id].defaultBaseUrl"
                                        type="text"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                                        :placeholder="
                                            vendor.vendorCode === 'QWEN_ONLINE'
                                                ? 'https://dashscope.aliyuncs.com/compatible-mode'
                                                : '例如：http://127.0.0.1:8000'
                                        "
                                        @click.stop
                                    />
                                </label>
                                <label class="block space-y-1.5">
                                    <span class="text-xs font-semibold text-slate-600"
                                        >默认 API Key</span
                                    >
                                    <input
                                        type="password"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                                        :placeholder="
                                            vendorDrafts[vendor.id].apiKeyConfigured
                                                ? '已配置，留空则保持原值'
                                                : vendor.vendorCode === 'VLLM'
                                                  ? '可选；无鉴权部署可留空'
                                                  : '请输入默认 API Key'
                                        "
                                        :value="vendorDrafts[vendor.id].apiKey"
                                        @focus="handleVendorApiKeyFocus(vendor.id)"
                                        @input="handleVendorApiKeyInput(vendor.id, $event)"
                                        @click.stop
                                    />
                                </label>
                            </div>

                            <p
                                v-if="vendorErrorMap[vendor.id]"
                                class="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600"
                            >
                                {{ vendorErrorMap[vendor.id] }}
                            </p>

                            <div class="mt-4 flex items-center justify-between gap-3">
                                <p class="text-xs text-slate-400">
                                    {{
                                        vendor.apiKeyConfigured
                                            ? '已存在可继承的默认密钥'
                                            : vendor.vendorCode === 'VLLM'
                                              ? '当前未配置默认密钥，无鉴权部署可直接使用'
                                              : '当前尚未配置默认密钥'
                                    }}
                                </p>
                                <button
                                    type="button"
                                    class="flex-shrink-0 whitespace-nowrap rounded-xl bg-primary px-5 py-2 text-sm font-semibold text-white transition-all active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300"
                                    :disabled="vendorSavingMap[vendor.id]"
                                    @click.stop="handleSaveVendor(vendor)"
                                >
                                    {{ vendorSavingMap[vendor.id] ? '保存中...' : '保存配置' }}
                                </button>
                            </div>
                        </div>
                    </div>
                </article>
            </section>
        </div>

        <BaseModal :open="modelModalOpen" panel-class="max-w-6xl" @close="modelModalOpen = false">
            <template #header>
                <div class="border-b border-slate-200 px-5 py-4">
                    <div class="flex items-center justify-between">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">
                                {{ modelModalTitle }}
                            </h3>
                            <p class="mt-1 text-sm text-slate-500">
                                基础信息按厂商与模型类型动态收敛，高级参数默认折叠。
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
                <div class="space-y-5 px-5 py-5">
                    <div
                        v-if="modelSaveError"
                        class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                    >
                        {{ modelSaveError }}
                    </div>

                    <div class="grid gap-4 md:grid-cols-2">
                        <label class="block space-y-2">
                            <span class="text-sm font-semibold text-slate-600">模型名称</span>
                            <input
                                v-model.trim="modelForm.displayName"
                                type="text"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="用于展示的名称，例如：通义增强对话模型"
                            />
                        </label>

                        <label class="block space-y-2">
                            <span class="text-sm font-semibold text-slate-600">模型标识</span>
                            <input
                                v-model.trim="modelForm.modelName"
                                type="text"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                placeholder="调用时使用的 model 参数，例如：qwen-plus"
                            />
                        </label>

                        <label class="block space-y-2">
                            <span class="text-sm font-semibold text-slate-600">能力类型</span>
                            <AppSelect
                                v-model="modelForm.capabilityType"
                                :options="capabilityOptions"
                                :button-class="ADMIN_SELECT_BUTTON_CLASS"
                            />
                        </label>

                        <label v-if="showBasicConnectionFields()" class="block space-y-2">
                            <span class="text-sm font-semibold text-slate-600">Base URL</span>
                            <input
                                type="text"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                :value="modelForm.baseUrl"
                                :placeholder="modelBaseUrlPlaceholder()"
                                @input="handleModelBaseUrlInput"
                            />
                            <p class="text-xs text-slate-400">
                                vLLM 与私有部署通常需要根据实际端口和网关地址调整。
                            </p>
                        </label>

                        <label v-if="showBasicConnectionFields()" class="block space-y-2">
                            <span class="text-sm font-semibold text-slate-600">API Key</span>
                            <input
                                type="password"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                :placeholder="
                                    modelForm.apiKeyConfigured
                                        ? '已配置，重新输入后将覆盖当前值'
                                        : resolveModelVendorCode(modelForm.vendorId) === 'VLLM'
                                          ? '可选；无鉴权部署可留空'
                                          : '如需单独覆盖，可在此输入模型级 API Key'
                                "
                                :value="modelForm.apiKey"
                                @focus="handleModelApiKeyFocus"
                                @input="handleModelApiKeyInput"
                            />
                            <p class="text-xs text-slate-400">
                                {{
                                    resolveModelVendorCode(modelForm.vendorId) === 'VLLM'
                                        ? '无鉴权部署可留空；填写后优先覆盖厂商默认密钥。'
                                        : '模型层填写后优先覆盖厂商默认密钥。'
                                }}
                            </p>
                        </label>

                        <label
                            v-if="showBasicPathField()"
                            class="block space-y-2 md:col-span-2"
                        >
                            <span class="text-sm font-semibold text-slate-600">请求路径</span>
                            <input
                                v-model.trim="modelForm.path"
                                type="text"
                                class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                :placeholder="modelPathPlaceholder()"
                            />
                            <p class="text-xs text-slate-400">
                                {{ modelPathHint() }}
                            </p>
                        </label>

                    </div>

                    <section class="rounded-2xl border border-slate-200 bg-slate-50/80">
                        <button
                            type="button"
                            class="flex w-full items-center justify-between px-4 py-3 text-left"
                            @click="modelAdvancedOpen = !modelAdvancedOpen"
                        >
                            <div>
                                <p class="text-sm font-semibold text-slate-900">高级参数设置</p>
                                <p class="mt-1 text-xs text-slate-500">
                                    已按当前厂商与模型类型填充默认模板，可继续微调。
                                </p>
                            </div>
                            <span class="material-symbols-outlined text-slate-400">
                                {{ modelAdvancedOpen ? 'expand_less' : 'expand_more' }}
                            </span>
                        </button>

                        <div v-if="modelAdvancedOpen" class="border-t border-slate-200 px-4 py-4">
                            <div class="grid gap-4 md:grid-cols-2">
                                <label
                                    v-if="showAdvancedConnectionFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">Base URL</span>
                                    <input
                                        type="text"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                        :value="modelForm.baseUrl"
                                        :placeholder="modelBaseUrlPlaceholder()"
                                        @input="handleModelBaseUrlInput"
                                    />
                                    <p class="text-xs text-slate-400">
                                        在线模型默认已填好标准地址，通常无需修改。
                                    </p>
                                </label>

                                <label
                                    v-if="showAdvancedConnectionFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">API Key</span>
                                    <input
                                        type="password"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                        :placeholder="
                                            modelForm.apiKeyConfigured
                                                ? '已配置，重新输入后将覆盖当前值'
                                                : resolveModelVendorCode(modelForm.vendorId) === 'VLLM'
                                                  ? '可选；无鉴权部署可留空'
                                                  : '如需单独覆盖，可在此输入模型级 API Key'
                                        "
                                        :value="modelForm.apiKey"
                                        @focus="handleModelApiKeyFocus"
                                        @input="handleModelApiKeyInput"
                                    />
                                    <p class="text-xs text-slate-400">
                                        {{
                                            resolveModelVendorCode(modelForm.vendorId) === 'VLLM'
                                                ? '无鉴权部署可留空；不填则继续使用厂商层密钥。'
                                                : '不填则继续使用厂商层密钥。'
                                        }}
                                    </p>
                                </label>

                                <label
                                    v-if="showAdvancedPathField()"
                                    class="block space-y-2 md:col-span-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">请求路径</span>
                                    <input
                                        v-model.trim="modelForm.path"
                                        type="text"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                        :placeholder="modelPathPlaceholder()"
                                    />
                                    <p class="text-xs text-slate-400">
                                        {{ modelPathHint() }}
                                    </p>
                                </label>

                                <label
                                    v-if="showChatAdvancedFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">temperature</span>
                                    <input
                                        v-model.trim="modelForm.temperature"
                                        type="number"
                                        step="0.1"
                                        min="0"
                                        max="2"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    />
                                </label>

                                <label
                                    v-if="showChatAdvancedFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">maxTokens</span>
                                    <input
                                        v-model.trim="modelForm.maxTokens"
                                        type="number"
                                        min="1"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    />
                                </label>

                                <label
                                    v-if="showEmbeddingAdvancedFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">dimensions</span>
                                    <input
                                        v-model.trim="modelForm.dimensions"
                                        type="number"
                                        min="1"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    />
                                </label>

                                <label
                                    v-if="showRerankAdvancedFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">协议</span>
                                    <AppSelect
                                        v-model="modelForm.protocol"
                                        :options="rerankProtocolOptions"
                                        :button-class="ADMIN_SELECT_BUTTON_CLASS"
                                    />
                                </label>

                                <label
                                    v-if="showRerankAdvancedFields()"
                                    class="block space-y-2"
                                >
                                    <span class="text-sm font-semibold text-slate-600">timeoutMs</span>
                                    <input
                                        v-model.trim="modelForm.timeoutMs"
                                        type="number"
                                        min="1"
                                        class="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
                                    />
                                </label>

                                <label
                                    v-if="showRerankAdvancedFields()"
                                    class="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-3.5 py-3 md:col-span-2"
                                >
                                    <div>
                                        <p class="text-sm font-semibold text-slate-700">Rerank 失败时回退 RRF</p>
                                        <p class="mt-1 text-xs text-slate-400">
                                            默认开启，避免重排序服务偶发异常直接影响检索结果。
                                        </p>
                                    </div>
                                    <input
                                        v-model="modelForm.fallbackRrf"
                                        type="checkbox"
                                        class="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary/20"
                                    />
                                </label>
                            </div>
                        </div>
                    </section>
                </div>
            </template>

            <template #footer>
                <div
                    class="flex items-center justify-end gap-3 border-t border-slate-200 px-5 py-4"
                >
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-5 py-2 text-sm font-semibold text-slate-700 transition-all hover:bg-slate-100 active:scale-95"
                        @click="modelModalOpen = false"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-xl bg-primary px-5 py-2 text-sm font-semibold text-white transition-all active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300"
                        :disabled="modelSaving"
                        @click="handleSaveModel"
                    >
                        {{ modelSaving ? '保存中...' : '保存模型' }}
                    </button>
                </div>
            </template>
        </BaseModal>

        <BaseModal
            :open="defaultModalOpen"
            panel-class="max-w-2xl"
            @close="defaultModalOpen = false"
        >
            <template #header>
                <div class="border-b border-slate-200 px-5 py-4">
                    <div class="flex items-center justify-between">
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">默认模型</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                为对话、向量和 Rerank 分别设置默认模型。
                            </p>
                        </div>
                        <button
                            type="button"
                            class="text-slate-400 transition hover:text-slate-700"
                            @click="defaultModalOpen = false"
                        >
                            <span class="material-symbols-outlined">close</span>
                        </button>
                    </div>
                </div>
            </template>

            <template #content>
                <div class="space-y-4 px-5 py-5">
                    <div
                        v-for="capability in capabilityOptions"
                        :key="capability.value"
                        class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
                    >
                        <div class="mb-3">
                            <p class="text-sm font-semibold text-slate-900">
                                {{ capability.label }}
                            </p>
                            <p class="mt-1 text-sm text-slate-500">
                                {{ defaultCardDescription(capability.value) }}
                            </p>
                        </div>

                        <AppSelect
                            v-model="defaultSelections[capability.value]"
                            :options="createDefaultOptions(capability.value)"
                            placeholder="请选择默认模型"
                            :button-class="ADMIN_SELECT_BUTTON_CLASS"
                        />
                    </div>
                </div>
            </template>

            <template #footer>
                <div
                    class="flex items-center justify-end gap-3 border-t border-slate-200 px-5 py-4"
                >
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-5 py-2 text-sm font-semibold text-slate-700 transition-all hover:bg-slate-100 active:scale-95"
                        @click="defaultModalOpen = false"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        class="rounded-xl bg-primary px-5 py-2 text-sm font-semibold text-white transition-all active:scale-95 disabled:cursor-not-allowed disabled:bg-slate-300"
                        :disabled="defaultModalSaving"
                        @click="handleSaveAllDefaults"
                    >
                        {{ defaultModalSaving ? '保存中...' : '保存默认模型' }}
                    </button>
                </div>
            </template>
        </BaseModal>
    </section>
</template>
