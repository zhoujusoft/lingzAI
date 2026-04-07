<script setup>
import PageLayout from '@/components/PageLayout.vue';
import KnowledgeStepProgress from './KnowledgeStepProgress.vue';

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
});

const emit = defineEmits(['back', 'go-document']);

const embeddedFiles = [
    {
        id: 'done-1',
        name: 'GB-T 1094.2-2013 电力变压器 第2部分：液浸式变压器的温升.pdf',
    },
];
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeCompletePanel"
        class="admin-page admin-page--knowledge-complete-step3"
    >
        <template #left>
            <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-50"
                @click="emit('back')"
            >
                <span class="material-symbols-outlined">arrow_back</span>
            </button>
        </template>

        <template #center>
            <KnowledgeStepProgress :current-step="3" />
        </template>

        <div class="custom-scrollbar flex-1 overflow-y-auto bg-slate-50/50 p-8">
            <div class="mx-auto max-w-4xl space-y-8">
                <div class="flex items-center gap-3">
                    <span class="text-3xl">🎉</span>
                    <h2 class="text-2xl font-bold text-slate-800">文档处理已完成</h2>
                </div>

                <div class="rounded-[24px] border border-slate-100 bg-white p-8 shadow-sm">
                    <div class="flex items-center gap-6">
                        <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50 text-primary">
                            <span class="material-symbols-outlined fill-1 text-4xl">smart_toy</span>
                        </div>
                        <div class="flex-1">
                            <div class="mb-1 text-xs font-medium text-slate-400">知识库名称</div>
                            <div class="rounded-xl border border-slate-100 bg-slate-50/80 px-4 py-3.5 font-medium text-slate-700">
                                {{ props.knowledge.name }}
                            </div>
                        </div>
                    </div>
                </div>

                <div class="space-y-4">
                    <h3 class="px-1 text-lg font-bold text-slate-800">嵌入已完成</h3>
                    <div
                        v-for="item in embeddedFiles"
                        :key="item.id"
                        class="flex items-center justify-between rounded-2xl border border-slate-100 bg-white p-5 shadow-sm"
                    >
                        <div class="flex items-center gap-3">
                            <span class="material-symbols-outlined text-red-500">picture_as_pdf</span>
                            <span class="font-medium text-slate-700">{{ item.name }}</span>
                        </div>
                        <span class="material-symbols-outlined fill-1 font-bold text-green-500">check_circle</span>
                    </div>
                </div>

                <div class="grid grid-cols-2 gap-x-12 gap-y-8 px-1 pt-4">
                    <div class="flex items-center justify-between border-b border-slate-100 pb-4">
                        <span class="text-slate-500">分段模式</span>
                        <span class="font-medium text-slate-800">自定义</span>
                    </div>
                    <div class="flex items-center justify-between border-b border-slate-100 pb-4">
                        <span class="text-slate-500">索引方式</span>
                        <span class="flex items-center gap-1.5 font-medium text-primary">
                            <span class="material-symbols-outlined fill-1 text-base">star</span>
                            高质量
                        </span>
                    </div>
                    <div class="flex items-center justify-between border-b border-slate-100 pb-4">
                        <span class="text-slate-500">最大分段长度</span>
                        <span class="font-medium text-slate-800">500</span>
                    </div>
                    <div class="flex items-center justify-between border-b border-slate-100 pb-4">
                        <span class="text-slate-500">检索设置</span>
                        <span class="flex items-center gap-1.5 font-medium text-primary">
                            <span class="material-symbols-outlined text-base">widgets</span>
                            向量检索
                        </span>
                    </div>
                    <div class="col-span-2 space-y-3">
                        <div class="text-slate-500">文本预处理规则</div>
                        <div class="font-medium text-slate-800">替换掉连续的空格，换行符和制表符</div>
                    </div>
                </div>

                <div class="flex justify-start pt-6">
                    <button
                        type="button"
                        class="flex items-center gap-2 rounded-xl bg-primary px-10 py-3.5 font-bold text-white shadow-xl shadow-blue-500/25 transition-all hover:bg-blue-700"
                        @click="emit('go-document')"
                    >
                        <span>前往文档</span>
                        <span class="material-symbols-outlined">arrow_forward</span>
                    </button>
                </div>
            </div>
        </div>
    </PageLayout>
</template>
