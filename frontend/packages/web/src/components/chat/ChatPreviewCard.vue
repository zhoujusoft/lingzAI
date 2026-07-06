<script setup>
/**
 * ChatPreviewCard - 预览卡片组件
 * 支持 HTML 预览和 Artifact 两种类型
 */
import { computed } from 'vue';

const props = defineProps({
    type: {
        type: String,
        required: true,
        validator: v => ['html', 'artifact'].includes(v),
    },
    title: {
        type: String,
        default: '',
    },
    size: {
        type: String,
        default: '',
    },
    fileName: {
        type: String,
        default: '',
    },
    previewable: {
        type: Boolean,
        default: false,
    },
    downloadUrl: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['preview', 'download']);

const displayTitle = computed(() => {
    if (props.type === 'artifact') {
        return props.fileName || props.title || '文件产物';
    }
    return props.title || 'HTML 预览';
});

const icon = computed(() => {
    return props.type === 'artifact' ? 'preview' : 'code';
});

function handlePreview() {
    emit('preview');
}

function handleDownload() {
    if (props.downloadUrl) {
        window.open(props.downloadUrl, '_blank', 'noopener');
    }
    emit('download');
}
</script>

<template>
    <div
        :class="[
            'chat-preview-card chat-tool-card',
            { 'chat-preview-card-clickable': type === 'html' },
        ]"
        @click="type === 'html' && handlePreview()"
    >
        <div class="chat-preview-header">
            <div class="chat-preview-title">
                <span class="material-symbols-outlined chat-preview-icon">
                    {{ icon }}
                </span>
                <span>{{ displayTitle }}</span>
            </div>

            <!-- HTML Type: Show Size -->
            <span v-if="type === 'html' && size" class="chat-preview-size">
                {{ size }}
            </span>

            <!-- Artifact Type: Show Actions -->
            <div v-if="type === 'artifact'" class="chat-preview-actions">
                <button
                    v-if="previewable"
                    type="button"
                    class="chat-tool-btn"
                    @click.stop="handlePreview"
                >
                    预览
                </button>
                <a
                    v-if="downloadUrl"
                    :href="downloadUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="chat-tool-btn"
                    @click.stop
                >
                    下载
                </a>
            </div>
        </div>
    </div>
</template>
