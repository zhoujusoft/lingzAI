<script setup>
import { computed, shallowRef, watch } from 'vue';
import BaseModal from './BaseModal.vue';
import { overlayState, resolveActiveOverlay } from '@/composables/useModal.js';

const activeModal = computed(() => overlayState.active.modal || null);
const renderedModal = shallowRef(null);

watch(
    activeModal,
    modal => {
        if (modal) {
            renderedModal.value = modal;
        }
    },
    { immediate: true }
);

function handleConfirm(value) {
    resolveActiveOverlay(value);
}

function handleCancel() {
    resolveActiveOverlay(false);
}

function handleAfterLeave() {
    renderedModal.value = null;
}
</script>

<template>
    <BaseModal
        v-if="renderedModal"
        :open="overlayState.active.open"
        :panel-class="renderedModal?.panelClass || ''"
        @close="handleCancel"
        @after-leave="handleAfterLeave"
    >
        <template #header>
            <component
                :is="renderedModal?.header?.component"
                v-if="renderedModal?.header?.component"
                v-bind="renderedModal.header.props"
                :context="renderedModal.context"
                @close="handleCancel"
            />
        </template>

        <template #content>
            <component
                :is="renderedModal?.content?.component"
                v-if="renderedModal?.content?.component"
                v-bind="renderedModal.content.props"
                :context="renderedModal.context"
            />
        </template>

        <template #footer>
            <component
                :is="renderedModal?.footer?.component"
                v-if="renderedModal?.footer?.component"
                v-bind="renderedModal.footer.props"
                :context="renderedModal.context"
                @confirm="handleConfirm"
                @cancel="handleCancel"
            />
        </template>
    </BaseModal>
</template>
