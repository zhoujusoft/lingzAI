<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import { alert, confirm, openModal } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { useRouter } from 'vue-router';
import {
    listLowcodePlatforms,
    listLowcodeApps,
    listLowcodeApis,
    registerLowcodeApi,
    unregisterLowcodeApi,
    testExecuteLowcodeApi,
} from '@/api/skills';
import { ROUTE_PATHS } from '@/router/routePaths';
import AdminLowcodeApiDetailModalContent from '@/views/admin/components/AdminLowcodeApiDetailModalContent.vue';
import { ADMIN_SELECT_BUTTON_CLASS } from '@/views/admin/components/mcp-management/mcpManagementShared';

const router = useRouter();

const loadingPlatforms = ref(false);
const loadingApps = ref(false);
const loadingApis = ref(false);
const registerLoadingCode = ref('');
const loadError = ref('');

const platforms = ref([]);
const apps = ref([]);
const apis = ref([]);

const selectedPlatformKey = ref('');
const selectedAppId = ref('');
const apiKeyword = ref('');

const executePayloadTextByCode = ref({});
const executeResultByCode = ref({});
const executeErrorByCode = ref({});
const executeLoadingCode = ref('');

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

const selectedPlatform = computed(
    () => platforms.value.find(item => item.key === selectedPlatformKey.value) || null
);

const platformOptions = computed(() =>
    platforms.value.map(item => ({
        value: item.key,
        label: item.name || item.key,
        description: item.key && item.name && item.name !== item.key ? item.key : '',
    }))
);

const selectedApp = computed(
    () => apps.value.find(item => item.appId === selectedAppId.value) || null
);

const filteredApis = computed(() => {
    const keyword = apiKeyword.value.trim().toLowerCase();
    return apis.value.filter(item => {
        if (!keyword) {
            return true;
        }
        return `${item.apiName || ''} ${item.apiCode || ''} ${item.apiRemark || ''} ${item.method || ''} ${item.url || ''}`
            .toLowerCase()
            .includes(keyword);
    });
});

function prettyJson(value) {
    if (value == null || value === '') {
        return '';
    }
    if (typeof value === 'string') {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
            return value;
        }
    }
    try {
        return JSON.stringify(value, null, 2);
    } catch (error) {
        return String(value);
    }
}

function parseMaybeJson(value) {
    if (value == null || value === '') {
        return null;
    }
    if (typeof value === 'string') {
        try {
            return JSON.parse(value);
        } catch (error) {
            return value;
        }
    }
    return value;
}

function inferPayloadTemplate(api) {
    const inputParams = parseMaybeJson(api?.inputParams);
    if (Array.isArray(inputParams)) {
        return inputParams.reduce((result, item) => {
            const key =
                item?.name || item?.field || item?.code || item?.paramName || item?.key || '';
            if (key) {
                result[key] = '';
            }
            return result;
        }, {});
    }
    if (inputParams && typeof inputParams === 'object') {
        return inputParams;
    }
    return {};
}

function ensurePayloadText(api) {
    const apiCode = api?.apiCode || '';
    if (!apiCode) {
        return '{}';
    }
    if (!executePayloadTextByCode.value[apiCode]) {
        executePayloadTextByCode.value = {
            ...executePayloadTextByCode.value,
            [apiCode]: prettyJson(inferPayloadTemplate(api)) || '{}',
        };
    }
    return executePayloadTextByCode.value[apiCode] || '{}';
}

function setExecutePayloadText(apiCode, text) {
    executePayloadTextByCode.value = {
        ...executePayloadTextByCode.value,
        [apiCode]: text,
    };
}

async function loadPlatforms() {
    loadingPlatforms.value = true;
    loadError.value = '';
    try {
        const data = await listLowcodePlatforms(handleUnauthorized);
        platforms.value = Array.isArray(data) ? data : [];
        selectedPlatformKey.value =
            selectedPlatformKey.value &&
            platforms.value.some(item => item.key === selectedPlatformKey.value)
                ? selectedPlatformKey.value
                : platforms.value[0]?.key || '';
    } catch (error) {
        loadError.value = error?.message || '低代码平台加载失败';
        platforms.value = [];
        selectedPlatformKey.value = '';
    } finally {
        loadingPlatforms.value = false;
    }
}

