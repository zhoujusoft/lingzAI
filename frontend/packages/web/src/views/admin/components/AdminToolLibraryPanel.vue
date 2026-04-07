<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listSkillTools } from '@/api/skills';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const searchKeyword = ref('');
const typeFilter = ref('ALL');
const tools = ref([]);

const toolIcons = {
    GLOBAL: 'build',
    SKILL_NATIVE: 'smart_toy',
    MCP_REMOTE: 'hub',
    LOWCODE_API: 'api',
    DATASET_TOOL: 'database',
    KNOWLEDGE_BASE_TOOL: 'library_books',
};

const toolColorClass = {
    GLOBAL: 'from-blue-500 via-blue-500 to-cyan-400',
    SKILL_NATIVE: 'from-slate-600 via-slate-500 to-slate-400',
    MCP_REMOTE: 'from-violet-500 via-fuchsia-500 to-pink-400',
    LOWCODE_API: 'from-emerald-500 via-teal-500 to-cyan-400',
    DATASET_TOOL: 'from-amber-500 via-orange-500 to-yellow-400',
    KNOWLEDGE_BASE_TOOL: 'from-indigo-500 via-blue-500 to-cyan-400',
};

const filteredTools = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    return tools.value.filter(item => {
        const matchesType = typeFilter.value === 'ALL' || item.type === typeFilter.value;
        const matchesKeyword =
            !keyword ||
            `${item.displayName || ''} ${item.name || ''} ${item.description || ''} ${item.ownerSkillName || ''} ${item.ownerSkillDisplayName || ''} ${item.source || ''}`
                .toLowerCase()
                .includes(keyword);
        return matchesType && matchesKeyword;
    });
});

