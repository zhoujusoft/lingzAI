<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listSkillStudioProjects, deleteSkillStudioProject } from '@/api/skillstudio';
import AppSelect from '@/components/AppSelect.vue';
import MiniPagination from '@/components/MiniPagination.vue';
import { confirm, openModal } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import { resolveSkillIcon } from '@/utils/skillVisuals';
import AdminSkillStudioCreateProjectModalContent from './AdminSkillStudioCreateProjectModalContent.vue';
import AdminSkillStudioCreateProjectModalFooter from './AdminSkillStudioCreateProjectModalFooter.vue';

const router = useRouter();

const loading = ref(false);
const deletingProjectId = ref(null);
const loadError = ref('');
const projects = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];
const filters = reactive({
    keyword: '',
    projectType: '',
    status: '',
});

const projectTypeOptions = [
    { value: '', label: '全部项目类型' },
    ...['技能', '网页应用', '智能体', '低代码', '数据分析', '知识问答'].map(value => ({
        value,
        label: value,
    })),
];
const statusOptions = [
    { value: '', label: '全部项目状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'PUBLISHED', label: '已发布' },
];
const publishedCount = computed(
    () => projects.value.filter(item => item.status === 'PUBLISHED').length
);
const draftCount = computed(
    () => projects.value.filter(item => item.status !== 'PUBLISHED').length
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function loadProjects(targetPage = page.value) {
    loading.value = true;
    loadError.value = '';
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await listSkillStudioProjects(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: filters.keyword,
                projectType: filters.projectType,
                status: filters.status,
            },
            handleUnauthorized
        );
        projects.value = Array.isArray(data?.list) ? data.list : [];
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? pageSize.value) || pageSize.value;
        if (projects.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadProjects(lastPage);
            }
        }
    } catch (error) {
        loadError.value = error?.message || '技能工坊项目加载失败';
        projects.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
}

async function openCreateProjectDialog() {
    const result = await openModal({
        title: '新增技能工坊项目',
        content: {
            component: AdminSkillStudioCreateProjectModalContent,
        },
        footer: {
            component: AdminSkillStudioCreateProjectModalFooter,
            props: {
                confirmText: '确认创建',
                cancelText: '取消',
            },
        },
        panelClass: 'max-w-[820px]',
        context: {
            description: '',
            formError: '',
            submitError: '',
            onUnauthorized: handleUnauthorized,
        },
    });
    if (result?.id) {
        await router.push(ROUTE_PATHS.adminSkillStudioProject(result.id, { bootstrap: true }));
    }
}

function openProject(item) {
    if (!item?.id) {
        return;
    }
    router.push(ROUTE_PATHS.adminSkillStudioProject(item.id));
}

async function handleDeleteProject(item) {
    if (!item?.id || deletingProjectId.value === item.id) {
        return;
    }
    const confirmed = await confirm({
        title: '删除工坊项目',
        message: `确认删除“${item?.name || '当前项目'}”吗？删除后将无法恢复！`,
        confirmText: '删除',
        cancelText: '取消',
    });
    if (!confirmed) {
        return;
    }
    deletingProjectId.value = item.id;
    loadError.value = '';
    try {
        await deleteSkillStudioProject(item.id, handleUnauthorized);
        await loadProjects(page.value);
    } catch (error) {
        loadError.value = error?.message || '删除技能工坊项目失败';
    } finally {
        deletingProjectId.value = null;
    }
}

function handleSearch() {
    loadProjects(1);
}

