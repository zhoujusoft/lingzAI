<script setup>
import { computed } from 'vue';
import ChatFrontendRenderCard from './ChatFrontendRenderCard.vue';
import ChatRankingListCard from './ChatRankingListCard.vue';

const emit = defineEmits(['action']);

const props = defineProps({
    payload: {
        type: Object,
        default: () => ({}),
    },
});

const componentRegistry = {
    StructuredFormCard: ChatFrontendRenderCard,
    RankingListCard: ChatRankingListCard,
};

const currentComponent = computed(() => {
    const code = String(props.payload?.componentCode || props.payload?.componentType || '').trim();
    return componentRegistry[code] || ChatFrontendRenderCard;
});

function handleAction(payload) {
    emit('action', payload);
}
</script>

<template>
    <component :is="currentComponent" :payload="payload" @action="handleAction" />
</template>
