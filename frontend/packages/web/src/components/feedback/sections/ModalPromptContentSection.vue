<script setup>
defineProps({
    placeholder: {
        type: String,
        default: '请输入',
    },
    context: {
        type: Object,
        default: () => ({ value: '' }),
    },
});
</script>

<template>
    <div class="px-6 py-6">
        <input
            v-model="context.value"
            type="text"
            :placeholder="placeholder"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        >
    </div>
</template>
