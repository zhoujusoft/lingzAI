<script setup>
import { ref } from 'vue';

const props = defineProps({
    onConfirm: {
        type: Function,
        default: null,
    },
    onCancel: {
        type: Function,
        default: null,
    },
    loading: {
        type: Boolean,
        default: false,
    },
});

const handleConfirm = () => {
    if (props.onConfirm) {
        props.onConfirm();
    }
};

const handleCancel = () => {
    if (props.onCancel) {
        props.onCancel();
    }
};
</script>

<template>
    <div class="flex justify-end gap-3 border-t border-gray-200 bg-gray-50 px-6 py-4">
        <button
            type="button"
            :disabled="loading"
            class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            @click="handleCancel"
        >
            取消
        </button>
        <button
            type="button"
            :disabled="loading"
            class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            @click="handleConfirm"
        >
            {{ loading ? '保存中...' : '保存' }}
        </button>
    </div>
</template>
