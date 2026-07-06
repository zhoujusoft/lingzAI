<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listSkillTools, updateSkillTool, batchUpdateSkillTools } from '@/api/skills';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { alert } from '@/composables/useModal';
import { USER_TYPES } from '@/model/enums/user-type';
import {
    getResourcePermissionBadgeClass,
    getResourcePermissionLabel,
} from '@/model/resource-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const searchKeyword = ref('');
const typeFilter = ref('ALL');
const tools = ref([]);
const savingToolNames = ref([]);

const toolIcons = {
    GLOBAL: 'build',
    RUNTIME: 'terminal',
    SKILL_NATIVE: 'smart_toy',
    MCP_REMOTE: 'hub',
    LOWCODE_API: 'api',
    DATASET_TOOL: 'database',
    KNOWLEDGE_BASE_TOOL: 'library_books',
};

const toolColorClass = {
    GLOBAL: 'from-blue-500 via-blue-500 to-cyan-400',
    RUNTIME: 'from-rose-500 via-orange-500 to-amber-400',
    SKILL_NATIVE: 'from-slate-600 via-slate-500 to-slate-400',
    MCP_REMOTE: 'from-blue-600 via-blue-500 to-indigo-400',
    LOWCODE_API: 'from-emerald-500 via-teal-500 to-cyan-400',
    DATASET_TOOL: 'from-amber-500 via-orange-500 to-yellow-400',
    KNOWLEDGE_BASE_TOOL: 'from-blue-500 via-blue-500 to-cyan-400',
};

const visibleTools = computed(() => tools.value.filter(item => item.type !== 'SKILL_NATIVE'));

// 工具分组逻辑
const groupedTools = computed(() => {
    const groups = new Map();
    const singles = [];

    for (const tool of visibleTools.value) {
        // 识别数据集工具（source 格式：dataset:{datasetCode}）
        if (tool.type === 'DATASET_TOOL' && tool.source?.startsWith('dataset:')) {
            const groupKey = tool.source;
            if (!groups.has(groupKey)) {
                // 从 displayName 提取数据集名称（格式："数据集名 / 功能名"）
                const datasetName = tool.displayName?.split('/')[0]?.trim() || '未知数据集';
                groups.set(groupKey, {
                    type: 'group',
                    groupKey: groupKey,
                    displayName: datasetName,
                    children: [],
                    permissionScope: tool.permissionScope,
                    source: tool.source,
                });
            }
            groups.get(groupKey).children.push(tool);
        } else {
            // 普通工具
            singles.push({
                ...tool,
                type: 'single',
                toolType: tool.type, // 保存原始类型用于过滤
            });
        }
    }

    // 合并：普通工具 + 工具组
    return [...singles, ...Array.from(groups.values())];
});

const filteredTools = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    return groupedTools.value.filter(item => {
        // 类型过滤
        const matchesType =
            typeFilter.value === 'ALL' ||
            (item.type === 'group' && typeFilter.value === 'DATASET_TOOL') ||
            (item.type === 'single' && item.toolType === typeFilter.value);

        if (!matchesType) return false;

        // 关键词搜索
        if (!keyword) return true;

        // 普通工具：直接匹配
        if (item.type === 'single') {
            return `${item.displayName || ''} ${item.name || ''} ${item.description || ''} ${item.ownerSkillName || ''} ${item.ownerSkillDisplayName || ''} ${item.source || ''}`
                .toLowerCase()
                .includes(keyword);
        }

        // 工具组：匹配组名或子工具
        if (item.type === 'group') {
            const matchGroup =
                item.displayName?.toLowerCase().includes(keyword) ||
                item.description?.toLowerCase().includes(keyword);
            const matchChildren = item.children.some(
                child =>
                    child.displayName?.toLowerCase().includes(keyword) ||
                    child.name?.toLowerCase().includes(keyword)
            );
            return matchGroup || matchChildren;
        }

        return false;
    });
});

