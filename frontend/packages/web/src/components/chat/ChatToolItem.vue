<script setup>
/**
 * ChatToolItem - 单个工具项组件
 * 展示工具的状态、名称、参数和输出
 */
import { ref, computed } from 'vue';
import ChatToolDetail from './ChatToolDetail.vue';

const props = defineProps({
    tool: {
        type: Object,
        required: true,
    },
    defaultOpen: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['toggle']);

const isOpen = ref(props.defaultOpen);
const inputExpanded = ref(props.tool.inputExpanded ?? true);
const outputExpanded = ref(props.tool.outputExpanded ?? false);

const toolName = computed(() => {
    return props.tool.actionLabel || props.tool.displayName || props.tool.name || '工具调用';
});

const status = computed(() => props.tool.status || 'done');

const isRunning = computed(() => status.value === 'running');
const isError = computed(() => status.value === 'error' || status.value === 'failed');
const isDone = computed(() => !isRunning.value && !isError.value);

function toggleOpen() {
    isOpen.value = !isOpen.value;
    emit('toggle', isOpen.value);
}

function toggleInput() {
    inputExpanded.value = !inputExpanded.value;
}

function toggleOutput() {
    outputExpanded.value = !outputExpanded.value;
}

function isEmptyContent(content) {
    if (!content) return true;
    const trimmed = String(content).trim();
    return trimmed === '' || trimmed === '{}' || trimmed === '[]';
}
</script>

<template>
    <div class="chat-tool-item chat-tool-card">
        <button type="button" class="chat-tool-header" @click="toggleOpen">
            <!-- Running Status -->
            <span v-if="isRunning" class="chat-tool-icon chat-tool-icon-running">
                <span class="chat-tool-status-dot"></span>
            </span>
            <!-- Error Status -->
            <span
                v-else-if="isError"
                class="material-symbols-outlined chat-tool-icon chat-tool-icon-error"
            >
                error
            </span>
            <!-- Done Status -->
            <span v-else class="material-symbols-outlined chat-tool-icon chat-tool-icon-done">
                terminal
            </span>

            <!-- Tool Name -->
            <span class="chat-tool-info">{{ toolName }}</span>

            <!-- Running Text -->
            <span v-if="isRunning" class="chat-tool-status-text">执行中</span>

            <!-- Expand Icon -->
            <span
                class="material-symbols-outlined chat-tool-expand-icon"
                :class="{ expanded: isOpen }"
            >
                chevron_right
            </span>
        </button>

        <!-- Detail Content -->
        <div v-if="isOpen">
            <ChatToolDetail
                label="参数"
                :content="tool.inputText"
                :expanded="inputExpanded"
                :is-empty="isEmptyContent(tool.inputText)"
                @toggle="toggleInput"
            />
            <ChatToolDetail
                label="输出"
                :content="tool.response"
                :expanded="outputExpanded"
                :is-empty="isEmptyContent(tool.response)"
                :status="isRunning ? 'waiting' : 'done'"
                @toggle="toggleOutput"
            />
        </div>
    </div>
</template>
