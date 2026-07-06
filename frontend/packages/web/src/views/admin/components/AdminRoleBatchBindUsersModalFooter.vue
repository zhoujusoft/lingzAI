<script setup>
import { ref } from 'vue';
import { batchBindRoleUsers } from '@/api/roles';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '批量绑定',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

async function handleSubmit() {
    if (submitting.value) {
        return;
    }
    const roleId = props.context.roleId;
    const userIds = Array.isArray(props.context.selectedUserIds)
        ? props.context.selectedUserIds
        : [];
    if (!roleId) {
        props.context.submitError = '角色ID不能为空';
        return;
    }
    if (userIds.length === 0) {
        props.context.submitError = '请先选择用户';
        return;
    }

    submitting.value = true;
    props.context.submitError = '';
    try {
        await batchBindRoleUsers(
            roleId,
            { userIds },
            typeof props.context.onUnauthorized === 'function'
                ? props.context.onUnauthorized
                : undefined
        );
        emit('confirm', true);
    } catch (error) {
        props.context.submitError = error?.message || '批量绑定失败';
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
