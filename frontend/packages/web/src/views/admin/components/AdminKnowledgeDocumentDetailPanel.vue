<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { marked } from 'marked';
import { useRouter } from 'vue-router';
import MiniPagination from '@/components/MiniPagination.vue';
import PageLayout from '@/components/PageLayout.vue';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import {
    appendDocumentChunk,
    getDocumentDetail,
    listDocumentChunks,
    updateDocumentChunk,
} from '@/api/datasets';
import { ROUTE_PATHS } from '@/router/routePaths';

marked.setOptions({
    breaks: true,
    gfm: true,
});

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
    document: {
        type: Object,
        required: true,
    },
});

const emit = defineEmits(['back']);

const router = useRouter();
const detail = ref(null);
const chunks = ref([]);
const loadingDetail = ref(false);
const loadingChunks = ref(false);
const savingChunk = ref(false);
const loadError = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pageSizeOptions = [10, 20, 50];
const addFormOpen = ref(false);
const chunkContent = ref('');
const chunkType = ref('MANUAL');
const headingsText = ref('');
const keywordsText = ref('');
const editingChunkId = ref(null);
const savingEditedChunkId = ref(null);
const editForm = reactive({
    chunkContent: '',
    chunkType: '',
    headingsText: '',
    keywordsText: '',
});

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function statusText(status) {
    switch (Number(status)) {
        case 0:
            return '待处理';
        case 1:
            return '处理中';
        case 2:
            return '已完成';
        case 3:
            return '失败';
        default:
            return '未知';
    }
}

function statusClass(status) {
    switch (Number(status)) {
        case 0:
            return 'bg-slate-100 text-slate-600';
        case 1:
            return 'bg-amber-50 text-amber-700';
        case 2:
            return 'bg-green-50 text-green-700';
        case 3:
            return 'bg-red-50 text-red-600';
        default:
            return 'bg-slate-100 text-slate-500';
    }
}

function formatFileSize(fileSize) {
    if (typeof fileSize !== 'number' || !Number.isFinite(fileSize) || fileSize <= 0) {
        return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    const index = Math.min(Math.floor(Math.log(fileSize) / Math.log(1024)), units.length - 1);
    const value = fileSize / 1024 ** index;
    return `${Math.round(value * 100) / 100} ${units[index]}`;
}

function fileIcon(fileType) {
    const type = String(fileType || '').toLowerCase();
    if (type === 'pdf') {
        return 'picture_as_pdf';
    }
    if (type === 'doc' || type === 'docx') {
        return 'description';
    }
    if (type === 'md') {
        return 'article';
    }
    return 'article';
}

function parseStringList(raw) {
    if (!raw) {
        return [];
    }
    if (Array.isArray(raw)) {
        return raw.filter(Boolean).map(value => String(value));
    }
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed)
            ? parsed.filter(Boolean).map(value => String(value))
            : String(raw)
                  .split(/,|，/)
                  .map(item => item.trim())
                  .filter(Boolean);
    } catch (error) {
        return String(raw)
            .split(/,|，/)
            .map(item => item.trim())
            .filter(Boolean);
    }
}

function parseChunkConfig(raw) {
    if (!raw) {
        return {};
    }
    if (typeof raw === 'object') {
        return raw;
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        return {
            raw: String(raw),
        };
    }
}

function formatChunkStrategy(strategy) {
    switch (String(strategy || '').toUpperCase()) {
        case 'DELIMITER_WINDOW':
            return '分隔符分块';
        case 'HEADING_DIRECTORY':
            return '章节目录分块';
        case 'AUTO':
            return '自动策略';
        case 'MANUAL':
            return '手动分块';
        default:
            return strategy || '-';
    }
}

function formatDelimiter(value) {
    if (value == null || value === '') {
        return '-';
    }
    return String(value).replace(/\r/g, '\\r').replace(/\n/g, '\\n');
}

function normalizeChunk(item) {
    return {
        ...item,
        chunkId: item?.chunkId ?? item?.id ?? '',
        chunkOrder: Number(item?.chunkOrder || 0),
        headings: parseStringList(item?.headings),
        keywords: parseStringList(item?.keywords),
        chunkType: item?.chunkType || 'TEXT',
        chunkContent: item?.chunkContent || '',
        charCount: Number(item?.charCount || 0),
    };
}

