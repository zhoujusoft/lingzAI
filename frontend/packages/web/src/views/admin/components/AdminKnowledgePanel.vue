<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { KnowledgeBean } from '@/model/bean';
import MiniPagination from '@/components/MiniPagination.vue';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession, currentUserState } from '@/composables/useCurrentUser';
import { canOperateKnowledgeBase } from '@/model/knowledge-permissions';
import {
    getResourcePermissionBadgeClass,
    getResourcePermissionDescription,
} from '@/model/resource-permissions';
import { ROUTE_PATHS } from '@/router/routePaths';
import {
    deleteKnowledgeBase,
    disableKnowledgeBase,
    listKnowledgeBases,
    publishKnowledgeBase,
} from '@/api/knowledge-bases';

const emit = defineEmits([
    'create-knowledge',
    'open-knowledge',
    'open-edit-knowledge',
    'open-recall-test',
]);

const router = useRouter();
const knowledgeCards = ref([]);
const searchKeyword = ref('');
const loading = ref(false);
const loadError = ref('');
const publishingId = ref(null);
const deletingId = ref(null);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];
const currentUserProfile = computed(() => currentUserState.profile);
const publishedCount = computed(
    () => knowledgeCards.value.filter(item => item.publishStatus === 'PUBLISHED').length
);
const totalDocumentCount = computed(() =>
    knowledgeCards.value.reduce((sum, item) => sum + (Number(item.docCount) || 0), 0)
);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function formatCount(value, suffix) {
    const safeCount = Number.isFinite(value) ? value : 0;
    return `${safeCount} ${suffix}`;
}

function formatCharCount(value) {
    const safeCount = Number.isFinite(value) ? value : 0;
    const kiloChars = Math.round((safeCount / 1000) * 10) / 10;
    return `${kiloChars} 千字符`;
}

function formatUpdateText(item) {
    return item?.updatedAt ? `更新于 ${item.updatedAt}` : '更新于 -';
}

async function loadKnowledgeCards(targetPage = page.value) {
    loading.value = true;
    loadError.value = '';
    try {
        const requestedPage = Math.max(1, Number(targetPage) || 1);
        const data = await listKnowledgeBases(
            {
                page: requestedPage,
                pageSize: pageSize.value,
                keyword: searchKeyword.value,
            },
            handleUnauthorized
        );
        const list = Array.isArray(data?.list)
            ? data.list
            : Array.isArray(data?.records)
              ? data.records
              : [];
        knowledgeCards.value = list.map(item => KnowledgeBean.fromApi(item));
        total.value = Number(data?.total ?? 0) || 0;
        page.value = Number(data?.page ?? data?.current ?? requestedPage) || requestedPage;
        pageSize.value = Number(data?.pageSize ?? data?.size ?? pageSize.value) || pageSize.value;
        if (knowledgeCards.value.length === 0 && total.value > 0 && page.value > 1) {
            const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
            if (lastPage !== page.value) {
                await loadKnowledgeCards(lastPage);
            }
        }
    } catch (error) {
        knowledgeCards.value = [];
        total.value = 0;
        loadError.value = error?.message || '知识库列表加载失败，请稍后重试。';
    } finally {
        loading.value = false;
    }
}

function handleSearch() {
    loadKnowledgeCards(1);
}

function handlePageChange(nextPage) {
    loadKnowledgeCards(nextPage);
}

function handlePageSizeChange(nextSize) {
    const safeSize = Number(nextSize);
    if (!Number.isFinite(safeSize) || safeSize <= 0 || safeSize === pageSize.value) {
        return;
    }
    pageSize.value = safeSize;
    loadKnowledgeCards(1);
}

function openRecallTest(item) {
    if (!canOperateKnowledge(item)) {
        return;
    }
    emit('open-recall-test', item);
}

function openKnowledge(item) {
    emit('open-knowledge', item);
}

function openEditKnowledge(item) {
    if (!canOperateKnowledge(item)) {
        return;
    }
    emit('open-edit-knowledge', item);
}

