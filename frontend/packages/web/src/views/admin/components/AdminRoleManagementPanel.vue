<script setup>
import { ref } from 'vue';
import AdminRoleListPanel from './role-management/AdminRoleListPanel.vue';
import AdminRoleCreatePanel from './role-management/AdminRoleCreatePanel.vue';
import AdminRoleDetailPanel from './role-management/AdminRoleDetailPanel.vue';
import AdminRoleResourcePermissionPanel from './role-management/AdminRoleResourcePermissionPanel.vue';

const creatingRole = ref(false);
const selectedRoleId = ref(null);
const selectedResourceRoleId = ref(null);

function openRoleCreate() {
    creatingRole.value = true;
    selectedRoleId.value = null;
    selectedResourceRoleId.value = null;
}

function openRoleDetail(row) {
    selectedRoleId.value = row?.id ?? null;
    selectedResourceRoleId.value = null;
    creatingRole.value = false;
}

function openRoleResources(row) {
    selectedResourceRoleId.value = row?.id ?? null;
    selectedRoleId.value = null;
    creatingRole.value = false;
}

function backToList() {
    creatingRole.value = false;
    selectedRoleId.value = null;
    selectedResourceRoleId.value = null;
}

function handleSaved() {
    // Stay on detail page; user can click back manually
}
</script>

<template>
    <AdminRoleListPanel
        v-if="!creatingRole && !selectedRoleId && !selectedResourceRoleId"
        @open-role-create="openRoleCreate"
        @open-role-detail="openRoleDetail"
        @open-role-resources="openRoleResources"
    />
    <AdminRoleCreatePanel v-else-if="creatingRole" @back="backToList" @saved="backToList" />
    <AdminRoleDetailPanel
        v-else-if="selectedRoleId"
        :role-id="selectedRoleId"
        @back="backToList"
        @saved="handleSaved"
    />
    <AdminRoleResourcePermissionPanel
        v-else
        :role-id="selectedResourceRoleId"
        @back="backToList"
        @saved="handleSaved"
    />
</template>