const resolvedDetail = computed(() => detail.value || props.document || {});
const knowledgeId = computed(
    () => detail.value?.kbId || props.document?.kbId || props.knowledge?.id || null
);
const documentName = computed(() => resolvedDetail.value?.name || '未命名文档');
const documentPath = computed(() => resolvedDetail.value?.path || props.knowledge?.name || '-');
const documentIcon = computed(() => fileIcon(resolvedDetail.value?.fileType));
const parsedChunkConfig = computed(() => parseChunkConfig(detail.value?.chunkConfig));

const summaryItems = computed(() => {
    const current = resolvedDetail.value;
    return [
        {
            label: '文件类型',
            value: current.fileType || '-',
            icon: 'description',
        },
        {
            label: '文件大小',
            value: formatFileSize(current.fileSize),
            icon: 'data_object',
        },
        {
            label: '分段数量',
            value: `${current.chunkCount || total.value || 0}`,
            icon: 'segment',
        },
        {
            label: '字符总数',
            value: `${current.charCount || 0}`,
            icon: 'text_fields',
        },
    ];
});

const canAppendChunk = computed(() => Number(detail.value?.status) === 2);
const metadataItems = computed(() => [
    { label: '文件名', value: documentName.value },
    { label: '所属知识库', value: props.knowledge?.name || '-' },
    { label: '文件类型', value: resolvedDetail.value?.fileType || '-' },
    { label: '文件大小', value: formatFileSize(resolvedDetail.value?.fileSize) },
    { label: '上传时间', value: resolvedDetail.value?.uploadTime || '-' },
    { label: '文档路径', value: documentPath.value },
]);
const technicalItems = computed(() => {
    const config = parsedChunkConfig.value;
    const items = [
        {
            label: '分段方式',
            value: formatChunkStrategy(detail.value?.chunkStrategy),
        },
    ];

    if (config.chunkSize != null) {
        items.push({
            label: '块大小',
            value: `${config.chunkSize}`,
        });
    }
    if (config.overlapSize != null) {
        items.push({
            label: '重叠度',
            value: `${config.overlapSize}`,
        });
    }
    if (config.delimiter != null) {
        items.push({
            label: '分隔符',
            value: formatDelimiter(config.delimiter),
            monospace: true,
        });
    }
    if (config.raw) {
        items.push({
            label: '原始配置',
            value: config.raw,
            monospace: true,
            multiline: true,
        });
    }
    return items;
});
const chunkSectionDescription = computed(
    () => `按 chunk_order 分页展示当前文档内容，共 ${total.value || 0} 个分段。`
);

function renderChunkContent(content) {
    return marked.parse(String(content || ''));
}

async function loadDetail() {
    if (!props.document?.docId) {
        detail.value = null;
        return;
    }
    loadingDetail.value = true;
    loadError.value = '';
    try {
        detail.value = await getDocumentDetail(props.document.docId, handleUnauthorized);
    } catch (error) {
        detail.value = null;
        loadError.value = error?.message || '文档详情加载失败';
    } finally {
        loadingDetail.value = false;
    }
}

async function loadChunks() {
    if (!props.document?.docId) {
        chunks.value = [];
        total.value = 0;
        return;
    }
    loadingChunks.value = true;
    loadError.value = '';
    try {
        const data = await listDocumentChunks(
            {
                docId: props.document.docId,
                pageNum: page.value,
                pageSize: pageSize.value,
            },
            handleUnauthorized
        );
        chunks.value = Array.isArray(data?.records) ? data.records.map(normalizeChunk) : [];
        total.value = Number(data?.total || 0);
    } catch (error) {
        chunks.value = [];
        total.value = 0;
        loadError.value = error?.message || '分块列表加载失败';
    } finally {
        loadingChunks.value = false;
    }
}

async function refreshDetailAndChunks() {
    await loadDetail();
    await loadChunks();
}

function normalizeInputList(text) {
    return String(text || '')
        .split(/\n|,|，/)
        .map(item => item.trim())
        .filter(Boolean);
}

