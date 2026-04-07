<template>
    <AdminIntegrationDatasetFormPanel :mode="mode" :dataset-id="datasetId" />
</template>

<script setup>
import AdminIntegrationDatasetFormPanel from '@/views/admin/components/integration/AdminIntegrationDatasetFormPanel.vue';

defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    datasetId: {
        type: Number,
        default: null,
    },
});
</script>
