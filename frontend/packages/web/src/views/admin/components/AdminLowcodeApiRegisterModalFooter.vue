<script setup>
import { ref } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '确认注册',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

function validateForm() {
    const errors = {};
    const normalizedToolDisplayName = String(props.context.toolDisplayName || '').trim();
    if (!normalizedToolDisplayName) {
        errors.toolDisplayName = '工具名称不能为空';
    }
    props.context.formErrors = errors;
    if (Object.keys(errors).length > 0) {
        props.context.submitError = '';
        return null;
    }
    return normalizedToolDisplayName;
}

function handleConfirm() {
    if (submitting.value) {
        return;
    }
    const normalizedToolDisplayName = validateForm();
    if (!normalizedToolDisplayName) {
        return;
    }
    submitting.value = true;
    props.context.toolDisplayName = normalizedToolDisplayName;
    props.context.toolRemark = String(props.context.toolRemark || '').trim();
    emit('confirm', {
        toolDisplayName: normalizedToolDisplayName,
        toolRemark: props.context.toolRemark,
    });
}
</script>

<template>
    <div
        class="flex items-center justify-end gap-3 border-t border-slate-100 bg-[#f8fafc]/70 px-8 py-5"
    >
        <button
            type="button"
            class="rounded-xl px-6 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100"
            @click="emit('cancel')"
        >
            {{ cancelText }}
        </button>
        <button
            type="button"
            class="rounded-xl bg-sky-600 px-8 py-2.5 text-sm font-medium text-white shadow-lg shadow-sky-500/25 transition-all hover:bg-sky-700 active:scale-95"
            @click="handleConfirm"
        >
            {{ confirmText }}
        </button>
    </div>
</template>