function stringifyInputList(text) {
    return JSON.stringify(normalizeInputList(text));
}

function resetForm() {
    chunkContent.value = '';
    chunkType.value = 'MANUAL';
    headingsText.value = '';
    keywordsText.value = '';
}

function resetEditForm() {
    editForm.chunkContent = '';
    editForm.chunkType = '';
    editForm.headingsText = '';
    editForm.keywordsText = '';
}

function cancelEditingChunk() {
    editingChunkId.value = null;
    resetEditForm();
}

function formatEditableList(values) {
    return Array.isArray(values) ? values.join(', ') : '';
}

function startEditingChunk(chunk) {
    editingChunkId.value = chunk.chunkId;
    editForm.chunkContent = chunk.chunkContent || '';
    editForm.chunkType = chunk.chunkType || 'TEXT';
    editForm.headingsText = formatEditableList(chunk.headings);
    editForm.keywordsText = formatEditableList(chunk.keywords);
}

async function submitChunk() {
    const content = chunkContent.value.trim();
    if (!content) {
        await alert({
            title: '请输入分块内容',
            message: '手动新增分块时，分块正文不能为空。',
        });
        return;
    }

    savingChunk.value = true;
    try {
        await appendDocumentChunk(
            {
                docId: props.document.docId,
                chunkContent: content,
                chunkType: chunkType.value.trim() || 'MANUAL',
                headings: normalizeInputList(headingsText.value),
                keywords: normalizeInputList(keywordsText.value),
            },
            handleUnauthorized
        );
        await loadDetail();
        const nextTotal = Number(detail.value?.chunkCount || 0);
        page.value = Math.max(1, Math.ceil(nextTotal / pageSize.value));
        await loadChunks();
        resetForm();
        addFormOpen.value = false;
    } catch (error) {
        await alert({
            title: '新增分块失败',
            message: error?.message || '分块追加失败，请稍后重试。',
        });
    } finally {
        savingChunk.value = false;
    }
}

async function submitChunkEdit(chunk) {
    const kbId = knowledgeId.value;
    if (kbId == null) {
        await alert({
            title: '保存失败',
            message: '当前知识库信息缺失，无法保存分段修改。',
        });
        return;
    }

    const content = editForm.chunkContent.trim();
    if (!content) {
        await alert({
            title: '请输入分段内容',
            message: '编辑分段时，分段正文不能为空。',
        });
        return;
    }

    savingEditedChunkId.value = chunk.chunkId;
    try {
        await updateDocumentChunk(
            {
                kbId,
                chunkId: chunk.chunkId,
                chunkContent: content,
                chunkType: editForm.chunkType.trim() || chunk.chunkType || 'TEXT',
                headings: stringifyInputList(editForm.headingsText),
                keywords: stringifyInputList(editForm.keywordsText),
            },
            handleUnauthorized
        );
        await loadDetail();
        await loadChunks();
        cancelEditingChunk();
    } catch (error) {
        await alert({
            title: '保存失败',
            message: error?.message || '分段修改失败，请稍后重试。',
        });
    } finally {
        savingEditedChunkId.value = null;
    }
}

watch(
    () => props.document?.docId,
    async () => {
        page.value = 1;
        resetForm();
        cancelEditingChunk();
        addFormOpen.value = false;
        await refreshDetailAndChunks();
    },
    { immediate: true }
);

