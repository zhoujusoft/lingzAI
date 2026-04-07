<template>
    <div class="shrink-0 border-t border-slate-200 bg-white px-5 py-4">
        <form
            class="relative mx-auto max-w-4xl rounded-xl border border-slate-200 bg-slate-50 p-3 shadow-sm transition focus-within:border-primary focus-within:ring-1 focus-within:ring-primary/20"
            @submit.prevent="$emit('submit')"
        >
            <div
                v-if="showActions"
                class="mb-2 flex items-center gap-3 border-b border-slate-100 pb-2"
            >
                <button
                    type="button"
                    class="text-slate-400 transition-colors hover:text-primary"
                    @click="$emit('trigger-file-picker')"
                >
                    <span class="material-symbols-outlined text-xl">attachment</span>
                </button>
            </div>
            <div v-if="showActions && pendingFiles.length" class="mb-2 flex flex-wrap gap-2">
                <div
                    v-for="file in pendingFiles"
                    :key="file.id"
                    class="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-1 text-xs text-slate-600"
                >
                    <span class="material-symbols-outlined text-sm">description</span>
                    <span>{{ file.name }}</span>
                    <button
                        type="button"
                        class="text-slate-500"
                        @click="$emit('remove-pending-file', file.id)"
                    >
                        ×
                    </button>
                </div>
            </div>
            <div class="flex items-end gap-2">
                <textarea
                    :value="draft"
                    :placeholder="placeholder"
                    rows="2"
                    class="flex-1 resize-none border-none bg-transparent p-0 text-sm placeholder-slate-400 focus:outline-none focus:ring-0"
                    :disabled="sending"
                    @input="$emit('update:draft', $event.target.value)"
                    @keydown.enter="handleEnter"
                ></textarea>
                <button
                    type="submit"
                    class="flex items-center justify-center rounded-xl bg-primary p-2.5 text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    :disabled="sending"
                >
                    <span class="material-symbols-outlined">send</span>
                </button>
            </div>
        </form>
        <div class="mx-auto mt-2 flex max-w-4xl items-center justify-between px-1">
            <span class="text-[10px] text-slate-400">{{ statusText }}</span>
            <div class="flex items-center gap-1">
                <span class="h-1.5 w-1.5 rounded-full bg-green-500"></span>
                <span class="text-[10px] font-bold uppercase tracking-widest text-slate-400"
                    >{{ stateLabel }}</span
                >
            </div>
        </div>
        <p v-if="chatError" class="mx-auto mt-1 max-w-4xl text-xs text-red-600">
            {{ chatError }}
        </p>
    </div>
</template>

<script>
export default {
    name: 'ChatComposer',
    emits: ['submit', 'trigger-file-picker', 'remove-pending-file', 'update:draft'],
    props: {
        draft: {
            type: String,
            default: '',
        },
        sending: {
            type: Boolean,
            default: false,
        },
        pendingFiles: {
            type: Array,
            default: () => [],
        },
        showActions: {
            type: Boolean,
            default: true,
        },
        placeholder: {
            type: String,
            default: '',
        },
        statusText: {
            type: String,
            default: '',
        },
        chatError: {
            type: String,
            default: '',
        },
        stateLabel: {
            type: String,
            default: 'Ready',
        },
    },
    methods: {
        handleEnter(event) {
            if (event.shiftKey) {
                return;
            }
            event.preventDefault();
            this.$emit('submit');
        },
    },
};
</script>
