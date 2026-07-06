<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listEnabledExpertPackages } from '@/api/agents';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    groupDatasetTools,
    resolveGroupDescription,
    resolveToolIcon,
    resolveToolTypeLabel,
} from '@/utils/toolVisuals';

const emit = defineEmits(['unauthorized']);
const router = useRouter();
const packages = ref([]);
const loading = ref(false);
const loadError = ref('');
const keyword = ref('');

const filteredPackages = computed(() => {
    const normalizedKeyword = keyword.value.trim().toLowerCase();
    if (!normalizedKeyword) {
        return packages.value;
    }
    return packages.value.filter(item => {
        const skillText = (item.skills || [])
            .map(skill => `${skill.displayName || ''} ${skill.category || ''}`)
            .join(' ');
        const toolText = (item.tools || [])
            .map(tool => `${tool.displayName || ''} ${tool.toolName || ''}`)
            .join(' ');
        return `${item.agentName || ''} ${item.agentCode || ''} ${item.description || ''} ${skillText} ${toolText}`
            .toLowerCase()
            .includes(normalizedKeyword);
    });
});

function emitUnauthorized() {
    emit('unauthorized');
}

function resolvePackageIcon(item) {
    const icon = String(item?.icon || '').trim();
    return icon || 'psychology';
}

function isMaterialIcon(icon) {
    return /^[a-z][a-z0-9_]*$/.test(String(icon || '').trim());
}

function visibleSkills(item) {
    return Array.isArray(item?.skills) ? item.skills.slice(0, 4) : [];
}

function visibleTools(item) {
    return packageToolVisuals(item).slice(0, 4);
}

function packageToolVisuals(item) {
    const tools = Array.isArray(item?.tools)
        ? item.tools.map(tool => ({
              ...tool,
              type: tool.toolType,
          }))
        : [];
    return groupDatasetTools(tools);
}

function toolVisualKey(tool) {
    return tool.itemType === 'group' ? tool.groupKey : tool.id;
}

function toolVisualName(tool) {
    return tool.displayName || tool.toolName || '未命名工具';
}

function toolVisualDescription(tool) {
    return tool.itemType === 'group'
        ? resolveGroupDescription(tool)
        : tool.description || resolveToolTypeLabel(tool.toolType);
}

function toolVisualType(tool) {
    return tool.itemType === 'group' ? 'DATASET_TOOL' : tool.toolType;
}

function remainingToolCount(item) {
    return Math.max(0, packageToolVisuals(item).length - 4);
}

function remainingCount(items) {
    return Math.max(0, (Array.isArray(items) ? items.length : 0) - 4);
}

function openPackageChat(item) {
    if (!item?.id) {
        return;
    }
    router.push({
        path: ROUTE_PATHS.frontChat,
        query: {
            expertPackageId: item.id,
        },
    });
}

async function loadPackages() {
    loading.value = true;
    loadError.value = '';
    try {
        const data = await listEnabledExpertPackages(emitUnauthorized);
        packages.value = Array.isArray(data) ? data : [];
    } catch (error) {
        packages.value = [];
        loadError.value = error?.message || '加载专家技能包失败';
    } finally {
        loading.value = false;
    }
}

