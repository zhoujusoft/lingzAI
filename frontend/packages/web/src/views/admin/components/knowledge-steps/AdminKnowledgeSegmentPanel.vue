<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { marked } from 'marked';
import { useRouter } from 'vue-router';
import KnowledgeStepProgress from './KnowledgeStepProgress.vue';
import PageLayout from '@/components/PageLayout.vue';
import { previewDocumentChunks, processDocument } from '@/api/datasets';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
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
});

const emit = defineEmits(['back', 'next-step']);

const DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE = '500';
const DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE = '3000';
const DEFAULT_LAW_ARTICLE_MAX_CHARS = 1800;
const DEFAULT_LAW_CLAUSE_SPLIT_THRESHOLD = 2200;

const router = useRouter();
const chunkStrategy = ref('DELIMITER_WINDOW');
const separator = ref('\\n\\n');
const maxLength = ref(DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE);
const overlapLength = ref('50');
const lawModeEnabled = ref(false);
const previewChunks = ref([]);
const loadingPreview = ref(false);
const processing = ref(false);

const currentDocId = computed(() => props.knowledge?.document?.docId || null);
const currentFileName = computed(() => props.knowledge?.document?.name || '未命名文件');
const currentFileType = computed(() => {
    const explicitType = String(props.knowledge?.document?.fileType || '').toLowerCase();
    if (explicitType) {
        return explicitType;
    }
    const segments = currentFileName.value.split('.');
    return segments.length > 1 ? String(segments[segments.length - 1] || '').toLowerCase() : '';
});
const supportsHeadingDirectoryChunk = computed(() => ['pdf', 'doc', 'docx'].includes(currentFileType.value));
const supportsLawMode = computed(() => ['pdf', 'doc', 'docx'].includes(currentFileType.value));
const limitedPreviewChunks = computed(() => previewChunks.value.slice(0, 10));

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

function buildChunkConfig() {
    const defaultChunkSize = chunkStrategy.value === 'HEADING_DIRECTORY'
        ? Number(DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE)
        : Number(DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE);
    const config = {
        delimiter: separator.value,
        chunkSize: Number(maxLength.value) || defaultChunkSize,
        overlapSize: Number(overlapLength.value) || 50,
    };
    if (lawModeEnabled.value && chunkStrategy.value === 'HEADING_DIRECTORY' && supportsLawMode.value) {
        config.documentDomain = 'LAW';
        config.lawMode = 'ARTICLE_FIRST';
        config.preserveWholeArticle = true;
        config.articleMaxChars = DEFAULT_LAW_ARTICLE_MAX_CHARS;
        config.clauseSplitThreshold = DEFAULT_LAW_CLAUSE_SPLIT_THRESHOLD;
    }
    return config;
}

function resetSettings() {
    chunkStrategy.value = 'DELIMITER_WINDOW';
    separator.value = '\\n\\n';
    maxLength.value = DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE;
    overlapLength.value = '50';
    lawModeEnabled.value = false;
}

function selectChunkStrategy(strategy) {
    if (strategy === 'HEADING_DIRECTORY' && !supportsHeadingDirectoryChunk.value) {
        return;
    }
    chunkStrategy.value = strategy;
    if (strategy !== 'HEADING_DIRECTORY') {
        lawModeEnabled.value = false;
    }
}

function renderChunkContent(content) {
    return marked.parse(String(content || ''));
}

async function loadPreview() {
    if (!currentDocId.value) {
        previewChunks.value = [];
        return;
    }
    loadingPreview.value = true;
    try {
        const data = await previewDocumentChunks(
            {
                docId: currentDocId.value,
                chunkStrategy: chunkStrategy.value,
                chunkConfig: buildChunkConfig(),
            },
            handleUnauthorized,
        );
        previewChunks.value = Array.isArray(data) ? data.slice(0, 10) : [];
    } catch (error) {
        previewChunks.value = [];
        await alert({
            title: '预览失败',
            message: error?.message || '分块预览失败，请稍后重试。',
        });
    } finally {
        loadingPreview.value = false;
    }
}

