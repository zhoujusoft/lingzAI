<template>
    <AdminMcpServerFormPanel :mode="mode" :server-id="serverId" />
</template>

<script setup>
import AdminMcpServerFormPanel from '@/views/admin/components/mcp-management/AdminMcpServerFormPanel.vue';

defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    serverId: {
        type: Number,
        default: null,
    },
});
</script>