const globalToolCount = computed(() => tools.value.filter(item => item.type === 'GLOBAL').length);
const remoteToolCount = computed(() => tools.value.filter(item => item.type === 'MCP_REMOTE').length);
const lowcodeToolCount = computed(() => tools.value.filter(item => item.type === 'LOWCODE_API').length);
const datasetToolCount = computed(() => tools.value.filter(item => item.type === 'DATASET_TOOL').length);
const knowledgeBaseToolCount = computed(() =>
    tools.value.filter(item => item.type === 'KNOWLEDGE_BASE_TOOL').length
);
const nativeToolCount = computed(() => tools.value.filter(item => item.type === 'SKILL_NATIVE').length);

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
    if (type === 'SKILL_NATIVE') {
        return 'bg-slate-100 text-slate-600';
    }
    if (type === 'MCP_REMOTE') {
        return 'bg-violet-50 text-violet-600';
    }
    if (type === 'LOWCODE_API') {
        return 'bg-emerald-50 text-emerald-600';
    }
    if (type === 'DATASET_TOOL') {
        return 'bg-amber-50 text-amber-600';
    }
    if (type === 'KNOWLEDGE_BASE_TOOL') {
        return 'bg-sky-50 text-sky-600';
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

function openMcpManagement() {
    router.push({ path: ROUTE_PATHS.adminMcpManagement });
}

onMounted(() => {
    loadTools();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div>
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.32em] text-slate-400">
                        Tool Library
                    </p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">工具库</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        展示当前项目已注册的公共工具、技能原生工具、MCP 远程工具、低代码 API 工具、数据集工具和知识库工具。
                    </p>
                </div>
            </div>

            <div class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-7">
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Tools
                    </p>
                    <p class="mt-2 text-2xl font-bold text-slate-900">{{ tools.length }}</p>
                    <p class="mt-1 text-xs text-slate-500">工具总数</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Global
                    </p>
                    <p class="mt-2 text-2xl font-bold text-blue-600">{{ globalToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">可跨技能追加的公共工具</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Remote
                    </p>
                    <p class="mt-2 text-2xl font-bold text-violet-600">{{ remoteToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">MCP 远程工具</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Low-code
                    </p>
                    <p class="mt-2 text-2xl font-bold text-emerald-600">{{ lowcodeToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">低代码 API 工具</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Dataset
                    </p>
                    <p class="mt-2 text-2xl font-bold text-amber-600">{{ datasetToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">数据集工具</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Knowledge
                    </p>
                    <p class="mt-2 text-2xl font-bold text-sky-600">{{ knowledgeBaseToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">知识库工具</p>
                </article>
                <article class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                        Native
                    </p>
                    <p class="mt-2 text-2xl font-bold text-slate-700">{{ nativeToolCount }}</p>
                    <p class="mt-1 text-xs text-slate-500">技能原生工具</p>
                </article>
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
                class="rounded-[28px] border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
            >
                加载中...
            </div>

            <div v-else class="space-y-6">
                <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                        <div>
                            <h3 class="text-xl font-bold text-slate-900">工具列表</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                公共工具可直接绑定给 skill，MCP 远程工具由独立的 MCP 服务页负责连接配置与目录刷新。
                            </p>
                        </div>

                        <div
                            class="flex w-full max-w-2xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-end"
                        >
                            <button
                                type="button"
                                class="rounded-xl border border-violet-200 bg-violet-50 px-4 py-2.5 text-sm font-semibold text-violet-700 transition hover:bg-violet-100"
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
                                    class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-100"
                                />
                            </div>
                        </div>
                    </div>

                    <div class="mt-5 flex flex-wrap gap-2">
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'ALL'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'ALL'"
                        >
                            全部
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'GLOBAL'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'GLOBAL'"
                        >
                            公共工具
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'SKILL_NATIVE'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'SKILL_NATIVE'"
                        >
                            技能原生工具
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'MCP_REMOTE'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'MCP_REMOTE'"
                        >
                            MCP 远程工具
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'LOWCODE_API'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'LOWCODE_API'"
                        >
                            低代码 API 工具
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'DATASET_TOOL'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'DATASET_TOOL'"
                        >
                            数据集工具
                        </button>
                        <button
                            type="button"
                            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
                            :class="
                                typeFilter === 'KNOWLEDGE_BASE_TOOL'
                                    ? 'bg-violet-600 text-white shadow-sm shadow-violet-100'
                                    : 'border border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:text-violet-600'
                            "
                            @click="typeFilter = 'KNOWLEDGE_BASE_TOOL'"
                        >
                            知识库工具
                        </button>
                    </div>

                    <div class="mt-6 grid gap-5 md:grid-cols-2 2xl:grid-cols-3">
                        <article
                            v-for="tool in filteredTools"
                            :key="tool.ownerSkillName ? `${tool.ownerSkillName}-${tool.name}` : tool.name"
                            class="rounded-[28px] border border-slate-200 bg-slate-50 p-5 transition hover:-translate-y-0.5 hover:shadow-md"
                        >
                            <div class="flex items-start gap-4">
                                <div
                                    class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-lg"
                                    :class="getToolColorClass(tool.type)"
                                >
                                    <span class="material-symbols-outlined text-[24px]">
                                        {{ getToolIcon(tool.type) }}
                                    </span>
                                </div>
                                <div class="min-w-0 flex-1">
                                    <div class="flex flex-wrap items-center gap-2">
                                        <h4 class="truncate text-lg font-bold text-slate-900">
                                            {{ tool.displayName || tool.name }}
                                        </h4>
                                        <span
                                            class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                            :class="getToolTypeBadgeClass(tool.type)"
                                        >
                                            {{ getToolTypeLabel(tool.type) }}
                                        </span>
                                        <span
                                            v-if="
                                                tool.type === 'SKILL_NATIVE' &&
                                                (tool.ownerSkillDisplayName || tool.ownerSkillName)
                                            "
                                            class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-600"
                                        >
                                            {{ tool.ownerSkillDisplayName || tool.ownerSkillName }}
                                        </span>
                                        <span
                                            v-if="
                                                tool.type === 'MCP_REMOTE' &&
                                                getServerKeyFromSource(tool.source)
                                            "
                                            class="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-semibold text-violet-600"
                                        >
                                            {{ getServerKeyFromSource(tool.source) }}
                                        </span>
                                    </div>
                                    <p
                                        v-if="tool.displayName && tool.displayName !== tool.name"
                                        class="mt-1 truncate text-xs text-slate-400"
                                    >
                                        {{ tool.name }}
                                    </p>
                                    <p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
                                        {{ toolPreviewText(tool) }}
                                    </p>
                                </div>
                            </div>
                        </article>

                        <div
                            v-if="!filteredTools.length"
                            class="md:col-span-2 2xl:col-span-3 rounded-[28px] border border-dashed border-slate-200 bg-slate-50 px-6 py-14 text-center text-sm text-slate-400"
                        >
                            当前筛选条件下没有匹配工具
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>
