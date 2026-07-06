<template>
    <div
        v-if="isVisible"
        class="preview-tab-container flex h-full flex-col overflow-hidden rounded-2xl bg-white border border-slate-200 shadow-sm"
    >
        <!-- Tab Header -->
        <div
            class="tab-header flex shrink-0 items-center justify-between border-b border-slate-200 bg-slate-50 rounded-t-2xl"
        >
            <div class="flex">
                <button
                    v-for="tab in tabs"
                    :key="tab.key"
                    type="button"
                    class="tab-item flex items-center gap-1.5 px-5 py-3 text-sm font-medium transition-colors"
                    :class="[
                        activeTab === tab.key
                            ? 'border-b-2 border-primary text-primary bg-white'
                            : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100',
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
                class="mr-3 flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-200 hover:text-slate-600"
                title="关闭"
                @click="handleClose"
            >
                <span class="material-symbols-outlined text-lg">close</span>
            </button>
        </div>

        <!-- Tab Content -->
        <div class="tab-content flex-1 overflow-hidden">
            <!-- HTML 预览 Tab -->
            <div v-show="activeTab === 'preview'" class="h-full">
                <FrontAgentChatPreview :render-payload="renderPayload" @close="handleClose" />
            </div>

            <!-- 工作区 Tab -->
            <div v-show="activeTab === 'files'" class="h-full">
                <FilePreviewPane :session-code="sessionCode" :enabled="activeTab === 'files'" />
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import FrontAgentChatPreview from './FrontAgentChatPreview.vue';
import FilePreviewPane from './FilePreviewPane.vue';

const props = defineProps({
    renderPayload: {
        type: Object,
        default: null,
    },
    sessionCode: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['close']);

const tabs = [
    { key: 'preview', label: 'HTML 预览', icon: 'overview' },
    { key: 'files', label: '工作区', icon: 'folder' },
];

const activeTab = ref('preview');
const visible = ref(true);

// 计算是否可见
const isVisible = computed(() => {
    if (!visible.value) return false;
    return Boolean(props.renderPayload);
});

// 当有新的预览内容时，自动切换到预览 tab 并显示
watch(
    () => props.renderPayload,
    newVal => {
        if (newVal) {
            visible.value = true;
            activeTab.value = 'preview';
        }
    }
);

function handleClose() {
    visible.value = false;
    emit('close');
}
</script>

<style scoped>
.preview-tab-container {
    background: #ffffff;
}

.tab-header {
    padding-left: 0;
}

.tab-item {
    position: relative;
    border-bottom: 2px solid transparent;
}

.tab-item:first-child {
    border-top-left-radius: 1rem;
}
</style>
