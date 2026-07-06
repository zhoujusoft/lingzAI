<script setup>
import { computed } from 'vue';
import AppSelect from '@/components/AppSelect.vue';
import {
    INTEGRATION_CONTENT_TYPE_OPTIONS,
    INTEGRATION_METHOD_OPTIONS,
    INTEGRATION_REQUEST_TABS,
    INTEGRATION_SELECT_BUTTON_CLASS,
    buildIntegrationInputClass,
    getIntegrationPublishStatusMeta,
} from './integrationConnectorShared';
import IntegrationParamTable from './IntegrationParamTable.vue';
import IntegrationVariableInput from './IntegrationVariableInput.vue';
import IntegrationVariableNamedValueEditor from './IntegrationVariableNamedValueEditor.vue';

const props = defineProps({
    apiListError: {
        type: String,
        default: '',
    },
    apiListLoading: {
        type: Boolean,
        default: false,
    },
    connectorApis: {
        type: Array,
        default: () => [],
    },
    apiEditorOpen: {
        type: Boolean,
        default: false,
    },
    apiEditorMode: {
        type: String,
        default: 'create',
    },
    apiSaveError: {
        type: String,
        default: '',
    },
    apiSaving: {
        type: Boolean,
        default: false,
    },
    apiTesting: {
        type: Boolean,
        default: false,
    },
    apiTestError: {
        type: String,
        default: '',
    },
    apiTestResult: {
        type: null,
        default: null,
    },
    apiTestArgumentsText: {
        type: String,
        default: '{}',
    },
    apiPublishLoadingId: {
        type: [String, Number],
        default: null,
    },
    apiCodeGenerating: {
        type: Boolean,
        default: false,
    },
    apiForm: {
        type: Object,
        required: true,
    },
    apiFieldTouched: {
        type: Object,
        required: true,
    },
    apiFormErrors: {
        type: Object,
        required: true,
    },
    apiVariableGroups: {
        type: Array,
        default: () => [],
    },
    authOptions: {
        type: Array,
        default: () => [],
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
    'open-create',
    'open-edit',
    'publish',
    'disable',
    'delete',
    'reset-editor',
    'save',
    'test',
    'pick-output',
    'update:apiTestArgumentsText',
    'update:activeRequestTab',
    'api-name-input',
    'api-code-input',
]);

const methodOptions = INTEGRATION_METHOD_OPTIONS;
const contentTypeOptions = INTEGRATION_CONTENT_TYPE_OPTIONS;
const requestTabs = INTEGRATION_REQUEST_TABS;
const selectButtonClass = INTEGRATION_SELECT_BUTTON_CLASS;

const activeRequestTabProxy = computed({
    get: () => props.activeRequestTab,
    set: value => emit('update:activeRequestTab', value),
});

const apiTestArgumentsTextProxy = computed({
    get: () => props.apiTestArgumentsText,
    set: value => emit('update:apiTestArgumentsText', value),
});

const authSelectOptions = computed(() => [
    { value: '', label: '不使用鉴权' },
    ...props.authOptions,
]);

function inputClass(invalid = false, disabled = false) {
    return buildIntegrationInputClass(invalid, disabled);
}
</script>

<template>
    <section class="min-h-[680px] flex-1">
        <div
            v-if="!apiEditorOpen"
            class="relative flex max-h-[calc(100vh-300px)] min-h-[680px] flex-col rounded-3xl border border-slate-200 bg-white p-4 shadow-sm"
        >
            <div class="mb-3 flex justify-end">
                <button
                    class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white"
                    type="button"
                    @click="emit('open-create')"
                >
                    新建
                </button>
            </div>

            <div
                v-if="apiListError"
                class="mb-3 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ apiListError }}
            </div>
            <div v-if="apiListLoading" class="px-4 py-16 text-center text-sm text-slate-400">
                正在加载 API...
            </div>
            <div
                v-else-if="!connectorApis.length"
                class="rounded-2xl bg-slate-50 px-4 py-16 text-center text-sm text-slate-400"
            >
                暂无 API
            </div>
            <div
                v-else
                class="custom-scrollbar min-h-0 flex-1 overflow-auto rounded-2xl border border-slate-200"
            >
                <div
                    class="grid min-w-[980px] grid-cols-[minmax(180px,1.1fr),110px,120px,minmax(220px,1.2fr),minmax(160px,0.8fr),220px] gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-600"
                >
                    <div>名称</div>
                    <div>方法</div>
                    <div>状态</div>
                    <div>接口地址</div>
                    <div>鉴权</div>
                    <div class="text-right">操作</div>
                </div>
                <div class="min-w-[980px] divide-y divide-slate-100">
                    <div
                        v-for="item in connectorApis"
                        :key="item.id"
                        class="grid grid-cols-[minmax(180px,1.1fr),110px,120px,minmax(220px,1.2fr),minmax(160px,0.8fr),220px] gap-3 px-4 py-3 text-sm transition hover:bg-slate-50"
                    >
                        <div class="min-w-0">
                            <button
                                class="block max-w-full truncate text-left font-semibold text-slate-800 transition hover:text-primary"
                                type="button"
                                @click="emit('open-edit', item)"
                            >
                                {{ item.apiName || item.apiCode }}
                            </button>
                            <div class="mt-1 truncate font-mono text-xs text-slate-400">
                                {{ item.apiCode }}
                            </div>
                        </div>
                        <div class="pt-1 text-xs font-semibold text-slate-500">
                            {{ item.method || '-' }}
                        </div>
                        <div class="pt-0.5">
                            <span
                                class="rounded-full px-2 py-1 text-xs font-semibold"
                                :class="
                                    getIntegrationPublishStatusMeta(item.publishStatus).badgeClass
                                "
                            >
                                {{ getIntegrationPublishStatusMeta(item.publishStatus).label }}
                            </span>
                        </div>
                        <div class="min-w-0 break-all pt-1 text-xs text-slate-500">
                            {{ item.url || '-' }}
                        </div>
                        <div class="min-w-0 truncate pt-1 text-xs text-slate-500">
                            {{ item.connectName || '不使用鉴权' }}
                        </div>
                        <div class="flex items-center justify-end gap-2 whitespace-nowrap">
                            <button
                                class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-primary hover:text-primary"
                                type="button"
                                @click="emit('open-edit', item)"
                            >
                                编辑
                            </button>
                            <button
                                v-if="item.publishStatus !== 'PUBLISHED'"
                                :disabled="apiPublishLoadingId === item.id"
                                class="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-semibold text-blue-600 transition hover:bg-blue-50 disabled:opacity-50"
                                type="button"
                                @click="emit('publish', item)"
                            >
                                发布
                            </button>
                            <button
                                v-else
                                :disabled="apiPublishLoadingId === item.id"
                                class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
                                type="button"
                                @click="emit('disable', item)"
                            >
                                停用
                            </button>
                            <button
                                class="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-500 transition hover:bg-rose-50"
                                type="button"
                                @click="emit('delete', item)"
                            >
                                删除
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div
            v-else
            class="grid max-h-[calc(100vh-300px)] min-h-[680px] gap-5 xl:grid-cols-[minmax(0,1fr),340px]"
        >
            <div
                class="custom-scrollbar overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"
            >
                <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <h2 class="text-lg font-bold text-slate-900">
                            {{ apiEditorMode === 'edit' ? '编辑 API' : '新建 API' }}
                        </h2>
                        <p class="mt-1 text-sm text-slate-500">
                            输入框可填写常量，也可通过变量按钮选择变量。
                        </p>
                    </div>
                    <button
                        class="whitespace-nowrap rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-500 transition hover:border-slate-300"
                        type="button"
                        @click="emit('reset-editor')"
                    >
                        返回列表
                    </button>
                </div>

                <div
                    v-if="apiSaveError"
                    class="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ apiSaveError }}
                </div>

                <div class="grid gap-4 lg:grid-cols-2">
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">
                            <span class="text-rose-500">*</span>
                            API 名称
                        </span>
                        <input
                            :value="apiForm.apiName"
                            :class="
                                inputClass(
                                    apiFieldTouched.apiName && Boolean(apiFormErrors.apiName)
                                )
                            "
                            type="text"
                            placeholder="请输入 API 名称"
                            @blur="apiFieldTouched.apiName = true"
                            @input="event => emit('api-name-input', event.target.value)"
                        />
                        <span
                            v-if="apiFieldTouched.apiName && apiFormErrors.apiName"
                            class="mt-1 block text-xs text-rose-500"
                        >
                            {{ apiFormErrors.apiName }}
                        </span>
                    </label>
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">
                            <span class="text-rose-500">*</span>
                            API 编码
                        </span>
                        <input
                            :value="apiForm.apiCode"
                            :disabled="apiEditorMode === 'edit'"
                            :class="
                                inputClass(
                                    apiFieldTouched.apiCode && Boolean(apiFormErrors.apiCode),
                                    apiEditorMode === 'edit'
                                )
                            "
                            type="text"
                            placeholder="根据连接器编码和 API 名称自动生成"
                            @blur="apiFieldTouched.apiCode = true"
                            @input="event => emit('api-code-input', event.target.value)"
                        />
                        <span class="mt-1 block text-xs text-slate-400">
                            {{
                                apiCodeGenerating
                                    ? '正在生成编码...'
                                    : '新增时可修改，编辑后不可修改'
                            }}
                        </span>
                        <span
                            v-if="apiFieldTouched.apiCode && apiFormErrors.apiCode"
                            class="mt-1 block text-xs text-rose-500"
                        >
                            {{ apiFormErrors.apiCode }}
                        </span>
                    </label>
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >请求方式</span
                        >
                        <AppSelect
                            :model-value="apiForm.method"
                            :options="methodOptions"
                            :button-class="selectButtonClass"
                            menu-class="w-full"
                            @update:modelValue="value => (apiForm.method = value)"
                        />
                    </label>
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600">
                            <span class="text-rose-500">*</span>
                            接口地址
                        </span>
                        <IntegrationVariableInput
                            v-model="apiForm.url"
                            :groups="apiVariableGroups"
                            :invalid="apiFieldTouched.url && Boolean(apiFormErrors.url)"
                            placeholder="请输入接口地址"
                            @blur="apiFieldTouched.url = true"
                        />
                        <span
                            v-if="apiFieldTouched.url && apiFormErrors.url"
                            class="mt-1 block text-xs text-rose-500"
                        >
                            {{ apiFormErrors.url }}
                        </span>
                    </label>
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >选择鉴权</span
                        >
                        <AppSelect
                            :model-value="apiForm.connectId"
                            :options="authSelectOptions"
                            :button-class="selectButtonClass"
                            menu-class="w-full"
                            @update:modelValue="value => (apiForm.connectId = value)"
                        />
                    </label>
                    <label class="block">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >内容类型</span
                        >
                        <AppSelect
                            :model-value="apiForm.contentType"
                            :options="contentTypeOptions"
                            :button-class="selectButtonClass"
                            menu-class="w-full"
                            @update:modelValue="value => (apiForm.contentType = value)"
                        />
                    </label>
                    <label class="block lg:col-span-2">
                        <span class="mb-2 block text-sm font-semibold text-slate-600"
                            >API 说明</span
                        >
                        <input
                            v-model="apiForm.apiRemark"
                            :class="inputClass(false)"
                            type="text"
                            placeholder="请输入说明"
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
                            v-model="apiForm.headers"
                            :groups="apiVariableGroups"
                            add-text="新增 header"
                            name-placeholder="header 名称"
                            value-placeholder="header 值"
                        />
                        <IntegrationVariableNamedValueEditor
                            v-else-if="activeRequestTabProxy === 'forms'"
                            v-model="apiForm.forms"
                            :groups="apiVariableGroups"
                            add-text="新增 form"
                            name-placeholder="form 名称"
                            value-placeholder="form 值"
                        />
                        <IntegrationVariableInput
                            v-else
                            v-model="apiForm.body"
                            :groups="apiVariableGroups"
                            multiline
                            :rows="8"
                            placeholder="请输入 body"
                        />
                    </div>
                </div>

                <div class="mt-6">
                    <h3 class="mb-3 text-sm font-bold text-slate-700">入参结构</h3>
                    <IntegrationParamTable v-model="apiForm.inputParams" />
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
                    <IntegrationParamTable v-model="apiForm.outputParams" output-mode />
                </div>

                <div class="mt-6 flex justify-end gap-3">
                    <button
                        class="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:border-slate-300"
                        type="button"
                        @click="emit('reset-editor')"
                    >
                        取消
                    </button>
                    <button
                        :disabled="apiSaving"
                        class="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                        type="button"
                        @click="emit('save')"
                    >
                        {{ apiSaving ? '保存中...' : '保存' }}
                    </button>
                </div>
            </div>

            <aside
                class="custom-scrollbar overflow-y-auto rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"
            >
                <div class="mb-4 flex items-center justify-between gap-3">
                    <div>
                        <h3 class="text-base font-bold text-slate-900">调试</h3>
                        <p class="mt-1 text-xs text-slate-400">保存 API 后可发起调试。</p>
                    </div>
                    <button
                        :disabled="apiTesting"
                        class="whitespace-nowrap rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                        type="button"
                        @click="emit('test')"
                    >
                        {{ apiTesting ? '调试中...' : '调试' }}
                    </button>
                </div>
                <label class="block">
                    <span class="mb-2 block text-sm font-semibold text-slate-600">调试入参</span>
                    <textarea
                        v-model="apiTestArgumentsTextProxy"
                        class="custom-scrollbar w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 font-mono text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                        rows="8"
                        placeholder="调试入参 JSON，例如：{}"
                    />
                </label>
                <div
                    v-if="apiTestError"
                    class="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ apiTestError }}
                </div>
                <div class="mt-5">
                    <div class="mb-2 text-sm font-semibold text-slate-600">调试返回</div>
                    <pre
                        v-if="apiTestResult"
                        class="custom-scrollbar max-h-[420px] overflow-auto rounded-2xl bg-slate-900 p-4 text-xs text-slate-100"
                        >{{ formatJson(apiTestResult) }}</pre
                    >
                    <div
                        v-else
                        class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-400"
                    >
                        暂无调试结果
                    </div>
                </div>
            </aside>
        </div>
    </section>
</template>