async function removeKnowledge(item) {
    if (!canOperateKnowledge(item) || deletingId.value === item.id) {
        return;
    }
    const confirmed = await confirm({
        title: '删除知识库',
        message: `确认删除知识库“${item.name || ''}”吗？删除后不可恢复。`,
        confirmText: '删除',
        cancelText: '取消',
        destructive: true,
    });
    if (!confirmed) {
        return;
    }

    deletingId.value = item.id;
    try {
        await deleteKnowledgeBase(item.id, handleUnauthorized);
        await loadKnowledgeCards(page.value);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除知识库失败，请稍后重试。',
        });
    } finally {
        deletingId.value = null;
    }
}

async function publishKnowledge(item) {
    if (!canOperateKnowledge(item) || publishingId.value === item.id) {
        return;
    }
    const confirmed = await confirm({
        title: '发布知识库工具',
        message: `确认将知识库“${item.name || ''}”发布到工具库吗？`,
        confirmText: '发布',
        cancelText: '取消',
    });
    if (!confirmed) {
        return;
    }

    publishingId.value = item.id;
    try {
        await publishKnowledgeBase(item.id, handleUnauthorized);
        await loadKnowledgeCards(page.value);
    } catch (error) {
        await alert({
            title: '发布失败',
            message: error?.message || '知识库发布失败，请稍后重试。',
        });
    } finally {
        publishingId.value = null;
    }
}

async function disableKnowledge(item) {
    if (!canOperateKnowledge(item) || publishingId.value === item.id) {
        return;
    }
    const confirmed = await confirm({
        title: '停用知识库工具',
        message: `确认停用知识库“${item.name || ''}”在工具库中的发布状态吗？`,
        confirmText: '停用',
        cancelText: '取消',
    });
    if (!confirmed) {
        return;
    }

    publishingId.value = item.id;
    try {
        await disableKnowledgeBase(item.id, handleUnauthorized);
        await loadKnowledgeCards(page.value);
    } catch (error) {
        await alert({
            title: '停用失败',
            message: error?.message || '知识库停用失败，请稍后重试。',
        });
    } finally {
        publishingId.value = null;
    }
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
        return 'border border-emerald-100 bg-emerald-50 text-emerald-700';
    }
    if (item?.publishStatus === 'DISABLED') {
        return 'border border-slate-200 bg-slate-50 text-slate-500';
    }
    return 'border border-amber-100 bg-amber-50 text-amber-700';
}

function permissionScopeLabel(item) {
    return getResourcePermissionDescription(item?.permissionScope);
}

function permissionScopeClass(item) {
    return getResourcePermissionBadgeClass(item?.permissionScope);
}

function canOperateKnowledge(item) {
    return canOperateKnowledgeBase(item, currentUserProfile.value);
}

onMounted(() => {
    loadKnowledgeCards();
});
</script>

