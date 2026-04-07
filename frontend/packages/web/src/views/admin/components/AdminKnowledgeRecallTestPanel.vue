<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import PageLayout from '@/components/PageLayout.vue';
import { alert } from '@/composables/useModal';
import { clearUserSession } from '@/composables/useCurrentUser';
import { recallKnowledgeBase } from '@/api/knowledge-bases';
import { ROUTE_PATHS } from '@/router/routePaths';

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
});

const emit = defineEmits(['back']);
const router = useRouter();

const sourceText = ref('对于Um=72.5kV且额定容量为 10 000 kVA及以上和U.>72.5 kV的变压器');
const maxChars = 200;
const running = ref(false);

const testRecords = ref([]);

const recallChunks = ref([]);

const charCount = computed(() => sourceText.value.length);

function handleUnauthorized() {
    clearUserSession();
    router.replace(ROUTE_PATHS.login);
}

async function runRecallTest() {
    const query = sourceText.value.trim();
    if (!query) {
        await alert({
            title: '请输入查询文本',
            message: '召回测试需要一段查询文本。',
        });
        return;
    }

    running.value = true;
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const hh = String(now.getHours()).padStart(2, '0');
    const min = String(now.getMinutes()).padStart(2, '0');

    try {
        const chunks = await recallKnowledgeBase(
            {
                kbId: props.knowledge?.id ?? props.knowledge?.kbId,
                query,
                topK: 8,
            },
            handleUnauthorized,
        );
        recallChunks.value = chunks.map(chunk => ({
            ...chunk,
            score: Number(chunk?.score || 0).toFixed(2),
            tags: Array.isArray(chunk?.tags) ? chunk.tags : [],
        }));
        testRecords.value.unshift({
            id: `record-${Date.now()}`,
            source: 'Hybrid Search',
            text: query,
            date: `${yyyy}-${mm}-${dd}`,
            time: `${hh}:${min}`,
        });
    } catch (error) {
        await alert({
            title: '召回测试失败',
            message: error?.message || '混合检索执行失败，请稍后重试。',
        });
    } finally {
        running.value = false;
    }
}
</script>