async function saveAndProcess() {
    if (!currentDocId.value) {
        return;
    }
    processing.value = true;
    try {
        await processDocument(
            {
                docId: currentDocId.value,
                chunkStrategy: chunkStrategy.value,
                chunkConfig: buildChunkConfig(),
            },
            handleUnauthorized,
        );
        emit('next-step', {
            ...props.knowledge,
            chunkStrategy: chunkStrategy.value,
            chunkConfig: buildChunkConfig(),
        });
    } catch (error) {
        await alert({
            title: '提交失败',
            message: error?.message || '分块处理提交失败，请稍后重试。',
        });
    } finally {
        processing.value = false;
    }
}

watch(
    () => supportsHeadingDirectoryChunk.value,
    supported => {
        if (!supported && chunkStrategy.value === 'HEADING_DIRECTORY') {
            chunkStrategy.value = 'DELIMITER_WINDOW';
        }
        if (!supported) {
            lawModeEnabled.value = false;
        }
    },
    { immediate: true },
);

watch(
    () => chunkStrategy.value,
    (nextStrategy, previousStrategy) => {
        const previousDefault = previousStrategy === 'HEADING_DIRECTORY'
            ? DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE
            : DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE;
        const nextDefault = nextStrategy === 'HEADING_DIRECTORY'
            ? DEFAULT_HEADING_DIRECTORY_CHUNK_SIZE
            : DEFAULT_DELIMITER_WINDOW_CHUNK_SIZE;

        if (!maxLength.value || maxLength.value === previousDefault) {
            maxLength.value = nextDefault;
        }
    },
);

watch(
    () => [chunkStrategy.value, separator.value, maxLength.value, overlapLength.value, lawModeEnabled.value, currentDocId.value],
    () => {
        loadPreview();
    },
);

onMounted(() => {
    loadPreview();
});
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeSegmentPanel"
        class="admin-page admin-page--knowledge-segment-step2"
        footer-class="px-8 py-4"
        body-class="min-h-0 flex flex-col"
    >
