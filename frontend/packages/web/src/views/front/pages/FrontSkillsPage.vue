<template>
    <FrontSkillsPanel
        @unauthorized="emitUnauthorized"
        @open-skill-chat="handleOpenSkillChat"
    />
</template>

<script setup>
import { useRouter } from 'vue-router';
import FrontSkillsPanel from '@/views/front/components/FrontSkillsPanel.vue';
import { ROUTE_PATHS } from '@/router/routePaths';

const emit = defineEmits(['unauthorized']);
const router = useRouter();

function emitUnauthorized() {
    emit('unauthorized');
}

function handleOpenSkillChat(skill) {
    if (!skill?.id) {
        router.push({ path: ROUTE_PATHS.frontChat });
        return;
    }
    router.push({
        path: ROUTE_PATHS.frontChat,
        query: {
            skillId: skill.id,
            skillName: skill.displayName || '',
            skillDescription: skill.description || '',
        },
    });
}
</script>