onMounted(() => {
    loadPackages();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col gap-3 text-strong">
        <div class="flex shrink-0 items-center justify-between gap-4 px-1">
            <div class="flex items-center gap-2 text-xs text-muted">
                <span class="material-symbols-outlined text-base text-primary">psychology</span>
                <span>共 {{ packages.length }} 个可用专家技能包</span>
            </div>
            <div class="relative w-48 shrink-0 sm:w-64">
                <span
                    class="material-symbols-outlined pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-base text-muted"
                >
                    search
                </span>
                <input
                    v-model.trim="keyword"
                    type="text"
                    placeholder="搜索专家包、技能或工具"
                    class="w-full rounded-lg border border-border-soft bg-surface py-2 pl-8 pr-3 text-sm text-strong outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                />
            </div>
        </div>

        <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto px-1 pb-3">
            <p
                v-if="loadError"
                class="mb-3 rounded-lg border border-danger/15 bg-danger/10 px-3 py-2 text-sm text-danger"
            >
                {{ loadError }}
            </p>

            <div
                v-if="loading"
                class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4"
            >
                <article
                    v-for="index in 6"
                    :key="index"
                    class="front-card min-h-[236px] animate-pulse p-3"
                >
                    <div class="h-9 w-9 rounded-lg bg-slate-100"></div>
                    <div class="mt-3 h-3.5 w-2/3 rounded bg-slate-100"></div>
                    <div class="mt-2 h-3 w-full rounded bg-slate-100"></div>
                    <div class="mt-1.5 h-3 w-5/6 rounded bg-slate-100"></div>
                    <div class="mt-4 grid grid-cols-2 gap-2">
                        <div class="h-16 rounded-lg bg-slate-100"></div>
                        <div class="h-16 rounded-lg bg-slate-100"></div>
                    </div>
                </article>
            </div>

            <div
                v-else
                class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4"
            >
                <article
                    v-for="item in filteredPackages"
                    :key="item.id"
                    class="front-card group flex min-h-[236px] flex-col overflow-hidden transition-colors hover:border-primary/40"
                >
                    <div class="flex items-start gap-2.5 border-b border-border-soft/50 p-3">
                        <div
                            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary text-white shadow-sm"
                        >
                            <span
                                v-if="isMaterialIcon(resolvePackageIcon(item))"
                                class="material-symbols-outlined text-lg"
                            >
                                {{ resolvePackageIcon(item) }}
                            </span>
                            <span v-else class="text-lg">{{ resolvePackageIcon(item) }}</span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <div class="flex items-center justify-between gap-2">
                                <div class="min-w-0">
                                    <h3 class="truncate text-sm font-semibold text-strong">
                                        {{ item.agentName || '未命名专家包' }}
                                    </h3>
                                    <p class="mt-0.5 truncate text-[11px] text-muted">
                                        {{ item.agentCode || '未设置编码' }}
                                    </p>
                                </div>
                                <span
                                    class="shrink-0 rounded bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-600"
                                >
                                    可用
                                </span>
                            </div>
                            <p class="mt-1.5 line-clamp-2 text-xs leading-4 text-body">
                                {{ item.description || '暂无场景描述' }}
                            </p>
                        </div>
                    </div>

                    <div class="grid flex-1 gap-2 p-3">
                        <section class="rounded-lg bg-surface-alt/45 px-2.5 py-2">
                            <div class="flex items-center justify-between gap-2">
                                <h4
                                    class="flex items-center gap-1.5 text-[11px] font-semibold text-strong"
                                >
                                    <span class="material-symbols-outlined text-sm text-primary">
                                        description
                                    </span>
                                    包含技能
                                </h4>
                                <span class="text-[11px] text-muted">
                                    {{ item.skills?.length || 0 }} 项
                                </span>
                            </div>
                            <div class="mt-1.5 flex min-h-6 flex-wrap gap-1">
                                <span
                                    v-for="skill in visibleSkills(item)"
                                    :key="skill.id"
                                    class="inline-flex max-w-full items-center gap-1 rounded bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-primary"
                                    :title="skill.description || skill.displayName"
                                >
                                    <span class="material-symbols-outlined text-xs">
                                        description
                                    </span>
                                    <span class="max-w-24 truncate">
                                        {{ skill.displayName || skill.runtimeSkillName }}
                                    </span>
                                </span>
                                <span
                                    v-if="remainingCount(item.skills)"
                                    class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-muted"
                                >
                                    +{{ remainingCount(item.skills) }}
                                </span>
                                <span
                                    v-if="!item.skills?.length"
                                    class="text-[10px] leading-6 text-muted"
                                >
                                    未配置技能
                                </span>
                            </div>
                        </section>

                        <section class="rounded-lg bg-surface-alt/45 px-2.5 py-2">
                            <div class="flex items-center justify-between gap-2">
                                <h4
                                    class="flex items-center gap-1.5 text-[11px] font-semibold text-strong"
                                >
                                    <span class="material-symbols-outlined text-sm text-cyan-700">
                                        build
                                    </span>
                                    包含工具
                                </h4>
                                <span class="text-[11px] text-muted">
                                    {{ item.tools?.length || 0 }} 项
                                </span>
                            </div>
                            <div class="mt-1.5 flex min-h-6 flex-wrap gap-1">
                                <span
                                    v-for="tool in visibleTools(item)"
                                    :key="toolVisualKey(tool)"
                                    class="inline-flex max-w-full items-center gap-1 rounded bg-cyan-50 px-1.5 py-0.5 text-[10px] font-medium text-cyan-700"
                                    :title="toolVisualDescription(tool)"
                                >
                                    <span class="material-symbols-outlined text-xs">
                                        {{ resolveToolIcon(toolVisualType(tool)) }}
                                    </span>
                                    <span class="max-w-24 truncate">
                                        {{ toolVisualName(tool) }}
                                    </span>
                                </span>
                                <span
                                    v-if="remainingToolCount(item)"
                                    class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-muted"
                                >
                                    +{{ remainingToolCount(item) }}
                                </span>
                                <span
                                    v-if="!item.tools?.length"
                                    class="text-[10px] leading-6 text-muted"
                                >
                                    未配置工具
                                </span>
                            </div>
                        </section>
                    </div>

                    <div
                        class="flex items-center justify-between gap-2 border-t border-border-soft/50 px-3 py-2"
                    >
                        <span class="text-[11px] text-muted">
                            {{ (item.skills?.length || 0) + (item.tools?.length || 0) }} 项专家能力
                        </span>
                        <button
                            type="button"
                            class="inline-flex items-center gap-1 rounded-lg bg-accent-soft px-2.5 py-1 text-xs font-medium text-primary transition-colors hover:bg-primary hover:text-white"
                            @click="openPackageChat(item)"
                        >
                            进入对话
                            <span class="material-symbols-outlined text-xs">arrow_forward</span>
                        </button>
                    </div>
                </article>
            </div>

            <div
                v-if="!loading && !filteredPackages.length && !loadError"
                class="flex flex-col items-center justify-center py-16 text-muted"
            >
                <span class="material-symbols-outlined mb-2 text-4xl">search_off</span>
                <p class="text-sm font-semibold text-strong">
                    {{ packages.length ? '未找到匹配的专家技能包' : '暂无可用专家技能包' }}
                </p>
                <p class="mt-1 text-xs">
                    {{
                        packages.length
                            ? '可以尝试搜索专家包名称、技能或工具。'
                            : '管理员启用专家技能包后，会出现在这里。'
                    }}
                </p>
            </div>
        </div>
    </section>
</template>
