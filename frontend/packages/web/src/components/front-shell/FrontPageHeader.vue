<script setup>
defineProps({
    title: {
        type: String,
        default: '',
    },
    description: {
        type: String,
        default: '',
    },
});
</script>

<template>
    <header class="mb-4 flex items-start justify-between gap-4 lg:mb-5">
        <div class="min-w-0">
            <h1 class="text-[26px] font-semibold tracking-tight text-strong lg:text-[30px]">
                {{ title }}
            </h1>
            <p v-if="description" class="mt-2 max-w-3xl text-sm leading-6 text-body">
                {{ description }}
            </p>
        </div>
    </header>
</template>
