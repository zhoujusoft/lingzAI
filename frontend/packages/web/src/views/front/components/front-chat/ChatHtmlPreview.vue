<template>
    <transition name="preview-slide">
        <aside
            v-if="activeHtml"
            class="preview-pane order-2 flex h-full min-w-0 flex-1 flex-col overflow-hidden border-l border-border-soft bg-surface"
        >
            <div class="flex h-11 items-center justify-between bg-surface px-3 py-2">
                <p class="truncate text-sm font-medium text-strong">
                    {{ activeHtml.fileName || activeHtml.title || 'HTML 预览' }}
                </p>
                <div class="flex items-center gap-1">
                    <button
                        type="button"
                        class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                        @click="$emit('open-external', activeHtml)"
                        title="新窗口打开"
                        aria-label="新窗口打开"
                    >
                        <span class="material-symbols-outlined text-base">open_in_new</span>
                    </button>
                    <a
                        v-if="activeHtml.downloadUrl"
                        :href="activeHtml.downloadUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                        title="下载"
                        aria-label="下载"
                    >
                        <span class="material-symbols-outlined text-base">download</span>
                    </a>
                    <button
                        type="button"
                        class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                        @click="$emit('close')"
                        title="关闭"
                        aria-label="关闭"
                    >
                        <span class="material-symbols-outlined text-base">close</span>
                    </button>
                </div>
            </div>
            <div class="h-px bg-border-soft"></div>
            <iframe
                class="min-h-0 flex-1 bg-surface"
                :src="activeHtml.src || null"
                :srcdoc="activeHtml.src ? null : activeHtml.html"
                sandbox="allow-scripts"
            ></iframe>
        </aside>
    </transition>
</template>

<script>
export default {
    name: 'ChatHtmlPreview',
    emits: ['close', 'open-external'],
    props: {
        activeHtml: {
            type: Object,
            default: null,
        },
    },
};
</script>

<style scoped>
/* Slide animation for preview panel */
.preview-slide-enter-active,
.preview-slide-leave-active {
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.preview-slide-enter-from,
.preview-slide-leave-to {
    opacity: 0;
    transform: translateX(24px);
}
</style>
