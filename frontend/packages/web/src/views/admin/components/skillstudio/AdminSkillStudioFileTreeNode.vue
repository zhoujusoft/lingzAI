<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
    item: {
        type: Object,
        required: true,
    },
    depth: {
        type: Number,
        default: 0,
    },
    selectedPath: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['select-file']);

const expanded = ref(props.item?.expanded !== false);

const isFolder = computed(() => props.item?.type === 'folder');
const isActive = computed(
    () => !isFolder.value && String(props.selectedPath || '') === String(props.item?.path || '')
);
const paddingLeft = computed(() => `${8 + props.depth * 12}px`);

function toggle() {
    if (!isFolder.value) {
        emit('select-file', props.item?.path || '');
        return;
    }
    expanded.value = !expanded.value;
}
</script>

<template>
    <div>
        <div
            :class="[
                'flex cursor-pointer items-center gap-1.5 rounded px-2 py-1 text-sm transition-colors',
                isActive
                    ? 'bg-[#E8F3FF] text-[#165DFF]'
                    : 'text-muted-foreground hover:bg-editor-line',
            ]"
            :style="{ paddingLeft }"
            @click="toggle"
        >
            <template v-if="isFolder">
                <span class="material-symbols-outlined shrink-0 text-[12px] text-[#6b7280]">
                    {{ expanded ? 'expand_more' : 'chevron_right' }}
                </span>
                <span class="material-symbols-outlined shrink-0 text-[13px] text-blue-400"
                    >folder</span
                >
            </template>
            <template v-else>
                <span class="w-3 shrink-0"></span>
                <span class="material-symbols-outlined shrink-0 text-[13px] text-[#9ca3af]">
                    {{ item.icon || 'insert_drive_file' }}
                </span>
            </template>

            <span
                class="flex-1 truncate text-xs"
                :class="isFolder ? 'font-medium text-editor-text' : ''"
            >
                {{ item.name }}
            </span>
            <span v-if="item.badge" class="ml-auto shrink-0 text-[12px] font-bold text-[#F7BA1E]">
                {{ item.badge }}
            </span>
        </div>

        <template v-if="isFolder && expanded">
            <AdminSkillStudioFileTreeNode
                v-for="child in item.children || []"
                :key="child.key"
                :item="child"
                :depth="depth + 1"
                :selected-path="selectedPath"
                @select-file="emit('select-file', $event)"
            />
        </template>
    </div>
</template>
