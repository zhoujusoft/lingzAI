<template>
    <FrontIntelligentChatPanel @unauthorized="emitUnauthorized" />
</template>

<script setup>
import FrontIntelligentChatPanel from '@/views/front/components/FrontIntelligentChatPanel.vue';

const emit = defineEmits(['unauthorized']);

function emitUnauthorized() {
    emit('unauthorized');
}
</script>
