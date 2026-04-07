<template>
    <div
        v-if="activeCitation"
        class="absolute inset-0 z-40 flex items-center justify-center bg-slate-900/35 p-4"
        @click.self="$emit('close')"
    >
        <section class="w-full max-w-2xl rounded-2xl border border-slate-200 bg-white shadow-2xl">
            <header class="flex items-start justify-between border-b border-slate-100 px-5 py-4">
                <div class="space-y-1">
                    <p class="text-xs font-semibold uppercase tracking-widest text-slate-400">引用详情</p>
                    <h3 class="text-base font-semibold text-slate-900">
                        引用 {{ activeCitation.ref || '-' }}
                    </h3>
                </div>
                <button
                    type="button"
                    class="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600 transition hover:border-primary hover:text-primary"
                    @click="$emit('close')"
                >
                    关闭
                </button>
            </header>

            <div class="space-y-3 px-5 py-4">
                <div class="grid gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm">
                    <p class="text-slate-700">
                        <span class="font-medium text-slate-500">文档：</span>
                        {{ activeCitation.fileName || 'unknown' }}
                    </p>
                    <p class="text-slate-700">
                        <span class="font-medium text-slate-500">索引：</span>
                        {{ activeCitation.indexId || '-' }}
                    </p>
                    <p class="text-slate-700">
                        <span class="font-medium text-slate-500">得分：</span>
                        {{ activeCitation.score || '-' }}
                    </p>
                </div>

                <div class="rounded-xl border border-emerald-200 bg-emerald-50/60 p-3">
                    <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-emerald-700">块内容</p>
                    <p class="max-h-[40vh] overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed text-emerald-900">
                        {{ activeCitation.snippet || '（无摘要）' }}
                    </p>
                </div>
            </div>
        </section>
    </div>
</template>

<script>
export default {
    name: 'ChatCitationModal',
    emits: ['close'],
    props: {
        activeCitation: {
            type: Object,
            default: null,
        },
    },
};
</script>
