<script setup>
import { computed } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import {
    INTEGRATION_AUTH_METHOD_OPTIONS,
    INTEGRATION_AUTH_STATE_OPTIONS,
    INTEGRATION_REQUEST_TABS,
    INTEGRATION_SELECT_BUTTON_CLASS,
    buildIntegrationInputClass,
} from './integrationConnectorShared';
import IntegrationParamTable from './IntegrationParamTable.vue';
import IntegrationVariableInput from './IntegrationVariableInput.vue';
import IntegrationVariableNamedValueEditor from './IntegrationVariableNamedValueEditor.vue';

const props = defineProps({
    authItems: {
        type: Array,
        default: () => [],
    },
    selectedAuthId: {
        type: String,
        default: '',
    },
    authForm: {
        type: Object,
        required: true,
    },
    authVariableGroups: {
        type: Array,
        default: () => [],
    },
    authSaveError: {
        type: String,
        default: '',
    },
    authSaving: {
        type: Boolean,
        default: false,
    },
    authTesting: {
        type: Boolean,
        default: false,
    },
    authTestError: {
        type: String,
        default: '',
    },
    authTestResult: {
        type: null,
        default: null,
    },
    authTestVariablesText: {
        type: String,
        default: '{}',
    },
    activeRequestTab: {
        type: String,
        default: 'headers',
    },
    formatJson: {
        type: Function,
        required: true,
    },
});

const emit = defineEmits([
    'create',
    'select',
    'delete',
    'save',
    'test',
    'pick-output',
    'update:authTestVariablesText',
    'update:activeRequestTab',
]);

const requestTabs = INTEGRATION_REQUEST_TABS;
const authMethodOptions = INTEGRATION_AUTH_METHOD_OPTIONS;
const authStateOptions = INTEGRATION_AUTH_STATE_OPTIONS;
const selectButtonClass = INTEGRATION_SELECT_BUTTON_CLASS;

const activeRequestTabProxy = computed({
    get: () => props.activeRequestTab,
    set: value => emit('update:activeRequestTab', value),
});

const authTestVariablesTextProxy = computed({
    get: () => props.authTestVariablesText,
    set: value => emit('update:authTestVariablesText', value),
});

function inputClass(invalid = false, disabled = false) {
    return buildIntegrationInputClass(invalid, disabled);
}
</script>

