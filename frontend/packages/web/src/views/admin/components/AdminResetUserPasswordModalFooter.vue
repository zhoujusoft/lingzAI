<script setup>
import { ref } from 'vue';
import { resetUserPassword } from '@/api/users';
import { encryptPassword } from '@/utils/password-encrypt';
import { validatePasswordPolicy } from '@/utils/password-policy';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '确认重置',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

function normalizeText(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function validateForm() {
    const errors = {};
    const password = normalizeText(props.context.password);
    const confirmPassword = normalizeText(props.context.confirmPassword);

    const passwordError = validatePasswordPolicy(password);
    if (passwordError) {
        errors.password = passwordError;
    }
    if (!confirmPassword) {
        errors.confirmPassword = '请再次输入新密码';
    } else if (password !== confirmPassword) {
        errors.confirmPassword = '两次输入的密码不一致';
    }

    props.context.formErrors = errors;
    if (Object.keys(errors).length > 0) {
        props.context.submitError = '';
        return false;
    }
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
        const encryptedPassword = await encryptPassword(normalizeText(props.context.password));
        await resetUserPassword(
            {
                id: props.context.id,
                password: encryptedPassword,
            },
            typeof props.context.onUnauthorized === 'function'
                ? props.context.onUnauthorized
                : undefined,
        );
        emit('confirm', true);
    } catch (error) {
        props.context.submitError = error?.message || '重置密码失败';
    } finally {
        submitting.value = false;
    }
}
</script>

<template>
    <div class="flex items-center justify-end gap-3 border-t border-slate-100 bg-[#f8fafc]/50 px-8 py-5">
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
