<script setup>
/**
 * ChatToolDetail - 工具详情展示组件
 * 用于展示工具的参数和输出内容
 */
import { computed } from 'vue';

const props = defineProps({
    label: {
        type: String,
        required: true,
    },
    content: {
        type: String,
        default: '',
    },
    expanded: {
        type: Boolean,
        default: true,
    },
    isEmpty: {
        type: Boolean,
        default: false,
    },
    status: {
        type: String,
        default: 'done', // 'waiting' | 'done' | 'error'
    },
});

const emit = defineEmits(['toggle']);

const hasContent = computed(() => !props.isEmpty && props.content);

function handleToggle() {
    emit('toggle');
}

function formatBlock(text) {
    if (!text) return '';
    try {
        const parsed = JSON.parse(text);
        return JSON.stringify(parsed, null, 2);
    } catch {
        return text;
    }
}
</script>

<template>
    <div class="detail-section">
        <button type="button" class="detail-toggle" @click="handleToggle">
            <span
                class="material-symbols-outlined detail-toggle-icon"
                :class="{ expanded: expanded }"
            >
                chevron_right
            </span>
            {{ label }}
        </button>
        <div v-if="expanded" class="detail-content">
            <pre v-if="hasContent" class="detail-code">{{ formatBlock(content) }}</pre>
            <div v-else-if="status === 'waiting'" class="detail-waiting">
                <span class="status-dot-animated"></span>
                <span>等待返回...</span>
            </div>
            <div v-else class="detail-empty">无{{ label === '参数' ? '参数' : '返回内容' }}</div>
        </div>
    </div>
</template>
