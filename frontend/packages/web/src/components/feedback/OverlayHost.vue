<script setup>
import { computed } from 'vue';
import BaseModal from './BaseModal.vue';
import { finalizeOverlayById, overlayState, resolveOverlayById } from '@/composables/useModal.js';

const overlayStack = computed(() => overlayState.stack);

function handleConfirm(requestId, value) {
    resolveOverlayById(requestId, value);
}

function handleCancel(requestId) {
    resolveOverlayById(requestId, false);
}

function handleAfterLeave(requestId) {
    finalizeOverlayById(requestId);
}
</script>

<template>
    <BaseModal
        v-for="(request, index) in overlayStack"
        :key="request.id"
        :open="request.open"
        :closable="index === overlayStack.length - 1"
        :constrained-height="request.modal?.constrainedHeight"
        :panel-class="request.modal?.panelClass || ''"
        :z-index="1000 + index * 20"
        @close="handleCancel(request.id)"
        @after-leave="handleAfterLeave(request.id)"
    >
        <template #header>
            <div v-if="request.modal?.header?.component" class="shrink-0">
                <component
                    :is="request.modal.header.component"
                    v-bind="request.modal.header.props"
                    :context="request.modal.context"
                    @close="handleCancel(request.id)"
                />
            </div>
        </template>

        <template #content>
            <div
                v-if="request.modal?.content?.component"
                :class="request.modal?.contentScrollable ? 'min-h-0 flex-1 overflow-y-auto' : ''"
            >
                <component
                    :is="request.modal.content.component"
                    v-bind="request.modal.content.props"
                    :context="request.modal.context"
                />
            </div>
        </template>

        <template #footer>
            <div v-if="request.modal?.footer?.component" class="shrink-0">
                <component
                    :is="request.modal.footer.component"
                    v-bind="request.modal.footer.props"
                    :context="request.modal.context"
                    @confirm="handleConfirm(request.id, $event)"
                    @cancel="handleCancel(request.id)"
                />
            </div>
        </template>
    </BaseModal>
</template>
