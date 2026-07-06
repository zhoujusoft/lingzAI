<template>
    <section :class="sectionClass">
        <!-- Section header -->
        <div class="mb-3 flex items-center gap-2">
            <span
                class="material-symbols-outlined text-lg"
                :class="isAuthorized ? 'text-emerald-500' : 'text-muted'"
                >{{ isAuthorized ? 'check_circle' : 'lock' }}</span
            >
            <h2
                class="text-base font-semibold"
                :class="isAuthorized ? 'text-strong' : 'text-muted'"
            >
                {{ title }}
            </h2>
            <span
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="
                    isAuthorized ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'
                "
                >{{ totalCount }}</span
            >
        </div>

        <!-- Empty state -->
        <div
            v-if="totalCount === 0"
            class="flex min-h-[120px] flex-col items-center justify-center rounded-2xl border-2 border-dashed border-slate-300 bg-slate-100/50 px-6 py-8 text-center"
        >
            <span class="material-symbols-outlined mb-2 text-4xl text-slate-400">{{
                emptyIcon
            }}</span>
            <p class="text-sm font-medium text-slate-500">{{ emptyText }}</p>
        </div>

        <!-- Content sections (only show when has data) -->
        <template v-else>
            <!-- Skills section -->
            <div v-if="showSkills && hasSkills" class="mb-6">
                <h3
                    class="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-muted"
                >
                    <span class="material-symbols-outlined text-sm">widgets</span>
                    技能
                    <span class="ml-1 text-slate-400">({{ skillFlatCount }})</span>
                </h3>

                <!-- Skill category tabs -->
                <div class="mb-3 flex flex-wrap gap-1.5">
                    <button
                        v-for="tab in skillTabs"
                        :key="tab.key"
                        type="button"
                        :class="[
                            'rounded-full px-3 py-1 text-xs font-medium transition-all',
                            activeSkillTab === tab.key
                                ? 'bg-primary text-white'
                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                        ]"
                        @click="activeSkillTab = tab.key"
                    >
                        {{ tab.label }}
                        <span class="ml-1 opacity-70">({{ tab.count }})</span>
                    </button>
                </div>

                <!-- Skills grid -->
                <div
                    class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4"
                    :class="{ 'opacity-50': !isAuthorized }"
                >
                    <article
                        v-for="skill in filteredSkills"
                        :key="skill.id"
                        class="front-card relative flex flex-col overflow-hidden transition-colors"
                        :class="isAuthorized ? 'hover:border-primary/40' : ''"
                    >
                        <div class="flex flex-1 flex-col p-4">
                            <div class="flex items-start gap-3">
                                <div
                                    :class="[
                                        'flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-white',
                                        getSkillGradient(skill),
                                    ]"
                                >
                                    <span class="material-symbols-outlined text-xl">{{
                                        getSkillIcon(skill)
                                    }}</span>
                                </div>
                                <div class="min-w-0 flex-1">
                                    <h4 class="truncate text-sm font-semibold text-strong">
                                        {{ skill.displayName }}
                                    </h4>
                                    <p
                                        class="mt-1 line-clamp-2 overflow-hidden text-xs leading-5 text-body"
                                    >
                                        {{ skill.description }}
                                    </p>
                                </div>
                            </div>
                            <div class="mt-3 flex flex-wrap gap-1">
                                <span
                                    class="rounded bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-primary"
                                >
                                    {{ skill.category || '通用能力' }}
                                </span>
                                <span
                                    v-if="skill.version"
                                    class="rounded bg-warning/10 px-1.5 py-0.5 text-[10px] font-medium text-warning"
                                >
                                    v{{ skill.version }}
                                </span>
                                <span
                                    class="rounded px-1.5 py-0.5 text-[10px] font-medium"
                                    :class="
                                        isAuthorized
                                            ? 'bg-emerald-50 text-emerald-600'
                                            : 'bg-slate-100 text-slate-500'
                                    "
                                    >{{ isAuthorized ? '已授权' : '未授权' }}</span
                                >
                            </div>
                        </div>
                        <!-- Lock overlay for unauthorized -->
                        <div
                            v-if="!isAuthorized"
                            class="pointer-events-none absolute inset-0 flex items-center justify-center"
                        >
                            <span class="material-symbols-outlined text-3xl text-slate-300"
                                >lock</span
                            >
                        </div>
                    </article>
                </div>
            </div>

            <!-- Tools section -->
            <div v-if="showTools && hasTools">
                <h3
                    class="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-muted"
                >
                    <span class="material-symbols-outlined text-sm">build</span>
                    工具
                    <span class="ml-1 text-slate-400">({{ toolFlatCount }})</span>
                </h3>

                <!-- Tool type tabs -->
                <div class="mb-3 flex flex-wrap gap-1.5">
                    <button
                        v-for="tab in toolTabs"
                        :key="tab.key"
                        type="button"
                        :class="[
                            'rounded-full px-3 py-1 text-xs font-medium transition-all',
                            activeToolTab === tab.key
                                ? 'bg-primary text-white'
                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                        ]"
                        @click="activeToolTab = tab.key"
                    >
                        {{ tab.label }}
                        <span class="ml-1 opacity-70">({{ tab.count }})</span>
                    </button>
                </div>

                <!-- Tools grid -->
                <div
                    class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4"
                    :class="{ 'opacity-50': !isAuthorized }"
                >
                    <template v-for="item in filteredTools" :key="item.id || item.groupKey">
                        <!-- Single tool card -->
                        <article
                            v-if="item.itemType === 'single'"
                            class="front-card relative flex flex-col overflow-hidden transition-colors"
                            :class="isAuthorized ? 'hover:border-primary/40' : ''"
                        >
                            <div class="flex flex-1 flex-col p-4">
                                <div class="flex items-start gap-3">
                                    <div
                                        :class="[
                                            'flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-sm',
                                            resolveToolGradient(item.type),
                                        ]"
                                    >
                                        <span class="material-symbols-outlined text-xl">{{
                                            resolveToolIcon(item.type)
                                        }}</span>
                                    </div>
                                    <div class="min-w-0 flex-1">
                                        <h4 class="truncate text-sm font-semibold text-strong">
                                            {{ item.displayName || item.name }}
                                        </h4>
                                        <p
                                            class="mt-1 line-clamp-2 overflow-hidden text-xs leading-5 text-body"
                                        >
                                            {{ item.description }}
                                        </p>
                                    </div>
                                </div>
                                <div class="mt-3 flex flex-wrap gap-1">
                                    <span
                                        class="rounded px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="resolveToolTypeBadgeClass(item.type)"
                                    >
                                        {{ resolveToolTypeLabel(item.type) }}
                                    </span>
                                    <span
                                        class="rounded px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="
                                            isAuthorized
                                                ? 'bg-emerald-50 text-emerald-600'
                                                : 'bg-slate-100 text-slate-500'
                                        "
                                        >{{ isAuthorized ? '已授权' : '未授权' }}</span
                                    >
                                </div>
                            </div>
                            <div
                                v-if="!isAuthorized"
                                class="pointer-events-none absolute inset-0 flex items-center justify-center"
                            >
                                <span class="material-symbols-outlined text-3xl text-slate-300"
                                    >lock</span
                                >
                            </div>
                        </article>

                        <!-- Dataset tool group card -->
                        <article
                            v-else-if="item.itemType === 'group'"
                            class="front-card relative flex flex-col overflow-hidden transition-colors"
                            :class="isAuthorized ? 'hover:border-primary/40' : ''"
                        >
                            <div class="flex flex-1 flex-col p-4">
                                <div class="flex items-start gap-3">
                                    <div
                                        :class="[
                                            'flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-sm',
                                            resolveToolGradient('DATASET_TOOL'),
                                        ]"
                                    >
                                        <span class="material-symbols-outlined text-xl"
                                            >database</span
                                        >
                                    </div>
                                    <div class="min-w-0 flex-1">
                                        <h4 class="truncate text-sm font-semibold text-strong">
                                            {{ item.displayName }}
                                        </h4>
                                        <p
                                            class="mt-1 line-clamp-2 overflow-hidden text-xs leading-5 text-body"
                                        >
                                            {{ item.children.length }}个子工具：{{
                                                getChildrenNames(item.children, 'tool')
                                            }}
                                        </p>
                                    </div>
                                </div>
                                <div class="mt-3 flex flex-wrap gap-1">
                                    <span
                                        class="rounded bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700"
                                    >
                                        数据集工具组
                                    </span>
                                    <span
                                        class="rounded px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="
                                            isAuthorized
                                                ? 'bg-emerald-50 text-emerald-600'
                                                : 'bg-slate-100 text-slate-500'
                                        "
                                        >{{ isAuthorized ? '已授权' : '未授权' }}</span
                                    >
                                </div>
                            </div>
                            <div
                                v-if="!isAuthorized"
                                class="pointer-events-none absolute inset-0 flex items-center justify-center"
                            >
                                <span class="material-symbols-outlined text-3xl text-slate-300"
                                    >lock</span
                                >
                            </div>
                        </article>
                    </template>
                </div>
            </div>
        </template>
    </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { getSkillIconGradientClass, resolveSkillIcon } from '@/utils/skillVisuals';
