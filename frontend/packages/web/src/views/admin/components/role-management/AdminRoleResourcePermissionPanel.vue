<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { clearUserSession } from '@/composables/useCurrentUser';
import { showToast } from '@/composables/useToast';
import { getRoleDetail, getRoleResources, updateRoleResources } from '@/api/roles';
import { listSkillCatalog } from '@/api/skills';
import { listToolCatalog } from '@/api/tools';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    filterUserFacingTools,
    getChildrenNames,
    groupToolsByType,
    isGroupPartialSelected,
    isGroupSelected,
} from '@/utils/groupingUtils';

const props = defineProps({
    roleId: {
        type: Number,
        required: true,
    },
});

const emit = defineEmits(['back', 'saved']);

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const detail = ref(null);
const activeResourceTab = ref('skills');
const activeSkillTab = ref('all');
const activeToolTab = ref('all');
const skillSearchKeyword = ref('');
const toolSearchKeyword = ref('');
const allSkills = ref([]);
const allTools = ref([]);
const selectedSkillIds = ref([]);
const selectedToolIds = ref([]);

const skillTabs = computed(() => {
    const categoryCount = new Map();
    for (const skill of allSkills.value) {
        const category = skill.category || '通用能力';
        categoryCount.set(category, (categoryCount.get(category) || 0) + 1);
    }
    const tabs = [{ key: 'all', label: '全部', count: allSkills.value.length }];
    const categories = Array.from(categoryCount.entries()).sort((a, b) =>
        a[0].localeCompare(b[0], 'zh-CN')
    );
    for (const [category, count] of categories) {
        tabs.push({ key: category, label: category, count });
    }
    return tabs;
});

const toolTabs = computed(() => {
    const typeLabels = {
        MCP_REMOTE: 'MCP 远程',
        LOWCODE_API: '低代码 API',
        DATASET_TOOL: '数据集',
        KNOWLEDGE_BASE_TOOL: '知识库',
    };
    const typeCount = new Map();
    for (const tool of allTools.value) {
        const type = tool.type || 'OTHER';
        typeCount.set(type, (typeCount.get(type) || 0) + 1);
    }
    const tabs = [{ key: 'all', label: '全部', count: allTools.value.length }];
    for (const type of ['MCP_REMOTE', 'LOWCODE_API', 'DATASET_TOOL', 'KNOWLEDGE_BASE_TOOL']) {
        const count = typeCount.get(type);
        if (count) {
            tabs.push({ key: type, label: typeLabels[type] || type, count });
        }
    }
    return tabs;
});

const filteredSkills = computed(() => {
    const keyword = skillSearchKeyword.value.trim().toLowerCase();
    let result = allSkills.value;
    if (activeSkillTab.value !== 'all') {
        result = result.filter(skill => (skill.category || '通用能力') === activeSkillTab.value);
    }
    if (keyword) {
        result = result.filter(skill =>
            [skill.displayName, skill.runtimeSkillName, skill.description]
                .filter(Boolean)
                .some(value => String(value).toLowerCase().includes(keyword))
        );
    }
    return result;
});

const groupedTools = computed(() => groupToolsByType(allTools.value));

const filteredTools = computed(() => {
    const keyword = toolSearchKeyword.value.trim().toLowerCase();
    const typeFilter = activeToolTab.value;
    if (!keyword && typeFilter === 'all') {
        return groupedTools.value;
    }

    const result = [];
    const matchedGroups = new Set();
    for (const item of groupedTools.value) {
        if (item.itemType === 'group') {
            if (typeFilter !== 'all' && item.toolType !== typeFilter) {
                continue;
            }
            const matchGroup = item.displayName.toLowerCase().includes(keyword);
            const matchChildren = item.children.some(child =>
                [child.displayName, child.name, child.description]
                    .filter(Boolean)
                    .some(value => String(value).toLowerCase().includes(keyword))
            );
            if (!keyword || matchGroup || matchChildren) {
                matchedGroups.add(item.groupKey);
            }
        }
    }

    for (const item of groupedTools.value) {
        if (item.itemType === 'group' && matchedGroups.has(item.groupKey)) {
            result.push(item);
            if (item.toolType !== 'DATASET_TOOL') {
                result.push(...item.children);
            }
        }
    }
    return result;
});

