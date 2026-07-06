<template>
    <header
        :class="[
            'z-10 flex w-full shrink-0 items-center justify-between h-auto bg-transparent px-2 pb-3 pt-4 sm:px-3 lg:px-4',
        ]"
    >
        <div class="flex min-w-0 flex-1 items-center gap-3">
            <div
                :class="[
                    'flex h-10 w-10 items-center justify-center rounded-full border border-transparent bg-[#f0f4f9] text-primary text-strong',
                ]"
            >
                <img
                    v-if="isImageIcon"
                    :src="icon"
                    alt="chat assistant icon"
                    class="h-6 w-6 object-contain"
                />
                <span v-else class="material-symbols-outlined text-[18px]">{{ icon }}</span>
            </div>
            <div class="min-w-0">
                <h2 class="truncate text-base font-semibold leading-tight text-strong">
                    {{ title }}
                </h2>
                <div class="mt-1 flex min-w-0 items-center gap-1.5">
                    <span class="h-1.5 w-1.5 rounded-full" :class="statusDotClass"></span>
                    <span class="min-w-0 flex-1 truncate text-[11px] text-muted">{{
                        statusText
                    }}</span>
                </div>
            </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
            <span
                v-if="!showFilePanelButton && !showAgentSettingsButton"
                class="hidden rounded-full bg-[#f0f4f9] px-3 py-1 text-[11px] font-medium text-muted md:inline-flex"
            >
                Chat
            </span>
            <div v-if="showFilePanelButton" class="group relative">
                <button
                    type="button"
                    class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-transparent text-body transition hover:bg-[#f0f4f9] hover:text-strong active:scale-[0.98]"
                    :class="{ 'bg-[#e8edf5] text-strong': filePanelOpen }"
                    :title="filePanelOpen ? '关闭工作区' : '打开工作区'"
                    aria-label="工作区"
                    @click="$emit('toggle-file-panel')"
                >
                    <span class="material-symbols-outlined text-[18px]">
                        {{ filePanelOpen ? 'folder_open' : 'folder' }}
                    </span>
                </button>
                <span class="toolbar-tooltip">工作区</span>
            </div>
            <div v-if="showAgentSettingsButton" class="group relative">
                <button
                    type="button"
                    class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-transparent text-body transition hover:bg-[#f0f4f9] hover:text-strong active:scale-[0.98]"
                    :class="{ 'bg-[#e8edf5] text-strong': agentSettingsOpen }"
                    :title="
                        agentSettingsOpen
                            ? `关闭 ${agentSettingsLabel}`
                            : `打开 ${agentSettingsLabel}`
                    "
                    :aria-label="agentSettingsLabel"
                    @click="$emit('toggle-agent-settings')"
                >
                    <span class="material-symbols-outlined text-[18px]">tune</span>
                </button>
                <span class="toolbar-tooltip">{{ agentSettingsLabel }}</span>
            </div>
        </div>
    </header>
</template>

<script>
import { isImageSource } from '@/utils/iconDisplay';

export default {
    name: 'ChatHeaderBar',
    emits: ['toggle-file-panel', 'toggle-agent-settings'],
    props: {
        title: {
            type: String,
            default: '',
        },
        statusText: {
            type: String,
            default: '',
        },
        statusTone: {
            type: String,
            default: 'success',
        },
        icon: {
            type: String,
            default: 'description',
        },
        showFilePanelButton: {
            type: Boolean,
            default: false,
        },
        filePanelOpen: {
            type: Boolean,
            default: false,
        },
        showAgentSettingsButton: {
            type: Boolean,
            default: false,
        },
        agentSettingsOpen: {
            type: Boolean,
            default: false,
        },
        agentSettingsLabel: {
            type: String,
            default: 'Agent 设置',
        },
    },
    computed: {
        isImageIcon() {
            return isImageSource(this.icon);
        },
        statusDotClass() {
            if (this.statusTone === 'danger') {
                return 'bg-danger';
            }
            if (this.statusTone === 'warning') {
                return 'bg-warning';
            }
            if (this.statusTone === 'loading') {
                return 'animate-pulse bg-primary';
            }
            return 'bg-success';
        },
    },
};
</script>

<style scoped>
.toolbar-tooltip {
    position: absolute;
    left: 50%;
    top: calc(100% + 8px);
    transform: translateX(-50%) translateY(-2px);
    pointer-events: none;
    white-space: nowrap;
    border-radius: 6px;
    background: #111827;
    padding: 4px 8px;
    font-size: 12px;
    line-height: 1.4;
    color: #ffffff;
    opacity: 0;
    transition:
        opacity 0.15s ease,
        transform 0.15s ease;
}

.group:hover .toolbar-tooltip,
.group:focus-within .toolbar-tooltip {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}
</style>
