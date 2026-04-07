<template>
    <FrontChatPanel
        :key="panelKey"
        :initial-skill="initialSkill"
        @unauthorized="emitUnauthorized"
    />
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import FrontChatPanel from '@/views/front/components/FrontChatPanel.vue';

const emit = defineEmits(['unauthorized']);
const route = useRoute();

const initialSkill = computed(() => {
    const skillId = route.query.skillId;
    if (!skillId) {
        return null;
    }
    return {
        id: skillId,
        displayName: route.query.skillName || '',
        description: route.query.skillDescription || '',
    };
});

const panelKey = computed(() => `chat-${initialSkill.value?.id || 'general'}`);

function emitUnauthorized() {
    emit('unauthorized');
}
</script>