async function loadApps() {
    if (!selectedPlatformKey.value) {
        apps.value = [];
        apis.value = [];
        selectedAppId.value = '';
        return;
    }
    loadingApps.value = true;
    loadError.value = '';
    try {
        const data = await listLowcodeApps(selectedPlatformKey.value, handleUnauthorized);
        apps.value = Array.isArray(data) ? data : [];
        selectedAppId.value =
            selectedAppId.value && apps.value.some(item => item.appId === selectedAppId.value)
                ? selectedAppId.value
                : apps.value[0]?.appId || '';
    } catch (error) {
        loadError.value = error?.message || '连接器应用加载失败';
        apps.value = [];
        selectedAppId.value = '';
        apis.value = [];
    } finally {
        loadingApps.value = false;
    }
}

async function loadApis() {
    if (!selectedPlatformKey.value || !selectedAppId.value) {
        apis.value = [];
        return;
    }
    loadingApis.value = true;
    loadError.value = '';
    try {
        const data = await listLowcodeApis(
            selectedPlatformKey.value,
            selectedAppId.value,
            handleUnauthorized
        );
        apis.value = Array.isArray(data) ? data : [];
        executePayloadTextByCode.value = {};
        executeResultByCode.value = {};
        executeErrorByCode.value = {};
    } catch (error) {
        loadError.value = error?.message || 'API 列表加载失败';
        apis.value = [];
    } finally {
        loadingApis.value = false;
    }
}

async function handleTestExecuteByCode(apiCode, payloadText) {
    if (!selectedPlatformKey.value || !apiCode || executeLoadingCode.value) {
        return null;
    }
    let argumentsPayload = {};
    try {
        argumentsPayload = payloadText.trim() ? JSON.parse(payloadText) : {};
    } catch (error) {
        executeErrorByCode.value = {
            ...executeErrorByCode.value,
            [apiCode]: '测试入参不是合法 JSON',
        };
        executeResultByCode.value = {
            ...executeResultByCode.value,
            [apiCode]: null,
        };
        return null;
    }

    executeLoadingCode.value = apiCode;
    executeErrorByCode.value = {
        ...executeErrorByCode.value,
        [apiCode]: '',
    };
    try {
        const result = await testExecuteLowcodeApi(
            {
                platformKey: selectedPlatformKey.value,
                apiCode,
                arguments: argumentsPayload,
            },
            handleUnauthorized
        );
        executeResultByCode.value = {
            ...executeResultByCode.value,
            [apiCode]: result,
        };
        return result;
    } catch (error) {
        executeErrorByCode.value = {
            ...executeErrorByCode.value,
            [apiCode]: error?.message || '测试执行失败',
        };
        executeResultByCode.value = {
            ...executeResultByCode.value,
            [apiCode]: null,
        };
        return null;
    } finally {
        executeLoadingCode.value = '';
    }
}

async function handleRegister(api) {
    if (!api?.apiCode || registerLoadingCode.value) {
        return;
    }
    registerLoadingCode.value = api.apiCode;
    try {
        const result = await registerLowcodeApi(
            {
                platformKey: selectedPlatformKey.value,
                appId: selectedApp.value?.appId || api.appId || '',
                appName: selectedApp.value?.name || '',
                apiId: api.apiId || '',
                apiCode: api.apiCode,
                apiName: api.apiName,
                description: api.apiRemark || '',
                remoteSchema: api.raw || api,
            },
            handleUnauthorized
        );
        await loadApis();
        await alert({
            title: '注册成功',
            message: `已注册为工具：${result?.toolName || api.apiCode}`,
        });
    } catch (error) {
        await alert({
            title: '注册失败',
            message: error?.message || '低代码 API 注册失败',
        });
    } finally {
        registerLoadingCode.value = '';
    }
}

