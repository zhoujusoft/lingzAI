<script setup>
import { ref } from 'vue';
import { updateUserTokenQuota } from '@/api/users';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '确认修改',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

function normalizeNonNegativeInteger(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return 0;
    }
    return Math.max(0, Math.trunc(number));
}

function validateForm() {
    const errors = {};
    const remainingTokens = normalizeNonNegativeInteger(props.context.remainingTokens);
    if (remainingTokens < 0) {
        errors.remainingTokens = '剩余额度不能小于 0';
    }
    props.context.formErrors = errors;
    if (Object.keys(errors).length > 0) {
        props.context.submitError = '';
        return false;
    }
    props.context.remainingTokens = remainingTokens;
    return true;
}

async function handleSubmit() {
    if (submitting.value) {
        return;
    }
    if (!validateForm()) {
        return;
    }
    submitting.value = true;
    props.context.submitError = '';
    try {
        await updateUserTokenQuota(
            {
                userId: props.context.id,
                remainingTokens: props.context.remainingTokens,
                unlimited: Boolean(props.context.unlimited),
            },
            typeof props.context.onUnauthorized === 'function'
                ? props.context.onUnauthorized
                : undefined
        );
        emit('confirm', true);
    } catch (error) {
        props.context.submitError = error?.message || '修改额度失败';
    } finally {
        submitting.value = false;
    }
}
</script>

<template>
    <div
        class="flex items-center justify-end gap-3 border-t border-slate-100 bg-[#f8fafc]/50 px-8 py-5"
    >
        <button
            type="button"
            class="rounded-xl px-6 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="submitting"
            @click="emit('cancel')"
        >
            {{ cancelText }}
        </button>
        <button
            type="button"
            class="rounded-xl bg-primary px-8 py-2.5 text-sm font-medium text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 active:scale-95 disabled:cursor-not-allowed disabled:opacity-75"
            :disabled="submitting"
            @click="handleSubmit"
        >
            {{ submitting ? '提交中...' : confirmText }}
        </button>
    </div>
</template>
