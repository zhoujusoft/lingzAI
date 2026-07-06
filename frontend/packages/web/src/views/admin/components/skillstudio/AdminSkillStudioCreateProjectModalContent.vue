<script setup>
import { computed } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const formError = computed(() => props.context.formError || '');
const submitError = computed(() => props.context.submitError || '');

function clearErrors() {
    props.context.formError = '';
    props.context.submitError = '';
}
</script>

<template>
    <div class="space-y-4 px-6 py-5">
        <p class="text-sm leading-6 text-slate-500">
            输入你想创建的技能描述。确认后系统会基于描述自动生成项目名称、运行时技能名、图标、分类等元信息，进入新的技能工坊页面后还会自动触发第一轮
            Creator 生成。
        </p>
        <div
            class="rounded-2xl border border-blue-100 bg-blue-50/70 px-4 py-3 text-xs leading-6 text-blue-700"
        >
            <div class="font-semibold">系统自动生成内容</div>
            <div class="mt-1">
                项目名称、skill 名称、runtime_skill_name、Lingz 图标、颜色、分类、摘要说明
            </div>
        </div>
        <div
            v-if="submitError"
            class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
        >
            {{ submitError }}
        </div>
        <div class="space-y-2">
            <label class="text-sm font-medium text-slate-700">新建技能描述</label>
            <textarea
                v-model="context.description"
                class="min-h-[180px] w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                placeholder="例如：创建一个技能工坊项目，用于生成和调试报销制度知识问答技能，支持绑定 lingz 知识库、查看生成结果并持续迭代。"
                @input="clearErrors"
            />
            <p v-if="formError" class="text-xs text-rose-500">{{ formError }}</p>
        </div>
    </div>
</template>