import {
    resolveToolIcon,
    resolveToolGradient,
    resolveToolTypeLabel,
    resolveToolTypeBadgeClass,
} from '@/utils/toolVisuals';
import { getChildrenNames } from '@/utils/groupingUtils';

const props = defineProps({
    /** 区域标题 */
    title: { type: String, required: true },
    /** 是否为已授权区域 */
    isAuthorized: { type: Boolean, default: true },
    /** 技能列表（已分组） */
    skillItems: { type: Array, default: () => [] },
    /** 工具列表（已分组） */
    toolItems: { type: Array, default: () => [] },
    /** 空状态图标 */
    emptyIcon: { type: String, default: 'inventory_2' },
    /** 空状态文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 区域额外 class */
    sectionClass: { type: String, default: '' },
    /** 是否显示技能部分 */
    showSkills: { type: Boolean, default: true },
    /** 是否显示工具部分 */
    showTools: { type: Boolean, default: true },
});

// --- Tab state ---
const activeSkillTab = ref('all');
const activeToolTab = ref('all');

// Reset tabs when data changes
watch(
    () => props.skillItems,
    () => {
        activeSkillTab.value = 'all';
    }
);
watch(
    () => props.toolItems,
    () => {
        activeToolTab.value = 'all';
    }
);

// --- Computed: flat skill list ---
const flatSkills = computed(() => {
    return props.skillItems.filter(item => item.itemType === 'single');
});

