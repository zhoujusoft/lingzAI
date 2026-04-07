<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
    listMcpServers,
    listSkillCatalogs,
    listSkillTools,
    updateSkillBindings,
    updateSkillCatalog,
} from '@/api/skills';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    skillId: {
        type: Number,
        required: true,
    },
});

const emit = defineEmits(['back']);

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const bindingSaving = ref(false);
const loadError = ref('');
const selectedSkill = ref(null);
const toolLibrary = ref([]);
const mcpServers = ref([]);
const selectedBoundToolNames = ref([]);

const skillIcons = ['grid_view', 'smart_toy', 'rocket_launch', 'inventory_2', 'dataset', 'hub'];
const skillGradients = [
    'from-blue-500 via-blue-500 to-cyan-400',
    'from-indigo-500 via-indigo-500 to-sky-400',
    'from-emerald-500 via-teal-500 to-cyan-400',
    'from-amber-500 via-orange-500 to-rose-400',
    'from-slate-600 via-slate-500 to-slate-400',
    'from-violet-500 via-fuchsia-500 to-pink-400',
];

const form = reactive({
    displayName: '',
    description: '',
    category: '',
    sortOrder: 0,
    visible: true,
});

const toolMap = computed(() => {
    const map = new Map();
    for (const item of toolLibrary.value) {
        map.set(item.name, item);
    }
    return map;
});

const globalBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'GLOBAL')
);

const knowledgeBaseBindableTools = computed(() =>
    toolLibrary.value.filter(item => item.bindable && item.type === 'KNOWLEDGE_BASE_TOOL')
);

const mcpServerToolMap = computed(() => {
    const map = new Map();
    for (const server of mcpServers.value) {
        map.set(server.serverKey, []);
    }
    for (const tool of toolLibrary.value) {
        if (tool.type !== 'MCP_REMOTE' || !tool.source?.startsWith('mcp:')) {
            continue;
        }
        const serverKey = tool.source.slice(4);
        const list = map.get(serverKey) || [];
        list.push(tool);
        map.set(serverKey, list);
    }
    return map;
});

const selectedToolItems = computed(() =>
    selectedBoundToolNames.value.map(
        name =>
            toolMap.value.get(name) || {
                name,
                displayName: name,
                description: '该工具不在当前可用目录中，可能尚未刷新或已从远端下线。',
                type: 'UNKNOWN',
                source: '',
            }
    )
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function syncForm(skill) {
    form.displayName = skill?.displayName || '';
    form.description = skill?.description || '';
    form.category = skill?.category || '';
    form.sortOrder = Number(skill?.sortOrder || 0);
    form.visible = Boolean(skill?.visible);
    selectedBoundToolNames.value = Array.isArray(skill?.boundGlobalToolNames)
        ? [...skill.boundGlobalToolNames]
        : [];
}

function getSkillIcon(skillId) {
    if (!skillId) {
        return skillIcons[0];
    }
    return skillIcons[skillId % skillIcons.length];
}

function getSkillGradient(skillId) {
    if (!skillId) {
        return skillGradients[0];
    }
    return skillGradients[skillId % skillGradients.length];
}

function isToolSelected(toolName) {
    return selectedBoundToolNames.value.includes(toolName);
}

function toggleTool(toolName) {
    if (!toolName) {
        return;
    }
    const next = new Set(selectedBoundToolNames.value);
    if (next.has(toolName)) {
        next.delete(toolName);
    } else {
        next.add(toolName);
    }
    selectedBoundToolNames.value = Array.from(next);
}

function bindServerTools(serverKey) {
    const next = new Set(selectedBoundToolNames.value);
    for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
        next.add(tool.name);
    }
    selectedBoundToolNames.value = Array.from(next);
}

function removeServerTools(serverKey) {
    const next = new Set(selectedBoundToolNames.value);
    for (const tool of mcpServerToolMap.value.get(serverKey) || []) {
        next.delete(tool.name);
    }
    selectedBoundToolNames.value = Array.from(next);
}

async function loadSkill() {
    loading.value = true;
    loadError.value = '';
    try {
        const [catalogs, tools, servers] = await Promise.all([
            listSkillCatalogs({}, handleUnauthorized),
            listSkillTools(handleUnauthorized),
            listMcpServers(handleUnauthorized),
        ]);
        const matched = Array.isArray(catalogs)
            ? catalogs.find(item => item.id === props.skillId) || null
            : null;
        if (!matched) {
            throw new Error('技能不存在或已下线');
        }
        selectedSkill.value = matched;
        toolLibrary.value = Array.isArray(tools) ? tools.filter(item => item.bindable) : [];
        mcpServers.value = Array.isArray(servers) ? servers : [];
        syncForm(matched);
    } catch (error) {
        loadError.value = error?.message || '加载技能详情失败';
    } finally {
        loading.value = false;
    }
}

