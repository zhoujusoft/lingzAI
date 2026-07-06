<template>
    <div class="file-preview-pane flex h-full flex-col overflow-hidden">
        <!-- Toolbar -->
        <div
            class="toolbar flex shrink-0 items-center justify-between px-4 py-3 border-b border-slate-100"
        >
            <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-slate-400 text-lg">folder</span>
                <span class="text-sm font-medium text-slate-700">工作区</span>
                <span v-if="pagination.total > 0" class="text-xs text-slate-400">
                    ({{ pagination.total }} 个文件)
                </span>
            </div>
            <button
                type="button"
                class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
                :disabled="loading"
                @click="refresh"
            >
                <span class="material-symbols-outlined text-sm">refresh</span>
                <span>刷新</span>
            </button>
        </div>

        <!-- Loading State -->
        <div v-if="loading && assets.length === 0" class="flex-1 flex items-center justify-center">
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-slate-300 animate-spin">
                    progress_activity
                </span>
                <p class="mt-2 text-sm text-slate-400">加载中...</p>
            </div>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-rose-300">error</span>
                <p class="mt-2 text-sm text-rose-500">加载失败</p>
                <button
                    type="button"
                    class="mt-3 text-sm text-violet-600 hover:underline"
                    @click="refresh"
                >
                    重试
                </button>
            </div>
        </div>

        <!-- Empty State -->
        <div v-else-if="assets.length === 0" class="flex-1 flex items-center justify-center px-6">
            <div class="text-center">
                <span class="material-symbols-outlined text-4xl text-slate-300">folder_open</span>
                <p class="mt-2 text-sm text-slate-500">暂无文件</p>
                <p class="mt-1 text-xs text-slate-400">上传文件或执行技能后，文件将显示在这里</p>
            </div>
        </div>

        <!-- File List -->
        <div v-else class="flex-1 overflow-y-auto custom-scrollbar p-2">
            <!-- Upload Files Group -->
            <div v-if="groupedAssets.UPLOAD.length > 0" class="file-group mb-4">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">upload</span>
                    <span>上传文件</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.UPLOAD"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>

            <!-- Artifact Files Group -->
            <div v-if="groupedAssets.ARTIFACT.length > 0" class="file-group mb-4">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">auto_awesome</span>
                    <span>技能产物</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.ARTIFACT"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>

            <!-- Temp Files Group -->
            <div v-if="groupedAssets.TEMP.length > 0" class="file-group">
                <div
                    class="group-header flex items-center gap-2 px-2 py-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider"
                >
                    <span class="material-symbols-outlined text-sm">temp_preferences_custom</span>
                    <span>临时文件</span>
                </div>
                <FilePreviewItem
                    v-for="file in groupedAssets.TEMP"
                    :key="file.fileCode"
                    :file="file"
                />
            </div>
        </div>
    </div>
</template>

<script setup>
import { toRef } from 'vue';
import { useRuntimeFileAssets } from '@/composables/useRuntimeFileAssets';
import FilePreviewItem from './FilePreviewItem.vue';

const props = defineProps({
    sessionCode: {
        type: String,
        default: '',
    },
    enabled: {
        type: Boolean,
        default: false,
    },
});

const {
    loading,
    error,
    assets,
    groupedAssets,
    pagination,
    loadAssets: refresh,
} = useRuntimeFileAssets({
    sessionCode: toRef(props, 'sessionCode'),
    enabled: toRef(props, 'enabled'),
});
</script>

<style scoped>
.file-preview-pane {
    background: #fafbfc;
}

.group-header {
    background: #f1f5f9;
    border-radius: 6px;
    margin-bottom: 4px;
}
</style>
