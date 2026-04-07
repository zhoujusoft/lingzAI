<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppSelect from '@/components/AppSelect.vue';
import { clearUserSession } from '@/composables/useCurrentUser';
import { confirm } from '@/composables/useModal';
import {
    disableIntegrationDataset,
    deleteIntegrationDataset,
    listIntegrationDataSources,
    listIntegrationDatasets,
    publishIntegrationDataset,
} from '@/api/integration';
import { ROUTE_PATHS } from '@/router/routePaths';

const router = useRouter();

const loading = ref(false);
const publishingId = ref(null);
const loadError = ref('');
const dataSources = ref([]);
const datasets = ref([]);
const filters = reactive({
    keyword: '',
    sourceKind: '',
    aiDataSourceId: '',
});

const sourceKindOptions = [
    { value: '', label: '全部来源' },
    { value: 'AI_SOURCE', label: 'AI 数据源' },
    { value: 'LOWCODE_APP', label: '低代码应用' },
];

const dataSourceFilterOptions = computed(() => [
    { value: '', label: '全部数据库' },
    ...dataSources.value.map(item => ({
        value: item.id,
        label: item.alias || item.name,
    })),
]);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadPageData() {
    loading.value = true;
    loadError.value = '';
    try {
        const [datasetResult, dataSourceResult] = await Promise.all([
            listIntegrationDatasets(filters, handleUnauthorized),
            listIntegrationDataSources({}, handleUnauthorized),
        ]);
        datasets.value = Array.isArray(datasetResult) ? datasetResult : [];
        dataSources.value = Array.isArray(dataSourceResult) ? dataSourceResult : [];
    } catch (error) {
        loadError.value = error?.message || '数据集加载失败';
        datasets.value = [];
    } finally {
        loading.value = false;
    }
}

const groupedDatasets = computed(() => {
    const groups = {};
    datasets.value.forEach(item => {
        const key = item.sourceKind === 'AI_SOURCE'
            ? item.aiDataSourceName || '未绑定数据源'
            : item.lowcodeAppName || '低代码应用';
        if (!groups[key]) {
            groups[key] = [];
        }
        groups[key].push(item);
    });
    return Object.entries(groups);
});

async function handleDelete(item) {
    const ok = await confirm({
        title: '删除数据集',
        message: `确认删除“${item?.name || '当前数据集'}”吗？`,
        confirmText: '删除',
        cancelText: '取消',
    });
    if (!ok) {
        return;
    }
    await deleteIntegrationDataset(item.id, handleUnauthorized);
    await loadPageData();
}

async function handlePublish(item) {
    const ok = await confirm({
        title: '发布数据集工具',
        message: `确认将“${item?.name || '当前数据集'}”发布到工具库吗？`,
        confirmText: '发布',
        cancelText: '取消',
    });
    if (!ok) {
        return;
    }
    publishingId.value = item.id;
    try {
        await publishIntegrationDataset(item.id, handleUnauthorized);
        await loadPageData();
    } catch (error) {
        loadError.value = error?.message || '数据集发布失败';
    } finally {
        publishingId.value = null;
    }
}

async function handleDisable(item) {
    const ok = await confirm({
        title: '停用数据集工具',
        message: `确认停用“${item?.name || '当前数据集'}”在工具库中的发布状态吗？`,
        confirmText: '停用',
        cancelText: '取消',
    });
    if (!ok) {
        return;
    }
    publishingId.value = item.id;
    try {
        await disableIntegrationDataset(item.id, handleUnauthorized);
        await loadPageData();
    } catch (error) {
        loadError.value = error?.message || '数据集停用失败';
    } finally {
        publishingId.value = null;
    }
}

function sourceLabel(item) {
    return item?.sourceKind === 'LOWCODE_APP' ? '低代码应用' : 'AI 数据源';
}

function sourceBadgeClass(item) {
    return item?.sourceKind === 'LOWCODE_APP'
        ? 'bg-emerald-50 text-emerald-600'
        : 'bg-blue-50 text-primary';
}

function datasetPreviewText(item) {
    const rawText = item?.description || item?.businessLogic || '暂无业务说明';
    return String(rawText || '')
        .replace(/\s*\n+\s*/g, ' ')
        .replace(/\s{2,}/g, ' ')
        .trim();
}

function publishStatusLabel(item) {
    if (item?.publishStatus === 'PUBLISHED') {
        return '已发布';
    }
    if (item?.publishStatus === 'DISABLED') {
        return '已停用';
    }
    return '草稿';
}

function publishStatusClass(item) {
    if (item?.publishStatus === 'PUBLISHED') {
        return 'bg-emerald-50 text-emerald-600';
    }
    if (item?.publishStatus === 'DISABLED') {
        return 'bg-slate-100 text-slate-500';
    }
    return 'bg-amber-50 text-amber-600';
}

function publishActionLabel(item) {
    return item?.publishStatus === 'PUBLISHED' ? '重新发布' : '发布工具';
}

onMounted(loadPageData);
</script>