async function saveMeta() {
    if (!selectedSkill.value || saving.value) {
        return;
    }
    saving.value = true;
    try {
        const updated = await updateSkillCatalog(
            selectedSkill.value.id,
            {
                displayName: form.displayName,
                description: form.description,
                category: form.category,
                sortOrder: Number(form.sortOrder || 0),
                visible: form.visible,
            },
            handleUnauthorized
        );
        selectedSkill.value = {
            ...selectedSkill.value,
            ...updated,
            boundGlobalToolNames: selectedBoundToolNames.value,
        };
        syncForm(selectedSkill.value);
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '技能信息保存失败',
        });
    } finally {
        saving.value = false;
    }
}

async function saveBindings() {
    if (!selectedSkill.value || bindingSaving.value) {
        return;
    }
    bindingSaving.value = true;
    try {
        const result = await updateSkillBindings(
            selectedSkill.value.id,
            selectedBoundToolNames.value,
            handleUnauthorized
        );
        selectedBoundToolNames.value = Array.isArray(result?.toolNames) ? result.toolNames : [];
        selectedSkill.value = {
            ...selectedSkill.value,
            boundGlobalToolNames: selectedBoundToolNames.value,
        };
    } catch (error) {
        await alert({
            title: '绑定失败',
            message: error?.message || '技能工具绑定失败',
        });
    } finally {
        bindingSaving.value = false;
    }
}

watch(
    () => props.skillId,
    () => {
        loadSkill();
    }
);

onMounted(() => {
    loadSkill();
});
</script>