const globalToolCount = computed(
    () => visibleTools.value.filter(item => item.type === 'GLOBAL').length
);
const runtimeToolCount = computed(
    () => visibleTools.value.filter(item => item.type === 'RUNTIME').length
);
const remoteToolCount = computed(
    () => visibleTools.value.filter(item => item.type === 'MCP_REMOTE').length
);
const lowcodeToolCount = computed(
    () => visibleTools.value.filter(item => item.type === 'LOWCODE_API').length
);
// 数据集工具：统计工具组数量（而不是子工具数量）
const datasetToolCount = computed(() => {
    const groups = new Set();
    visibleTools.value.forEach(item => {
        if (item.type === 'DATASET_TOOL' && item.source?.startsWith('dataset:')) {
            groups.add(item.source);
        }
    });
    return groups.size;
});
const knowledgeBaseToolCount = computed(
    () => visibleTools.value.filter(item => item.type === 'KNOWLEDGE_BASE_TOOL').length
);
const isCurrentUserAdmin = computed(
    () => Number(currentUserState.profile?.userType) === Number(USER_TYPES.ADMIN)
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadTools() {
    loading.value = true;
    loadError.value = '';
    try {
        const toolData = await listSkillTools(handleUnauthorized);
        tools.value = Array.isArray(toolData) ? toolData : [];
    } catch (error) {
        loadError.value = error?.message || '工具库加载失败';
    } finally {
        loading.value = false;
    }
}

function getToolIcon(type) {
    return toolIcons[type] || 'construction';
}

function getToolColorClass(type) {
    return toolColorClass[type] || toolColorClass.SKILL_NATIVE;
}

function getToolTypeLabel(type) {
    if (type === 'GLOBAL') {
        return '公共工具';
    }
    if (type === 'RUNTIME') {
        return '运行时工具';
    }
    if (type === 'SKILL_NATIVE') {
        return '原生工具';
    }
    if (type === 'MCP_REMOTE') {
        return 'MCP 远程工具';
    }
    if (type === 'LOWCODE_API') {
        return '低代码 API 工具';
    }
    if (type === 'DATASET_TOOL') {
        return '数据集工具';
    }
    if (type === 'KNOWLEDGE_BASE_TOOL') {
        return '知识库工具';
    }
    return '其他工具';
}

function getToolTypeBadgeClass(type) {
    if (type === 'GLOBAL') {
        return 'bg-emerald-50 text-emerald-600';
    }
    if (type === 'RUNTIME') {
        return 'bg-rose-50 text-rose-600';
    }
    if (type === 'SKILL_NATIVE') {
        return 'bg-slate-100 text-slate-600';
    }
    if (type === 'MCP_REMOTE') {
        return 'bg-indigo-50 text-indigo-600';
    }
    if (type === 'LOWCODE_API') {
        return 'bg-emerald-50 text-emerald-600';
    }
    if (type === 'DATASET_TOOL') {
        return 'bg-amber-50 text-amber-600';
    }
    if (type === 'KNOWLEDGE_BASE_TOOL') {
        return 'bg-blue-50 text-blue-600';
    }
    return 'bg-amber-50 text-amber-600';
}

function getServerKeyFromSource(source = '') {
    return source.startsWith('mcp:') ? source.slice(4) : '';
}

function toolPreviewText(tool) {
    return String(tool?.description || '')
        .replace(/\s*\n+\s*/g, ' ')
        .replace(/\s{2,}/g, ' ')
        .trim();
}

function isSavingTool(toolName) {
    return savingToolNames.value.includes(toolName);
}

function isGlobalToggleDisabled(tool) {
    return (
        !isCurrentUserAdmin.value || !tool?.globalAvailabilityEditable || isSavingTool(tool.name)
    );
}

function globalAvailabilityText(tool) {
    if (tool?.type === 'RUNTIME') {
        return '系统运行时固定提供';
    }
    if (!isCurrentUserAdmin.value) {
        return '仅管理员用户可配置';
    }
    return tool?.enabledGlobal ? '已加入全局对话' : '仅按需绑定/使用';
}

function hasPermissionScope(tool) {
    const toolType = tool?.toolType || tool?.type;
    return (
        toolType === 'KNOWLEDGE_BASE_TOOL' ||
        toolType === 'DATASET_TOOL' ||
        toolType === 'MCP_REMOTE' ||
        toolType === 'LOWCODE_API'
    );
}

function permissionScopeLabel(tool) {
    return getResourcePermissionLabel(tool?.permissionScope);
}

function permissionScopeClass(tool) {
    return getResourcePermissionBadgeClass(tool?.permissionScope);
}

// 工具组相关方法
function isAllGroupGlobalEnabled(group) {
    return group.children.every(c => c.enabledGlobal);
}

function isPartialGroupGlobalEnabled(group) {
    const enabledCount = group.children.filter(c => c.enabledGlobal).length;
    return enabledCount > 0 && enabledCount < group.children.length;
}

function getGroupGlobalEnabledCount(group) {
    return group.children.filter(c => c.enabledGlobal).length;
}

function groupGlobalAvailabilityText(group) {
    if (!isCurrentUserAdmin.value) {
        return '仅管理员用户可配置';
    }

    const total = group.children.length;
    const enabled = getGroupGlobalEnabledCount(group);

    if (enabled === total) {
        return `全部已加入全局对话 (${enabled}/${total})`;
    } else if (enabled > 0) {
        return `部分已加入全局对话 (${enabled}/${total})`;
    } else {
        return `未加入全局对话 (0/${total})`;
    }
}

function getChildToolShortName(child) {
    const parts = child.displayName?.split('/') || [];
    return parts[1]?.trim() || child.displayName || child.name;
}

async function toggleGlobalAvailability(tool) {
    if (
        !isCurrentUserAdmin.value ||
        !tool?.name ||
        !tool?.globalAvailabilityEditable ||
        isSavingTool(tool.name)
    ) {
        return;
    }
    const nextEnabled = !tool.enabledGlobal;
    savingToolNames.value = [...savingToolNames.value, tool.name];
    try {
        const updated = await updateSkillTool(
            tool.name,
            { enabledGlobal: nextEnabled },
            handleUnauthorized
        );
        tools.value = tools.value.map(item =>
            item?.name === tool.name
                ? {
                      ...item,
                      enabledGlobal: updated?.enabledGlobal === true,
                      globalAvailabilityEditable: updated?.globalAvailabilityEditable !== false,
                  }
                : item
        );
    } catch (error) {
        await alert({
            title: '更新失败',
            message: error?.message || '工具全局可用状态更新失败',
        });
    } finally {
        savingToolNames.value = savingToolNames.value.filter(name => name !== tool.name);
    }
}

// 工具组批量操作
async function toggleGroupGlobalAvailability(group) {
    if (!isCurrentUserAdmin.value) {
        return;
    }

    const allEnabled = isAllGroupGlobalEnabled(group);
    const nextEnabled = !allEnabled;

    // 需要更新的子工具名称
    const childNames = group.children
        .filter(c => c.globalAvailabilityEditable && c.enabledGlobal !== nextEnabled)
        .map(c => c.name);

    if (childNames.length === 0) {
        return;
    }

    savingToolNames.value = [...savingToolNames.value, ...childNames];

    try {
        const updatedItems = await batchUpdateSkillTools(
            childNames,
            { enabledGlobal: nextEnabled },
            handleUnauthorized
        );

        // 用后端返回的最新数据更新本地状态
        const updatedMap = new Map(updatedItems.map(item => [item.name, item]));
        tools.value = tools.value.map(item =>
            updatedMap.has(item.name) ? { ...item, ...updatedMap.get(item.name) } : item
        );
    } catch (error) {
        await alert({
            title: '批量更新失败',
            message: error?.message || '工具组全局可用状态更新失败',
        });
    } finally {
        savingToolNames.value = savingToolNames.value.filter(name => !childNames.includes(name));
    }
}

function openMcpManagement() {
    router.push({ path: ROUTE_PATHS.adminMcpManagement });
}

onMounted(() => {
    loadTools();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-6 py-4">
            <div>
                <div>
                    <h2
                        class="text-[28px] font-semibold leading-tight tracking-tight text-slate-900"
                    >
                        工具库
                    </h2>
                    <p class="mt-1.5 max-w-3xl text-[13px] leading-5 text-slate-500">
                        展示当前项目已注册的公共工具、运行时工具、MCP 远程工具、低代码 API
                        工具、数据集工具和知识库工具，并统一设置它们是否全局进入对话。
                    </p>
                </div>
            </div>

            <div class="mt-4 grid gap-2 grid-cols-2 sm:grid-cols-4 lg:grid-cols-7">
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ groupedTools.length }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">工具总数</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ globalToolCount }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">公共工具</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ runtimeToolCount }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">运行时工具</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ remoteToolCount }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">MCP 远程</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ lowcodeToolCount }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">低代码 API</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">{{ datasetToolCount }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">数据集</p>
                </article>
                <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p class="text-base font-semibold text-slate-900">
                        {{ knowledgeBaseToolCount }}
                    </p>
                    <p class="mt-0.5 text-xs text-slate-500">知识库</p>
                </article>
            </div>
        </header>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-5">
            <p
                v-if="loadError"
                class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
            >
                {{ loadError }}
            </p>
            <div
                v-if="loading"
                class="rounded-2xl border border-slate-200 bg-white px-5 py-8 text-sm text-slate-400 shadow-sm"
            >
                加载中...
            </div>

            <div v-else class="space-y-6">
                <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                        <div>
                            <h3 class="text-base font-semibold text-slate-900">工具列表</h3>
                            <p class="mt-1 text-[13px] leading-5 text-slate-500">
                                公共工具可直接绑定给
                                skill，运行时工具仅供系统运行期使用，不会进入技能绑定列表；MCP
                                远程工具由独立的 MCP
                                服务页负责连接配置与目录刷新，全局可用状态统一在这里设置。数据集工具以工具组形式展示，点击展开可查看子工具。
                            </p>
                        </div>

                        <div
                            class="flex w-full max-w-2xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-end"
                        >
                            <button
                                type="button"
                                class="inline-flex h-9 items-center justify-center rounded-xl border border-blue-200 bg-blue-50 px-3.5 text-[13px] font-semibold text-blue-700 transition hover:bg-blue-100"
                                @click="openMcpManagement"
                            >
                                前往 MCP 服务管理
                            </button>
                            <div class="relative flex-1">
                                <span
                                    class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                                >
                                    search
                                </span>
                                <input
                                    v-model.trim="searchKeyword"
                                    type="text"
                                    placeholder="搜索工具名称、描述、来源"
                                    class="h-9 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3 text-[13px] outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                                />
                            </div>
                        </div>
                    </div>

                    <div class="mt-4 flex flex-wrap gap-1.5">
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'ALL'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'ALL'"
                        >
                            全部
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'GLOBAL'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'GLOBAL'"
                        >
                            公共工具
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'RUNTIME'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'RUNTIME'"
                        >
                            运行时工具
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'MCP_REMOTE'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'MCP_REMOTE'"
                        >
                            MCP 远程
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'LOWCODE_API'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'LOWCODE_API'"
                        >
                            低代码 API
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'DATASET_TOOL'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'DATASET_TOOL'"
                        >
                            数据集
                        </button>
                        <button
                            type="button"
                            class="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors"
                            :class="
                                typeFilter === 'KNOWLEDGE_BASE_TOOL'
                                    ? 'bg-primary text-white shadow-sm'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            "
                            @click="typeFilter = 'KNOWLEDGE_BASE_TOOL'"
                        >
                            知识库
                        </button>
                    </div>

                    <div
                        class="mt-6 grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"
                    >
                        <template v-for="item in filteredTools" :key="item.id || item.groupKey">
                            <!-- 普通工具卡片 -->
                            <article
                                v-if="item.type === 'single'"
                                class="rounded-xl border border-slate-200 bg-slate-50 p-3 transition hover:-translate-y-0.5 hover:shadow-md"
                            >
                                <div class="flex items-center gap-2.5">
                                    <div
                                        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow"
                                        :class="getToolColorClass(item.toolType)"
                                    >
                                        <span class="material-symbols-outlined text-[16px]">
                                            {{ getToolIcon(item.toolType) }}
                                        </span>
                                    </div>
                                    <h4
                                        class="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900"
                                    >
                                        {{ item.displayName || item.name }}
                                    </h4>
                                    <button
                                        v-if="item.globalAvailabilityEditable"
                                        type="button"
                                        class="relative shrink-0 inline-flex h-5 w-9 items-center rounded-full transition"
                                        :class="
                                            item.enabledGlobal ? 'bg-emerald-500' : 'bg-slate-300'
                                        "
                                        :disabled="isGlobalToggleDisabled(item)"
                                        @click.stop="toggleGlobalAvailability(item)"
                                    >
                                        <span
                                            class="inline-block h-4 w-4 rounded-full bg-white shadow transition"
                                            :class="
                                                item.enabledGlobal
                                                    ? 'translate-x-4'
                                                    : 'translate-x-0.5'
                                            "
                                        />
                                    </button>
                                </div>
                                <div class="mt-1.5 flex items-center gap-1">
                                    <span
                                        class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="getToolTypeBadgeClass(item.toolType)"
                                    >
                                        {{ getToolTypeLabel(item.toolType) }}
                                    </span>
                                    <span
                                        v-if="hasPermissionScope(item)"
                                        class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="permissionScopeClass(item)"
                                    >
                                        {{ permissionScopeLabel(item) }}
                                    </span>
                                    <span
                                        v-if="
                                            item.toolType === 'MCP_REMOTE' &&
                                            getServerKeyFromSource(item.source)
                                        "
                                        class="shrink-0 rounded-full bg-blue-50 px-1.5 py-0.5 text-[10px] font-medium text-primary"
                                    >
                                        {{ getServerKeyFromSource(item.source) }}
                                    </span>
                                </div>
                            </article>

                            <!-- 工具组卡片（数据集工具） -->
                            <article
                                v-else-if="item.type === 'group'"
                                class="rounded-xl border-2 border-amber-200 bg-gradient-to-br from-amber-50 to-orange-50 p-3 transition hover:-translate-y-0.5 hover:shadow-md"
                            >
                                <div class="flex items-center gap-2.5">
                                    <div
                                        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-amber-500 via-orange-500 to-yellow-400 text-white shadow"
                                    >
                                        <span class="material-symbols-outlined text-[16px]">
                                            database
                                        </span>
                                    </div>
                                    <h4
                                        class="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900"
                                    >
                                        {{ item.displayName }}
                                    </h4>
                                    <button
                                        type="button"
                                        class="relative shrink-0 inline-flex h-5 w-9 items-center rounded-full transition"
                                        :class="
                                            isAllGroupGlobalEnabled(item)
                                                ? 'bg-emerald-500'
                                                : isPartialGroupGlobalEnabled(item)
                                                  ? 'bg-amber-400'
                                                  : 'bg-slate-300'
                                        "
                                        :disabled="
                                            !isCurrentUserAdmin ||
                                            savingToolNames.some(n =>
                                                item.children.some(c => c.name === n)
                                            )
                                        "
                                        @click.stop="toggleGroupGlobalAvailability(item)"
                                    >
                                        <span
                                            class="inline-block h-4 w-4 rounded-full bg-white shadow transition"
                                            :class="
                                                isAllGroupGlobalEnabled(item)
                                                    ? 'translate-x-4'
                                                    : 'translate-x-0.5'
                                            "
                                        />
                                    </button>
                                </div>
                                <div class="mt-1.5 flex items-center gap-1">
                                    <span
                                        class="shrink-0 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700"
                                    >
                                        数据集工具组
                                    </span>
                                    <span
                                        class="shrink-0 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700"
                                    >
                                        {{ item.children.length }}个工具
                                    </span>
                                    <span
                                        v-if="hasPermissionScope(item)"
                                        class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                                        :class="permissionScopeClass(item)"
                                    >
                                        {{ permissionScopeLabel(item) }}
                                    </span>
                                </div>
                            </article>
                        </template>

                        <div
                            v-if="!filteredTools.length"
                            class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-5 py-12 text-center text-sm text-slate-400 md:col-span-2 2xl:col-span-3"
                        >
                            当前筛选条件下没有匹配工具
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>