<template>
    <section class="grid min-h-[680px] flex-1 gap-5 xl:grid-cols-[300px,minmax(0,1fr),340px]">
        <aside
            class="flex max-h-[calc(100vh-300px)] min-h-0 flex-col rounded-3xl border border-slate-200 bg-white p-4 shadow-sm"
        >
            <div class="mb-4 flex items-center justify-between gap-3">
                <h2 class="text-lg font-bold text-slate-900">鉴权列表</h2>
                <button
                    class="rounded-xl bg-primary px-3 py-2 text-sm font-semibold text-white"
                    type="button"
                    @click="emit('create')"
                >
                    新建
                </button>
            </div>
            <div
                v-if="!authItems.length"
                class="rounded-2xl bg-slate-50 px-4 py-8 text-center text-sm text-slate-400"
            >
                暂无鉴权
            </div>
            <div v-else class="custom-scrollbar min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
                <button
                    v-for="item in authItems"
                    :key="item.id"
                    :class="[
                        'w-full rounded-2xl border px-4 py-3 text-left transition',
                        selectedAuthId === item.id
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-slate-200 text-slate-600 hover:border-primary/40',
                    ]"
                    type="button"
                    @click="emit('select', item.id)"
                >
                    <span class="block truncate text-sm font-semibold">
                        {{ item.name || '未命名鉴权' }}
                    </span>
                    <span class="mt-1 block break-all text-xs text-slate-400">
                        {{ item.authInfo?.url || '未配置接口地址' }}
                    </span>
                </button>
            </div>
        </aside>

        <div
            class="custom-scrollbar max-h-[calc(100vh-300px)] overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"
        >
            <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h2 class="text-lg font-bold text-slate-900">鉴权配置</h2>
                    <p class="mt-1 text-sm text-slate-500">配置获取令牌的接口请求和返回字段。</p>
                </div>
                <div class="flex flex-wrap gap-2">
                    <button
                        class="whitespace-nowrap rounded-xl border border-rose-200 px-4 py-2 text-sm font-semibold text-rose-500 transition hover:bg-rose-50"
                        type="button"
                        @click="emit('delete')"
                    >
                        删除鉴权
                    </button>
                </div>
            </div>

            <div
                v-if="authSaveError"
                class="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ authSaveError }}
            </div>

            <div class="grid gap-4 lg:grid-cols-2">
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">
                        <span class="text-rose-500">*</span>
                        鉴权名称
                    </span>
                    <input
                        v-model="authForm.name"
                        :class="inputClass(false)"
                        type="text"
                        placeholder="请输入鉴权名称"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">鉴权说明</span>
                    <input
                        v-model="authForm.remark"
                        :class="inputClass(false)"
                        type="text"
                        placeholder="请输入说明"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">请求方式</span>
                    <AppSelect
                        :model-value="authForm.authInfo.method"
                        :options="authMethodOptions"
                        :button-class="selectButtonClass"
                        menu-class="w-full"
                        @update:modelValue="value => (authForm.authInfo.method = value)"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">
                        <span class="text-rose-500">*</span>
                        接口地址
                    </span>
                    <IntegrationVariableInput
                        v-model="authForm.authInfo.url"
                        :groups="authVariableGroups"
                        placeholder="请输入鉴权接口地址"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600"
                        >过期时长（分钟）</span
                    >
                    <input
                        v-model.number="authForm.authInfo.expireAfterMinutes"
                        :class="inputClass(false)"
                        min="1"
                        type="number"
                    />
                </label>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">状态</span>
                    <AppSelect
                        :model-value="authForm.state"
                        :options="authStateOptions"
                        :button-class="selectButtonClass"
                        menu-class="w-full"
                        @update:modelValue="value => (authForm.state = value)"
                    />
                </label>
            </div>

            <div class="mt-6 overflow-hidden rounded-2xl border border-slate-200">
                <div class="flex gap-2 border-b border-slate-200 bg-slate-100 p-2">
                    <button
                        v-for="tab in requestTabs"
                        :key="tab.value"
                        :class="[
                            'min-w-24 rounded-xl border px-4 py-2 text-sm font-semibold transition',
                            activeRequestTabProxy === tab.value
                                ? 'border-primary bg-white text-primary shadow-sm ring-2 ring-primary/10'
                                : 'border-transparent text-slate-500 hover:border-slate-200 hover:bg-white/80 hover:text-slate-800',
                        ]"
                        type="button"
                        @click="activeRequestTabProxy = tab.value"
                    >
                        {{ tab.label }}
                    </button>
                </div>
                <div class="p-4">
                    <IntegrationVariableNamedValueEditor
                        v-if="activeRequestTabProxy === 'headers'"
                        v-model="authForm.authInfo.headers"
                        :groups="authVariableGroups"
                        add-text="新增 header"
                        name-placeholder="header 名称"
                        value-placeholder="header 值"
                    />
                    <IntegrationVariableNamedValueEditor
                        v-else-if="activeRequestTabProxy === 'forms'"
                        v-model="authForm.authInfo.forms"
                        :groups="authVariableGroups"
                        add-text="新增 form"
                        name-placeholder="form 名称"
                        value-placeholder="form 值"
                    />
                    <IntegrationVariableInput
                        v-else
                        v-model="authForm.authInfo.body"
                        :groups="authVariableGroups"
                        multiline
                        :rows="7"
                        placeholder="请输入 body"
                    />
                </div>
            </div>

            <div class="mt-6">
                <div class="mb-3 flex items-center justify-between gap-3">
                    <h3 class="text-sm font-bold text-slate-700">出参结构</h3>
                    <button
                        class="whitespace-nowrap rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-600 transition hover:border-primary hover:text-primary"
                        type="button"
                        @click="emit('pick-output')"
                    >
                        从调试结果选择
                    </button>
                </div>
                <IntegrationParamTable v-model="authForm.authInfo.returnInfo" output-mode />
            </div>

            <div class="mt-6 flex justify-end gap-3">
                <button
                    :disabled="authSaving"
                    class="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                    type="button"
                    @click="emit('save')"
                >
                    {{ authSaving ? '保存中...' : '保存' }}
                </button>
            </div>
        </div>

        <aside
            class="custom-scrollbar max-h-[calc(100vh-300px)] overflow-y-auto rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"
        >
            <div class="mb-4 flex items-center justify-between gap-3">
                <div>
                    <h3 class="text-base font-bold text-slate-900">调试</h3>
                    <p class="mt-1 text-xs text-slate-400">配置鉴权请求后可发起调试。</p>
                </div>
                <button
                    :disabled="authTesting"
                    class="whitespace-nowrap rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                    type="button"
                    @click="emit('test')"
                >
                    {{ authTesting ? '调试中...' : '调试' }}
                </button>
            </div>
            <label class="block">
                <span class="mb-2 block text-sm font-semibold text-slate-600">调试变量</span>
                <textarea
                    v-model="authTestVariablesTextProxy"
                    class="custom-scrollbar w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 font-mono text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                    rows="8"
                    placeholder="调试变量 JSON，例如：{}"
                />
            </label>
            <div
                v-if="authTestError"
                class="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ authTestError }}
            </div>
            <div class="mt-5">
                <div class="mb-2 text-sm font-semibold text-slate-600">调试返回</div>
                <pre
                    v-if="authTestResult"
                    class="custom-scrollbar max-h-[420px] overflow-auto rounded-2xl bg-slate-900 p-4 text-xs text-slate-100"
                    >{{ formatJson(authTestResult) }}</pre
                >
                <div
                    v-else
                    class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-400"
                >
                    暂无调试结果
                </div>
            </div>
        </aside>
    </section>
</template>
