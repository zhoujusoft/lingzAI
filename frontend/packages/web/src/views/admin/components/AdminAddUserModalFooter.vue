<script setup>
import { ref } from 'vue';
import { createUser, updateUserProfile } from '@/api/users';
import { encryptPassword } from '@/utils/password-encrypt';
import { validatePasswordPolicy } from '@/utils/password-policy';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '确定',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
    mode: {
        type: String,
        default: 'create',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

function normalizeText(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function validateForm() {
    const errors = {};
    const isCreateMode = props.mode === 'create';
    const nextPassword = normalizeText(props.context.password);

    if (!normalizeText(props.context.name)) {
        errors.name = '姓名不能为空';
    }
    if (isCreateMode && !normalizeText(props.context.account)) {
        errors.account = '登录名不能为空';
    }
    if (isCreateMode) {
        const passwordError = validatePasswordPolicy(nextPassword);
        if (passwordError) {
            errors.password = passwordError;
        }
    }
    props.context.formErrors = errors;
    if (Object.keys(errors).length > 0) {
        props.context.submitError = '';
        return false;
    }
    return true;
}

async function submitCreateUser() {
    if (submitting.value) {
        return;
    }
    if (!validateForm()) {
        return;
    }

    submitting.value = true;
    props.context.submitError = '';
    try {
        const mode = props.mode;
        if (mode === 'create') {
            const encryptedPassword = await encryptPassword(normalizeText(props.context.password));
            await createUser(
                {
                    name: normalizeText(props.context.name),
                    account: normalizeText(props.context.account),
                    password: encryptedPassword,
                    mobile: normalizeText(props.context.mobile),
                    email: normalizeText(props.context.email),
                    userType: props.context.userType,
                },
                typeof props.context.onUnauthorized === 'function'
                    ? props.context.onUnauthorized
                    : undefined,
            );
        } else if (mode === 'edit-profile') {
            await updateUserProfile(
                {
                    id: props.context.id,
                    name: normalizeText(props.context.name),
                    mobile: normalizeText(props.context.mobile),
                    email: normalizeText(props.context.email),
                    userType: props.context.userType,
                },
                typeof props.context.onUnauthorized === 'function'
                    ? props.context.onUnauthorized
                    : undefined,
            );
        }
        emit('confirm', true);
    } catch (error) {
        props.context.submitError =
            error?.message || (props.mode === 'create' ? '新增用户失败' : '保存用户资料失败');
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
            @click="submitCreateUser"
        >
            {{ submitting ? '提交中...' : confirmText }}
        </button>
    </div>
</template>
