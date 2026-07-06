<template>
    <FrontResourcesPanel @unauthorized="emit('unauthorized')" />
</template>

<script setup>
import FrontResourcesPanel from '@/views/front/components/FrontResourcesPanel.vue';

const emit = defineEmits(['unauthorized']);
</script>
