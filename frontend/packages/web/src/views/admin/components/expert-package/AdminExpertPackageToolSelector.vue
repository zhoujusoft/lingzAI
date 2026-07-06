<script setup>
import { computed, ref } from 'vue';
import {
    filterUserFacingTools,
    getChildrenNames,
    groupToolsByType,
    isGroupPartialSelected,
    isGroupSelected,
    toggleGroupSelection,
} from '@/utils/groupingUtils';

const props = defineProps({
    tools: {
        type: Array,
        default: () => [],
    },
    modelValue: {
        type: Array,
        default: () => [],
    },
});

const emit = defineEmits(['update:modelValue']);
const activeTab = ref('all');
const keyword = ref('');

const selectableTools = computed(() => filterUserFacingTools(props.tools));
const selectedIds = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []));
const groupedTools = computed(() => groupToolsByType(selectableTools.value));
const tabs = computed(() => {
    const labels = {
        MCP_REMOTE: 'MCP 远程',
        LOWCODE_API: '低代码 API',
        DATASET_TOOL: '数据集',
        KNOWLEDGE_BASE_TOOL: '知识库',
    };
    const counts = new Map();
    for (const tool of selectableTools.value) {
        counts.set(tool.type, (counts.get(tool.type) || 0) + 1);
    }
    return [
        { key: 'all', label: '全部', count: selectableTools.value.length },
        ...Object.entries(labels)
            .filter(([type]) => counts.has(type))
            .map(([key, label]) => ({ key, label, count: counts.get(key) })),
    ];
});
const filteredItems = computed(() => {
    const search = keyword.value.trim().toLowerCase();
    return groupedTools.value.filter(item => {
        if (activeTab.value !== 'all' && item.toolType !== activeTab.value) {
            return false;
        }
        if (!search) {
            return true;
        }
        return [item.displayName, item.name, item.description, getChildrenNames(item.children)]
            .filter(Boolean)
            .some(value => String(value).toLowerCase().includes(search));
    });
});
const boundItems = computed(() => filteredItems.value.filter(item => isItemSelected(item)));
const unboundItems = computed(() => filteredItems.value.filter(item => !isItemSelected(item)));

function isItemSelected(item) {
    if (item.itemType === 'group') {
        return isGroupSelected(item, selectedIds.value);
    }
    return selectedIds.value.includes(item.id);
}

function toggleItem(item) {
    if (item.itemType === 'group') {
        emit('update:modelValue', toggleGroupSelection(item, selectedIds.value));
        return;
    }
    emit(
        'update:modelValue',
        selectedIds.value.includes(item.id)
            ? selectedIds.value.filter(id => id !== item.id)
            : [...selectedIds.value, item.id]
    );
}

function toggleAll() {
    const ids = selectableTools.value.map(tool => tool.id);
    const allSelected = ids.length > 0 && ids.every(id => selectedIds.value.includes(id));
    emit(
        'update:modelValue',
        allSelected
            ? selectedIds.value.filter(id => !ids.includes(id))
            : Array.from(new Set([...selectedIds.value, ...ids]))
    );
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex flex-wrap gap-2">
                <button
                    v-for="tab in tabs"
                    :key="tab.key"
                    type="button"
                    :class="[
                        'rounded-lg px-3 py-2 text-xs font-semibold transition',
                        activeTab === tab.key
                            ? 'bg-blue-50 text-primary'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                    ]"
                    @click="activeTab = tab.key"
                >
                    {{ tab.label }} ({{ tab.count }})
                </button>
            </div>
            <div class="flex items-center gap-2">
                <div class="relative">
                    <span
                        class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-base text-slate-400"
                    >
                        search
                    </span>
                    <input
                        v-model.trim="keyword"
                        type="text"
                        placeholder="搜索工具"
                        class="w-56 rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                    />
                </div>
                <button
                    type="button"
                    class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                    @click="toggleAll"
                >
                    全选 / 清空
                </button>
            </div>
        </div>

        <div class="grid gap-4 xl:grid-cols-2">
            <section class="rounded-lg border border-blue-100 bg-blue-50/40 p-4">
                <div class="mb-3 flex items-center justify-between">
                    <h4 class="text-sm font-bold text-slate-900">已选择工具</h4>
                    <span class="text-xs font-semibold text-primary"
                        >{{ modelValue.length }} 项</span
                    >
                </div>
                <div v-if="boundItems.length" class="grid gap-2">
                    <button
                        v-for="item in boundItems"
                        :key="`bound-${item.id || item.groupKey}`"
                        type="button"
                        class="flex min-w-0 items-start gap-3 rounded-lg border border-blue-100 bg-white p-3 text-left transition hover:border-blue-300 active:translate-y-px"
                        @click="toggleItem(item)"
                    >
                        <span class="material-symbols-outlined mt-0.5 text-lg text-primary">
                            check_box
                        </span>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate text-sm font-semibold text-slate-800">
                                {{ item.displayName || item.name }}
                            </span>
                            <span class="mt-1 block truncate text-xs text-slate-500">
                                {{
                                    item.itemType === 'group'
                                        ? `${item.children.length} 个子工具 · ${getChildrenNames(item.children)}`
                                        : item.description || item.name || item.type
                                }}
                            </span>
                        </span>
                    </button>
                </div>
                <p
                    v-else
                    class="rounded-lg border border-dashed border-blue-100 bg-white/70 px-4 py-8 text-center text-sm text-slate-400"
                >
                    当前筛选条件下没有已选择工具
                </p>
            </section>

            <section class="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div class="mb-3 flex items-center justify-between">
                    <h4 class="text-sm font-bold text-slate-900">可选择工具</h4>
                    <span class="text-xs font-semibold text-slate-500"
                        >{{ unboundItems.length }} 项</span
                    >
                </div>
                <div v-if="unboundItems.length" class="grid gap-2">
                    <button
                        v-for="item in unboundItems"
                        :key="`unbound-${item.id || item.groupKey}`"
                        type="button"
                        class="flex min-w-0 items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 text-left transition hover:border-blue-300 active:translate-y-px"
                        @click="toggleItem(item)"
                    >
                        <span class="material-symbols-outlined mt-0.5 text-lg text-slate-400">
                            {{
                                item.itemType === 'group' &&
                                isGroupPartialSelected(item, selectedIds)
                                    ? 'indeterminate_check_box'
                                    : 'check_box_outline_blank'
                            }}
                        </span>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate text-sm font-semibold text-slate-800">
                                {{ item.displayName || item.name }}
                            </span>
                            <span class="mt-1 block truncate text-xs text-slate-500">
                                {{
                                    item.itemType === 'group'
                                        ? `${item.children.length} 个子工具 · ${getChildrenNames(item.children)}`
                                        : item.description || item.name || item.type
                                }}
                            </span>
                        </span>
                    </button>
                </div>
                <p
                    v-else
                    class="rounded-lg border border-dashed border-slate-200 bg-white px-4 py-8 text-center text-sm text-slate-400"
                >
                    当前筛选条件下没有可选择工具
                </p>
            </section>
        </div>
    </div>
</template>
