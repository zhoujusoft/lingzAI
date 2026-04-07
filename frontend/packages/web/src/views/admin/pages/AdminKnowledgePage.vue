<template>
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
        <AdminKnowledgePanel
            v-if="!selectedKnowledge && knowledgePageMode === 'detail'"
            @create-knowledge="openCreateKnowledge"
            @open-knowledge="openKnowledgeDetail"
            @open-edit-knowledge="openKnowledgeDetail"
            @open-recall-test="openKnowledgeRecallTest"
        />
        <AdminKnowledgeDetailPanel
            v-else-if="knowledgePageMode === 'detail' && selectedKnowledge"
            :knowledge="selectedKnowledge"
            @back="backToKnowledgeList"
            @edit-knowledge="openKnowledgeEdit"
            @open-document="openKnowledgeDocumentDetail"
            @upload-document="handleKnowledgeUploadSuccess"
            @process-document="handleKnowledgeProcessRequest"
        />
        <AdminKnowledgeDocumentDetailPanel
            v-else-if="knowledgePageMode === 'document-detail'"
            :knowledge="selectedKnowledge"
            :document="selectedDocument"
            @back="backToKnowledgeDetail"
        />
        <AdminKnowledgeRecallTestPanel
            v-else-if="knowledgePageMode === 'recall-test'"
            :knowledge="selectedKnowledge"
            @back="backToKnowledgeList"
        />
        <AdminKnowledgeUploadPanel
            v-else-if="knowledgePageMode === 'upload'"
            :knowledge="selectedKnowledge"
            @back="backToKnowledgeDetail"
            @next-step="handleKnowledgeUploadSuccess"
        />
        <AdminKnowledgeSegmentPanel
            v-else-if="knowledgePageMode === 'segment'"
            :knowledge="selectedKnowledge"
            @back="cancelKnowledgeSegment"
            @next-step="handleKnowledgeProcessSubmitted"
        />
        <AdminKnowledgeCompletePanel
            v-else-if="knowledgePageMode === 'complete'"
            :knowledge="selectedKnowledge"
            @back="backToKnowledgeSegment"
            @go-document="goKnowledgeDocument"
        />
        <div
            v-else
            class="flex flex-1 items-center justify-center text-sm text-slate-400"
        >
            暂无可展示内容
        </div>
    </div>
</template>

<script setup>
import { ref } from 'vue';
import AdminKnowledgePanel from '@/views/admin/components/AdminKnowledgePanel.vue';
import AdminKnowledgeDetailPanel from '@/views/admin/components/AdminKnowledgeDetailPanel.vue';
import AdminKnowledgeDocumentDetailPanel from '@/views/admin/components/AdminKnowledgeDocumentDetailPanel.vue';
import AdminKnowledgeRecallTestPanel from '@/views/admin/components/AdminKnowledgeRecallTestPanel.vue';
import AdminKnowledgeSegmentPanel from '@/views/admin/components/knowledge-steps/AdminKnowledgeSegmentPanel.vue';
import AdminKnowledgeUploadPanel from '@/views/admin/components/knowledge-steps/AdminKnowledgeUploadPanel.vue';
import AdminKnowledgeCompletePanel from '@/views/admin/components/knowledge-steps/AdminKnowledgeCompletePanel.vue';

const selectedKnowledge = ref(null);
const selectedDocument = ref(null);
const knowledgePageMode = ref('detail');
const isCreatingKnowledge = ref(false);

function openKnowledgeDetail(item) {
    selectedKnowledge.value = item;
    selectedDocument.value = null;
    knowledgePageMode.value = 'detail';
    isCreatingKnowledge.value = false;
}

function openKnowledgeEdit(item) {
    if (item) {
        selectedKnowledge.value = item;
    }
    knowledgePageMode.value = 'upload';
    isCreatingKnowledge.value = false;
}

function openKnowledgeRecallTest(item) {
    selectedKnowledge.value = item;
    selectedDocument.value = null;
    knowledgePageMode.value = 'recall-test';
    isCreatingKnowledge.value = false;
}

function openCreateKnowledge() {
    selectedKnowledge.value = {
        name: '',
    };
    selectedDocument.value = null;
    knowledgePageMode.value = 'upload';
    isCreatingKnowledge.value = true;
}

function backToKnowledgeDetail() {
    if (isCreatingKnowledge.value) {
        selectedKnowledge.value = null;
        selectedDocument.value = null;
        knowledgePageMode.value = 'detail';
        return;
    }
    selectedDocument.value = null;
    knowledgePageMode.value = 'detail';
}

function openKnowledgeDocumentDetail(document) {
    selectedDocument.value = document;
    selectedKnowledge.value = {
        ...selectedKnowledge.value,
        parentId: document?.parentId ?? selectedKnowledge.value?.parentId ?? null,
    };
    knowledgePageMode.value = 'document-detail';
}

function cancelKnowledgeSegment() {
    knowledgePageMode.value = 'detail';
    isCreatingKnowledge.value = false;
}

function backToKnowledgeSegment() {
    knowledgePageMode.value = 'segment';
}

function goKnowledgeDocument() {
    knowledgePageMode.value = 'detail';
}

function handleKnowledgeUploadSuccess(knowledge) {
    if (knowledge) {
        selectedKnowledge.value = knowledge;
    }
    if (knowledge?.document?.docId) {
        knowledgePageMode.value = 'segment';
        return;
    }
    knowledgePageMode.value = 'detail';
    isCreatingKnowledge.value = false;
}

function handleKnowledgeProcessSubmitted(knowledge) {
    if (knowledge) {
        selectedKnowledge.value = knowledge;
    }
    knowledgePageMode.value = 'detail';
    isCreatingKnowledge.value = false;
}

function handleKnowledgeProcessRequest(knowledge) {
    if (knowledge) {
        selectedKnowledge.value = knowledge;
    }
    knowledgePageMode.value = 'segment';
}

function backToKnowledgeList() {
    selectedKnowledge.value = null;
    selectedDocument.value = null;
    knowledgePageMode.value = 'detail';
    isCreatingKnowledge.value = false;
}
</script>
