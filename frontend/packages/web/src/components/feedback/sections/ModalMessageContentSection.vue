<script setup>
defineProps({
    message: {
        type: String,
        default: '',
    },
});
</script>

<template>
    <div class="px-6 py-6">
        <p class="whitespace-pre-line text-sm leading-6 text-slate-600">{{ message }}</p>
    </div>
</template>
