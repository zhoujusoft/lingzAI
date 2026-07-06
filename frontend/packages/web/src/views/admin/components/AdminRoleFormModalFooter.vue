<script setup>
import { computed, ref } from 'vue';
import { createRole } from '@/api/roles';

const props = defineProps({
    context: {
        type: Object,
        default: () => ({}),
    },
    confirmText: {
        type: String,
        default: '确定',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

// 从 context 中获取值（仅用于新增角色）
const roleCode = computed(() => props.context.roleCode);
const roleName = computed(() => props.context.roleName);
const description = computed(() => props.context.description);
const enabled = computed(() => props.context.enabled);
const agentId = computed(() => props.context.agentId);
const menuPermissions = computed(() => props.context.menuPermissions);
const onUnauthorized = computed(() => props.context.onUnauthorized);

function normalizeText(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function validateForm() {
    const errors = {};

    if (!normalizeText(roleCode.value)) {
        errors.roleCode = '角色编码不能为空';
    }
    if (!normalizeText(roleName.value)) {
        errors.roleName = '角色名称不能为空';
    }

    props.context.formErrors = errors;
    if (Object.keys(errors).length > 0) {
        props.context.submitError = '';
        return false;
    }
    return true;
}

function normalizeMenuPermissions(value) {
    if (!Array.isArray(value)) {
        return [];
    }
    const normalized = [];
    const seen = new Set();
    for (const item of value) {
        const key = normalizeText(item);
        if (!key || seen.has(key)) {
            continue;
        }
        normalized.push(key);
        seen.add(key);
    }
    return normalized;
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

    const payload = {
        roleCode: normalizeText(roleCode.value),
        roleName: normalizeText(roleName.value),
        description: normalizeText(description.value) || null,
        enabled: enabled.value ?? 1,
        agentId: agentId.value,
        menuPermissions: normalizeMenuPermissions(menuPermissions.value),
    };

    try {
        await createRole(payload, onUnauthorized.value);
        emit('confirm', true);
    } catch (error) {
        props.context.submitError = error?.message || '保存失败';
    } finally {
        submitting.value = false;
    }
}

function handleCancel() {
    emit('cancel');
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
            @click="handleCancel"
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
