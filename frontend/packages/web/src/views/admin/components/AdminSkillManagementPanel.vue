<script setup>
import { ref } from 'vue';
import AdminSkillCatalogDetailPanel from './skill-management/AdminSkillCatalogDetailPanel.vue';
import AdminSkillCatalogListPanel from './skill-management/AdminSkillCatalogListPanel.vue';

const selectedSkillId = ref(null);

function openSkillDetail(skill) {
    selectedSkillId.value = skill?.id ?? null;
}

function backToList() {
    selectedSkillId.value = null;
}
</script>

<template>
    <AdminSkillCatalogListPanel
        v-if="!selectedSkillId"
        @open-skill-detail="openSkillDetail"
    />
    <AdminSkillCatalogDetailPanel
        v-else
        :skill-id="selectedSkillId"
        @back="backToList"
    />
</template>
