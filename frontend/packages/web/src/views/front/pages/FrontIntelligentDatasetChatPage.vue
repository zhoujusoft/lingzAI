<template>
    <FrontIntelligentDatasetChatPanel @unauthorized="emitUnauthorized" />
</template>

<script setup>
import FrontIntelligentDatasetChatPanel from '@/views/front/components/FrontIntelligentDatasetChatPanel.vue';

const emit = defineEmits(['unauthorized']);

function emitUnauthorized() {
    emit('unauthorized');
}
</script>
