<template>
    <div
        v-if="activeCitation"
        class="absolute inset-0 z-40 flex items-center justify-center bg-strong/32 p-4 backdrop-blur-sm"
        @click.self="$emit('close')"
    >
        <section
            class="w-full max-w-2xl rounded-[28px] border border-border-soft/80 bg-surface/94 shadow-panel"
        >
            <header
                class="flex items-start justify-between border-b border-border-soft/60 px-5 py-4"
            >
                <div class="space-y-1">
                    <p class="text-xs font-semibold uppercase tracking-widest text-muted">
                        引用详情
                    </p>
                    <h3 class="text-base font-semibold text-strong">
                        引用 {{ activeCitation.ref || '-' }}
                    </h3>
                </div>
                <button
                    type="button"
                    class="rounded-full border border-border-soft/70 bg-surface-alt/72 px-3 py-1 text-xs text-body transition hover:border-primary/20 hover:bg-accent-soft/48 hover:text-strong"
                    @click="$emit('close')"
                >
                    关闭
                </button>
            </header>

            <div class="space-y-3 px-5 py-4">
                <div
                    class="grid gap-2 rounded-2xl border border-border-soft/70 bg-surface-alt/68 p-3 text-sm"
                >
                    <p class="text-body">
                        <span class="font-medium text-muted">文档：</span>
                        {{ activeCitation.fileName || 'unknown' }}
                    </p>
                    <p class="text-body">
                        <span class="font-medium text-muted">索引：</span>
                        {{ activeCitation.indexId || '-' }}
                    </p>
                    <p class="text-body">
                        <span class="font-medium text-muted">得分：</span>
                        {{ activeCitation.score || '-' }}
                    </p>
                </div>

                <div class="rounded-2xl border border-primary/12 bg-accent-soft/52 p-3">
                    <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-primary">
                        块内容
                    </p>
                    <p
                        class="max-h-[40vh] overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed text-body"
                    >
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
