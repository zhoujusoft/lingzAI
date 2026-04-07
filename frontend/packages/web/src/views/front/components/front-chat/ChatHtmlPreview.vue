<template>
    <aside
        v-if="activeHtml"
        class="absolute right-5 top-20 z-30 flex h-[calc(100%-6rem)] w-[44%] min-w-[320px] max-w-[620px] flex-col gap-2 rounded-2xl border border-slate-200 bg-white p-3 shadow-xl shadow-slate-900/10"
    >
        <div class="flex items-center justify-between">
            <div>
                <p class="text-sm font-semibold">{{ activeHtml.title || 'HTML 预览' }}</p>
                <span class="text-[10px] text-slate-400">{{ activeHtml.size }}</span>
            </div>
            <button
                type="button"
                class="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600 transition hover:border-primary hover:text-primary"
                @click="$emit('close')"
            >
                关闭
            </button>
        </div>
        <iframe
            class="min-h-0 flex-1 rounded-xl border border-slate-100"
            :srcdoc="activeHtml.html"
            sandbox="allow-scripts"
        ></iframe>
    </aside>
</template>

<script>
export default {
    name: 'ChatHtmlPreview',
    emits: ['close'],
    props: {
        activeHtml: {
            type: Object,
            default: null,
        },
    },
};
</script>