const boundSkills = computed(() =>
    filteredSkills.value.filter(skill => selectedSkillIds.value.includes(skill.id))
);

const unboundSkills = computed(() =>
    filteredSkills.value.filter(skill => !selectedSkillIds.value.includes(skill.id))
);

const boundTools = computed(() => splitToolsByBinding(filteredTools.value).bound);
const unboundTools = computed(() => splitToolsByBinding(filteredTools.value).unbound);

const isAllSkillsSelected = computed(() => {
    return (
        filteredSkills.value.length > 0 &&
        filteredSkills.value.every(skill => selectedSkillIds.value.includes(skill.id))
    );
});

const isAllToolsSelected = computed(() => {
    const allIds = collectToolIds(groupedTools.value);
    return allIds.length > 0 && allIds.every(id => selectedToolIds.value.includes(id));
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function collectToolIds(items) {
    const ids = [];
    for (const item of items) {
        if (item.itemType === 'single') {
            ids.push(item.id);
        } else if (item.itemType === 'group') {
            ids.push(...item.children.map(child => child.id));
        }
    }
    return ids;
}

function isToolSelected(item) {
    if (!item) {
        return false;
    }
    if (item.itemType === 'single') {
        return selectedToolIds.value.includes(item.id);
    }
    if (item.itemType === 'group') {
        return isToolGroupSelected(item);
    }
    return false;
}

function isToolUnselected(item) {
    if (!item) {
        return false;
    }
    if (item.itemType === 'single') {
        return !selectedToolIds.value.includes(item.id);
    }
    if (item.itemType === 'group') {
        return !isToolGroupSelected(item);
    }
    return false;
}

function splitToolsByBinding(items) {
    const bound = [];
    const unbound = [];
    let currentBoundGroup = null;
    let currentUnboundGroup = null;

    for (const item of items) {
        if (item.itemType === 'group') {
            if (item.toolType === 'DATASET_TOOL') {
                if (isToolSelected(item)) {
                    bound.push(item);
                }
                if (isToolUnselected(item)) {
                    unbound.push(item);
                }
                currentBoundGroup = null;
                currentUnboundGroup = null;
                continue;
            }
            currentBoundGroup = cloneToolGroup(item);
            currentUnboundGroup = cloneToolGroup(item);
            continue;
        }
        if (item.itemType !== 'single') {
            continue;
        }
        if (selectedToolIds.value.includes(item.id)) {
            if (currentBoundGroup && !bound.includes(currentBoundGroup)) {
                bound.push(currentBoundGroup);
            }
            bound.push(item);
        } else {
            if (currentUnboundGroup && !unbound.includes(currentUnboundGroup)) {
                unbound.push(currentUnboundGroup);
            }
            unbound.push(item);
        }
    }
    return { bound, unbound };
}

function cloneToolGroup(group) {
    return {
        ...group,
        children: Array.isArray(group.children) ? [...group.children] : [],
    };
}

function toggleAllSkills() {
    const currentIds = new Set(filteredSkills.value.map(skill => skill.id));
    if (isAllSkillsSelected.value) {
        selectedSkillIds.value = selectedSkillIds.value.filter(id => !currentIds.has(id));
        return;
    }
    selectedSkillIds.value = Array.from(new Set([...selectedSkillIds.value, ...currentIds]));
}

function toggleAllTools() {
    const allIds = collectToolIds(groupedTools.value);
    if (isAllToolsSelected.value) {
        selectedToolIds.value = selectedToolIds.value.filter(id => !allIds.includes(id));
        return;
    }
    selectedToolIds.value = Array.from(new Set([...selectedToolIds.value, ...allIds]));
}

function toggleToolGroup(group) {
    const childIds = group.children.map(child => child.id);
    const allSelected = childIds.every(id => selectedToolIds.value.includes(id));
    if (allSelected) {
        selectedToolIds.value = selectedToolIds.value.filter(id => !childIds.includes(id));
        return;
    }
    selectedToolIds.value = Array.from(new Set([...selectedToolIds.value, ...childIds]));
}

function isToolGroupSelected(group) {
    return isGroupSelected(group, selectedToolIds.value);
}

function isToolGroupPartialSelected(group) {
    return isGroupPartialSelected(group, selectedToolIds.value);
}

async function loadRoleDetail() {
    const data = await getRoleDetail(props.roleId, handleUnauthorized);
    if (!data) {
        throw new Error('角色不存在');
    }
    detail.value = data;
}

async function loadSkills() {
    const data = await listSkillCatalog({}, handleUnauthorized);
    allSkills.value = data?.items || data || [];
}

async function loadTools() {
    const data = await listToolCatalog({}, handleUnauthorized);
    allTools.value = filterUserFacingTools(data?.items || data || []);
}

async function loadRoleResources() {
    const data = await getRoleResources(props.roleId, handleUnauthorized);
    selectedSkillIds.value = Array.isArray(data?.skillIds) ? data.skillIds : [];
    selectedToolIds.value = Array.isArray(data?.toolIds) ? data.toolIds : [];
}

async function loadPage() {
    loading.value = true;
    loadError.value = '';
    try {
        await Promise.all([loadRoleDetail(), loadSkills(), loadTools(), loadRoleResources()]);
    } catch (error) {
        loadError.value = error?.message || '加载资源权限失败';
    } finally {
        loading.value = false;
    }
}

async function handleSave() {
    if (saving.value || loading.value) {
        return;
    }
    saving.value = true;
    try {
        await updateRoleResources(
            props.roleId,
            {
                roleId: props.roleId,
                skillIds: selectedSkillIds.value,
                toolIds: selectedToolIds.value,
            },
            handleUnauthorized
        );
        showToast('资源权限已保存', 'success');
        emit('saved');
    } catch (error) {
        showToast(error?.message || '保存资源权限失败', 'error');
    } finally {
        saving.value = false;
    }
}

onMounted(() => {
    loadPage();
});
</script>

<template>
    <section
        class="flex h-full min-h-0 flex-col bg-slate-100"
        data-component="AdminRoleResourcePermissionPanel"
    >
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="min-w-0">
                    <div class="flex items-center gap-3">
                        <button
                            type="button"
                            class="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition hover:bg-slate-50"
                            @click="emit('back')"
                        >
                            <span class="material-symbols-outlined text-[20px]">arrow_back</span>
                        </button>
                        <div class="min-w-0">
                            <h2 class="truncate text-3xl font-bold tracking-tight text-slate-900">
                                资源权限 - {{ detail?.roleName || '角色' }}
                            </h2>
                            <p class="mt-2 text-sm text-slate-500">
                                已选 {{ selectedSkillIds.length }} 个技能、{{
                                    selectedToolIds.length
                                }}
                                个工具
                            </p>
                        </div>
                    </div>
                </div>
                <div class="flex items-center gap-3">
                    <button
                        type="button"
                        class="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="emit('back')"
                    >
                        返回列表
                    </button>
                    <button
                        type="button"
                        class="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="saving || loading"
                        @click="handleSave"
                    >
                        {{ saving ? '保存中...' : '保存资源权限' }}
                    </button>
                </div>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-6">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="rounded-[24px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400"
            >
                加载中...
            </div>

            <section v-else class="rounded-[24px] border border-slate-200 bg-white p-6 shadow-sm">
                <div class="mb-5 flex border-b border-slate-200">
                    <button
                        type="button"
                        :class="[
                            'px-4 py-2.5 text-sm font-medium transition-colors',
                            activeResourceTab === 'skills'
                                ? 'border-b-2 border-blue-600 text-blue-600'
                                : 'text-slate-500 hover:text-slate-700',
                        ]"
                        @click="activeResourceTab = 'skills'"
                    >
                        技能权限
                        <span
                            class="ml-1.5 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500"
                        >
                            {{ selectedSkillIds.length }}
                        </span>
                    </button>
                    <button
                        type="button"
                        :class="[
                            'px-4 py-2.5 text-sm font-medium transition-colors',
                            activeResourceTab === 'tools'
                                ? 'border-b-2 border-blue-600 text-blue-600'
                                : 'text-slate-500 hover:text-slate-700',
                        ]"
                        @click="activeResourceTab = 'tools'"
                    >
                        工具权限
                        <span
                            class="ml-1.5 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500"
                        >
                            {{ selectedToolIds.length }}
                        </span>
                    </button>
                </div>

                <div v-show="activeResourceTab === 'skills'">
                    <div
                        class="mb-4 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between"
                    >
                        <div class="flex flex-wrap gap-1.5">
                            <button
                                v-for="tab in skillTabs"
                                :key="tab.key"
                                type="button"
                                :class="[
                                    'rounded-lg px-2.5 py-1 text-xs font-medium transition-colors',
                                    activeSkillTab === tab.key
                                        ? 'bg-primary text-white shadow-sm'
                                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                                ]"
                                @click="activeSkillTab = tab.key"
                            >
                                {{ tab.label }}
                                <span class="ml-1 opacity-70">({{ tab.count }})</span>
                            </button>
                        </div>
                        <div class="flex items-center gap-2">
                            <input
                                v-model.trim="skillSearchKeyword"
                                type="text"
                                placeholder="搜索技能"
                                class="w-60 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                            />
                            <button
                                type="button"
                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                                @click="toggleAllSkills"
                            >
                                {{ isAllSkillsSelected ? '取消全选' : '全选当前' }}
                            </button>
                        </div>
                    </div>

                    <div class="space-y-5">
                        <section class="rounded-2xl border border-emerald-100 bg-emerald-50/40 p-4">
                            <div class="mb-3 flex items-center justify-between gap-3">
                                <h3 class="text-sm font-bold text-slate-900">已绑定技能</h3>
                                <span
                                    class="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-emerald-700"
                                >
                                    {{ boundSkills.length }} 个
                                </span>
                            </div>
                            <div
                                v-if="boundSkills.length"
                                class="grid gap-3 md:grid-cols-2 xl:grid-cols-4"
                            >
                                <label
                                    v-for="skill in boundSkills"
                                    :key="skill.id"
                                    class="flex cursor-pointer gap-3 rounded-xl border border-emerald-100 bg-white p-4 transition hover:shadow-sm"
                                >
                                    <input
                                        v-model="selectedSkillIds"
                                        type="checkbox"
                                        :value="skill.id"
                                        class="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                    />
                                    <div class="min-w-0 flex-1">
                                        <h3 class="truncate text-sm font-semibold text-slate-900">
                                            {{ skill.displayName || skill.runtimeSkillName }}
                                        </h3>
                                        <p class="mt-1 truncate text-xs text-slate-400">
                                            {{ skill.runtimeSkillName || skill.category || '-' }}
                                        </p>
                                        <p
                                            class="mt-2 line-clamp-2 min-h-[2.5rem] text-xs leading-5 text-slate-500"
                                        >
                                            {{ skill.description || '暂无描述' }}
                                        </p>
                                    </div>
                                </label>
                            </div>
                            <div
                                v-else
                                class="rounded-xl border border-dashed border-emerald-100 bg-white/70 px-4 py-8 text-center text-sm text-slate-400"
                            >
                                当前筛选条件下没有已绑定技能
                            </div>
                        </section>

                        <section class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                            <div class="mb-3 flex items-center justify-between gap-3">
                                <h3 class="text-sm font-bold text-slate-900">未绑定技能</h3>
                                <span
                                    class="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-slate-600"
                                >
                                    {{ unboundSkills.length }} 个
                                </span>
                            </div>
                            <div
                                v-if="unboundSkills.length"
                                class="grid gap-3 md:grid-cols-2 xl:grid-cols-4"
                            >
                                <label
                                    v-for="skill in unboundSkills"
                                    :key="skill.id"
                                    class="flex cursor-pointer gap-3 rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-sm"
                                >
                                    <input
                                        v-model="selectedSkillIds"
                                        type="checkbox"
                                        :value="skill.id"
                                        class="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                    />
                                    <div class="min-w-0 flex-1">
                                        <h3 class="truncate text-sm font-semibold text-slate-900">
                                            {{ skill.displayName || skill.runtimeSkillName }}
                                        </h3>
                                        <p class="mt-1 truncate text-xs text-slate-400">
                                            {{ skill.runtimeSkillName || skill.category || '-' }}
                                        </p>
                                        <p
                                            class="mt-2 line-clamp-2 min-h-[2.5rem] text-xs leading-5 text-slate-500"
                                        >
                                            {{ skill.description || '暂无描述' }}
                                        </p>
                                    </div>
                                </label>
                            </div>
                            <div
                                v-else
                                class="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-8 text-center text-sm text-slate-400"
                            >
                                当前筛选条件下没有未绑定技能
                            </div>
                        </section>
                    </div>
                </div>

                <div v-show="activeResourceTab === 'tools'">
                    <div
                        class="mb-4 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between"
                    >
                        <div class="flex flex-wrap gap-1.5">
                            <button
                                v-for="tab in toolTabs"
                                :key="tab.key"
                                type="button"
                                :class="[
                                    'rounded-lg px-2.5 py-1 text-xs font-medium transition-colors',
                                    activeToolTab === tab.key
                                        ? 'bg-primary text-white shadow-sm'
                                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                                ]"
                                @click="activeToolTab = tab.key"
                            >
                                {{ tab.label }}
                                <span class="ml-1 opacity-70">({{ tab.count }})</span>
                            </button>
                        </div>
                        <div class="flex items-center gap-2">
                            <input
                                v-model.trim="toolSearchKeyword"
                                type="text"
                                placeholder="搜索工具"
                                class="w-60 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                            />
                            <button
                                type="button"
                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                                @click="toggleAllTools"
                            >
                                {{ isAllToolsSelected ? '取消全选' : '全选' }}
                            </button>
                        </div>
                    </div>

                    <div class="space-y-5">
                        <section class="rounded-2xl border border-emerald-100 bg-emerald-50/40 p-4">
                            <div class="mb-3 flex items-center justify-between gap-3">
                                <h3 class="text-sm font-bold text-slate-900">已绑定工具</h3>
                                <span
                                    class="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-emerald-700"
                                >
                                    {{ boundTools.length }} 项
                                </span>
                            </div>
                            <div
                                v-if="boundTools.length"
                                class="grid gap-3 md:grid-cols-2 xl:grid-cols-4"
                            >
                                <template
                                    v-for="item in boundTools"
                                    :key="`bound-${item.id || item.groupKey}`"
                                >
                                    <article
                                        v-if="
                                            item.itemType === 'group' &&
                                            item.toolType === 'DATASET_TOOL'
                                        "
                                        class="flex cursor-pointer items-center gap-3 rounded-xl border border-emerald-100 bg-white p-4 transition hover:shadow-sm"
                                        @click="toggleToolGroup(item)"
                                    >
                                        <input
                                            type="checkbox"
                                            :checked="isToolGroupSelected(item)"
                                            :indeterminate="isToolGroupPartialSelected(item)"
                                            class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                            @click.stop
                                            @change="toggleToolGroup(item)"
                                        />
                                        <div class="min-w-0 flex-1">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h3
                                                    class="truncate text-sm font-semibold text-slate-900"
                                                >
                                                    {{ item.displayName }}
                                                </h3>
                                                <span
                                                    class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700"
                                                >
                                                    数据集工具组
                                                </span>
                                                <span
                                                    class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600"
                                                >
                                                    {{ item.children.length }} 个子工具
                                                </span>
                                            </div>
                                            <p class="mt-2 truncate text-xs text-slate-500">
                                                {{ getChildrenNames(item.children, 'tool') }}
                                            </p>
                                        </div>
                                    </article>
                                    <div
                                        v-else-if="item.itemType === 'group'"
                                        class="col-span-full flex items-center gap-2 rounded-xl border border-emerald-100 bg-white px-4 py-3"
                                    >
                                        <span class="text-sm font-semibold text-slate-700">{{
                                            item.displayName
                                        }}</span>
                                        <span
                                            class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600"
                                        >
                                            {{ item.children.length }} 个
                                        </span>
                                    </div>
                                    <label
                                        v-else-if="item.itemType === 'single'"
                                        class="flex cursor-pointer gap-3 rounded-xl border border-emerald-100 bg-white p-4 transition hover:shadow-sm"
                                    >
                                        <input
                                            v-model="selectedToolIds"
                                            type="checkbox"
                                            :value="item.id"
                                            class="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                        />
                                        <div class="min-w-0 flex-1">
                                            <h3
                                                class="truncate text-sm font-semibold text-slate-900"
                                            >
                                                {{ item.displayName || item.name }}
                                            </h3>
                                            <p class="mt-1 truncate text-xs text-slate-400">
                                                {{ item.name || item.type || '-' }}
                                            </p>
                                            <p
                                                class="mt-2 line-clamp-2 text-xs leading-5 text-slate-500"
                                            >
                                                {{ item.description || '暂无描述' }}
                                            </p>
                                        </div>
                                    </label>
                                </template>
                            </div>
                            <div
                                v-else
                                class="rounded-xl border border-dashed border-emerald-100 bg-white/70 px-4 py-8 text-center text-sm text-slate-400"
                            >
                                当前筛选条件下没有已绑定工具
                            </div>
                        </section>

                        <section class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                            <div class="mb-3 flex items-center justify-between gap-3">
                                <h3 class="text-sm font-bold text-slate-900">未绑定工具</h3>
                                <span
                                    class="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-slate-600"
                                >
                                    {{ unboundTools.length }} 项
                                </span>
                            </div>
                            <div
                                v-if="unboundTools.length"
                                class="grid gap-3 md:grid-cols-2 xl:grid-cols-4"
                            >
                                <template
                                    v-for="item in unboundTools"
                                    :key="`unbound-${item.id || item.groupKey}`"
                                >
                                    <article
                                        v-if="
                                            item.itemType === 'group' &&
                                            item.toolType === 'DATASET_TOOL'
                                        "
                                        class="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-sm"
                                        @click="toggleToolGroup(item)"
                                    >
                                        <input
                                            type="checkbox"
                                            :checked="isToolGroupSelected(item)"
                                            :indeterminate="isToolGroupPartialSelected(item)"
                                            class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                            @click.stop
                                            @change="toggleToolGroup(item)"
                                        />
                                        <div class="min-w-0 flex-1">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h3
                                                    class="truncate text-sm font-semibold text-slate-900"
                                                >
                                                    {{ item.displayName }}
                                                </h3>
                                                <span
                                                    class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700"
                                                >
                                                    数据集工具组
                                                </span>
                                                <span
                                                    class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600"
                                                >
                                                    {{ item.children.length }} 个子工具
                                                </span>
                                            </div>
                                            <p class="mt-2 truncate text-xs text-slate-500">
                                                {{ getChildrenNames(item.children, 'tool') }}
                                            </p>
                                        </div>
                                    </article>
                                    <div
                                        v-else-if="item.itemType === 'group'"
                                        class="col-span-full flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3"
                                    >
                                        <span class="text-sm font-semibold text-slate-700">{{
                                            item.displayName
                                        }}</span>
                                        <span
                                            class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600"
                                        >
                                            {{ item.children.length }} 个
                                        </span>
                                    </div>
                                    <label
                                        v-else-if="item.itemType === 'single'"
                                        class="flex cursor-pointer gap-3 rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-sm"
                                    >
                                        <input
                                            v-model="selectedToolIds"
                                            type="checkbox"
                                            :value="item.id"
                                            class="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600/30"
                                        />
                                        <div class="min-w-0 flex-1">
                                            <h3
                                                class="truncate text-sm font-semibold text-slate-900"
                                            >
                                                {{ item.displayName || item.name }}
                                            </h3>
                                            <p class="mt-1 truncate text-xs text-slate-400">
                                                {{ item.name || item.type || '-' }}
                                            </p>
                                            <p
                                                class="mt-2 line-clamp-2 text-xs leading-5 text-slate-500"
                                            >
                                                {{ item.description || '暂无描述' }}
                                            </p>
                                        </div>
                                    </label>
                                </template>
                            </div>
                            <div
                                v-else
                                class="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-8 text-center text-sm text-slate-400"
                            >
                                当前筛选条件下没有未绑定工具
                            </div>
                        </section>
                    </div>
                </div>
            </section>
        </div>
    </section>
</template>