watch([page, pageSize], () => {
    cancelEditingChunk();
    loadChunks();
});
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeDocumentDetailPanel"
        class="admin-page admin-page--knowledge-document-detail"
        header-class="border-b border-slate-200 bg-white px-6"
        body-class="min-h-0 bg-[#f6f7f8]"
    >
        <template #left>
            <div class="flex min-w-0 items-center gap-3">
                <button
                    type="button"
                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-slate-500 transition-colors hover:bg-slate-100"
                    @click="emit('back')"
                >
                    <span class="material-symbols-outlined">arrow_back</span>
                </button>

                <div
                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-primary"
                >
                    <span class="material-symbols-outlined text-[22px]">{{ documentIcon }}</span>
                </div>

                <div class="min-w-0">
                    <h1 class="truncate text-lg font-bold text-slate-900">{{ documentName }}</h1>
                    <p class="truncate text-sm text-slate-500">{{ documentPath }}</p>
                </div>
            </div>
        </template>

        <template #right>
            <div class="flex flex-wrap items-center justify-end gap-3">
                <span
                    class="rounded-full px-3 py-1 text-xs font-semibold"
                    :class="statusClass(detail?.status)"
                >
                    {{ statusText(detail?.status) }}
                </span>
                <button
                    type="button"
                    class="flex items-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-500/15 transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                    :disabled="!canAppendChunk || savingChunk"
                    @click="addFormOpen = !addFormOpen"
                >
                    <span class="material-symbols-outlined text-lg">add</span>
                    <span>{{ addFormOpen ? '收起新增' : '添加分段' }}</span>
                </button>
            </div>
        </template>

        <div class="custom-scrollbar h-full overflow-y-auto">
            <div
                class="mx-auto flex min-h-full w-full max-w-[1440px] flex-col gap-6 p-6 xl:flex-row"
            >
                <div class="min-w-0 flex-1 space-y-6">
                    <div
                        v-if="loadError"
                        class="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
                    >
                        {{ loadError }}
                    </div>

                    <section
                        class="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm"
                    >
                        <div class="border-b border-slate-100 px-6 py-6">
                            <div
                                class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"
                            >
                                <div>
                                    <div
                                        class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400"
                                    >
                                        Document Detail
                                    </div>
                                    <h2 class="mt-2 text-2xl font-bold text-slate-900">
                                        {{ documentName }}
                                    </h2>
                                    <p class="mt-2 text-sm leading-6 text-slate-500">
                                        在当前详情页中查看文档元数据、分页分段内容，并按现有入库流程追加新的手动分段。
                                    </p>
                                </div>

                                <div
                                    class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600"
                                >
                                    <div class="text-xs uppercase tracking-[0.18em] text-slate-400">
                                        当前策略
                                    </div>
                                    <div class="mt-1 font-semibold text-slate-800">
                                        {{ formatChunkStrategy(detail?.chunkStrategy) }}
                                    </div>
                                </div>
                            </div>

                            <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                                <article
                                    v-for="item in summaryItems"
                                    :key="item.label"
                                    class="rounded-3xl border border-slate-200 bg-slate-50/80 px-5 py-4"
                                >
                                    <div class="flex items-start justify-between gap-3">
                                        <div>
                                            <div
                                                class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-400"
                                            >
                                                {{ item.label }}
                                            </div>
                                            <div
                                                class="mt-2 text-base font-semibold text-slate-800"
                                            >
                                                {{ item.value }}
                                            </div>
                                        </div>
                                        <div
                                            class="flex h-10 w-10 items-center justify-center rounded-2xl bg-white text-slate-400 shadow-sm"
                                        >
                                            <span class="material-symbols-outlined text-lg">{{
                                                item.icon
                                            }}</span>
                                        </div>
                                    </div>
                                </article>
                            </div>

                            <div
                                v-if="detail?.errorMessage"
                                class="mt-5 rounded-2xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600"
                            >
                                {{ detail.errorMessage }}
                            </div>

                            <div
                                v-if="!canAppendChunk"
                                class="mt-5 rounded-2xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700"
                            >
                                当前文档状态为
                                {{ statusText(detail?.status) }}，仅“已完成”文档支持手动追加分段。
                            </div>
                        </div>
                        <section
                            v-if="addFormOpen"
                            class="border-t border-slate-100 bg-slate-50/70 px-6 py-6"
                        >
                            <div class="mb-5 flex items-center justify-between">
                                <div>
                                    <h3 class="text-lg font-semibold text-slate-900">追加分段</h3>
                                    <p class="mt-1 text-sm text-slate-500">
                                        新增内容会作为最后一个分段写入当前文档，并同步向量索引。
                                    </p>
                                </div>
                            </div>

                            <div class="grid gap-4 lg:grid-cols-2">
                                <label class="block">
                                    <span class="mb-2 block text-sm font-medium text-slate-700"
                                        >分段类型</span
                                    >
                                    <input
                                        v-model="chunkType"
                                        type="text"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        placeholder="例如：MANUAL"
                                    />
                                </label>
                                <label class="block">
                                    <span class="mb-2 block text-sm font-medium text-slate-700"
                                        >标题路径</span
                                    >
                                    <input
                                        v-model="headingsText"
                                        type="text"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        placeholder="例如：第一章, 概述"
                                    />
                                </label>
                            </div>

                            <label class="mt-4 block">
                                <span class="mb-2 block text-sm font-medium text-slate-700"
                                    >关键词</span
                                >
                                <input
                                    v-model="keywordsText"
                                    type="text"
                                    class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                    placeholder="多个关键词使用逗号或换行分隔"
                                />
                            </label>

                            <label class="mt-4 block">
                                <span class="mb-2 block text-sm font-medium text-slate-700"
                                    >分段内容</span
                                >
                                <textarea
                                    v-model="chunkContent"
                                    rows="8"
                                    class="w-full rounded-3xl border border-slate-200 bg-white px-4 py-3 text-sm leading-6 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                    placeholder="输入要追加到文档末尾的分段正文"
                                />
                            </label>

                            <div class="mt-5 flex items-center justify-end gap-3">
                                <button
                                    type="button"
                                    class="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-600 transition hover:bg-slate-50"
                                    @click="addFormOpen = false"
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                                    :disabled="savingChunk"
                                    @click="submitChunk"
                                >
                                    {{ savingChunk ? '提交中...' : '确认追加' }}
                                </button>
                            </div>
                        </section>
                    </section>

                    <section
                        class="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm"
                    >
                        <div
                            class="flex flex-col gap-4 border-b border-slate-100 px-6 py-5 lg:flex-row lg:items-center lg:justify-between"
                        >
                            <div>
                                <h2 class="text-lg font-bold text-slate-900">分段列表</h2>
                                <p class="mt-1 text-sm text-slate-500">
                                    {{ chunkSectionDescription }}
                                </p>
                            </div>

                            <MiniPagination
                                :page="page"
                                :page-size="pageSize"
                                :total="total"
                                :page-size-options="pageSizeOptions"
                                @page-change="page = $event"
                                @page-size-change="
                                    value => {
                                        pageSize = value;
                                        page = 1;
                                    }
                                "
                            />
                        </div>

                        <div class="p-6">
                            <div
                                v-if="loadingDetail || loadingChunks"
                                class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-500"
                            >
                                正在加载文档内容...
                            </div>

                            <div
                                v-else-if="chunks.length === 0"
                                class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-500"
                            >
                                当前文档还没有分段内容。
                            </div>

                            <div v-else class="space-y-4">
                                <article
                                    v-for="chunk in chunks"
                                    :key="chunk.chunkId"
                                    class="rounded-3xl border border-slate-200 bg-white p-5 transition-shadow hover:shadow-md"
                                >
                                    <div
                                        class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"
                                    >
                                        <div class="flex min-w-0 items-start gap-4">
                                            <div
                                                class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-primary"
                                            >
                                                <span class="material-symbols-outlined text-[20px]"
                                                    >notes</span
                                                >
                                            </div>

                                            <div class="min-w-0 flex-1">
                                                <div class="flex flex-wrap items-center gap-2">
                                                    <span class="text-sm font-bold text-slate-800"
                                                        >分段-{{ chunk.chunkOrder }}</span
                                                    >
                                                    <span
                                                        class="rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-primary"
                                                    >
                                                        {{ chunk.chunkType }}
                                                    </span>
                                                </div>

                                                <div
                                                    class="mt-2 flex flex-wrap items-center gap-3 text-xs text-slate-400"
                                                >
                                                    <span class="flex items-center gap-1">
                                                        <span
                                                            class="material-symbols-outlined text-sm"
                                                            >text_fields</span
                                                        >
                                                        <span>{{ chunk.charCount }} 字符</span>
                                                    </span>
                                                    <span class="flex items-center gap-1">
                                                        <span
                                                            class="material-symbols-outlined text-sm"
                                                            >tag</span
                                                        >
                                                        <span class="break-all">{{
                                                            chunk.indexId || '无索引 ID'
                                                        }}</span>
                                                    </span>
                                                </div>

                                                <div
                                                    v-if="chunk.headings.length"
                                                    class="mt-3 flex flex-wrap gap-2"
                                                >
                                                    <span
                                                        v-for="heading in chunk.headings"
                                                        :key="`${chunk.chunkId}-${heading}`"
                                                        class="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-medium text-amber-700"
                                                    >
                                                        {{ heading }}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        <div class="flex flex-wrap items-center justify-end gap-2">
                                            <button
                                                type="button"
                                                class="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
                                                :disabled="
                                                    !canAppendChunk ||
                                                    (savingEditedChunkId &&
                                                        savingEditedChunkId !== chunk.chunkId)
                                                "
                                                @click="
                                                    editingChunkId === chunk.chunkId
                                                        ? cancelEditingChunk()
                                                        : startEditingChunk(chunk)
                                                "
                                            >
                                                <span class="material-symbols-outlined text-[16px]">
                                                    {{
                                                        editingChunkId === chunk.chunkId
                                                            ? 'close'
                                                            : 'edit'
                                                    }}
                                                </span>
                                                <span>{{
                                                    editingChunkId === chunk.chunkId
                                                        ? '取消编辑'
                                                        : '编辑内容'
                                                }}</span>
                                            </button>
                                            <div class="text-xs text-slate-400">
                                                Chunk ID: {{ chunk.chunkId }}
                                            </div>
                                        </div>
                                    </div>

                                    <div
                                        v-if="editingChunkId === chunk.chunkId"
                                        class="mt-4 rounded-3xl bg-slate-50 p-4 ring-1 ring-slate-200"
                                    >
                                        <div class="grid gap-4 lg:grid-cols-2">
                                            <label class="block">
                                                <span
                                                    class="mb-2 block text-sm font-medium text-slate-700"
                                                    >分段类型</span
                                                >
                                                <input
                                                    v-model="editForm.chunkType"
                                                    type="text"
                                                    class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                                    placeholder="例如：MANUAL"
                                                />
                                            </label>
                                            <label class="block">
                                                <span
                                                    class="mb-2 block text-sm font-medium text-slate-700"
                                                    >标题路径</span
                                                >
                                                <input
                                                    v-model="editForm.headingsText"
                                                    type="text"
                                                    class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                                    placeholder="例如：第一章, 概述"
                                                />
                                            </label>
                                        </div>

                                        <label class="mt-4 block">
                                            <span
                                                class="mb-2 block text-sm font-medium text-slate-700"
                                                >关键词</span
                                            >
                                            <input
                                                v-model="editForm.keywordsText"
                                                type="text"
                                                class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                                placeholder="多个关键词使用逗号或换行分隔"
                                            />
                                        </label>

                                        <label class="mt-4 block">
                                            <span
                                                class="mb-2 block text-sm font-medium text-slate-700"
                                                >分段内容</span
                                            >
                                            <textarea
                                                v-model="editForm.chunkContent"
                                                rows="8"
                                                class="w-full rounded-3xl border border-slate-200 bg-white px-4 py-3 text-sm leading-6 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
                                                placeholder="请输入分段正文"
                                            />
                                        </label>

                                        <div class="mt-4 flex items-center justify-end gap-3">
                                            <button
                                                type="button"
                                                class="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-600 transition hover:bg-slate-50"
                                                @click="cancelEditingChunk()"
                                            >
                                                取消
                                            </button>
                                            <button
                                                type="button"
                                                class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                                                :disabled="savingEditedChunkId === chunk.chunkId"
                                                @click="submitChunkEdit(chunk)"
                                            >
                                                {{
                                                    savingEditedChunkId === chunk.chunkId
                                                        ? '保存中...'
                                                        : '保存修改'
                                                }}
                                            </button>
                                        </div>
                                    </div>

                                    <template v-else>
                                        <div
                                            class="chunk-markdown-render mt-4 overflow-x-auto rounded-3xl bg-slate-50 px-4 py-4 text-sm text-slate-700"
                                            v-html="renderChunkContent(chunk.chunkContent)"
                                        ></div>

                                        <div
                                            v-if="chunk.keywords.length"
                                            class="mt-4 flex flex-wrap gap-2"
                                        >
                                            <span
                                                v-for="keyword in chunk.keywords"
                                                :key="`${chunk.chunkId}-${keyword}`"
                                                class="rounded-full bg-white px-2.5 py-1 text-[11px] text-slate-500 ring-1 ring-slate-200"
                                            >
                                                #{{ keyword }}
                                            </span>
                                        </div>
                                    </template>
                                </article>
                            </div>
                        </div>
                    </section>
                </div>

                <aside class="w-full shrink-0 space-y-6 xl:w-[320px]">
                    <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h3 class="flex items-center gap-2 text-sm font-bold text-slate-900">
                            <span class="material-symbols-outlined text-primary">info</span>
                            元数据
                        </h3>

                        <div class="mt-5 space-y-4">
                            <div v-for="item in metadataItems" :key="item.label">
                                <div class="text-xs uppercase tracking-[0.16em] text-slate-400">
                                    {{ item.label }}
                                </div>
                                <div class="mt-1 break-all text-sm font-medium text-slate-700">
                                    {{ item.value }}
                                </div>
                            </div>
                        </div>
                    </section>

                    <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h3 class="flex items-center gap-2 text-sm font-bold text-slate-900">
                            <span class="material-symbols-outlined text-primary"
                                >settings_input_component</span
                            >
                            技术参数
                        </h3>

                        <div class="mt-5 space-y-3">
                            <div
                                v-for="item in technicalItems"
                                :key="item.label"
                                class="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3"
                            >
                                <div class="text-xs uppercase tracking-[0.16em] text-slate-400">
                                    {{ item.label }}
                                </div>
                                <div
                                    class="mt-1 text-sm font-medium text-slate-700"
                                    :class="[
                                        item.monospace ? 'font-mono text-[13px]' : '',
                                        item.multiline
                                            ? 'whitespace-pre-wrap break-all'
                                            : 'break-all',
                                    ]"
                                >
                                    {{ item.value }}
                                </div>
                            </div>
                        </div>

                        <div
                            class="mt-5 rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-slate-600"
                        >
                            仅支持末尾追加分段。新增成功后会立即写入向量索引，保证召回测试和详情页展示一致。
                        </div>
                    </section>
                </aside>
            </div>
        </div>
    </PageLayout>
