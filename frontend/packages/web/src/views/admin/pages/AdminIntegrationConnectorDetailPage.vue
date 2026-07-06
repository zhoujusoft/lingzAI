<template>
    <AdminIntegrationConnectorDetailPanel :connector-id="connectorId" />
</template>

<script setup>
import AdminIntegrationConnectorDetailPanel from '@/views/admin/components/integration/AdminIntegrationConnectorDetailPanel.vue';

defineProps({
    connectorId: {
        type: Number,
        default: null,
    },
});
</script>
