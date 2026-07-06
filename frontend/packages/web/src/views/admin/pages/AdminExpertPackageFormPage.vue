<script setup>
import AdminExpertPackageFormPanel from '@/views/admin/components/expert-package/AdminExpertPackageFormPanel.vue';

defineProps({
    mode: {
        type: String,
        default: 'create',
    },
    packageId: {
        type: Number,
        default: null,
    },
});
</script>

<template>
    <AdminExpertPackageFormPanel :mode="mode" :package-id="packageId" />
</template>