<!--        <template #left>-->
<!--            <button-->
<!--                type="button"-->
<!--                class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50"-->
<!--                @click="emit('back')"-->
<!--            >-->
<!--                取消-->
<!--            </button>-->
<!--        </template>-->

        <template #center>
            <KnowledgeStepProgress :current-step="2" />
        </template>

        <div class="min-h-0 flex flex-1 overflow-hidden bg-slate-50">
            <div class="custom-scrollbar min-h-0 w-1/2 overflow-y-auto border-r border-slate-200 bg-slate-50 p-8">
                <div class="mb-6">
                    <h2 class="text-xl font-bold text-slate-800">分段设置</h2>
                    <p class="mt-2 text-sm text-slate-500">上传成功后，先预览分块效果，确认后再开始真正入库处理。</p>
                </div>

                <div class="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
                    <label class="mb-4 block text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">分块模式</label>

                    <div class="space-y-4">
                        <button
                            type="button"
                            class="w-full rounded-3xl border p-5 text-left transition-all"
                            :class="chunkStrategy === 'DELIMITER_WINDOW'
                                ? 'border-primary bg-blue-50 shadow-sm shadow-blue-100/80'
                                : 'border-slate-200 bg-slate-50/70 hover:border-slate-300 hover:bg-white'"
                            @click="selectChunkStrategy('DELIMITER_WINDOW')"
                        >
                            <div class="flex items-start gap-4">
                                <div
                                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                                    :class="chunkStrategy === 'DELIMITER_WINDOW' ? 'bg-primary text-white' : 'bg-white text-slate-500'"
                                >
                                    <span class="material-symbols-outlined text-[22px]">segment</span>
                                </div>
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center justify-between gap-3">
                                        <div class="font-semibold text-slate-800">分隔符分块</div>
                                        <span
                                            class="flex h-5 w-5 items-center justify-center rounded-full border"
                                            :class="chunkStrategy === 'DELIMITER_WINDOW' ? 'border-primary bg-primary text-white' : 'border-slate-300 bg-white'"
                                        >
                                            <span v-if="chunkStrategy === 'DELIMITER_WINDOW'" class="material-symbols-outlined text-[14px]">done</span>
                                        </span>
                                    </div>
                                    <p class="mt-1 text-sm leading-6 text-slate-500">
                                        按分隔符切段，并用重叠窗口保持上下文连续性。
                                    </p>
                                </div>
                            </div>

                            <div class="mt-5 space-y-4 border-t border-slate-200/70 pt-5">
                                <div>
                                    <label class="mb-2 block text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">分段标识符</label>
                                    <input
                                        v-model="separator"
                                        type="text"
                                        class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        placeholder="例如：\\n\\n"
                                    />
                                    <p class="mt-2 text-xs text-slate-400">支持输入 <code>\n</code>、<code>\r\n</code>、<code>/n</code> 这类转义写法。</p>
                                </div>

                                <div class="grid grid-cols-2 gap-4">
                                    <div>
                                        <label class="mb-2 block text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">分段最大长度</label>
                                        <input
                                            v-model="maxLength"
                                            type="text"
                                            class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        />
                                    </div>
                                    <div>
                                        <label class="mb-2 block text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">分段重叠长度</label>
                                        <input
                                            v-model="overlapLength"
                                            type="text"
                                            class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/15"
                                        />
                                    </div>
                                </div>
                            </div>
                        </button>

                        <button
                            type="button"
                            class="w-full rounded-3xl border p-5 text-left transition-all"
                            :aria-disabled="!supportsHeadingDirectoryChunk"
                            :class="chunkStrategy === 'HEADING_DIRECTORY'
                                ? 'border-primary bg-blue-50 shadow-sm shadow-blue-100/80'
                                : supportsHeadingDirectoryChunk
                                    ? 'border-slate-200 bg-slate-50/70 hover:border-slate-300 hover:bg-white'
                                    : 'cursor-not-allowed border-slate-200 bg-slate-100/80 opacity-60'"
                            @click="selectChunkStrategy('HEADING_DIRECTORY')"
                        >
                            <div class="flex items-start gap-4">
                                <div
                                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                                    :class="chunkStrategy === 'HEADING_DIRECTORY' ? 'bg-primary text-white' : 'bg-white text-slate-500'"
                                >
                                    <span class="material-symbols-outlined text-[22px]">account_tree</span>
                                </div>
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center justify-between gap-3">
                                        <div class="flex items-center gap-2">
                                            <div class="font-semibold text-slate-800">章节目录分块</div>
                                            <div class="group relative">
                                                <span
                                                    title="只支持word和pdf文档"
                                                    class="flex h-5 w-5 items-center justify-center rounded-full border border-slate-300 bg-white text-[11px] font-bold text-slate-500"
                                                >
                                                    ?
                                                </span>
                                                <div
                                                    class="pointer-events-none absolute left-1/2 top-full z-10 mt-2 hidden -translate-x-1/2 whitespace-nowrap rounded-xl bg-slate-900 px-3 py-2 text-xs text-white shadow-xl group-hover:block"
                                                >
                                                    只支持word和pdf文档
                                                </div>
                                            </div>
                                        </div>
                                        <span
                                            class="flex h-5 w-5 items-center justify-center rounded-full border"
                                            :class="chunkStrategy === 'HEADING_DIRECTORY' ? 'border-primary bg-primary text-white' : 'border-slate-300 bg-white'"
                                        >
                                            <span v-if="chunkStrategy === 'HEADING_DIRECTORY'" class="material-symbols-outlined text-[14px]">done</span>
                                        </span>
                                    </div>
                                    <p class="mt-1 text-sm leading-6 text-slate-500">
                                        按文档标题层级自动切分，更适合规范、手册、制度等结构化文档。
                                    </p>
                                    <p v-if="!supportsHeadingDirectoryChunk" class="mt-2 text-xs font-medium text-amber-700">
                                        当前文件类型不可使用该模式。
                                    </p>
                                </div>
                            </div>

                            <div class="mt-5 border-t border-slate-200/70 pt-5">
                                <div class="flex items-start justify-between gap-4 rounded-2xl bg-white/80 px-4 py-4">
                                    <div class="min-w-0">
                                        <div class="font-medium text-slate-700">法律法规增强模式</div>
                                        <p class="mt-1 text-xs leading-5 text-slate-500">
                                            开启后优先按“第X条”聚合分块，适合法律、条例、办法等条文型文档。
                                        </p>
                                        <p
                                            v-if="!supportsLawMode || chunkStrategy !== 'HEADING_DIRECTORY'"
                                            class="mt-2 text-xs font-medium text-amber-700"
                                        >
                                            仅在章节目录分块的 Word/PDF 文档中可开启。
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        class="relative mt-1 inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors"
                                        :class="lawModeEnabled ? 'bg-emerald-500' : 'bg-slate-300'"
                                        :disabled="!supportsLawMode || chunkStrategy !== 'HEADING_DIRECTORY'"
                                        @click.stop="lawModeEnabled = !lawModeEnabled"
                                    >
                                        <span
                                            class="inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform"
                                            :class="lawModeEnabled ? 'translate-x-6' : 'translate-x-1'"
                                        />
                                    </button>
                                </div>
                            </div>
                        </button>
                    </div>

                    <div class="mt-8 flex justify-between border-t border-slate-100 pt-6">
                        <button
                            type="button"
                            class="text-sm font-medium text-primary hover:underline"
                            @click="loadPreview"
                        >
                            重新预览
                        </button>
                        <button
                            type="button"
                            class="text-sm font-medium text-slate-400 hover:text-slate-600"
                            @click="resetSettings"
                        >
                            重置
                        </button>
                    </div>
                </div>
            </div>

            <div class="min-h-0 flex w-1/2 min-w-0 flex-col overflow-hidden bg-white">
                <header class="flex items-center justify-between border-b border-slate-100 px-6 py-5">
                    <div>
                        <h2 class="text-base font-bold text-slate-800">分块预览</h2>
                        <p class="mt-1 text-xs text-slate-400">仅展示前 10 个分块，确认结构是否符合预期。</p>
                    </div>
                    <div class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-2 py-1">
                        <span class="material-symbols-outlined text-sm text-slate-400">description</span>
                        <span class="max-w-[150px] truncate text-xs text-slate-600">{{ currentFileName }}</span>
                    </div>
                </header>

                <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto p-6">
                    <div v-if="loadingPreview" class="rounded-2xl border border-slate-200 bg-slate-50 px-5 py-4 text-sm text-slate-500">正在生成预览...</div>
                    <div v-else-if="!limitedPreviewChunks.length" class="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-5 py-10 text-center text-sm text-slate-500">暂无预览结果</div>
                    <div v-else class="space-y-4">
                    <article
                        v-for="(chunk, index) in limitedPreviewChunks"
                        :key="chunk.id"
                        class="overflow-hidden rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"
                    >
                        <div class="mb-4 flex items-start justify-between gap-4">
                            <div class="space-y-2">
                                <span class="inline-flex rounded-full bg-blue-50 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-primary">
                                    {{ chunk.label || `Chunk-${index + 1}` }}
                                </span>
                                <div class="flex flex-wrap items-center gap-2 text-xs text-slate-400">
                                    <span>{{ chunk.length }} 字符</span>
                                    <span v-if="chunk.blockType" class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500">
                                        {{ chunk.blockType }}
                                    </span>
                                </div>
                            </div>
                            <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-slate-50 text-slate-400">
                                <span class="material-symbols-outlined text-lg">notes</span>
                            </div>
                        </div>
                        <div
                            class="chunk-markdown-render mt-4 overflow-x-auto rounded-2xl bg-slate-50 px-4 py-4 text-sm text-slate-600"
                            v-html="renderChunkContent(chunk.content)"
                        ></div>
                    </article>
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div class="z-10 flex items-center justify-between">
                <button
                    type="button"
                    class="rounded-lg border border-slate-200 px-6 py-2 font-medium text-slate-600 transition-colors hover:bg-slate-50"
                    @click="emit('back')"
                >
                    取消
                </button>
                <button
                    type="button"
                    class="rounded-lg bg-primary px-8 py-2.5 font-bold text-white shadow-lg shadow-blue-500/20 transition-all hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    :disabled="processing || loadingPreview || !currentDocId"
                    @click="saveAndProcess"
                >
                    确认并开始处理
                </button>
            </div>
        </template>
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