</template>

<style scoped>
.chunk-markdown-render :deep(:first-child) {
    margin-top: 0;
}

.chunk-markdown-render :deep(:last-child) {
    margin-bottom: 0;
}

.chunk-markdown-render :deep(h1),
.chunk-markdown-render :deep(h2),
.chunk-markdown-render :deep(h3),
.chunk-markdown-render :deep(h4),
.chunk-markdown-render :deep(h5),
.chunk-markdown-render :deep(h6) {
    margin: 0 0 0.75rem;
    font-weight: 700;
    color: #0f172a;
}

.chunk-markdown-render :deep(h1),
.chunk-markdown-render :deep(h2) {
    font-size: 1rem;
    line-height: 1.5;
}

.chunk-markdown-render :deep(h3) {
    font-size: 0.9375rem;
    line-height: 1.5;
}

.chunk-markdown-render :deep(h4),
.chunk-markdown-render :deep(h5),
.chunk-markdown-render :deep(h6) {
    font-size: 0.875rem;
    line-height: 1.45;
}

.chunk-markdown-render :deep(p) {
    margin: 0 0 0.75rem;
    line-height: 1.75;
}

.chunk-markdown-render :deep(table) {
    min-width: 100%;
    border-collapse: collapse;
    background: #ffffff;
}

.chunk-markdown-render :deep(td),
.chunk-markdown-render :deep(th) {
    border: 1px solid #dbe4f0;
    padding: 0.625rem 0.75rem;
    vertical-align: top;
    line-height: 1.65;
    color: #334155;
}

.chunk-markdown-render :deep(tr:first-child td),
.chunk-markdown-render :deep(tr:first-child th) {
    background: #f8fafc;
    font-weight: 600;
}
</style>
