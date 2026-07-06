<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
    context: {
        type: Object,
        required: true,
    },
});

const selectedToolNames = computed(() => props.context.selectedToolNames || []);
const groups = computed(() => props.context.groups || []);
const mcpServers = computed(() => props.context.mcpServers || []);
const mcpServerToolMap = computed(() => props.context.mcpServerToolMap || new Map());
const searchKeyword = ref('');
const activeCategory = ref('');
const expandedGroupKeys = ref([]);

const navigationItems = computed(() => {
    const groupItems = groups.value.map(group => ({
        key: group.key,
        title: group.title,
        description: group.description,
        icon: getCategoryIcon(group.key),
        count: group.tools?.length || 0,
    }));
    const mcpToolCount = mcpServers.value.reduce((sum, server) => {
        return sum + (mcpServerToolMap.value.get(server.serverKey) || []).length;
    }, 0);
    return [
        ...groupItems,
        {
            key: 'mcp',
            title: 'MCP 远程工具',
            description: '按 Server 分组选择远程同步工具。',
            icon: 'cloud_sync',
            count: mcpToolCount,
        },
    ];
});

const activeNavigationItem = computed(
    () =>
        navigationItems.value.find(item => item.key === activeCategory.value) ||
        navigationItems.value[0]
);

const activeGroup = computed(
    () => groups.value.find(group => group.key === activeCategory.value) || null
);

const groupedSourceTools = computed(() => {
    const map = new Map();
    for (const group of groups.value) {
        if (!['dataset', 'knowledge'].includes(group.key)) {
            continue;
        }
        const sourceMap = new Map();
        for (const tool of group.tools || []) {
            const sourceKey = String(tool.source || tool.name || '').trim();
            if (!sourceMap.has(sourceKey)) {
                sourceMap.set(sourceKey, {
                    key: sourceKey,
                    title: resolveSourceGroupTitle(group.key, tool),
                    description: resolveSourceGroupDescription(group.key, tool),
                    sourceLabel: resolveSourceLabel(group.key, tool),
                    tools: [],
                });
            }
            sourceMap.get(sourceKey).tools.push(tool);
        }
        map.set(group.key, Array.from(sourceMap.values()));
    }
    return map;
});

const filteredTools = computed(() => {
    if (!activeGroup.value) {
        return [];
    }
    const keyword = searchKeyword.value.trim().toLowerCase();
    if (!keyword) {
        return activeGroup.value.tools || [];
    }
    return (activeGroup.value.tools || []).filter(tool =>
        [tool.name, tool.displayName, tool.description].some(value =>
            String(value || '')
                .toLowerCase()
                .includes(keyword)
        )
    );
});

const filteredMcpServers = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    return mcpServers.value
        .map(server => {
            const tools = mcpServerToolMap.value.get(server.serverKey) || [];
            if (!keyword) {
                return {
                    ...server,
                    matchedTools: tools,
                };
            }
            const serverMatched = [server.displayName, server.serverKey, server.endpoint].some(
                value =>
                    String(value || '')
                        .toLowerCase()
                        .includes(keyword)
            );
            const matchedTools = tools.filter(tool =>
                [tool.name, tool.displayName, tool.description].some(value =>
                    String(value || '')
                        .toLowerCase()
                        .includes(keyword)
                )
            );
            return {
                ...server,
                matchedTools: serverMatched ? tools : matchedTools,
            };
        })
        .filter(server => server.matchedTools.length);
});

const filteredGroupedSourceTools = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    const sourceGroups = groupedSourceTools.value.get(activeCategory.value) || [];
    return sourceGroups
        .map(group => {
            if (!keyword) {
                return group;
            }
            const groupMatched = [group.title, group.description, group.sourceLabel].some(value =>
                String(value || '')
                    .toLowerCase()
                    .includes(keyword)
            );
            const matchedTools = group.tools.filter(tool =>
                [tool.name, tool.displayName, tool.description].some(value =>
                    String(value || '')
                        .toLowerCase()
                        .includes(keyword)
                )
            );
            return {
                ...group,
                tools: groupMatched ? group.tools : matchedTools,
            };
        })
        .filter(group => group.tools.length);
});