async function handleUnregister(api) {
    if (!api?.apiCode || !selectedPlatformKey.value) {
        return;
    }
    const confirmed = await confirm({
        title: '取消注册',
        message: `确认取消注册 API「${api.apiName || api.apiCode}」吗？相关技能绑定会一并移除。`,
        confirmText: '取消注册',
        cancelText: '保留',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }
    try {
        await unregisterLowcodeApi(selectedPlatformKey.value, api.apiCode, handleUnauthorized);
        await loadApis();
        await alert({
            title: '取消注册成功',
            message: '该 API 已从工具库和技能绑定中移除。',
        });
    } catch (error) {
        await alert({
            title: '取消注册失败',
            message: error?.message || '取消注册失败',
        });
    }
}

async function openApiDetail(api) {
    const apiCode = api?.apiCode || '';
    if (!apiCode) {
        return;
    }
    ensurePayloadText(api);
    await openModal({
        title: api.apiName || api.apiCode,
        showClose: true,
        showCancel: false,
        confirmText: '关闭',
        panelClass: '!max-w-[1280px]',
        content: {
            component: AdminLowcodeApiDetailModalContent,
            props: {
                api,
            },
        },
        context: {
            api,
            get payloadText() {
                return executePayloadTextByCode.value[apiCode] || '{}';
            },
            setPayloadText(value) {
                setExecutePayloadText(apiCode, value);
            },
            get executeResult() {
                return executeResultByCode.value[apiCode] || null;
            },
            get executeError() {
                return executeErrorByCode.value[apiCode] || '';
            },
            get executeLoading() {
                return executeLoadingCode.value === apiCode;
            },
            async onExecute() {
                await handleTestExecuteByCode(
                    apiCode,
                    executePayloadTextByCode.value[apiCode] || '{}'
                );
            },
        },
    });
}

watch(selectedPlatformKey, async () => {
    await loadApps();
});

watch(selectedAppId, async () => {
    await loadApis();
});

onMounted(async () => {
    await loadPlatforms();
    if (selectedPlatformKey.value) {
        await loadApps();
    }
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        API Library
                    </p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">API 库</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        浏览低代码平台连接器应用与 API 目录，查看接口详情，测试执行，并将 API
                        注册或取消注册为项目工具。
                    </p>
                </div>

                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <AppSelect
                        v-model="selectedPlatformKey"
                        :options="platformOptions"
                        placeholder="请选择低代码平台"
                        :button-class="ADMIN_SELECT_BUTTON_CLASS"
                        :full-width="false"
                        menu-class="w-[280px]"
                        :disabled="loadingPlatforms || !platforms.length"
                    />

                    <button
                        type="button"
                        class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                        @click="loadPlatforms"
                    >
                        刷新平台
                    </button>
                </div>
            </div>
        </header>

        <div class="flex min-h-0 flex-1 flex-col p-6">
            <p
                v-if="loadError"
                class="mb-5 shrink-0 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div class="grid min-h-0 flex-1 gap-6 xl:grid-cols-[320px,minmax(0,1fr)]">
                <section
                    class="flex min-h-0 flex-col rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm"
                >
                    <div
                        class="flex items-start justify-between gap-3 border-b border-slate-100 pb-4"
                    >
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">连接器应用</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                {{
                                    selectedPlatform
                                        ? `${selectedPlatform.name} 的应用目录`
                                        : '请选择平台'
                                }}
                            </p>
                        </div>
                        <button
                            type="button"
                            class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                            :disabled="loadingApps || !selectedPlatformKey"
                            @click="loadApps"
                        >
                            刷新
                        </button>
                    </div>

                    <div class="custom-scrollbar mt-4 min-h-0 flex-1 overflow-y-auto pr-1">
                        <div
                            v-if="loadingApps"
                            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-6 text-sm text-slate-400"
                        >
                            应用加载中...
                        </div>

                        <div v-else-if="!apps.length" class="text-sm text-slate-400">
                            暂无应用数据
                        </div>

                        <div v-else class="space-y-3">
                            <button
                                v-for="app in apps"
                                :key="app.appId"
                                type="button"
                                class="w-full rounded-2xl border px-4 py-4 text-left transition"
                                :class="
                                    selectedAppId === app.appId
                                        ? 'border-primary bg-[#f0f7ff]'
                                        : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'
                                "
                                @click="selectedAppId = app.appId"
                            >
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <p class="truncate text-sm font-semibold text-slate-900">
                                            {{ app.name || app.code || app.appId }}
                                        </p>
                                        <p class="mt-1 truncate text-xs text-slate-500">
                                            {{ app.code || app.appId }}
                                        </p>
                                    </div>
                                    <span
                                        class="rounded-full bg-slate-200 px-2.5 py-1 text-[11px] font-semibold text-slate-600"
                                    >
                                        {{ app.apiCount ?? 0 }} 个 API
                                    </span>
                                </div>
                                <p
                                    v-if="app.remark"
                                    class="mt-2 line-clamp-2 text-xs leading-5 text-slate-500"
                                >
                                    {{ app.remark }}
                                </p>
                            </button>
                        </div>
                    </div>
                </section>

                <section
                    class="flex min-h-0 flex-col rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm"
                >
                    <div
                        class="flex flex-col gap-4 border-b border-slate-100 pb-4 xl:flex-row xl:items-center xl:justify-between"
                    >
                        <div>
                            <h3 class="text-lg font-bold text-slate-900">API 列表</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                {{
                                    selectedApp
                                        ? `当前应用：${selectedApp.name || selectedApp.code || selectedApp.appId}`
                                        : '请选择应用'
                                }}
                            </p>
                        </div>

                        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                            <div class="relative min-w-[280px]">
                                <span
                                    class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                                >
                                    search
                                </span>
                                <input
                                    v-model.trim="apiKeyword"
                                    type="text"
                                    placeholder="搜索 API 名称、编码、备注"
                                    class="w-full rounded-2xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                />
                            </div>
                            <button
                                type="button"
                                class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                                :disabled="loadingApis || !selectedAppId"
                                @click="loadApis"
                            >
                                刷新 API
                            </button>
                        </div>
                    </div>

                    <div class="custom-scrollbar mt-5 min-h-0 flex-1 overflow-y-auto pr-1">
                        <div
                            v-if="loadingApis"
                            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-400"
                        >
                            API 列表加载中...
                        </div>

                        <div v-else-if="!filteredApis.length" class="text-sm text-slate-400">
                            暂无 API 数据
                        </div>

                        <div v-else class="grid gap-4">
                            <article
                                v-for="api in filteredApis"
                                :key="api.apiCode || api.apiId"
                                class="rounded-[24px] border border-slate-200 bg-slate-50 p-5"
                            >
                                <div
                                    class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between"
                                >
                                    <div class="min-w-0 flex-1">
                                        <div class="flex flex-wrap items-center gap-2">
                                            <h4 class="text-base font-bold text-slate-900">
                                                {{ api.apiName || api.apiCode }}
                                            </h4>
                                            <span
                                                class="rounded-full bg-slate-200 px-2.5 py-1 text-[11px] font-semibold text-slate-600"
                                            >
                                                {{ api.method || 'POST' }}
                                            </span>
                                            <span
                                                v-if="api.registered"
                                                class="rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-semibold text-emerald-600"
                                            >
                                                已注册
                                            </span>
                                        </div>
                                        <p class="mt-2 text-sm text-slate-500">
                                            <span class="font-medium text-slate-700"
                                                >API Code：</span
                                            >
                                            {{ api.apiCode || '-' }}
                                        </p>
                                        <p
                                            v-if="api.apiRemark"
                                            class="mt-2 text-sm leading-6 text-slate-500"
                                        >
                                            {{ api.apiRemark }}
                                        </p>
                                        <p v-if="api.toolName" class="mt-2 text-xs text-slate-500">
                                            已生成工具名：<span
                                                class="font-semibold text-slate-700"
                                                >{{ api.toolName }}</span
                                            >
                                        </p>
                                    </div>

                                    <div class="flex shrink-0 items-center gap-3">
                                        <button
                                            type="button"
                                            class="rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
                                            @click="openApiDetail(api)"
                                        >
                                            查看详情
                                        </button>
                                        <button
                                            type="button"
                                            class="rounded-2xl px-4 py-2.5 text-sm font-semibold transition"
                                            :class="
                                                api.registered
                                                    ? 'border border-emerald-200 bg-emerald-50 text-emerald-700'
                                                    : 'bg-primary text-white hover:bg-primary-hover'
                                            "
                                            :disabled="registerLoadingCode === api.apiCode"
                                            @click="handleRegister(api)"
                                        >
                                            {{
                                                registerLoadingCode === api.apiCode
                                                    ? '注册中...'
                                                    : api.registered
                                                      ? '重新注册'
                                                      : '注册为工具'
                                            }}
                                        </button>
                                        <button
                                            v-if="api.registered"
                                            type="button"
                                            class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600 transition hover:bg-rose-100"
                                            @click="handleUnregister(api)"
                                        >
                                            取消注册
                                        </button>
                                    </div>
                                </div>
                            </article>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>