function handlePageChange(nextPage) {
    loadProjects(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadProjects(1);
}

function handleFilterChange(key, value) {
    filters[key] = value;
    loadProjects(1);
}

function projectSummary(item) {
    return item?.coverSummary || item?.description || '暂无项目说明';
}

function formatUpdateText(item) {
    if (!item?.updatedAt) {
        return '更新于 -';
    }
    const date = new Date(item.updatedAt);
    if (Number.isNaN(date.getTime())) {
        return `更新于 ${item.updatedAt}`;
    }
    return `更新于 ${date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    })}`;
}

function creatorName(item) {
    return item?.creatorName || item?.creatorCode || '未知用户';
}

function statusLabel(status) {
    return status === 'PUBLISHED' ? '已发布' : '草稿';
}

function statusClass(status) {
    return status === 'PUBLISHED'
        ? 'border border-emerald-100 bg-emerald-50 text-emerald-700'
        : 'border border-amber-100 bg-amber-50 text-amber-700';
}

onMounted(() => {
    loadProjects();
});
</script>

<template>
    <section class="admin-page flex h-full min-h-0 flex-col bg-slate-100">
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1 class="text-3xl font-bold tracking-tight text-slate-900">技能工坊</h1>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        创建、调试和发布技能项目，持续完善技能能力与运行效果。
                    </p>
                </div>
                <button
                    type="button"
                    class="inline-flex self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 xl:self-auto"
                    @click="openCreateProjectDialog"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>新增项目</span>
                </button>
            </div>

            <div class="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ total }}</p>
                        <p class="text-[11px] text-slate-500">项目总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ publishedCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页已发布</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ draftCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页草稿</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <div class="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >
                            search
                        </span>
                        <input
                            v-model.trim="filters.keyword"
                            type="text"
                            placeholder="搜索项目名称或描述"
                            class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                            @keyup.enter="handleSearch"
                        />
                    </div>
                    <AppSelect
                        :model-value="filters.projectType"
                        :options="projectTypeOptions"
                        size="sm"
                        :full-width="false"
                        button-class="min-w-[132px] rounded-xl border-slate-200 bg-white py-2.5 shadow-none"
                        @update:modelValue="value => handleFilterChange('projectType', value)"
                    />
                    <AppSelect
                        :model-value="filters.status"
                        :options="statusOptions"
                        size="sm"
                        :full-width="false"
                        button-class="min-w-[132px] rounded-xl border-slate-200 bg-white py-2.5 shadow-none"
                        @update:modelValue="value => handleFilterChange('status', value)"
                    />
                    <button
                        type="button"
                        class="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        @click="handleSearch"
                    >
                        <span class="material-symbols-outlined text-[18px]">search</span>
                        <span>搜索</span>
                    </button>
                </div>
            </div>
        </header>

        <div class="flex min-h-0 flex-1 flex-col p-6">
            <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
                <div
                    v-if="loadError"
                    class="mb-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600"
                >
                    {{ loadError }}
                </div>

                <div
                    v-if="loading"
                    class="rounded-2xl border border-slate-200 bg-white px-6 py-10 text-sm text-slate-400 shadow-sm"
                >
                    技能工坊项目加载中...
                </div>

                <div
                    v-else-if="!projects.length"
                    class="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-14 text-center text-sm text-slate-400"
                >
                    当前筛选条件下没有匹配项目
                </div>

                <div
                    v-else
                    class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4"
                >
                    <article
                        v-for="item in projects"
                        :key="item.id"
                        class="flex min-h-[238px] cursor-pointer flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                        @click="openProject(item)"
                    >
                        <div class="flex items-start gap-3">
                            <div
                                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                            >
                                <span class="material-symbols-outlined text-[21px]">
                                    {{ resolveSkillIcon(item.icon) }}
                                </span>
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <h2 class="truncate text-base font-bold text-slate-900">
                                            {{ item.name || '未命名项目' }}
                                        </h2>
                                        <p class="mt-0.5 truncate text-[11px] text-slate-400">
                                            {{
                                                item.runtimeSkillName ||
                                                item.projectCode ||
                                                '未设置编码'
                                            }}
                                        </p>
                                    </div>
                                    <span
                                        class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                        :class="statusClass(item.status)"
                                    >
                                        {{ statusLabel(item.status) }}
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 min-h-10 text-xs leading-5 text-slate-500"
                                >
                                    {{ projectSummary(item) }}
                                </p>
                            </div>
                        </div>

                        <div class="mt-3 flex flex-wrap gap-1.5">
                            <span
                                class="rounded-full border border-blue-100 bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700"
                            >
                                {{ item.projectType || '技能' }}
                            </span>
                            <span
                                v-if="item.category"
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ item.category }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ creatorName(item) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ formatUpdateText(item) }}
                            </span>
                        </div>

                        <div class="mt-auto border-t border-slate-100 pt-3">
                            <div class="grid grid-cols-2 gap-1.5">
                                <button
                                    type="button"
                                    aria-label="进入技能工坊项目"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700"
                                    @click.stop="openProject(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]">
                                        design_services
                                    </span>
                                    <span>进入工坊</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="删除技能工坊项目"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="deletingProjectId === item.id"
                                    @click.stop="handleDeleteProject(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >delete</span
                                    >
                                    <span>
                                        {{ deletingProjectId === item.id ? '删除中...' : '删除' }}
                                    </span>
                                </button>
                            </div>
                        </div>
                    </article>
                </div>
            </div>
            <div class="flex h-[54px] shrink-0 items-end justify-end">
                <MiniPagination
                    :page="page"
                    :page-size="pageSize"
                    :total="total"
                    :page-size-options="pageSizeOptions"
                    @page-change="handlePageChange"
                    @page-size-change="handlePageSizeChange"
                />
            </div>
        </div>
    </section>
</template>