<template>
    <section
        class="admin-page admin-page--knowledge-list flex h-full min-h-0 flex-col bg-slate-100"
        data-component="AdminKnowledgePanel"
    >
        <header class="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
            <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                <div>
                    <h1 class="text-3xl font-bold tracking-tight text-slate-900">知识库</h1>
                    <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                        管理知识库、文档向量入库和发布到工具库后的检索能力。
                    </p>
                </div>
                <button
                    type="button"
                    class="inline-flex self-start items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/15 transition hover:bg-blue-700 xl:self-auto"
                    @click="emit('create-knowledge')"
                >
                    <span class="material-symbols-outlined text-base">add</span>
                    <span>创建知识库</span>
                </button>
            </div>

            <div class="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1fr)_auto] xl:items-center">
                <div class="grid gap-2 sm:grid-cols-3">
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ total }}</p>
                        <p class="text-[11px] text-slate-500">知识库总数</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ publishedCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页已发布</p>
                    </article>
                    <article class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-lg font-bold text-slate-900">{{ totalDocumentCount }}</p>
                        <p class="text-[11px] text-slate-500">当前页文档数</p>
                    </article>
                </div>
                <div class="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    <div class="relative min-w-0 flex-1 sm:w-72 sm:flex-none">
                        <span
                            class="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                        >
                            search
                        </span>
                        <input
                            v-model.trim="searchKeyword"
                            type="text"
                            placeholder="搜索知识库名称、编码或描述"
                            class="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/10"
                            @keyup.enter="handleSearch"
                        />
                    </div>
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
                    知识库加载中...
                </div>

                <div
                    v-else-if="!knowledgeCards.length"
                    class="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-14 text-center text-sm text-slate-400"
                >
                    当前筛选条件下没有匹配知识库
                </div>

                <div
                    v-else
                    class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3 min-[1680px]:grid-cols-4"
                >
                    <article
                        v-for="item in knowledgeCards"
                        :key="item.id || item.name"
                        class="flex min-h-[238px] cursor-pointer flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-[0_10px_24px_rgba(15,23,42,0.06)]"
                        @click="openKnowledge(item)"
                    >
                        <div class="flex items-start gap-3">
                            <div
                                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-blue-100 bg-blue-50 text-blue-600"
                            >
                                <span class="material-symbols-outlined text-[21px]">folder</span>
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <h2 class="truncate text-base font-bold text-slate-900">
                                            {{ item.name || '未命名知识库' }}
                                        </h2>
                                        <p class="mt-0.5 truncate text-[11px] text-slate-400">
                                            {{ item.kbCode || '未设置编码' }}
                                        </p>
                                    </div>
                                    <span
                                        class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                        :class="publishStatusClass(item)"
                                    >
                                        {{ publishStatusLabel(item) }}
                                    </span>
                                </div>
                                <p
                                    class="mt-2 line-clamp-2 min-h-10 text-xs leading-5 text-slate-500"
                                >
                                    {{ item.description || '暂无描述' }}
                                </p>
                            </div>
                        </div>

                        <div class="mt-3 flex flex-wrap gap-1.5">
                            <span
                                class="rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ring-black/5"
                                :class="permissionScopeClass(item)"
                            >
                                {{ permissionScopeLabel(item) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ formatCount(item.docCount, '文档') }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ formatCharCount(item.charCount) }}
                            </span>
                            <span
                                class="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-medium text-slate-500"
                            >
                                {{ formatUpdateText(item) }}
                            </span>
                        </div>

                        <p
                            v-if="item.lastPublishMessage"
                            class="mt-2 line-clamp-1 text-xs leading-5 text-slate-400"
                        >
                            {{ item.lastPublishMessage }}
                        </p>

                        <div
                            v-if="canOperateKnowledge(item)"
                            class="mt-auto border-t border-slate-100 pt-3"
                            @click.stop
                        >
                            <div class="grid grid-cols-4 gap-1.5">
                                <button
                                    type="button"
                                    aria-label="知识库召回测试"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg bg-blue-600 px-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/10 transition hover:bg-blue-700"
                                    @click="openRecallTest(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >science</span
                                    >
                                    <span>召回</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="编辑知识库"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                                    @click="openEditKnowledge(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]">edit</span>
                                    <span>编辑</span>
                                </button>
                                <button
                                    v-if="item.publishStatus !== 'PUBLISHED'"
                                    type="button"
                                    aria-label="发布知识库工具"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-blue-100 bg-blue-50 px-2 text-xs font-semibold text-blue-700 transition hover:border-blue-200 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="publishingId === item.id"
                                    @click="publishKnowledge(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]">
                                        {{ publishingId === item.id ? 'sync' : 'publish' }}
                                    </span>
                                    <span>{{
                                        publishingId === item.id ? '处理中...' : '发布'
                                    }}</span>
                                </button>
                                <button
                                    v-if="item.publishStatus === 'PUBLISHED'"
                                    type="button"
                                    aria-label="停用知识库发布"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="publishingId === item.id"
                                    @click="disableKnowledge(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]">block</span>
                                    <span>停用</span>
                                </button>
                                <button
                                    type="button"
                                    aria-label="删除知识库"
                                    class="inline-flex h-8 items-center justify-center gap-1 whitespace-nowrap rounded-lg border border-rose-200 bg-white px-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="deletingId === item.id"
                                    @click="removeKnowledge(item)"
                                >
                                    <span class="material-symbols-outlined text-[17px]"
                                        >delete</span
                                    >
                                    <span>
                                        {{ deletingId === item.id ? '删除中...' : '删除' }}
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
