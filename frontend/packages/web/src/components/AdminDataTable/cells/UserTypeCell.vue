<script setup>
import { computed } from 'vue';
import { resolveUserTypeLabel, USER_TYPES } from '@/model/enums/user-type';

const props = defineProps({
    value: {
        type: [String, Number],
        default: null,
    },
    adminValue: {
        type: [String, Number],
        default: USER_TYPES.ADMIN,
    },
    adminClass: {
        type: String,
        default: 'rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-600',
    },
    defaultClass: {
        type: String,
        default: 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600',
    },
});

const normalizedValue = computed(() => {
    const nextValue = Number(props.value);
    return Number.isNaN(nextValue) ? props.value : nextValue;
});

const isAdmin = computed(() => normalizedValue.value === Number(props.adminValue));

const displayLabel = computed(() => {
    if (typeof normalizedValue.value === 'number') {
        return resolveUserTypeLabel(normalizedValue.value, String(props.value ?? ''));
    }
    return String(props.value ?? '');
});
</script>

<template>
    <span :class="isAdmin ? adminClass : defaultClass">{{ displayLabel }}</span>
</template>
