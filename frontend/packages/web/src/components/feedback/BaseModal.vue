<script setup>
import { Dialog, DialogPanel, TransitionChild, TransitionRoot } from '@headlessui/vue';

defineProps({
    open: {
        type: Boolean,
        default: false,
    },
    panelClass: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['close', 'after-leave']);
</script>

<template>
    <Teleport to="body">
        <TransitionRoot appear :show="open" as="template" @after-leave="emit('after-leave')">
            <Dialog as="div" class="relative z-[999]" @close="emit('close')">
                <TransitionChild
                    as="template"
                    enter="ease-out duration-200"
                    enter-from="opacity-0"
                    enter-to="opacity-100"
                    leave="ease-in duration-150"
                    leave-from="opacity-100"
                    leave-to="opacity-0"
                >
                    <div class="fixed inset-0 bg-slate-900/50" />
                </TransitionChild>

                <div class="fixed inset-0 overflow-y-auto">
                    <div class="flex min-h-full items-center justify-center p-4">
                        <TransitionChild
                            as="template"
                            enter="ease-out duration-200"
                            enter-from="opacity-0 scale-95"
                            enter-to="opacity-100 scale-100"
                            leave="ease-in duration-150"
                            leave-from="opacity-100 scale-100"
                            leave-to="opacity-0 scale-95"
                        >
                            <DialogPanel
                                :class="[
                                    'flex w-full max-w-[480px] flex-col overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-200',
                                    panelClass,
                                ]"
                            >
                                <slot name="header" />
                                <slot name="content" />
                                <slot name="footer" />
                            </DialogPanel>
                        </TransitionChild>
                    </div>
                </div>
            </Dialog>
        </TransitionRoot>
    </Teleport>
</template>
