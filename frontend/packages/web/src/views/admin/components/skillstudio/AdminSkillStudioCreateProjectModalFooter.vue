<script setup>
import { ref } from 'vue';
import { createSkillStudioProject } from '@/api/skillstudio';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
    confirmText: {
        type: String,
        default: '确认',
    },
    cancelText: {
        type: String,
        default: '取消',
    },
});

const emit = defineEmits(['confirm', 'cancel']);
const submitting = ref(false);

function normalizeText(value) {
    return String(value || '').trim();
}

async function handleConfirm() {
    const description = normalizeText(props.context.description);
    props.context.formError = '';
    props.context.submitError = '';
    if (!description) {
        props.context.formError = '请输入技能描述';
        return;
    }

    submitting.value = true;
    try {
        const data = await createSkillStudioProject(
            {
                description,
            },
            typeof props.context.onUnauthorized === 'function'
                ? props.context.onUnauthorized
                : undefined
        );
        emit('confirm', data);
    } catch (error) {
        props.context.submitError = error?.message || '创建项目失败';
    } finally {
        submitting.value = false;
    }
}
</script>

<template>
    <div class="flex items-center justify-end gap-3 border-t border-slate-100 px-6 py-4">
        <button
            class="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-500 transition-all hover:border-slate-300 hover:bg-slate-50"
            type="button"
            :disabled="submitting"
            @click="emit('cancel')"
        >
            {{ cancelText }}
        </button>
        <button
            class="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white shadow-sm transition-all hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
            type="button"
            :disabled="submitting"
            @click="handleConfirm"
        >
            {{ submitting ? '创建中...' : confirmText }}
        </button>
    </div>
</template>
