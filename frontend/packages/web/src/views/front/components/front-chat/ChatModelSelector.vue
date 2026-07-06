<template>
    <div class="chat-model-selector shrink-0" :style="{ width }">
        <AppSelect
            :model-value="modelValue"
            :options="options"
            size="sm"
            menu-placement="top"
            :disabled="disabled || loading"
            :placeholder="placeholder"
            :button-class="computedButtonClass"
            @update:model-value="$emit('update:modelValue', $event)"
        />
    </div>
</template>

<script setup>
import { computed } from 'vue';
import AppSelect from '@/components/AppSelect.vue';

const props = defineProps({
    /** 当前选中的模型 ID */
    modelValue: {
        type: [String, Number, null],
        default: null,
    },
    /** 模型选项列表 */
    options: {
        type: Array,
        default: () => [],
    },
    /** 是否正在加载模型列表 */
    loading: {
        type: Boolean,
        default: false,
    },
    /** 是否禁用选择器 */
    disabled: {
        type: Boolean,
        default: false,
    },
    /** 当前选中的模型是否不可用 */
    unavailable: {
        type: Boolean,
        default: false,
    },
    /** 选择器宽度 */
    width: {
        type: String,
        default: '160px',
    },
    /** 占位文本 */
    placeholder: {
        type: String,
        default: '选择模型',
    },
});

defineEmits(['update:modelValue']);

const computedButtonClass = computed(() => {
    const baseClass = 'h-8 min-h-0 rounded-lg px-2.5 pr-9 text-xs';
    if (props.unavailable) {
        return `border-amber-300 bg-amber-50 text-amber-700 ${baseClass}`;
    }
    return baseClass;
});
</script>

<style scoped>
.chat-model-selector {
    display: block;
}
</style>
