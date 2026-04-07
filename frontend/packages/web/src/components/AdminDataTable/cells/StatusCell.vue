<script setup>
import { computed } from 'vue';
import { resolveUserStateLabel, resolveUserStateStyle } from '@/model/enums/user-state';

const props = defineProps({
    value: {
        type: [Number, String],
        default: -1,
    },
});

const normalizedState = computed(() => {
    const state = Number(props.value);
    return Number.isNaN(state) ? -1 : state;
});

const label = computed(() => resolveUserStateLabel(normalizedState.value));
const style = computed(() => resolveUserStateStyle(normalizedState.value));
</script>

<template>
    <div class="flex items-center gap-1.5">
        <span :class="['h-1.5 w-1.5 rounded-full', style.dotClass]" />
        <span
            :class="[
                'rounded px-2 py-0.5 text-sm font-medium',
                style.badgeClass,
            ]"
        >
            {{ label }}
        </span>
    </div>
</template>
