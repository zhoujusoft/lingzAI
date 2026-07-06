<template>
    <Transition
        enter-active-class="transition ease-out duration-300"
        enter-from-class="opacity-0 -translate-y-4"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition ease-in duration-200"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 -translate-y-4"
    >
        <div
            v-if="toastState.visible"
            class="fixed left-1/2 top-6 z-[120] -translate-x-1/2 flex items-center gap-2 rounded-xl px-4 py-3 shadow-lg"
            :class="toastClass"
        >
            <span class="material-symbols-outlined text-xl">{{ toastIcon }}</span>
            <span class="text-sm font-medium">{{ toastState.message }}</span>
        </div>
    </Transition>
</template>

<script setup>
import { computed } from 'vue';
import { useToast } from '@/composables/useToast';

const { toastState } = useToast();

const toastClass = computed(() => {
    switch (toastState.type) {
        case 'success':
            return 'bg-emerald-500 text-white';
        case 'error':
            return 'bg-rose-500 text-white';
        case 'warning':
            return 'bg-amber-500 text-white';
        default:
            return 'bg-emerald-500 text-white';
    }
});

const toastIcon = computed(() => {
    switch (toastState.type) {
        case 'success':
            return 'check_circle';
        case 'error':
            return 'error';
        case 'warning':
            return 'warning';
        default:
            return 'check_circle';
    }
});
</script>
