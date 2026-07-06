<script setup>
import { ref } from 'vue';
import { updateUserProfile } from '@/api/users';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '保存',
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

function isValidMobile(mobile) {
    return /^1\d{10}$/.test(mobile);
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function validateForm() {
    const errors = {};
    if (!normalizeText(props.context.name)) {
        errors.name = '姓名不能为空';
    }
    const mobile = normalizeText(props.context.mobile);
    const email = normalizeText(props.context.email);
    if (mobile && !isValidMobile(mobile)) {
        errors.mobile = '手机号格式不正确，请输入11位手机号';
    }
    if (email && !isValidEmail(email)) {
        errors.email = '邮箱格式不正确';
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
        await updateUserProfile(
            {
                id: props.context.id,
                name: normalizeText(props.context.name),
                mobile: normalizeText(props.context.mobile),
                email: normalizeText(props.context.email),
                userType: props.context.userType,
                roleId: props.context.roleId,
            },
            typeof props.context.onUnauthorized === 'function'
                ? props.context.onUnauthorized
                : undefined
        );
        emit('confirm', true);
    } catch (error) {
        props.context.submitError = error?.message || '保存个人信息失败';
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
            {{ submitting ? '保存中...' : confirmText }}
        </button>
    </div>
</template>
