<template>
    <AdminMcpServerDetailPanel :server-id="serverId" />
</template>

<script setup>
import AdminMcpServerDetailPanel from '@/views/admin/components/mcp-management/AdminMcpServerDetailPanel.vue';

defineProps({
    serverId: {
        type: Number,
        default: null,
    },
});
</script>
