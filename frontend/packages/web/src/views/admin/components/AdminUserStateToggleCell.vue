<script setup>
import { computed } from 'vue';
import { resolveUserStateLabel, resolveUserStateStyle, USER_STATES } from '@/model/enums/user-state';

const props = defineProps({
    row: {
        type: Object,
        default: () => ({}),
    },
    disabled: {
        type: Boolean,
        default: false,
    },
    onToggle: {
        type: Function,
        default: null,
    },
});

const normalizedState = computed(() => {
    const state = Number(props.row?.state);
    return Number.isNaN(state) ? USER_STATES.UNSPECIFIED : state;
});

const label = computed(() => resolveUserStateLabel(normalizedState.value));
const style = computed(() => resolveUserStateStyle(normalizedState.value));
const buttonTitle = computed(() => {
    if (props.disabled) {
        return '';
    }
    return normalizedState.value === USER_STATES.ACTIVE ? '点击禁用账号' : '点击启用账号';
});

function handleClick() {
    if (props.disabled || typeof props.onToggle !== 'function') {
        return;
    }
    props.onToggle(props.row);
}
</script>

<template>
    <div class="flex justify-center">
        <button
            type="button"
            :title="buttonTitle"
            :disabled="disabled"
            :class="[
                'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-medium transition-colors',
                disabled
                    ? 'cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400'
                    : 'border-transparent bg-white text-slate-700 hover:border-slate-200 hover:bg-slate-50',
            ]"
            @click="handleClick"
        >
            <span :class="['h-1.5 w-1.5 rounded-full', style.dotClass]" />
            <span :class="[style.badgeClass, 'rounded-full px-2 py-0.5 text-xs font-medium']">
                {{ label }}
            </span>
        </button>
    </div>
</template>