watch(
    navigationItems,
    items => {
        if (!items.length) {
            activeCategory.value = '';
            return;
        }
        if (!items.some(item => item.key === activeCategory.value)) {
            activeCategory.value = items[0].key;
        }
    },
    { immediate: true }
);

watch(activeCategory, () => {
    expandedGroupKeys.value = [];
});

function isSelected(toolName) {
    return selectedToolNames.value.includes(toolName);
}

function toggleTool(toolName) {
    props.context.toggleTool?.(toolName);
}

function bindServerTools(serverKey) {
    props.context.bindServerTools?.(serverKey);
}

function removeServerTools(serverKey) {
    props.context.removeServerTools?.(serverKey);
}

function isGroupExpanded(groupKey) {
    return expandedGroupKeys.value.includes(groupKey);
}

function toggleGroupExpanded(groupKey) {
    const next = new Set(expandedGroupKeys.value);
    if (next.has(groupKey)) {
        next.delete(groupKey);
    } else {
        next.add(groupKey);
    }
    expandedGroupKeys.value = Array.from(next);
}

function toggleTools(toolNames, shouldSelect) {
    for (const toolName of toolNames) {
        const selected = isSelected(toolName);
        if ((shouldSelect && !selected) || (!shouldSelect && selected)) {
            toggleTool(toolName);
        }
    }
}

function bindGroupTools(toolNames) {
    toggleTools(toolNames, true);
}

function removeGroupTools(toolNames) {
    toggleTools(toolNames, false);
}

function getBoundCount(toolNames) {
    return toolNames.filter(name => isSelected(name)).length;
}

function resolveSourceGroupTitle(groupKey, tool) {
    const displayName = String(tool?.displayName || '').trim();
    const [title] = displayName.split('/');
    if (title?.trim()) {
        return title.trim();
    }
    return resolveSourceLabel(groupKey, tool);
}

function resolveSourceGroupDescription(groupKey, tool) {
    if (groupKey === 'dataset') {
        return '按数据集聚合展示，可统一绑定该数据集下的摘要、结构和 SQL 工具。';
    }
    if (groupKey === 'knowledge') {
        return '按知识库聚合展示，可统一绑定该知识库下的检索能力。';
    }
    return '';
}

function resolveSourceLabel(groupKey, tool) {
    const source = String(tool?.source || '').trim();
    if (!source) {
        return tool?.name || '-';
    }
    if (groupKey === 'dataset' && source.startsWith('dataset:')) {
        return source.slice('dataset:'.length);
    }
    if (groupKey === 'knowledge' && source.startsWith('knowledge_base:')) {
        return source.slice('knowledge_base:'.length);
    }
    return source;
}

function getCategoryIcon(key) {
    switch (key) {
        case 'global':
            return 'apps';
        case 'dataset':
            return 'database';
        case 'knowledge':
            return 'auto_stories';
        case 'lowcode':
            return 'api';
        case 'connectorApi':
            return 'cable';
        default:
            return 'build';
    }
}

function getToolTypeLabel(type) {
    switch (type) {
        case 'GLOBAL':
            return '公共工具';
        case 'RUNTIME':
            return '运行时工具';
        case 'DATASET_TOOL':
            return '数据集';
        case 'KNOWLEDGE_BASE_TOOL':
            return '知识库';
        case 'LOWCODE_API':
            return 'API';
        case 'CONNECTOR_API':
            return '连接器 API';
        case 'MCP_REMOTE':
            return 'MCP';
        default:
            return '未识别';
    }
}

