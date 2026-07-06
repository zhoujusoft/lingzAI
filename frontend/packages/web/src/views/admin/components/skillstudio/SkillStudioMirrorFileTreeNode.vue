<script setup>
import { computed, ref } from 'vue';

defineOptions({
    name: 'SkillStudioMirrorFileTreeNode',
});

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

const emit = defineEmits(['select']);

const expanded = ref(props.item.type === 'folder');

const isFolder = computed(() => props.item.type === 'folder');
const isActive = computed(() => !isFolder.value && props.selectedPath === props.item.path);
const isRootFolder = computed(() => isFolder.value && props.depth === 0);
const paddingLeft = computed(() => `${8 + props.depth * 12}px`);

function handleClick() {
    if (isFolder.value) {
        expanded.value = !expanded.value;
        return;
    }
    emit('select', props.item.path);
}
</script>

<template>
    <div>
        <div
            :class="[
                'tree-row flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded px-2 text-sm transition-colors',
                isActive ? 'tree-row-active' : '',
            ]"
            :style="{ paddingLeft }"
            @click="handleClick"
        >
            <template v-if="isFolder">
                <span class="material-symbols-outlined tree-arrow">
                    {{ expanded ? 'keyboard_arrow_down' : 'chevron_right' }}
                </span>
                <span class="material-symbols-outlined tree-folder">folder_open</span>
            </template>
            <template v-else>
                <span class="tree-arrow tree-arrow-placeholder" />
                <span :class="['tree-file-token', item.tokenClass || 'tree-file-token-default']">
                    {{ item.token || 'M↓' }}
                </span>
            </template>

            <span :class="['tree-name truncate', isRootFolder ? 'tree-root-name' : '']">{{
                item.name
            }}</span>
        </div>

        <template v-if="isFolder && expanded">
            <SkillStudioMirrorFileTreeNode
                v-for="child in item.children || []"
                :key="child.key"
                :item="child"
                :depth="depth + 1"
                :selected-path="selectedPath"
                @select="emit('select', $event)"
            />
        </template>
    </div>
</template>

<style scoped>
.tree-row {
    color: var(--editor-text);
}

.tree-row:hover {
    background: var(--editor-line);
}

.tree-row-active {
    background: var(--editor-line);
}

.tree-arrow {
    width: 12px;
    min-width: 12px;
    font-size: 14px;
    color: var(--editor-comment);
}

.tree-arrow-placeholder {
    display: inline-block;
}

.tree-folder {
    font-size: 16px;
    color: #4f8ff7;
}

.tree-file-token {
    width: 18px;
    min-width: 18px;
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
    text-align: center;
}

.tree-file-token-default {
    color: #a0a7b4;
}

.tree-name {
    font-size: 13px;
    line-height: 36px;
}

.tree-root-name {
    font-size: 14px;
    font-weight: 600;
}
</style>
