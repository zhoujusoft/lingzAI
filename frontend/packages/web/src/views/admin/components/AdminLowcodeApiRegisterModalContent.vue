<script setup>
import { computed } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const formErrors = computed(() => props.context.formErrors || {});

const toolDisplayName = computed({
    get: () => props.context.toolDisplayName || '',
    set: value => {
        props.context.toolDisplayName = value;
    },
});

const toolRemark = computed({
    get: () => props.context.toolRemark || '',
    set: value => {
        props.context.toolRemark = value;
    },
});

function clearToolNameError() {
    if (props.context.formErrors?.toolDisplayName) {
        delete props.context.formErrors.toolDisplayName;
    }
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}

function clearSubmitError() {
    if (props.context.submitError) {
        props.context.submitError = '';
    }
}
</script>

<template>
    <div class="space-y-6 p-8">
        <div
            class="overflow-hidden rounded-[28px] border border-sky-100 bg-gradient-to-br from-sky-50 via-white to-cyan-50 shadow-sm"
        >
            <div class="flex flex-col gap-4 p-6 lg:flex-row lg:items-start lg:justify-between">
                <div class="min-w-0">
                    <p class="text-xs font-semibold uppercase tracking-[0.28em] text-sky-500">
                        Tool Register
                    </p>
                    <h3 class="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                        {{ context.apiName || context.apiCode }}
                    </h3>
                    <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
                        工具名称默认取 API
                        名称，修改后仅影响工具展示名称，不会改变技能绑定使用的工具编码。
                    </p>
                </div>

                <div
                    class="inline-flex items-center gap-2 rounded-full border border-white/80 bg-white/90 px-3 py-1.5 text-xs font-semibold text-slate-600 shadow-sm"
                >
                    <span class="h-2 w-2 rounded-full bg-emerald-400" />
                    {{ context.registered ? '重新注册' : '首次注册' }}
                </div>
            </div>

            <div class="grid gap-3 border-t border-white/80 bg-white/70 p-6 md:grid-cols-2">
                <article class="rounded-2xl border border-slate-200 bg-white/90 p-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Platform
                    </p>
                    <p class="mt-2 break-all text-sm font-semibold text-slate-800">
                        {{ context.platformKey || '-' }}
                    </p>
                </article>

                <article class="rounded-2xl border border-slate-200 bg-white/90 p-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        API Code
                    </p>
                    <p class="mt-2 break-all text-sm font-semibold text-slate-800">
                        {{ context.apiCode || '-' }}
                    </p>
                </article>

                <article
                    v-if="context.currentToolDisplayName"
                    class="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4 md:col-span-2"
                >
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-emerald-500">
                        Current Tool Name
                    </p>
                    <p class="mt-2 break-all text-sm text-emerald-700">
                        {{ context.currentToolDisplayName }}
                    </p>
                </article>

                <article
                    v-if="context.currentToolName"
                    class="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4 md:col-span-2"
                >
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-emerald-500">
                        Current Tool Code
                    </p>
                    <p class="mt-2 break-all font-mono text-sm text-emerald-700">
                        {{ context.currentToolName }}
                    </p>
                </article>

                <article
                    v-if="context.apiRemark || context.currentToolRemark"
                    class="rounded-2xl border border-slate-200 bg-white/90 p-4 md:col-span-2"
                >
                    <div v-if="context.apiRemark">
                        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                            API Remark
                        </p>
                        <p class="mt-2 text-sm leading-6 text-slate-600">
                            {{ context.apiRemark }}
                        </p>
                    </div>
                    <div v-if="context.currentToolRemark" :class="context.apiRemark ? 'mt-4' : ''">
                        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                            Current Tool Remark
                        </p>
                        <p class="mt-2 text-sm leading-6 text-slate-600">
                            {{ context.currentToolRemark }}
                        </p>
                    </div>
                </article>
            </div>
        </div>

        <div
            v-if="context.submitError"
            class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
        >
            {{ context.submitError }}
        </div>

        <section class="rounded-[24px] border border-slate-200 bg-white p-5 shadow-sm">
            <label class="block">
                <span class="text-sm font-semibold text-slate-800">
                    工具名称 <span class="text-rose-500">*</span>
                </span>
                <p class="mt-2 text-sm leading-6 text-slate-500">
                    默认使用 API 名称。这里是工具库展示名称，修改它不会改变工具编码。
                </p>
                <input
                    v-model="toolDisplayName"
                    type="text"
                    placeholder="请输入工具名称"
                    :class="[
                        'mt-4 w-full rounded-2xl border bg-slate-50 px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:bg-white focus:ring-2',
                        formErrors.toolDisplayName
                            ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100'
                            : 'border-slate-200 focus:border-sky-400 focus:ring-sky-100',
                    ]"
                    @input="clearToolNameError"
                />
            </label>

            <div class="mt-4 flex flex-wrap gap-2 text-xs text-slate-500">
                <span class="rounded-full bg-slate-100 px-3 py-1">必填</span>
                <span class="rounded-full bg-slate-100 px-3 py-1">默认 API 名称</span>
                <span class="rounded-full bg-slate-100 px-3 py-1">支持中文展示</span>
            </div>

            <p v-if="formErrors.toolDisplayName" class="mt-3 text-xs text-rose-500">
                {{ formErrors.toolDisplayName }}
            </p>

            <div class="mt-5 rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
                <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                    工具编码
                </p>
                <p class="mt-2 break-all font-mono text-sm font-semibold text-slate-700">
                    {{ context.currentToolName || context.defaultToolName || '-' }}
                </p>
                <p class="mt-2 text-xs leading-6 text-slate-500">
                    工具编码固定用于技能绑定与运行时解析，不受工具名称修改影响。
                </p>
            </div>

            <label class="mt-5 block">
                <span class="text-sm font-semibold text-slate-800">工具备注</span>
                <textarea
                    v-model="toolRemark"
                    rows="4"
                    placeholder="请输入工具备注"
                    class="custom-scrollbar mt-4 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-sky-400 focus:bg-white focus:ring-2 focus:ring-sky-100"
                    @input="clearSubmitError"
                />
            </label>
        </section>
    </div>
</template>