function getToolTypeBadgeClass(type) {
    switch (type) {
        case 'GLOBAL':
            return 'bg-slate-100 text-slate-600';
        case 'RUNTIME':
            return 'bg-rose-50 text-rose-600';
        case 'DATASET_TOOL':
            return 'bg-emerald-50 text-emerald-600';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'bg-blue-50 text-blue-600';
        case 'LOWCODE_API':
            return 'bg-amber-50 text-amber-600';
        case 'CONNECTOR_API':
            return 'bg-orange-50 text-orange-600';
        case 'MCP_REMOTE':
            return 'bg-blue-50 text-primary';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}

function getToolIcon(type) {
    switch (type) {
        case 'GLOBAL':
            return 'extension';
        case 'RUNTIME':
            return 'terminal';
        case 'DATASET_TOOL':
            return 'database';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'menu_book';
        case 'LOWCODE_API':
            return 'api';
        case 'CONNECTOR_API':
            return 'cable';
        case 'MCP_REMOTE':
            return 'hub';
        default:
            return 'build';
    }
}

function getToolIconClass(type, selected) {
    if (selected) {
        return 'bg-primary/10 text-primary';
    }
    switch (type) {
        case 'GLOBAL':
            return 'bg-slate-100 text-slate-500 group-hover:bg-blue-50 group-hover:text-primary';
        case 'RUNTIME':
            return 'bg-rose-50 text-rose-600';
        case 'DATASET_TOOL':
            return 'bg-emerald-50 text-emerald-600';
        case 'KNOWLEDGE_BASE_TOOL':
            return 'bg-blue-50 text-blue-600';
        case 'LOWCODE_API':
            return 'bg-amber-50 text-amber-600';
        case 'CONNECTOR_API':
            return 'bg-orange-50 text-orange-600';
        case 'MCP_REMOTE':
            return 'bg-blue-50 text-primary';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}
</script>

<template>
    <div class="max-h-[78vh] overflow-hidden rounded-[28px] bg-white">
        <section class="border-b border-slate-100 px-8 py-6">
            <div class="flex flex-wrap items-center justify-between gap-4">
                <div class="flex items-center gap-4">
                    <div
                        class="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-50 text-primary"
                    >
                        <span class="material-symbols-outlined">add_link</span>
                    </div>
                    <div>
                        <h3 class="text-xl font-extrabold tracking-tight text-slate-900">
                            绑定新工具
                        </h3>
                        <p class="mt-1 text-sm text-slate-500">
                            左侧选择工具类型，右侧挑选具体工具并完成追加绑定。
                        </p>
                    </div>
                </div>
                <span
                    class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600"
                >
                    已选择 {{ selectedToolNames.length }} 项
                </span>
            </div>
        </section>

        <section class="flex h-[calc(78vh-97px)] overflow-hidden">
            <aside class="w-[260px] shrink-0 border-r border-slate-100 bg-slate-50/70 p-5">
                <div class="space-y-2">
                    <button
                        v-for="item in navigationItems"
                        :key="item.key"
                        type="button"
                        class="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left transition-all"
                        :class="
                            activeCategory === item.key
                                ? 'bg-white font-bold text-primary shadow-sm'
                                : 'text-slate-600 hover:bg-white/70'
                        "
                        @click="activeCategory = item.key"
                    >
                        <span class="material-symbols-outlined text-xl">{{ item.icon }}</span>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate text-sm">{{ item.title }}</span>
                            <span class="block text-xs text-slate-400">{{ item.count }} 项</span>
                        </span>
                    </button>
                </div>

                <div class="mt-6 rounded-2xl border border-slate-200 bg-white/80 p-4">
                    <p class="text-[10px] font-bold text-slate-400">绑定说明</p>
                    <p class="mt-2 text-xs leading-6 text-slate-500">
                        这里只维护追加绑定工具，技能自带的运行时工具不需要重复选择。
                    </p>
                </div>
            </aside>

            <div class="flex min-w-0 flex-1 flex-col">
                <div class="border-b border-slate-100 px-6 py-5">
                    <div class="flex flex-wrap items-center justify-between gap-4">
                        <div>
                            <h4 class="text-lg font-bold text-slate-900">
                                {{ activeNavigationItem?.title || '工具列表' }}
                            </h4>
                            <p class="mt-1 text-sm text-slate-500">
                                {{ activeNavigationItem?.description || '请选择要绑定的工具。' }}
                            </p>
                        </div>
                        <div class="relative w-full max-w-sm">
                            <span
                                class="material-symbols-outlined pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
                            >
                                search
                            </span>
                            <input
                                v-model.trim="searchKeyword"
                                type="text"
                                class="w-full rounded-xl border border-slate-200 py-2.5 pl-12 pr-4 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-blue-100"
                                placeholder="搜索工具名称或描述"
                            />
                        </div>
                    </div>
                </div>

                <div class="flex-1 overflow-y-auto p-6">
                    <div v-if="['dataset', 'knowledge'].includes(activeCategory)" class="space-y-4">
                        <article
                            v-for="group in filteredGroupedSourceTools"
                            :key="group.key"
                            class="rounded-2xl border border-slate-100 p-4 transition-all hover:border-primary/40 hover:bg-slate-50/60"
                            :class="
                                getBoundCount(group.tools.map(item => item.name)) > 0
                                    ? 'border-primary/20 bg-primary/5'
                                    : 'bg-white'
                            "
                        >
                            <div
                                class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"
                            >
                                <button
                                    type="button"
                                    class="flex min-w-0 flex-1 items-start justify-between gap-4 text-left"
                                    @click="toggleGroupExpanded(group.key)"
                                >
                                    <div>
                                        <div class="flex flex-wrap items-center gap-2">
                                            <h5 class="text-base font-bold text-slate-900">
                                                {{ group.title }}
                                            </h5>
                                            <span
                                                class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                                :class="
                                                    activeCategory === 'dataset'
                                                        ? 'bg-emerald-50 text-emerald-600'
                                                        : 'bg-blue-50 text-blue-600'
                                                "
                                            >
                                                {{ group.sourceLabel }}
                                            </span>
                                            <span
                                                class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                            >
                                                {{
                                                    getBoundCount(
                                                        group.tools.map(item => item.name)
                                                    )
                                                }}/{{ group.tools.length }} 已绑定
                                            </span>
                                        </div>
                                        <p class="mt-1 text-sm text-slate-500">
                                            {{ group.description }}
                                        </p>
                                    </div>
                                    <span
                                        class="material-symbols-outlined mt-1 shrink-0 text-slate-400 transition-transform"
                                        :class="isGroupExpanded(group.key) ? 'rotate-180' : ''"
                                    >
                                        expand_more
                                    </span>
                                </button>
                                <div class="flex shrink-0">
                                    <button
                                        v-if="
                                            getBoundCount(group.tools.map(item => item.name)) <
                                            group.tools.length
                                        "
                                        type="button"
                                        class="rounded-lg border border-slate-200 px-4 py-1.5 text-center text-xs font-bold text-slate-600 transition-all hover:border-primary hover:bg-primary hover:text-white"
                                        @click="bindGroupTools(group.tools.map(item => item.name))"
                                    >
                                        一键绑定
                                    </button>
                                    <button
                                        v-else
                                        type="button"
                                        class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-1.5 text-center text-xs font-bold text-rose-600 transition-all hover:bg-rose-100"
                                        @click="
                                            removeGroupTools(group.tools.map(item => item.name))
                                        "
                                    >
                                        清除此组
                                    </button>
                                </div>
                            </div>

                            <div v-if="isGroupExpanded(group.key)" class="mt-4 space-y-3">
                                <div
                                    v-for="tool in group.tools"
                                    :key="tool.name"
                                    class="flex items-center justify-between gap-4 rounded-2xl border border-slate-100 p-4 transition-all"
                                    :class="
                                        isSelected(tool.name)
                                            ? 'border-primary/20 bg-primary/5'
                                            : 'bg-white'
                                    "
                                >
                                    <div class="flex min-w-0 flex-1 items-center gap-4">
                                        <div
                                            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl"
                                            :class="
                                                getToolIconClass(tool.type, isSelected(tool.name))
                                            "
                                        >
                                            <span class="material-symbols-outlined">
                                                {{ getToolIcon(tool.type) }}
                                            </span>
                                        </div>
                                        <div class="min-w-0 max-w-[32rem]">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h6
                                                    class="truncate text-sm font-bold text-slate-900"
                                                >
                                                    {{ tool.displayName || tool.name }}
                                                </h6>
                                                <span
                                                    class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                                    :class="getToolTypeBadgeClass(tool.type)"
                                                >
                                                    {{ getToolTypeLabel(tool.type) }}
                                                </span>
                                                <span
                                                    v-if="isSelected(tool.name)"
                                                    class="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary"
                                                >
                                                    已绑定
                                                </span>
                                                <span
                                                    v-if="
                                                        tool.displayName &&
                                                        tool.displayName !== tool.name
                                                    "
                                                    class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                                >
                                                    {{ tool.name }}
                                                </span>
                                            </div>
                                            <p
                                                class="mt-1 line-clamp-2 text-xs leading-6 text-slate-500"
                                            >
                                                {{ tool.description }}
                                            </p>
                                        </div>
                                    </div>
                                    <span
                                        v-if="isSelected(tool.name)"
                                        class="shrink-0 rounded-lg bg-primary/10 px-3 py-1.5 text-center text-xs font-bold text-primary min-w-[88px]"
                                    >
                                        已绑定
                                    </span>
                                    <span
                                        v-else
                                        class="shrink-0 rounded-lg bg-slate-50 px-3 py-1.5 text-center text-xs font-medium text-slate-400 min-w-[88px]"
                                    >
                                        未绑定
                                    </span>
                                </div>
                            </div>
                        </article>

                        <div
                            v-if="!filteredGroupedSourceTools.length"
                            class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-400"
                        >
                            当前分类下没有匹配的分组工具。
                        </div>
                    </div>

                    <div v-else-if="activeCategory !== 'mcp'" class="space-y-4">
                        <label
                            v-for="tool in filteredTools"
                            :key="tool.name"
                            class="group flex cursor-pointer items-center justify-between gap-4 rounded-2xl border border-slate-100 p-4 transition-all hover:border-primary/40 hover:bg-slate-50/60"
                            :class="
                                isSelected(tool.name)
                                    ? 'border-primary/20 bg-primary/5'
                                    : 'bg-white'
                            "
                        >
                            <div class="flex min-w-0 flex-1 items-center gap-4">
                                <div
                                    class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl transition-colors"
                                    :class="getToolIconClass(tool.type, isSelected(tool.name))"
                                >
                                    <span class="material-symbols-outlined">
                                        {{ getToolIcon(tool.type) }}
                                    </span>
                                </div>
                                <div class="min-w-0 max-w-[32rem]">
                                    <div class="flex flex-wrap items-center gap-2">
                                        <h5 class="truncate text-sm font-bold text-slate-900">
                                            {{ tool.displayName || tool.name }}
                                        </h5>
                                        <span
                                            class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                            :class="getToolTypeBadgeClass(tool.type)"
                                        >
                                            {{ getToolTypeLabel(tool.type) }}
                                        </span>
                                        <span
                                            v-if="isSelected(tool.name)"
                                            class="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary"
                                        >
                                            已绑定
                                        </span>
                                        <span
                                            v-if="
                                                tool.displayName && tool.displayName !== tool.name
                                            "
                                            class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                        >
                                            {{ tool.name }}
                                        </span>
                                    </div>
                                    <p class="mt-1 line-clamp-2 text-xs leading-6 text-slate-500">
                                        {{ tool.description }}
                                    </p>
                                </div>
                            </div>
                            <button
                                type="button"
                                class="shrink-0 rounded-lg border px-4 py-1.5 text-center text-xs font-bold transition-all min-w-[88px]"
                                :class="
                                    isSelected(tool.name)
                                        ? 'border-rose-200 bg-rose-50 text-rose-600 hover:bg-rose-100'
                                        : 'border-slate-200 text-slate-600 group-hover:border-primary group-hover:bg-primary group-hover:text-white'
                                "
                                @click.prevent="toggleTool(tool.name)"
                            >
                                {{ isSelected(tool.name) ? '取消绑定' : '绑定' }}
                            </button>
                            <input
                                :checked="isSelected(tool.name)"
                                type="checkbox"
                                class="sr-only"
                                @change="toggleTool(tool.name)"
                            />
                        </label>

                        <div
                            v-if="!filteredTools.length"
                            class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-400"
                        >
                            当前分类下没有匹配的工具。
                        </div>
                    </div>

                    <div v-else class="space-y-4">
                        <article
                            v-for="server in filteredMcpServers"
                            :key="server.id"
                            class="rounded-[24px] border border-slate-200 bg-slate-50 p-5"
                        >
                            <div
                                class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"
                            >
                                <button
                                    type="button"
                                    class="flex min-w-0 flex-1 items-start justify-between gap-4 text-left"
                                    @click="toggleGroupExpanded(`mcp:${server.serverKey}`)"
                                >
                                    <div>
                                        <div class="flex flex-wrap items-center gap-2">
                                            <h5 class="text-base font-bold text-slate-900">
                                                {{ server.displayName }}
                                            </h5>
                                            <span
                                                class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-primary"
                                            >
                                                {{ server.serverKey }}
                                            </span>
                                            <span
                                                class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                            >
                                                {{ server.matchedTools.length }} 项
                                            </span>
                                        </div>
                                        <p class="mt-1 text-sm text-slate-500">
                                            {{ server.endpoint }}
                                        </p>
                                    </div>
                                    <span
                                        class="material-symbols-outlined mt-1 shrink-0 text-slate-400 transition-transform"
                                        :class="
                                            isGroupExpanded(`mcp:${server.serverKey}`)
                                                ? 'rotate-180'
                                                : ''
                                        "
                                    >
                                        expand_more
                                    </span>
                                </button>
                                <div class="flex shrink-0 gap-2">
                                    <button
                                        type="button"
                                        class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                                        @click="bindServerTools(server.serverKey)"
                                    >
                                        一键绑定
                                    </button>
                                    <button
                                        type="button"
                                        class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                                        @click="removeServerTools(server.serverKey)"
                                    >
                                        清除此组
                                    </button>
                                </div>
                            </div>

                            <div
                                v-if="isGroupExpanded(`mcp:${server.serverKey}`)"
                                class="mt-4 space-y-3"
                            >
                                <label
                                    v-for="tool in server.matchedTools"
                                    :key="tool.name"
                                    class="group flex cursor-pointer items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-4 transition-all hover:border-primary/40 hover:bg-slate-50/60"
                                    :class="
                                        isSelected(tool.name)
                                            ? 'border-primary/20 bg-primary/5'
                                            : ''
                                    "
                                >
                                    <div class="flex min-w-0 flex-1 items-center gap-4">
                                        <div
                                            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl transition-colors"
                                            :class="
                                                getToolIconClass(
                                                    'MCP_REMOTE',
                                                    isSelected(tool.name)
                                                )
                                            "
                                        >
                                            <span class="material-symbols-outlined">hub</span>
                                        </div>
                                        <div class="min-w-0 max-w-[32rem]">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h6
                                                    class="truncate text-sm font-bold text-slate-900"
                                                >
                                                    {{ tool.displayName || tool.name }}
                                                </h6>
                                                <span
                                                    class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-primary"
                                                >
                                                    MCP
                                                </span>
                                                <span
                                                    v-if="isSelected(tool.name)"
                                                    class="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary"
                                                >
                                                    已绑定
                                                </span>
                                                <span
                                                    v-if="
                                                        tool.displayName &&
                                                        tool.displayName !== tool.name
                                                    "
                                                    class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                                >
                                                    {{ tool.name }}
                                                </span>
                                            </div>
                                            <p
                                                class="mt-1 line-clamp-2 text-xs leading-6 text-slate-500"
                                            >
                                                {{ tool.description }}
                                            </p>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        class="shrink-0 rounded-lg border px-4 py-1.5 text-center text-xs font-bold transition-all min-w-[88px]"
                                        :class="
                                            isSelected(tool.name)
                                                ? 'border-rose-200 bg-rose-50 text-rose-600 hover:bg-rose-100'
                                                : 'border-slate-200 text-slate-600 group-hover:border-primary group-hover:bg-primary group-hover:text-white'
                                        "
                                        @click.prevent="toggleTool(tool.name)"
                                    >
                                        {{ isSelected(tool.name) ? '取消绑定' : '绑定' }}
                                    </button>
                                    <input
                                        :checked="isSelected(tool.name)"
                                        type="checkbox"
                                        class="sr-only"
                                        @change="toggleTool(tool.name)"
                                    />
                                </label>
                            </div>
                        </article>

                        <div
                            v-if="!filteredMcpServers.length"
                            class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-400"
                        >
                            当前没有匹配的 MCP 工具。
                        </div>
                    </div>
                </div>
            </div>
        </section>
    </div>
</template>
