<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { KnowledgeBean } from '@/model/bean';
import { alert, confirm } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { ROUTE_PATHS } from '@/router/routePaths';
import { deleteKnowledgeBase, disableKnowledgeBase, listKnowledgeBases, publishKnowledgeBase } from '@/api/knowledge-bases';
import KnowledgeCardActionsMenu from './KnowledgeCardActionsMenu.vue';

const emit = defineEmits(['create-knowledge', 'open-knowledge', 'open-edit-knowledge', 'open-recall-test']);

const router = useRouter();
const knowledgeCards = ref([]);
const searchKeyword = ref('');
const loading = ref(false);
const publishingId = ref(null);

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

const filteredKnowledgeCards = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    if (!keyword) {
        return knowledgeCards.value;
    }

    return knowledgeCards.value.filter(item => {
        const name = (item.name || '').toLowerCase();
        const kbCode = (item.kbCode || '').toLowerCase();
        const description = (item.description || '').toLowerCase();
        return name.includes(keyword) || kbCode.includes(keyword) || description.includes(keyword);
    });
});

async function loadKnowledgeCards() {
    loading.value = true;
    try {
        const data = await listKnowledgeBases({}, handleUnauthorized);
        const list = Array.isArray(data?.records) ? data.records : [];
        knowledgeCards.value = list.map(item => KnowledgeBean.fromApi(item));
    } catch (error) {
        knowledgeCards.value = [];
        await alert({
            title: '加载失败',
            message: error?.message || '知识库列表加载失败，请稍后重试。',
        });
    } finally {
        loading.value = false;
    }
}

function openRecallTest(item) {
    emit('open-recall-test', item);
}

function openKnowledge(item) {
    emit('open-knowledge', item);
}

function openEditKnowledge(item) {
    emit('open-edit-knowledge', item);
}

async function removeKnowledge(item) {
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

    try {
        await deleteKnowledgeBase(item.id, handleUnauthorized);
        knowledgeCards.value = knowledgeCards.value.filter(card => card.id !== item.id);
    } catch (error) {
        await alert({
            title: '删除失败',
            message: error?.message || '删除知识库失败，请稍后重试。',
        });
    }
}

