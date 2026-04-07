<script setup>
import { DialogTitle } from '@headlessui/vue';

defineProps({
    title: {
        type: String,
        default: '',
    },
    showClose: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['close']);
</script>

<template>
    <div class="flex items-center justify-between border-b border-slate-100 px-6 py-5">
        <DialogTitle :class="showClose ? 'text-xl font-bold text-slate-800' : 'text-lg font-semibold text-slate-900'">
            {{ title }}
        </DialogTitle>
        <button
            v-if="showClose"
            type="button"
            class="text-slate-400 transition-colors hover:text-slate-600"
            @click="emit('close')"
        >
            <span class="material-symbols-outlined">close</span>
        </button>
    </div>
</template>
