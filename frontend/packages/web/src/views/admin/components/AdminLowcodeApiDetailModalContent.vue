<script setup>
import { computed } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

function getTypeLabel(type) {
    const normalized = String(type ?? '').trim();
    switch (normalized) {
        case '1':
        case 'String':
            return '文本';
        case '2':
        case 'Number':
            return '数字';
        case '3':
        case 'Double':
            return '浮点数';
        case '4':
        case 'Object':
            return '对象';
        case '5':
        case 'Array':
            return '数组';
        case '6':
        case 'ObjectArray':
            return '对象数组';
        case '7':
        case 'Boolean':
            return '布尔值';
        case '8':
        case 'DateTime':
            return '日期时间';
        default:
            return normalized || '-';
    }
}

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

function normalizeRows(value) {
    if (!value) {
        return [];
    }
    let raw = value;
    if (typeof raw === 'string') {
        try {
            raw = JSON.parse(raw);
        } catch (error) {
            return [];
        }
    }
    if (Array.isArray(raw)) {
        return raw.map((item, index) => ({
            id: item?.id || item?.name || item?.field || item?.code || `row-${index}`,
            name: item?.name || item?.field || item?.code || item?.paramName || '-',
            type: getTypeLabel(item?.type || item?.dataType || item?.valueType || item?.paramType),
            description:
                item?.description ||
                item?.desc ||
                item?.remark ||
                item?.comment ||
                item?.memo ||
                item?.fieldDesc ||
                item?.fieldRemark ||
                item?.fieldDescription ||
                item?.paramDesc ||
                item?.paramRemark ||
                item?.helpText ||
                item?.tips ||
                item?.placeholder ||
                item?.label ||
                item?.title ||
                item?.displayName ||
                '',
        }));
    }
    if (raw && typeof raw === 'object') {
        return Object.entries(raw).map(([key, item], index) => ({
            id: `${key}-${index}`,
            name: key,
            type:
                item && typeof item === 'object'
                    ? getTypeLabel(item.type || item.dataType || item.valueType)
                    : getTypeLabel(typeof item),
            description:
                item && typeof item === 'object'
                    ? item.description ||
                      item.desc ||
                      item.remark ||
                      item.comment ||
                      item.memo ||
                      item.fieldDesc ||
                      item.fieldRemark ||
                      item.fieldDescription ||
                      item.paramDesc ||
                      item.paramRemark ||
                      item.helpText ||
                      item.tips ||
                      item.placeholder ||
                      item.label ||
                      item.title ||
                      item.displayName ||
                      ''
                    : '',
        }));
    }
    return [];
}

const api = computed(() => props.context.api || {});
const inputRows = computed(() => normalizeRows(api.value.inputParams));
const outputRows = computed(() => normalizeRows(api.value.outputParams));
</script>