async function publishKnowledge(item) {
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
        await loadKnowledgeCards();
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
        await loadKnowledgeCards();
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

onMounted(() => {
    loadKnowledgeCards();
});
</script>

<template>
    <section
        class="admin-page admin-page--knowledge-list flex h-full min-h-0 flex-col bg-slate-50"
        data-component="AdminKnowledgePanel"
    >
        <header
            class="sticky top-0 z-20 flex items-center justify-between bg-slate-50/80 px-8 py-6 backdrop-blur-md"
        >
            <h2 class="text-2xl font-bold text-slate-900">知识库</h2>

            <div class="flex items-center gap-4">
                <div
                    class="flex items-center gap-4 rounded-lg border border-slate-200 bg-white px-3 py-1.5 shadow-sm"
                >
                    <label class="flex cursor-pointer items-center gap-2">
                        <input
                            checked
                            name="scope"
                            type="radio"
                            class="h-4 w-4 border-slate-300 text-primary focus:ring-primary"
                        >
                        <span class="text-sm text-slate-600">所有知识库</span>
                        <span class="material-symbols-outlined text-sm text-slate-300">help_outline</span>
                    </label>
                    <div class="h-4 w-px bg-slate-200"></div>
                    <div class="flex items-center gap-1 text-sm text-slate-500">
                        <span class="material-symbols-outlined text-sm">cloud_done</span>
                        <span>{{ loading ? '加载中...' : `${knowledgeCards.length} 个知识库` }}</span>
                    </div>
                </div>

                <div class="relative">
                    <span
                        class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-lg text-slate-400"
                    >search</span>
                    <input
                        v-model="searchKeyword"
                        type="text"
                        placeholder="搜索..."
                        class="w-64 rounded-lg border border-slate-200 bg-white py-2 pl-10 pr-4 text-sm transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                    >
                </div>

                <button
                    type="button"
                    class="flex items-center gap-1 rounded-lg bg-primary px-6 py-2 font-semibold text-white shadow-md shadow-blue-500/20 transition-all hover:bg-blue-700"
                    @click="emit('create-knowledge')"
                >
                    <span class="material-symbols-outlined text-sm">add</span>
                    <span>创建知识库</span>
                </button>
            </div>
        </header>

        <div
            v-if="!loading && filteredKnowledgeCards.length === 0"
            class="flex flex-1 items-center justify-center px-8 pb-12"
        >
            <div class="rounded-2xl border border-dashed border-slate-300 bg-white px-10 py-12 text-center">
                <div class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
                    <span class="material-symbols-outlined text-3xl">folder_off</span>
                </div>
                <h3 class="text-lg font-semibold text-slate-800">暂无知识库</h3>
                <p class="mt-2 text-sm text-slate-500">先创建一个知识库，再上传文档开始分块和向量入库。</p>
            </div>
        </div>

        <div
            v-else
            class="custom-scrollbar grid content-start flex-1 grid-cols-1 gap-6 overflow-y-auto px-8 pb-12 md:grid-cols-2 lg:grid-cols-2 xl:grid-cols-2 2xl:grid-cols-3"
        >
            <article
                v-for="item in filteredKnowledgeCards"
                :key="item.id || item.name"
                class="group flex h-full cursor-pointer flex-col rounded-xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary/30 hover:shadow-md"
                @click="openKnowledge(item)"
            >
                <div class="mb-4 flex items-start justify-between">
                    <div
                        class="flex h-12 w-12 items-center justify-center rounded-lg bg-blue-50 text-primary transition-transform group-hover:scale-110"
                    >
                        <span class="material-symbols-outlined">folder</span>
                    </div>
                    <KnowledgeCardActionsMenu
                        @recall-test="openRecallTest(item)"
                        @edit="openEditKnowledge(item)"
                        @delete="removeKnowledge(item)"
                    />
                </div>
                <div class="mb-2 flex items-center gap-2">
                    <h3 class="min-w-0 flex-1 truncate text-lg font-bold text-slate-900">
                        {{ item.name }}
                    </h3>
                    <span class="shrink-0 rounded px-2 py-1 text-[11px]" :class="publishStatusClass(item)">
                        {{ publishStatusLabel(item) }}
                    </span>
                </div>
                <div class="mb-3 flex items-center gap-2 text-xs text-slate-500">
                    <span class="rounded-md bg-slate-100 px-2 py-1 font-medium text-slate-600">Code</span>
                    <span class="font-mono text-slate-600">{{ item.kbCode || '-' }}</span>
                </div>
                <div class="mb-4 flex items-center gap-3 text-xs text-slate-400">
                    <span>{{ formatCount(item.docCount, '文档') }}</span>
                    <span>•</span>
                    <span>{{ formatCharCount(item.charCount) }}</span>
                </div>
                <div class="flex-1">
                    <p class="line-clamp-2 text-sm leading-relaxed text-slate-500">
                        {{ item.description || '暂无描述' }}
                    </p>
                    <p v-if="item.lastPublishMessage" class="mt-3 text-xs leading-5 text-slate-400">
                        {{ item.lastPublishMessage }}
                    </p>
                </div>
                <div class="mt-4 flex items-center gap-2" @click.stop>
                    <button
                        type="button"
                        class="rounded-lg border border-primary/20 px-3 py-1.5 text-sm font-medium text-primary transition-all hover:border-primary/30 hover:bg-primary/5 disabled:cursor-not-allowed disabled:opacity-50"
                        :disabled="publishingId === item.id"
                        @click="publishKnowledge(item)"
                    >
                        {{ publishingId === item.id ? '处理中...' : publishActionLabel(item) }}
                    </button>
                    <button
                        v-if="item.publishStatus === 'PUBLISHED'"
                        type="button"
                        class="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-500 transition-all hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                        :disabled="publishingId === item.id"
                        @click="disableKnowledge(item)"
                    >
                        停用发布
                    </button>
                </div>
            </article>
        </div>
    </section>
</template>
