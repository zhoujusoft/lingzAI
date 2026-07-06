<template>
    <div
        class="chat-layout-container relative flex flex-1 overflow-hidden"
        :class="{ 'justify-center': shouldCenter }"
    >
        <div
            class="chat-workspace-wrapper flex min-w-0 flex-none flex-col overflow-hidden"
            :style="{ width: chatWidthStyle }"
        >
            <slot />
        </div>
    </div>
</template>

<script setup>
import { useChatLayoutWidth } from '@/composables/useChatLayoutWidth';

const props = defineProps({
    /**
     * 最小宽度（像素）
     */
    minWidth: {
        type: Number,
        default: 500,
    },
    /**
     * 最大宽度（像素）
     */
    maxWidth: {
        type: Number,
        default: 600,
    },
    /**
     * 是否启用响应式宽度
     */
    enableResponsive: {
        type: Boolean,
        default: true,
    },
    /**
     * 页面左右 padding 总和
     */
    pagePadding: {
        type: Number,
        default: 64,
    },
});

const { chatWidthStyle, shouldCenter } = useChatLayoutWidth({
    minWidth: props.minWidth,
    maxWidth: props.maxWidth,
    enableResponsive: props.enableResponsive,
    pagePadding: props.pagePadding,
});
</script>

<style scoped>
.chat-layout-container {
    background: transparent;
}

.chat-workspace-wrapper {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 1rem;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.08);
}
</style>