<template>
    <div class="flex h-full flex-col overflow-hidden bg-[#f6f7f8]">
        <header class="border-b border-slate-200 bg-white px-8 pt-6">
            <div class="flex items-center gap-8">
                <button
                    class="border-b-2 border-transparent pb-4 text-sm font-medium text-slate-400"
                    type="button"
                    @click="router.push(ROUTE_PATHS.adminIntegrationDataSources)"
                >
                    数据库
                </button>
                <button class="border-b-2 border-primary pb-4 text-sm font-semibold text-primary">
                    数据集
                </button>
            </div>
        </header>

        <section class="flex flex-col gap-4 border-b border-slate-100 bg-white px-8 py-6 md:flex-row md:items-center md:justify-between">
            <div class="flex flex-1 flex-col gap-4 md:max-w-3xl md:flex-row">
                <div class="md:w-52">
                    <AppSelect
                        :model-value="filters.sourceKind"
                        :options="sourceKindOptions"
                        size="sm"
                        button-class="border-slate-200 bg-slate-50 shadow-none"
                        menu-class="w-full"
                        @update:modelValue="value => { filters.sourceKind = value; loadPageData(); }"
                    />
                </div>
                <div class="md:w-60">
                    <AppSelect
                        :model-value="filters.aiDataSourceId"
                        :options="dataSourceFilterOptions"
                        size="sm"
                        button-class="border-slate-200 bg-slate-50 shadow-none"
                        menu-class="w-full"
                        @update:modelValue="value => { filters.aiDataSourceId = value; loadPageData(); }"
                    />
                </div>
                <div class="relative flex-1">
                    <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">search</span>
                    <input
                        v-model="filters.keyword"
                        class="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/10"
                        placeholder="搜索数据集名称..."
                        type="text"
                        @keyup.enter="loadPageData"
                    />
                </div>
            </div>
            <button
                class="flex items-center justify-center gap-2 rounded-xl bg-primary px-6 py-2.5 font-medium text-white shadow-md transition-all active:scale-95"
                type="button"
                @click="router.push(ROUTE_PATHS.adminIntegrationDatasetCreate)"
            >
                <span class="material-symbols-outlined text-xl">add</span>
                创建数据集
            </button>
        </section>

        <section class="flex-1 overflow-y-auto px-8 pb-8 pt-6">
            <div v-if="loadError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
                {{ loadError }}
            </div>
            <div v-if="loading" class="py-16 text-center text-sm text-slate-400">数据集加载中...</div>
            <div v-else class="space-y-12">
                <section v-for="[groupName, items] in groupedDatasets" :key="groupName">
                    <div class="mb-6 flex items-center gap-3">
                        <span class="material-symbols-outlined text-primary">database</span>
                        <h2 class="text-sm font-bold uppercase tracking-[0.2em] text-slate-500">{{ groupName }}</h2>
                        <div class="h-px flex-1 bg-slate-200" />
                    </div>
                    <div class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
                        <article
                            v-for="item in items"
                            :key="item.id"
                            class="flex min-h-[220px] flex-col rounded-[1.25rem] border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary"
                        >
                            <div class="mb-4 flex items-start justify-between gap-4">
                                <div>
                                    <span class="mb-2 inline-flex rounded-full px-2.5 py-1 text-[10px] font-bold" :class="sourceBadgeClass(item)">
                                        {{ sourceLabel(item) }}
                                    </span>
                                    <h3 class="text-lg font-bold text-slate-900">{{ item.name }}</h3>
                                    <p v-if="item.datasetCode" class="mt-1 text-xs text-slate-400">
                                        {{ item.datasetCode }}
                                    </p>
                                </div>
                                <div class="flex items-center gap-2">
                                    <button class="text-slate-300 transition-colors hover:text-primary" type="button" @click.stop="router.push(ROUTE_PATHS.adminIntegrationDatasetView(item.id))">
                                        <span class="material-symbols-outlined text-[20px]">visibility</span>
                                    </button>
                                    <button class="text-slate-300 transition-colors hover:text-primary" type="button" @click.stop="router.push(ROUTE_PATHS.adminIntegrationDatasetEdit(item.id))">
                                        <span class="material-symbols-outlined text-[20px]">edit</span>
                                    </button>
                                    <button class="text-slate-300 transition-colors hover:text-rose-500" type="button" @click.stop="handleDelete(item)">
                                        <span class="material-symbols-outlined text-[20px]">delete</span>
                                    </button>
                                </div>
                            </div>
                            <p class="line-clamp-2 text-sm leading-6 text-slate-500">
                                {{ datasetPreviewText(item) }}
                            </p>
                            <div class="mt-4 flex flex-wrap gap-2 text-[11px] text-slate-400">
                                <span class="rounded bg-slate-100 px-2 py-1">{{ item.objectCount }} 个对象</span>
                                <span class="rounded bg-slate-100 px-2 py-1">{{ item.fieldCount }} 个字段</span>
                                <span class="rounded px-2 py-1" :class="publishStatusClass(item)">{{ publishStatusLabel(item) }}</span>
                                <span v-if="item.publishedVersion > 0" class="rounded bg-slate-100 px-2 py-1">v{{ item.publishedVersion }}</span>
                            </div>
                            <p v-if="item.lastPublishMessage" class="mt-3 text-xs leading-5 text-slate-400">
                                {{ item.lastPublishMessage }}
                            </p>
                            <div class="mt-auto flex items-center gap-2 pt-5">
                                <button
                                    class="rounded-xl border border-primary/20 px-3 py-2 text-sm font-medium text-primary transition-all hover:border-primary/30 hover:bg-primary/5 disabled:cursor-not-allowed disabled:opacity-50"
                                    type="button"
                                    :disabled="publishingId === item.id"
                                    @click.stop="handlePublish(item)"
                                >
                                    {{ publishingId === item.id ? '处理中...' : publishActionLabel(item) }}
                                </button>
                                <button
                                    v-if="item.publishStatus === 'PUBLISHED'"
                                    class="rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-500 transition-all hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                                    type="button"
                                    :disabled="publishingId === item.id"
                                    @click.stop="handleDisable(item)"
                                >
                                    停用发布
                                </button>
                            </div>
                        </article>
                    </div>
                </section>
            </div>
        </section>
    </div>
</template>
