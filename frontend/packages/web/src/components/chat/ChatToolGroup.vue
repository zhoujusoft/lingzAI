<script setup>
/**
 * ChatToolGroup - 工具分组容器组件
 * 展示多个工具调用的折叠/展开容器
 */
import { ref, computed } from 'vue';
import ChatToolItem from './ChatToolItem.vue';

const props = defineProps({
    tools: {
        type: Array,
        required: true,
    },
    defaultOpen: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['toggle']);

const isOpen = ref(props.defaultOpen);

const totalTools = computed(() => props.tools.length);

const summary = computed(() => {
    if (totalTools.value === 0) return '无工具调用';

    const running = props.tools.filter(t => t.status === 'running').length;
    const error = props.tools.filter(t => t.status === 'error' || t.status === 'failed').length;
    const done = props.tools.filter(t => t.status === 'done' || !t.status).length;

    // 单个工具：显示工具名和状态
    if (totalTools.value === 1) {
        const tool = props.tools[0];
        const name = tool.actionLabel || tool.displayName || tool.name || '工具调用';
        if (running > 0) return `${name} · 执行中`;
        if (error > 0) return `${name} · 失败`;
        return name;
    }

    // 多个工具：显示统计
    const parts = [`${totalTools.value} 个工具调用`];
    if (done > 0) parts.push(`${done} 完成`);
    if (running > 0) parts.push(`${running} 执行中`);
    if (error > 0) parts.push(`${error} 失败`);
    return parts.join(' · ');
});

function toggleOpen() {
    isOpen.value = !isOpen.value;
    emit('toggle', isOpen.value);
}
</script>

<template>
    <div class="chat-tool-group chat-tool-card">
        <button type="button" class="chat-tool-group-header" @click="toggleOpen">
            <span class="material-symbols-outlined chat-tool-group-icon"> terminal </span>
            <span class="chat-tool-group-summary">{{ summary }}</span>
            <span
                class="material-symbols-outlined chat-tool-group-expand"
                :class="{ expanded: isOpen }"
            >
                chevron_right
            </span>
        </button>

        <!-- Tools List -->
        <div v-if="isOpen" class="chat-tool-group-content">
            <ChatToolItem
                v-for="tool in tools"
                :key="tool.key || tool.name"
                :tool="tool"
                :default-open="tools.length === 1"
            />
        </div>
    </div>
</template>