const skillFlatCount = computed(() => flatSkills.value.length);
const hasSkills = computed(() => skillFlatCount.value > 0);

// --- Computed: skill tabs ---
const skillTabs = computed(() => {
    // Count by category
    const categoryCount = new Map();
    for (const skill of flatSkills.value) {
        const category = skill.category || '通用能力';
        categoryCount.set(category, (categoryCount.get(category) || 0) + 1);
    }

    // Build tabs: 全部 + categories
    const tabs = [{ key: 'all', label: '全部', count: skillFlatCount.value }];
    for (const [category, count] of categoryCount) {
        tabs.push({ key: category, label: category, count });
    }
    return tabs;
});

// --- Computed: filtered skills by tab ---
const filteredSkills = computed(() => {
    if (activeSkillTab.value === 'all') {
        return flatSkills.value;
    }
    return flatSkills.value.filter(
        skill => (skill.category || '通用能力') === activeSkillTab.value
    );
});

// --- Computed: flat tool list (with groups) ---
const toolItemsForDisplay = computed(() => {
    // Keep groups and singles for display
    return props.toolItems;
});

const toolFlatCount = computed(() => {
    // Count all tools including dataset tools in groups
    let count = 0;
    for (const item of props.toolItems) {
        if (item.itemType === 'single') {
            count++;
        } else if (item.itemType === 'group' && item.toolType === 'DATASET_TOOL') {
            // Dataset tools are in group.children
            count += item.children?.length || 0;
        }
    }
    return count;
});
const hasTools = computed(() => toolFlatCount.value > 0);

// --- Computed: tool tabs ---
const toolTabs = computed(() => {
    // Build tabs: 全部 + 各类型
    const typeLabels = {
        MCP_REMOTE: 'MCP 远程工具',
        LOWCODE_API: '低代码 API',
        DATASET_TOOL: '数据集工具',
        KNOWLEDGE_BASE_TOOL: '知识库工具',
    };

    const tabs = [{ key: 'all', label: '全部', count: toolFlatCount.value }];

    // Count by type
    const typeCount = new Map();
    for (const item of props.toolItems) {
        if (item.itemType === 'single') {
            const type = item.type || 'OTHER';
            typeCount.set(type, (typeCount.get(type) || 0) + 1);
        } else if (item.itemType === 'group' && item.toolType === 'DATASET_TOOL') {
            // Dataset tools count from group children
            typeCount.set(
                'DATASET_TOOL',
                (typeCount.get('DATASET_TOOL') || 0) + (item.children?.length || 0)
            );
        }
    }

    // Add type tabs in order
    const typeOrder = ['MCP_REMOTE', 'LOWCODE_API', 'DATASET_TOOL', 'KNOWLEDGE_BASE_TOOL'];
    for (const type of typeOrder) {
        const count = typeCount.get(type);
        if (count) {
            tabs.push({
                key: `type:${type}`,
                label: typeLabels[type] || type,
                count,
            });
        }
    }

    return tabs;
});

// --- Computed: filtered tools by tab ---
const filteredTools = computed(() => {
    if (activeToolTab.value === 'all') {
        return toolItemsForDisplay.value;
    }

    // Filter by type
    const targetType = activeToolTab.value.replace('type:', '');
    return toolItemsForDisplay.value.filter(item => {
        if (item.itemType === 'single') {
            return (item.type || 'OTHER') === targetType;
        } else if (item.itemType === 'group') {
            // Show dataset tool group when filtering by DATASET_TOOL
            return item.toolType === targetType;
        }
        return false;
    });
});

// --- Computed: total count ---
const totalCount = computed(() => {
    const skillCount = props.showSkills ? skillFlatCount.value : 0;
    const toolCount = props.showTools ? toolFlatCount.value : 0;
    return skillCount + toolCount;
});

// --- Helpers ---
function getSkillIcon(skill) {
    return resolveSkillIcon(skill?.icon);
}

function getSkillGradient(skill) {
    return getSkillIconGradientClass(skill?.iconColor);
}
</script>