<template>
    <PageLayout
        data-component="AdminKnowledgeRecallTestPanel"
        class="admin-page admin-page--knowledge-recall-test"
        body-class="bg-slate-50/50"
    >
        <template #left>
            <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-50"
                @click="emit('back')"
            >
                <span class="material-symbols-outlined">arrow_back</span>
            </button>
        </template>

        <template #center>
            <div class="text-center">
                <h2 class="text-lg font-semibold text-slate-800">召回测试</h2>
                <p class="text-xs text-slate-400">根据给定的查询文本测试知识的召回效果</p>
                <p class="text-xs text-slate-400">知识库：{{ props.knowledge?.name || '-' }}</p>
            </div>
        </template>

        <div class="min-h-0 flex-1 p-6">
            <div class="flex h-full min-h-0 flex-col gap-6 xl:flex-row">
                <div class="custom-scrollbar min-h-0 flex-1 space-y-6 overflow-y-auto">
                    <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="mb-4 flex items-center justify-between">
                            <div class="flex items-center gap-2">
                                <div class="h-4 w-1 rounded-full bg-primary"></div>
                                <h3 class="font-bold text-slate-800">源文本</h3>
                            </div>
                            <div class="flex items-center gap-1 text-xs text-slate-400">
                                <span class="material-symbols-outlined text-sm">search</span>
                                <span>向量检索</span>
                            </div>
                        </div>

                        <div class="relative">
                            <textarea
                                v-model="sourceText"
                                class="h-40 w-full resize-none rounded-xl border border-slate-100 bg-slate-50 p-4 pr-24 text-sm leading-relaxed text-slate-700 outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
                                placeholder="请输入查询文本..."
                                :maxlength="maxChars"
                            />
                            <div class="absolute bottom-4 left-4 text-xs text-slate-400">
                                {{ charCount }} / {{ maxChars }}
                            </div>
                            <button
                                type="button"
                                class="absolute bottom-4 right-4 rounded-lg bg-primary px-6 py-2 text-sm font-medium text-white shadow-md shadow-blue-500/20 transition-colors hover:bg-blue-700"
                                :disabled="running"
                                @click="runRecallTest"
                            >
                                {{ running ? '测试中...' : '测试' }}
                            </button>
                        </div>
                    </div>

                    <div class="space-y-4">
                        <h3 class="px-1 font-bold text-slate-800">记录</h3>
                        <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                            <table class="w-full text-left text-sm">
                                <thead class="border-b border-slate-100 bg-slate-50/50">
                                    <tr>
                                        <th class="px-6 py-4 font-semibold text-slate-500">检索源</th>
                                        <th class="px-6 py-4 font-semibold text-slate-500">文本</th>
                                        <th class="px-6 py-4 font-semibold text-slate-500">时间</th>
                                    </tr>
                                </thead>
                                <tbody class="divide-y divide-slate-100">
                                    <tr v-for="record in testRecords" :key="record.id">
                                        <td class="px-6 py-4">
                                            <div class="flex items-center gap-2">
                                                <div class="flex h-6 w-6 items-center justify-center rounded bg-blue-50 text-primary">
                                                    <span class="material-symbols-outlined text-sm">manage_search</span>
                                                </div>
                                                <span class="font-medium text-slate-700">{{ record.source }}</span>
                                            </div>
                                        </td>
                                        <td class="max-w-[280px] truncate px-6 py-4 text-slate-600">
                                            {{ record.text }}
                                        </td>
                                        <td class="px-6 py-4 text-xs leading-tight text-slate-400">
                                            {{ record.date }}<br />{{ record.time }}
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="custom-scrollbar min-h-0 w-full space-y-4 overflow-y-auto xl:w-[450px]">
                    <div class="flex items-center justify-between px-1">
                        <h3 class="text-lg font-bold text-slate-800">{{ recallChunks.length }} 个召回段落</h3>
                        <button type="button" class="text-slate-400 transition hover:text-slate-600">
                            <span class="material-symbols-outlined">tune</span>
                        </button>
                    </div>

                    <div
                        v-if="!recallChunks.length"
                        class="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500"
                    >
                        输入查询文本并点击测试后，这里会展示混合检索结果。
                    </div>

                    <article
                        v-for="chunk in recallChunks"
                        :key="chunk.id"
                        class="relative rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                    >
                        <div class="absolute right-0 top-0 rounded-bl-xl rounded-tr-2xl bg-blue-50 px-3 py-1 text-[10px] font-bold tracking-wider text-primary">
                            SCORE {{ chunk.score }}
                        </div>
                        <div class="mb-3 flex items-center gap-2 text-xs text-slate-400">
                            <span class="material-symbols-outlined text-sm text-primary">grid_view</span>
                            <span class="font-medium">{{ chunk.chunkLabel }}</span>
                        </div>
                        <p class="mb-4 text-sm leading-relaxed text-slate-600">
                            {{ chunk.content }}
                        </p>
                        <div class="mb-4 flex flex-wrap gap-2">
                            <span
                                v-for="tag in chunk.tags"
                                :key="`${chunk.id}-${tag}`"
                                class="rounded bg-slate-50 px-2 py-0.5 text-[10px] font-medium text-slate-500"
                            >
                                {{ tag }}
                            </span>
                        </div>
                        <div class="flex items-center justify-between border-t border-slate-100 pt-3">
                            <div class="flex items-center gap-2">
                                <span class="material-symbols-outlined text-sm text-red-400">picture_as_pdf</span>
                                <span class="w-48 truncate text-[11px] text-slate-400">{{ chunk.fileName }}</span>
                            </div>
                            <button type="button" class="flex items-center gap-1 text-[11px] font-bold text-primary">
                                打开
                                <span class="material-symbols-outlined text-sm">open_in_new</span>
                            </button>
                        </div>
                    </article>
                </div>
            </div>
        </div>
    </PageLayout>
</template>