<template>
    <section class="flex h-full min-h-0 flex-col bg-slate-100">
        <header class="border-b border-slate-200 bg-white px-8 py-6">
            <div class="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <p class="text-xs font-semibold tracking-[0.32em] text-slate-400">技能详情</p>
                    <h2 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">技能详情</h2>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        这里既可以编辑技能的业务展示信息，也可以把公共工具、知识库工具和 MCP
                        远程工具按需追加到当前 skill。
                    </p>
                </div>
                <button
                    type="button"
                    class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                    @click="emit('back')"
                >
                    返回列表
                </button>
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

            <div v-else-if="selectedSkill" class="space-y-6">
                <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                        <div class="flex items-start gap-4">
                            <div
                                class="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] bg-gradient-to-br text-white shadow-lg"
                                :class="getSkillGradient(selectedSkill.id)"
                            >
                                <span class="material-symbols-outlined text-[30px]">{{
                                    getSkillIcon(selectedSkill.id)
                                }}</span>
                            </div>
                            <div class="min-w-0">
                                <h1 class="text-3xl font-bold tracking-tight text-slate-900">
                                    {{ selectedSkill.displayName }}
                                </h1>
                                <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-500">
                                    {{ selectedSkill.description }}
                                </p>
                                <div class="mt-4 flex flex-wrap gap-2">
                                    <span
                                        class="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-600"
                                    >
                                        {{ selectedSkill.category || '通用能力' }}
                                    </span>
                                    <span
                                        class="rounded-full px-3 py-1 text-xs font-semibold"
                                        :class="
                                            selectedSkill.visible
                                                ? 'bg-emerald-50 text-emerald-600'
                                                : 'bg-slate-100 text-slate-500'
                                        "
                                    >
                                        {{ selectedSkill.visible ? '前台可见' : '前台隐藏' }}
                                    </span>
                                </div>
                            </div>
                        </div>

                        <div class="grid gap-3 sm:grid-cols-3">
                            <article
                                class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
                            >
                                <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">
                                    运行时工具
                                </p>
                                <p class="mt-2 text-2xl font-bold text-slate-900">
                                    {{ selectedSkill.runtimeTools.length }}
                                </p>
                            </article>
                            <article
                                class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
                            >
                                <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">
                                    追加绑定
                                </p>
                                <p class="mt-2 text-2xl font-bold text-violet-600">
                                    {{ selectedBoundToolNames.length }}
                                </p>
                            </article>
                            <article
                                class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4"
                            >
                                <p class="text-xs font-semibold tracking-[0.24em] text-slate-400">
                                    运行时标识
                                </p>
                                <p class="mt-2 truncate text-sm font-semibold text-slate-700">
                                    {{ selectedSkill.runtimeSkillName }}
                                </p>
                            </article>
                        </div>
                    </div>
                </section>

                <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                    <article class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="mb-5 flex items-center justify-between">
                            <div>
                                <h3 class="text-xl font-bold text-slate-900">基础信息</h3>
                                <p class="mt-1 text-sm text-slate-500">
                                    编辑技能的展示名称、分类、描述和前台可见性。
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="saving"
                                @click="saveMeta"
                            >
                                {{ saving ? '保存中...' : '保存信息' }}
                            </button>
                        </div>

                        <div class="grid gap-4 md:grid-cols-2">
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">展示名称</span>
                                <input
                                    v-model.trim="form.displayName"
                                    type="text"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">业务能力分类</span>
                                <input
                                    v-model.trim="form.category"
                                    type="text"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600 md:col-span-2">
                                <span class="font-medium">展示描述</span>
                                <textarea
                                    v-model.trim="form.description"
                                    rows="5"
                                    class="w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label class="space-y-2 text-sm text-slate-600">
                                <span class="font-medium">排序值</span>
                                <input
                                    v-model.number="form.sortOrder"
                                    type="number"
                                    class="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                                />
                            </label>
                            <label
                                class="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700"
                            >
                                <input
                                    v-model="form.visible"
                                    type="checkbox"
                                    class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600"
                                />
                                <span class="font-medium">前台技能市场可见</span>
                            </label>
                        </div>
                    </article>

                    <article class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="mb-5">
                            <h3 class="text-xl font-bold text-slate-900">运行时工具</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                这些工具由技能本身声明，只读展示，不在这里做跨技能编辑。
                            </p>
                        </div>

                        <div class="space-y-3">
                            <article
                                v-for="tool in selectedSkill.runtimeTools"
                                :key="`${selectedSkill.id}-${tool.name}`"
                                class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3"
                            >
                                <div class="flex items-center gap-2">
                                    <p class="font-medium text-slate-900">
                                        {{ tool.displayName || tool.name }}
                                    </p>
                                    <span
                                        v-if="tool.displayName && tool.displayName !== tool.name"
                                        class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                    >
                                        {{ tool.name }}
                                    </span>
                                </div>
                                <p class="mt-1 text-sm leading-6 text-slate-500">
                                    {{ tool.description }}
                                </p>
                            </article>
                            <p
                                v-if="!selectedSkill.runtimeTools.length"
                                class="text-sm text-slate-400"
                            >
                                当前技能未声明专属运行时工具
                            </p>
                        </div>
                    </article>
                </div>

                <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-5 flex flex-wrap items-center justify-between gap-4">
                        <div>
                            <h3 class="text-xl font-bold text-slate-900">追加绑定工具</h3>
                            <p class="mt-1 text-sm text-slate-500">
                                这里可把公共工具、知识库工具或 MCP 远程工具追加给当前 skill。底层仍按 tool
                                级绑定存储。
                            </p>
                        </div>
                        <button
                            type="button"
                            class="rounded-xl bg-violet-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="bindingSaving"
                            @click="saveBindings"
                        >
                            {{ bindingSaving ? '保存中...' : '保存绑定' }}
                        </button>
                    </div>

                    <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
                        <p class="text-sm font-semibold text-slate-700">当前已绑定</p>
                        <div class="mt-3 flex flex-wrap gap-2">
                            <span
                                v-for="item in selectedToolItems"
                                :key="item.name"
                                class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-600"
                            >
                                {{ item.displayName || item.name }}
                            </span>
                            <span v-if="!selectedToolItems.length" class="text-sm text-slate-400">
                                当前还没有追加绑定工具
                            </span>
                        </div>
                    </div>

                    <div class="mt-6 grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                        <div class="space-y-6">
                            <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                                <div class="mb-4">
                                    <h4 class="text-lg font-bold text-slate-900">公共工具</h4>
                                    <p class="mt-1 text-sm text-slate-500">
                                        适合通用文件、脚本、宿主级操作。
                                    </p>
                                </div>
                                <div class="space-y-3">
                                    <label
                                        v-for="tool in globalBindableTools"
                                        :key="tool.name"
                                        class="flex items-start gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700"
                                    >
                                        <input
                                            :checked="isToolSelected(tool.name)"
                                            type="checkbox"
                                            class="mt-1 h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-600"
                                            @change="toggleTool(tool.name)"
                                        />
                                        <span class="min-w-0 flex-1">
                                            <span class="font-medium text-slate-900">{{
                                                tool.displayName || tool.name
                                            }}</span>
                                            <span
                                                v-if="
                                                    tool.displayName && tool.displayName !== tool.name
                                                "
                                                class="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                            >
                                                {{ tool.name }}
                                            </span>
                                            <span class="mt-1 block text-sm leading-6 text-slate-500">{{
                                                tool.description
                                            }}</span>
                                        </span>
                                    </label>
                                    <p
                                        v-if="!globalBindableTools.length"
                                        class="text-sm text-slate-400"
                                    >
                                        当前没有可追加的公共工具
                                    </p>
                                </div>
                            </article>

                            <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                                <div class="mb-4">
                                    <h4 class="text-lg font-bold text-slate-900">知识库工具</h4>
                                    <p class="mt-1 text-sm text-slate-500">
                                        适合把已发布的知识库检索能力追加给当前 skill。
                                    </p>
                                </div>
                                <div class="space-y-3">
                                    <label
                                        v-for="tool in knowledgeBaseBindableTools"
                                        :key="tool.name"
                                        class="flex items-start gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700"
                                    >
                                        <input
                                            :checked="isToolSelected(tool.name)"
                                            type="checkbox"
                                            class="mt-1 h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-600"
                                            @change="toggleTool(tool.name)"
                                        />
                                        <span class="min-w-0 flex-1">
                                            <span class="font-medium text-slate-900">{{
                                                tool.displayName || tool.name
                                            }}</span>
                                            <span
                                                v-if="
                                                    tool.displayName && tool.displayName !== tool.name
                                                "
                                                class="ml-2 rounded-full bg-sky-50 px-2 py-0.5 text-[11px] font-semibold text-sky-600"
                                            >
                                                {{ tool.name }}
                                            </span>
                                            <span class="mt-1 block text-sm leading-6 text-slate-500">{{
                                                tool.description
                                            }}</span>
                                        </span>
                                    </label>
                                    <p
                                        v-if="!knowledgeBaseBindableTools.length"
                                        class="text-sm text-slate-400"
                                    >
                                        当前没有可追加的知识库工具
                                    </p>
                                </div>
                            </article>
                        </div>

                        <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                            <div class="mb-4">
                                <h4 class="text-lg font-bold text-slate-900">MCP 远程工具</h4>
                                <p class="mt-1 text-sm text-slate-500">
                                    按 server 分组展示，可逐个勾选，也可以对某个 server 一键全选。
                                </p>
                            </div>

                            <div class="space-y-4">
                                <article
                                    v-for="server in mcpServers"
                                    :key="server.id"
                                    class="rounded-2xl border border-slate-200 bg-white px-4 py-4"
                                >
                                    <div
                                        class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"
                                    >
                                        <div>
                                            <div class="flex flex-wrap items-center gap-2">
                                                <h5 class="text-base font-bold text-slate-900">
                                                    {{ server.displayName }}
                                                </h5>
                                                <span
                                                    class="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-semibold text-violet-600"
                                                >
                                                    {{ server.serverKey }}
                                                </span>
                                                <span
                                                    class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600"
                                                >
                                                    {{ server.toolCount }} 个同步工具
                                                </span>
                                            </div>
                                            <p class="mt-1 text-sm text-slate-500">
                                                {{ server.endpoint }}
                                            </p>
                                        </div>
                                        <div class="flex shrink-0 gap-2">
                                            <button
                                                type="button"
                                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                                                @click="bindServerTools(server.serverKey)"
                                            >
                                                一键绑定该 Server
                                            </button>
                                            <button
                                                type="button"
                                                class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                                                @click="removeServerTools(server.serverKey)"
                                            >
                                                清除此 Server 绑定
                                            </button>
                                        </div>
                                    </div>

                                    <div class="mt-4 grid gap-3">
                                        <label
                                            v-for="tool in mcpServerToolMap.get(server.serverKey) ||
                                            []"
                                            :key="tool.name"
                                            class="flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700"
                                        >
                                            <input
                                                :checked="isToolSelected(tool.name)"
                                                type="checkbox"
                                                class="mt-1 h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-600"
                                                @change="toggleTool(tool.name)"
                                            />
                                            <span class="min-w-0 flex-1">
                                                <span class="font-medium text-slate-900">{{
                                                    tool.displayName || tool.name
                                                }}</span>
                                                <span
                                                    v-if="
                                                        tool.displayName &&
                                                        tool.displayName !== tool.name
                                                    "
                                                    class="ml-2 rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-slate-500"
                                                >
                                                    {{ tool.name }}
                                                </span>
                                                <span
                                                    class="mt-1 block text-sm leading-6 text-slate-500"
                                                    >{{ tool.description }}</span
                                                >
                                            </span>
                                        </label>
                                        <p
                                            v-if="
                                                !(mcpServerToolMap.get(server.serverKey) || [])
                                                    .length
                                            "
                                            class="text-sm text-slate-400"
                                        >
                                            该 server 当前没有已同步的 MCP 工具。请先去 MCP
                                            服务页面执行刷新。
                                        </p>
                                    </div>
                                </article>

                                <p v-if="!mcpServers.length" class="text-sm text-slate-400">
                                    当前还没有配置 MCP server。请先到 MCP 服务页面完成 MCP server
                                    配置和刷新。
                                </p>
                            </div>
                        </article>
                    </div>
                </section>
            </div>
        </div>
    </section>
</template>
