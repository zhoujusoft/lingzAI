<template>
    <transition name="preview-slide">
        <aside
            v-if="isVisible"
            class="preview-tab-wrapper order-2 flex h-full min-w-0 flex-1 flex-col overflow-hidden border-l border-border-soft"
        >
            <!-- Tab Header -->
            <div class="tab-header flex shrink-0 items-center justify-between px-2">
                <div class="flex">
                    <button
                        v-for="tab in tabs"
                        :key="tab.key"
                        type="button"
                        class="tab-item flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium transition-colors"
                        :class="[
                            activeTab === tab.key
                                ? 'text-strong tab-active'
                                : 'text-muted hover:text-body',
                        ]"
                        @click="activeTab = tab.key"
                    >
                        <span class="material-symbols-outlined text-base">{{ tab.icon }}</span>
                        <span>{{ tab.label }}</span>
                    </button>
                </div>
                <!-- Close Button -->
                <button
                    type="button"
                    class="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                    title="关闭"
                    @click="handleClose"
                >
                    <span class="material-symbols-outlined text-base">close</span>
                </button>
            </div>

            <!-- Divider -->
            <div class="h-px bg-border-soft"></div>

            <!-- Tab Content -->
            <div class="tab-content flex-1 overflow-hidden bg-surface">
                <!-- 预览 Tab -->
                <div v-show="activeTab === 'preview'" class="h-full">
                    <div class="flex h-full flex-col overflow-hidden">
                        <div
                            v-if="effectiveActiveHtml"
                            class="flex h-11 items-center justify-between bg-surface px-3 py-2"
                        >
                            <p class="truncate text-sm font-medium text-strong">
                                {{
                                    effectiveActiveHtml.fileName ||
                                    effectiveActiveHtml.title ||
                                    '预览'
                                }}
                            </p>
                            <div class="flex items-center gap-1">
                                <button
                                    type="button"
                                    class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                                    @click="$emit('open-external', effectiveActiveHtml)"
                                    title="新窗口打开"
                                >
                                    <span class="material-symbols-outlined text-base"
                                        >open_in_new</span
                                    >
                                </button>
                                <a
                                    v-if="effectiveActiveHtml && effectiveActiveHtml.downloadUrl"
                                    :href="effectiveActiveHtml.downloadUrl"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition hover:bg-surface-alt hover:text-body"
                                    title="下载"
                                >
                                    <span class="material-symbols-outlined text-base"
                                        >download</span
                                    >
                                </a>
                            </div>
                        </div>
                        <FilePreviewRenderer
                            v-if="effectiveActiveHtml"
                            :key="previewRendererKey"
                            :file="effectiveActiveHtml"
                        />
                        <div v-else class="flex-1 flex items-center justify-center">
                            <p class="text-sm text-muted">暂无预览内容</p>
                        </div>
                    </div>
                </div>

                <!-- 工作区 Tab -->
                <div v-show="activeTab === 'files'" class="h-full">
                    <FileTreePane
                        :session-code="sessionId"
                        :enabled="activeTab === 'files'"
                        @preview-file="handleFilePreview"
                    />
                </div>
            </div>
        </aside>
    </transition>
</template>

<script>
import FilePreviewRenderer from './FilePreviewRenderer.vue';
import FileTreePane from '../agent-chat/FileTreePane.vue';

export default {
    name: 'ChatPreviewTabContainer',
    components: {
        FileTreePane,
        FilePreviewRenderer,
    },
    props: {
        activeHtml: {
            type: Object,
            default: null,
        },
        sessionId: {
            type: String,
            default: '',
        },
        showFileList: {
            type: Boolean,
            default: false,
        },
    },
    emits: ['close', 'open-external'],
    data() {
        return {
            visible: true,
            activeTab: 'files',
            activeFilePreview: null,
            previewSource: 'prop',
            tabs: [
                { key: 'files', label: '工作区', icon: 'folder' },
                { key: 'preview', label: '预览', icon: 'overview' },
            ],
        };
    },
    computed: {
        effectiveActiveHtml() {
            if (this.previewSource === 'workspace' && this.activeFilePreview) {
                return this.activeFilePreview;
            }
            return this.activeHtml || this.activeFilePreview;
        },
        isVisible() {
            // 如果用户手动关闭了，就不显示
            if (!this.visible) return false;
            // 工作区内部预览不单独决定面板可见性，避免父组件关闭后被内部状态顶回来
            return Boolean(this.activeHtml) || this.showFileList;
        },
        previewRendererKey() {
            const preview = this.effectiveActiveHtml || {};
            return [
                preview.src || '',
                preview.fileName || '',
                preview.title || '',
                preview.contentType || '',
            ].join('::');
        },
    },
    watch: {
        activeHtml(newVal) {
            if (newVal) {
                this.visible = true;
                this.activeFilePreview = null;
                this.previewSource = 'prop';
                this.activeTab = 'preview';
            }
        },
        showFileList(newVal) {
            if (newVal) {
                this.visible = true;
                if (!this.activeHtml) {
                    this.activeTab = 'files';
                }
            }
        },
        sessionId(newVal) {
            if (newVal) {
                this.visible = true;
                this.activeFilePreview = null;
                this.previewSource = 'prop';
            }
        },
    },
    methods: {
        handleClose() {
            this.visible = false;
            this.$emit('close');
        },
        handleFilePreview(filePreview) {
            if (!filePreview) return;
            this.visible = true;
            this.activeFilePreview = filePreview;
            this.previewSource = 'workspace';
            this.activeTab = 'preview';
        },
    },
};
</script>

<style scoped>
.preview-tab-wrapper {
    background: rgb(var(--color-bg-surface));
}

.tab-item {
    position: relative;
    border-bottom: 2px solid transparent;
    margin-bottom: -1px;
}

.tab-item.tab-active {
    border-bottom-color: rgb(var(--color-accent));
}

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