<template>
    <div class="max-h-[80vh] space-y-6 overflow-y-auto p-8">
        <section>
            <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                <h3 class="mt-3 text-xl font-bold text-slate-900">
                    {{ api.apiName || api.apiCode }}
                </h3>
                <dl class="mt-4 space-y-3 text-sm text-slate-600">
                    <div class="flex gap-3">
                        <dt class="w-24 shrink-0 font-semibold text-slate-800">请求方式</dt>
                        <dd>{{ api.method || '未返回' }}</dd>
                    </div>
                    <div class="flex gap-3">
                        <dt class="w-24 shrink-0 font-semibold text-slate-800">API Code</dt>
                        <dd class="break-all">{{ api.apiCode || '-' }}</dd>
                    </div>
                    <div class="flex gap-3">
                        <dt class="w-24 shrink-0 font-semibold text-slate-800">工具名</dt>
                        <dd class="break-all">{{ api.toolName || '尚未注册' }}</dd>
                    </div>
                </dl>
                <p v-if="api.apiRemark" class="mt-4 text-sm leading-6 text-slate-500">
                    {{ api.apiRemark }}
                </p>
            </article>
        </section>

        <section class="rounded-2xl border border-slate-200 bg-white">
            <div class="border-b border-slate-200 px-5 py-4">
                <h4 class="text-lg font-bold text-slate-900">输入参数</h4>
            </div>
            <div class="overflow-x-auto">
                <table class="min-w-full divide-y divide-slate-200 text-sm">
                    <thead class="bg-slate-50 text-left text-slate-600">
                        <tr>
                            <th class="px-5 py-3 font-semibold">参数名称</th>
                            <th class="px-5 py-3 font-semibold">参数类型</th>
                            <th class="px-5 py-3 font-semibold">参数描述</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100">
                        <tr v-if="!inputRows.length">
                            <td colspan="3" class="px-5 py-6 text-center text-slate-400">
                                暂未返回结构化入参
                            </td>
                        </tr>
                        <tr v-for="row in inputRows" :key="row.id" class="align-top">
                            <td class="px-5 py-4 font-medium text-slate-800">{{ row.name }}</td>
                            <td class="px-5 py-4 text-slate-600">{{ row.type || '-' }}</td>
                            <td class="px-5 py-4 text-slate-500">{{ row.description || '-' }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="rounded-2xl border border-slate-200 bg-white">
            <div class="border-b border-slate-200 px-5 py-4">
                <h4 class="text-lg font-bold text-slate-900">输出参数</h4>
            </div>
            <div class="overflow-x-auto">
                <table class="min-w-full divide-y divide-slate-200 text-sm">
                    <thead class="bg-slate-50 text-left text-slate-600">
                        <tr>
                            <th class="px-5 py-3 font-semibold">字段名称</th>
                            <th class="px-5 py-3 font-semibold">参数类型</th>
                            <th class="px-5 py-3 font-semibold">字段描述</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100">
                        <tr v-if="!outputRows.length">
                            <td colspan="3" class="px-5 py-6 text-center text-slate-400">
                                暂未返回结构化出参
                            </td>
                        </tr>
                        <tr v-for="row in outputRows" :key="row.id" class="align-top">
                            <td class="px-5 py-4 font-medium text-slate-800">{{ row.name }}</td>
                            <td class="px-5 py-4 text-slate-600">{{ row.type || '-' }}</td>
                            <td class="px-5 py-4 text-slate-500">{{ row.description || '-' }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5">
            <div class="flex items-center justify-between gap-3">
                <div>
                    <h4 class="text-base font-bold text-slate-900">测试执行</h4>
                    <p class="mt-1 text-sm text-slate-500">这里直接填写 JSON 入参并执行。</p>
                </div>
                <button
                    type="button"
                    class="rounded-2xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="context.executeLoading"
                    @click="context.onExecute?.()"
                >
                    {{ context.executeLoading ? '执行中...' : '测试执行' }}
                </button>
            </div>

            <label class="mt-4 block">
                <span class="mb-2 block text-sm font-semibold text-slate-700">测试入参 JSON</span>
                <textarea
                    :value="context.payloadText"
                    rows="10"
                    class="custom-scrollbar w-full rounded-2xl border border-emerald-200 bg-white px-4 py-3 font-mono text-sm leading-6 text-slate-700 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                    @input="context.setPayloadText?.($event.target.value)"
                />
            </label>

            <p
                v-if="context.executeError"
                class="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600"
            >
                {{ context.executeError }}
            </p>

            <div v-if="context.executeResult" class="mt-4">
                <h5 class="text-sm font-semibold text-slate-800">执行结果</h5>
                <pre
                    class="custom-scrollbar mt-2 max-h-72 overflow-auto rounded-xl bg-slate-900/95 p-3 text-xs leading-6 text-slate-100"
                    >{{ prettyJson(context.executeResult) }}</pre
                >
            </div>
        </section>
    </div>
</template>
