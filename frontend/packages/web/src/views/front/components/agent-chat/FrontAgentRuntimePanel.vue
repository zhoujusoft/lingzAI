<template>
    <div
        class="w-[min(560px,calc(100vw-2rem))] overflow-hidden rounded-[22px] border border-slate-200/90 bg-white/94 p-2.5 shadow-[0_18px_60px_rgba(15,23,42,0.14)] backdrop-blur"
    >
        <div class="flex flex-wrap items-center gap-2 border-b border-slate-200/80 pb-2.5">
            <div
                class="inline-flex items-center gap-2 rounded-full bg-slate-900 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-white"
            >
                <span class="h-2 w-2 rounded-full bg-emerald-400"></span>
                <span>Runtime V2</span>
            </div>
            <div
                v-for="item in runtimeMetricItems"
                :key="item.key"
                class="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2 py-1 text-[10px] font-medium text-slate-700"
            >
                <span class="text-slate-400">{{ item.label }}</span>
                <span class="text-slate-800">{{ item.value }}</span>
            </div>
            <div
                class="ml-auto inline-flex max-w-full items-center gap-2 rounded-full px-2.5 py-1 text-[10px] font-semibold"
                :class="runtimeStatusToneClass"
            >
                <span class="h-2 w-2 rounded-full" :class="runtimeStatusDotClass"></span>
                <span class="truncate">{{ runtimeHeadline }}</span>
            </div>
        </div>

        <div
            v-if="runtimeGraphVisible"
            class="mt-2.5 overflow-hidden rounded-2xl border border-sky-200 bg-[linear-gradient(180deg,rgba(248,250,252,0.96),rgba(239,246,255,0.96))] shadow-sm"
        >
            <button
                type="button"
                class="flex w-full items-start justify-between gap-3 px-4 py-3 text-left"
                @click="runtimeGraphOpen = !runtimeGraphOpen"
            >
                <div class="min-w-0">
                    <div
                        class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-sky-600"
                    >
                        <span class="material-symbols-outlined text-base">account_tree</span>
                        <span>{{ runtimeGraphEngineLabel }}</span>
                    </div>
                    <div class="mt-1 text-sm font-semibold text-slate-800">
                        {{ runtimeGraphHeadline }}
                    </div>
                    <div class="mt-1 text-xs text-slate-500">
                        {{ runtimeGraphPhaseLabel }}
                    </div>
                </div>
                <div class="flex items-center gap-2">
                    <span
                        class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                        :class="runtimeGraphStatusClass"
                    >
                        {{ runtimeGraphStatusLabel }}
                    </span>
                    <span
                        class="material-symbols-outlined text-slate-400 transition-transform"
                        :class="{ 'rotate-90': runtimeGraphOpen }"
                        >chevron_right</span
                    >
                </div>
            </button>
            <div
                class="grid overflow-hidden transition-all duration-200 ease-out"
                :class="
                    runtimeGraphOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                "
            >
                <div class="overflow-hidden border-t border-sky-100 px-4 py-3">
                    <div class="flex flex-wrap gap-2">
                        <span
                            v-for="item in runtimeGraphMetricItems"
                            :key="item.key"
                            class="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-slate-700 shadow-sm"
                        >
                            <span class="text-slate-400">{{ item.label }}</span>
                            <span class="ml-1 text-slate-800">{{ item.value }}</span>
                        </span>
                    </div>
                    <div v-if="runtimeGraphHistoryItems.length" class="mt-3">
                        <div
                            class="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                        >
                            节点轨迹
                        </div>
                        <div class="flex flex-wrap gap-2">
                            <span
                                v-for="item in runtimeGraphHistoryItems"
                                :key="item.key"
                                class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-medium text-slate-700"
                            >
                                {{ item.node }}
                                <span v-if="item.phase" class="ml-1 text-slate-400">
                                    {{ item.phase }}
                                </span>
                            </span>
                        </div>
                    </div>
                    <div v-if="runtimeGraphDecisionText" class="mt-3">
                        <div
                            class="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                        >
                            最后决策
                        </div>
                        <pre
                            class="overflow-x-auto rounded-xl bg-slate-950 px-3 py-2 text-xs text-slate-100"
                            >{{ runtimeGraphDecisionText }}</pre
                        >
                    </div>
                </div>
            </div>
        </div>

        <div class="mt-2.5 grid gap-2 xl:grid-cols-[220px_minmax(0,1fr)]">
            <div class="rounded-2xl border border-slate-200 bg-slate-50/90 p-2.5">
                <div class="flex items-center justify-between">
                    <div
                        class="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-400"
                    >
                        动态任务
                    </div>
                    <div class="text-[10px] text-slate-400">
                        {{ runtimeTaskItems.length }} steps
                    </div>
                </div>
                <div class="mt-2 max-h-56 space-y-1.5 overflow-y-auto pr-1">
                    <div
                        v-for="(item, index) in runtimeTaskItems"
                        :key="item.key"
                        class="rounded-2xl border px-2.5 py-2 transition-colors"
                        :class="taskCardClass(item.status)"
                    >
                        <div class="flex items-start gap-2">
                            <div
                                class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-bold"
                                :class="taskIndexClass(item.status)"
                            >
                                <span
                                    v-if="item.status === 'done'"
                                    class="material-symbols-outlined text-[14px]"
                                    >check</span
                                >
                                <span
                                    v-else-if="item.status === 'running'"
                                    class="material-symbols-outlined text-[14px]"
                                    >progress_activity</span
                                >
                                <span
                                    v-else-if="item.status === 'failed'"
                                    class="material-symbols-outlined text-[14px]"
                                    >close</span
                                >
                                <span v-else>{{ index + 1 }}</span>
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="truncate text-xs font-semibold text-slate-800">
                                    {{ item.title }}
                                </div>
                                <div class="mt-1 line-clamp-2 text-[11px] leading-4 text-slate-500">
                                    {{ item.description }}
                                </div>
                            </div>
                        </div>
                    </div>
                    <div
                        v-if="runtimeTaskItems.length === 0"
                        class="rounded-2xl border border-dashed border-slate-200 px-3 py-4 text-[11px] leading-5 text-slate-500"
                    >
                        等待 runtime phase 事件生成任务规划。
                    </div>
                </div>
            </div>

            <div class="overflow-hidden rounded-2xl border border-slate-900/90 bg-slate-950">
                <div
                    class="flex flex-wrap items-center gap-2 border-b border-slate-800 px-3 py-2.5 text-[10px] text-slate-300"
                >
                    <div class="flex items-center gap-1.5">
                        <span class="h-2.5 w-2.5 rounded-full bg-rose-400"></span>
                        <span class="h-2.5 w-2.5 rounded-full bg-amber-400"></span>
                        <span class="h-2.5 w-2.5 rounded-full bg-emerald-400"></span>
                    </div>
                    <div class="font-mono text-slate-100">{{ runtimeBufferTitle }}</div>
                    <div class="rounded-full bg-slate-800 px-2 py-0.5 font-mono text-slate-300">
                        {{ runtimeBufferMetric }}
                    </div>
                    <div class="ml-auto truncate font-mono text-slate-500">
                        {{ runtimeBufferPath }}
                    </div>
                </div>

                <div class="px-3 pt-2.5">
                    <div class="flex items-center justify-between text-[10px] text-slate-400">
                        <span>Buffer fill</span>
                        <span>{{ runtimeBufferPercentLabel }}</span>
                    </div>
                    <div class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-800">
                        <div
                            class="h-full rounded-full bg-gradient-to-r from-sky-400 via-cyan-300 to-emerald-300 transition-[width] duration-300 ease-out"
                            :style="{ width: runtimeBufferWidth }"
                        ></div>
                    </div>
                </div>

                <div class="grid gap-2 px-3 py-3">
                    <div class="rounded-2xl border border-slate-800 bg-slate-950/80 p-2.5">
                        <div
                            class="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500"
                        >
                            Live buffer
                        </div>
                        <div class="space-y-1.5 font-mono text-[11px] leading-5 text-slate-200">
                            <div
                                v-for="(line, index) in runtimePseudoCodeLines"
                                :key="`${runtimeBufferTitle}-${index}`"
                                class="flex gap-2"
                            >
                                <span class="w-5 shrink-0 text-right text-slate-600">{{
                                    String(index + 1).padStart(2, '0')
                                }}</span>
                                <span class="min-w-0 break-all">{{ line }}</span>
                            </div>
                        </div>
                    </div>

                    <div class="rounded-2xl border border-slate-800 bg-slate-900/60 p-2.5">
                        <div
                            class="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500"
                        >
                            最近回放
                        </div>
                        <div class="space-y-1.5">
                            <div
                                v-for="entry in runtimeConsoleEntries"
                                :key="entry.id"
                                class="rounded-xl border border-slate-800 bg-slate-950/70 px-2.5 py-2"
                            >
                                <div class="flex items-center gap-2 text-[10px] text-slate-400">
                                    <span class="truncate font-medium text-slate-200">{{
                                        entry.label
                                    }}</span>
                                    <span v-if="entry.at">{{ entry.at }}</span>
                                </div>
                                <div class="mt-1 line-clamp-2 text-[11px] leading-4 text-slate-300">
                                    {{ entry.message }}
                                </div>
                            </div>
                            <div
                                v-if="runtimeConsoleEntries.length === 0"
                                class="rounded-xl border border-dashed border-slate-800 px-3 py-3 text-[11px] leading-5 text-slate-500"
                            >
                                等待阶段反馈进入缓冲区。
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

const PLAN_BUFFER_LINES = [
    '{',
    '  "task": "dynamic-runtime-plan",',
    '  "output": { "mime": "text/html", "chartLibrary": "echarts" },',
    '  "strategy": ["读取输入", "汇总数据", "生成 HTML 报告"],',
    '  "constraints": ["JSON-safe", "ECharts only", "结论偏数据总结"]',
    '}',
];

const SCRIPT_BUFFER_LINES = [
    'import json',
    'from pathlib import Path',
    'import pandas as pd',
    'df = pd.read_excel(input_path)',
    'summary = build_summary(df)',
    'chart_payload = to_json_safe(summary)',
    'html = render_html_report(chart_payload)',
    'Path(output_path).write_text(html, encoding="utf-8")',
];

const EXECUTION_BUFFER_LINES = [
    '$ python /workspace/runtime_task.py <input> <output>',
    '[parse] workbook / document structure loaded',
    '[compute] monthly summary generated',
    '[normalize] numpy / timestamp values converted',
    '[render] echarts payload written into html',
    '[done] artifact output ready',
];

const PUBLISH_BUFFER_LINES = [
    '{',
    '  "artifact": "runtime-output",',
    '  "preview": true,',
    '  "status": "published"',
    '}',
];

const props = defineProps({
    payload: {
        type: Object,
        default: null,
    },
});

const activeRequestId = ref('');
const runtimeConsoleEntries = ref([]);
const runtimeObservedTasks = ref([]);
const lastProgressKey = ref('');
const maxReceivedChars = ref(0);
const runtimeGraphOpen = ref(true);

watch(
    () => props.payload,
    payload => {
        if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
            resetRuntimePanelState();
            return;
        }
        const requestKey = resolveRequestKey(payload);
        if (requestKey && requestKey !== activeRequestId.value) {
            resetRuntimePanelState();
            activeRequestId.value = requestKey;
        } else if (!activeRequestId.value) {
            activeRequestId.value = requestKey;
        }

        const receivedChars = extractReceivedChars(payload.progressMessage);
        if (receivedChars > maxReceivedChars.value) {
            maxReceivedChars.value = receivedChars;
        }

        syncObservedTasks(payload);

        const progressKey = [
            requestKey,
            payload.phase,
            payload.subStage,
            payload.progressMessage,
            payload.at,
            payload.llmCallCount,
            payload.toolCallCount,
        ].join('|');
        if (progressKey && progressKey !== lastProgressKey.value) {
            appendConsoleEntry(payload);
            lastProgressKey.value = progressKey;
        }
    },
    { immediate: true }
);

const normalizedPayload = computed(() => {
    if (!props.payload || typeof props.payload !== 'object' || Array.isArray(props.payload)) {
        return null;
    }
    return props.payload;
});

const normalizedGraphPayload = computed(() => {
    const graph = normalizedPayload.value?.graph;
    if (!graph || typeof graph !== 'object' || Array.isArray(graph)) {
        return null;
    }
    return graph;
});

const runtimeGraphVisible = computed(() => Boolean(normalizedGraphPayload.value));
const runtimeGraphEngineLabel = computed(
    () => String(normalizedGraphPayload.value?.engine || '').trim() || 'Runtime Graph'
);
const runtimeGraphHeadline = computed(() => {
    const payload = normalizedGraphPayload.value;
    if (!payload) {
        return '';
    }
    const nodeLabel = runtimeNodeLabel(payload.currentNode);
    if (
        String(payload.status || '')
            .trim()
            .toLowerCase() === 'completed'
    ) {
        return `执行完成 · ${nodeLabel}`;
    }
    return `当前节点 · ${nodeLabel}`;
});
const runtimeGraphPhaseLabel = computed(() =>
    runtimePhaseLabel(normalizedGraphPayload.value?.currentPhase)
);
const runtimeGraphStatusLabel = computed(() =>
    String(normalizedGraphPayload.value?.status || '')
        .trim()
        .toLowerCase() === 'completed'
        ? '已完成'
        : '运行中'
);
const runtimeGraphStatusClass = computed(() =>
    String(normalizedGraphPayload.value?.status || '')
        .trim()
        .toLowerCase() === 'completed'
        ? 'bg-emerald-100 text-emerald-700'
        : 'bg-sky-100 text-sky-700'
);
const runtimeGraphMetricItems = computed(() => {
    const state =
        normalizedGraphPayload.value?.state &&
        typeof normalizedGraphPayload.value.state === 'object'
            ? normalizedGraphPayload.value.state
            : {};
    const items = [];
    if (state.mode) {
        items.push({ key: 'mode', label: '模式', value: runtimeModeLabel(state.mode) });
    }
    if (state.phase) {
        items.push({
            key: 'phase',
            label: '阶段',
            value: runtimePhaseLabel(state.phase),
        });
    }
    if (state.iterationCount != null) {
        items.push({ key: 'iteration', label: '迭代', value: String(state.iterationCount) });
    }
    if (state.llmCallCount != null) {
        items.push({ key: 'llm', label: 'LLM', value: String(state.llmCallCount) });
    }
    if (state.toolCallCount != null) {
        items.push({ key: 'tool', label: 'Tool', value: String(state.toolCallCount) });
    }
    if (state.finishReason) {
        items.push({ key: 'finish', label: '结束', value: String(state.finishReason) });
    }
    return items;
});
const runtimeGraphHistoryItems = computed(() => {
    const history = Array.isArray(normalizedGraphPayload.value?.history)
        ? normalizedGraphPayload.value.history
        : [];
    return history.map(item => ({
        key: `${item.sequence || 0}-${item.node || ''}-${item.status || ''}`,
        node: runtimeNodeLabel(item.node),
        phase: runtimePhaseLabel(item.phase),
    }));
});
const runtimeGraphDecisionText = computed(() => {
    const decision = normalizedGraphPayload.value?.state?.lastDecision;
    if (!decision) {
        return '';
    }
    return typeof decision === 'string' ? decision : JSON.stringify(decision, null, 2);
});

const runtimeTaskItems = computed(() => {
    if (runtimeObservedTasks.value.length > 0) {
        return runtimeObservedTasks.value.slice(-5);
    }
    const payload = normalizedPayload.value;
    if (!payload) {
        return [];
    }
    return [
        {
            key: resolveTaskKey(payload) || 'current',
            title: resolveTaskTitle(payload),
            description: resolveTaskDescription(payload),
            status: resolveTaskStatus(payload),
        },
    ];
});

const runtimeMetricItems = computed(() => {
    const payload = normalizedPayload.value;
    if (!payload) {
        return [];
    }
    const items = [];
    if (payload.mode) {
        items.push({
            key: 'mode',
            label: '模式',
            value: runtimeModeLabel(payload.mode),
        });
    }
    if (payload.phase) {
        items.push({
            key: 'phase',
            label: '阶段',
            value: runtimePhaseLabel(payload.phase),
        });
    }
    if (payload.iterationCount > 0) {
        items.push({
            key: 'iteration',
            label: '迭代',
            value: String(payload.iterationCount),
        });
    }
    items.push({
        key: 'task',
        label: '任务',
        value: String(runtimeTaskItems.value.length),
    });
    items.push({
        key: 'tool',
        label: 'Tool',
        value: String(payload.toolCallCount || 0),
    });
    return items;
});

const runtimeHeadline = computed(() => {
    const payload = normalizedPayload.value;
    if (!payload) {
        return '等待执行';
    }
    if (payload.progressMessage) {
        return normalizeUserFacingRuntimeText(payload.progressMessage, payload.phase);
    }
    if (payload.subStageLabel) {
        return normalizeUserFacingRuntimeText(payload.subStageLabel, payload.phase);
    }
    return runtimePhaseLabel(payload.phase);
});

const runtimeStatusToneClass = computed(() => {
    const phase = normalizeUpper(normalizedPayload.value?.phase);
    if (phase === 'FAILED') {
        return 'bg-rose-50 text-rose-700';
    }
    if (phase === 'COMPLETED') {
        return 'bg-emerald-50 text-emerald-700';
    }
    return 'bg-sky-50 text-sky-700';
});

const runtimeStatusDotClass = computed(() => {
    const phase = normalizeUpper(normalizedPayload.value?.phase);
    if (phase === 'FAILED') {
        return 'bg-rose-500';
    }
    if (phase === 'COMPLETED') {
        return 'bg-emerald-500';
    }
    return 'bg-sky-500 animate-pulse';
});

const runtimeStageBucket = computed(() => resolveStageBucket(normalizedPayload.value));

const runtimeBufferTitle = computed(() => {
    if (runtimeStageBucket.value === 'plan') {
        return 'execution-plan.json';
    }
    if (runtimeStageBucket.value === 'script') {
        return 'runtime_task.py';
    }
    if (runtimeStageBucket.value === 'execute') {
        return 'report-output.html';
    }
    return 'artifact-manifest.json';
});

const runtimeBufferPath = computed(() => {
    if (runtimeStageBucket.value === 'plan') {
        return '/runtime/code-plan/execution-plan.json';
    }
    if (runtimeStageBucket.value === 'script') {
        return '/workspace/runtime_task.py';
    }
    if (runtimeStageBucket.value === 'execute') {
        return '/outputs/report-output.html';
    }
    return 'artifact://runtime/output';
});

const runtimeBufferMetric = computed(() => {
    if (maxReceivedChars.value > 0) {
        return `recv ${maxReceivedChars.value} chars`;
    }
    const phase = normalizeUpper(normalizedPayload.value?.phase);
    if (phase === 'COMPLETED') {
        return 'ready';
    }
    if (phase === 'FAILED') {
        return 'error';
    }
    return 'streaming';
});

const runtimeBufferProgress = computed(() => {
    const payload = normalizedPayload.value;
    const phase = normalizeUpper(payload?.phase);
    if (phase === 'COMPLETED') {
        return 100;
    }
    if (phase === 'FAILED') {
        return Math.max(24, Math.min(92, 28 + runtimeTaskItems.value.length * 10));
    }
    const taskFactor = Math.min(42, runtimeTaskItems.value.length * 8);
    const charFactor = Math.min(28, maxReceivedChars.value / 180);
    const bucketBaseMap = {
        plan: 18,
        script: 38,
        execute: 64,
        publish: 88,
    };
    const base = bucketBaseMap[runtimeStageBucket.value] || 16;
    return Math.max(12, Math.min(98, base + taskFactor + charFactor));
});

const runtimeBufferWidth = computed(() => `${runtimeBufferProgress.value}%`);
const runtimeBufferPercentLabel = computed(
    () => `${Math.round(runtimeBufferProgress.value)}% buffered`
);

const runtimePseudoCodeLines = computed(() => {
    let sourceLines = PLAN_BUFFER_LINES;
    if (runtimeStageBucket.value === 'script') {
        sourceLines = SCRIPT_BUFFER_LINES;
    } else if (runtimeStageBucket.value === 'execute') {
        sourceLines = EXECUTION_BUFFER_LINES;
    } else if (runtimeStageBucket.value === 'publish') {
        sourceLines = PUBLISH_BUFFER_LINES;
    }
    const visibleCount = resolveVisibleLineCount(sourceLines.length, maxReceivedChars.value);
    return sourceLines.slice(0, visibleCount);
});

function resetRuntimePanelState() {
    activeRequestId.value = '';
    runtimeConsoleEntries.value = [];
    runtimeObservedTasks.value = [];
    lastProgressKey.value = '';
    maxReceivedChars.value = 0;
    runtimeGraphOpen.value = true;
}

function syncObservedTasks(payload) {
    const taskKey = resolveTaskKey(payload);
    if (!taskKey) {
        return;
    }
    const nextTask = {
        key: taskKey,
        title: resolveTaskTitle(payload),
        description: resolveTaskDescription(payload),
        status: resolveTaskStatus(payload),
    };
    const nextItems = runtimeObservedTasks.value.map(item =>
        item.status === 'running' ? { ...item, status: 'done' } : item
    );
    const currentIndex = nextItems.findIndex(item => item.key === taskKey);

    if (currentIndex >= 0) {
        nextItems[currentIndex] = {
            ...nextItems[currentIndex],
            ...nextTask,
        };
    } else {
        nextItems.push(nextTask);
    }

    const phase = normalizeUpper(payload.phase);
    if (phase === 'FAILED') {
        const failedIndex = nextItems.findIndex(item => item.key === taskKey);
        if (failedIndex >= 0) {
            nextItems[failedIndex] = {
                ...nextItems[failedIndex],
                status: 'failed',
            };
        }
    }
    if (phase === 'COMPLETED') {
        runtimeObservedTasks.value = nextItems.slice(-6).map(item => ({
            ...item,
            status: 'done',
        }));
        return;
    }

    runtimeObservedTasks.value = nextItems.slice(-6);
}

function appendConsoleEntry(payload) {
    const message =
        String(payload.progressMessage || '').trim() ||
        `阶段切换到${runtimePhaseLabel(payload.phase)}`;
    runtimeConsoleEntries.value = [
        ...runtimeConsoleEntries.value,
        {
            id: [
                resolveRequestKey(payload),
                payload.phase,
                payload.subStage,
                payload.at,
                runtimeConsoleEntries.value.length,
            ].join('-'),
            label: resolveTaskTitle(payload),
            message,
            at: formatEventTime(payload.at),
        },
    ].slice(-4);
}

function resolveTaskKey(payload) {
    const subStage = normalizeUpper(payload?.subStage);
    if (subStage) {
        return `substage:${subStage}`;
    }
    const actionSignature = extractActionSignature(payload?.progressMessage);
    if (actionSignature) {
        return `action:${actionSignature}`;
    }
    const phase = normalizeUpper(payload?.phase);
    return phase ? `phase:${phase}` : '';
}

function resolveTaskTitle(payload) {
    const explicitLabel = String(payload?.subStageLabel || '').trim();
    if (explicitLabel) {
        return explicitLabel;
    }
    const progressTitle = extractActionTitle(payload?.progressMessage);
    if (progressTitle) {
        return progressTitle;
    }
    return runtimePhaseLabel(payload?.phase);
}

function resolveTaskDescription(payload) {
    const progressMessage = String(payload?.progressMessage || '').trim();
    if (progressMessage) {
        return progressMessage;
    }
    const phaseLabel = runtimePhaseLabel(payload?.phase);
    return phaseLabel ? `当前阶段：${phaseLabel}` : '等待更多阶段反馈';
}

function resolveTaskStatus(payload) {
    const phase = normalizeUpper(payload?.phase);
    if (phase === 'FAILED') {
        return 'failed';
    }
    if (phase === 'COMPLETED') {
        return 'done';
    }
    return 'running';
}

function resolveStageBucket(payload) {
    const subStage = normalizeUpper(payload?.subStage);
    const phase = normalizeUpper(payload?.phase);
    const toolCallCount = Number(payload?.toolCallCount || 0);
    if (
        phase === 'COMPLETED' ||
        phase === 'FINALIZING' ||
        String(payload?.finishReason || '').trim()
    ) {
        return 'publish';
    }
    if (toolCallCount >= 3 || phase === 'OBSERVATION') {
        return 'execute';
    }
    if (subStage.startsWith('CODE_SCRIPT') || subStage.startsWith('CODE_FILE_WRITE')) {
        return 'script';
    }
    return 'plan';
}

function extractReceivedChars(message) {
    const normalized = String(message || '').trim();
    const match = normalized.match(/已接收\s*(\d+)\s*字符/);
    return match ? Number(match[1] || 0) : 0;
}

function extractActionSignature(message) {
    const title = extractActionTitle(message);
    return title
        .replace(/\s+/g, '')
        .replace(/[，。、“”""'':：]/g, '')
        .slice(0, 40);
}

function extractActionTitle(message) {
    const normalized = String(message || '').trim();
    if (!normalized) {
        return '';
    }
    const cleaned = normalized.replace(/已接收\s*\d+\s*字符。?/g, '').trim();
    const segments = cleaned
        .split(/[，。]/)
        .map(item => item.trim())
        .filter(Boolean);
    const preferred =
        segments.find(item => item.includes('正在')) ||
        segments.reverse().find(item => item.includes('已')) ||
        '';
    return preferred
        .replace(/^已完成/, '')
        .replace(/^处理方案已确定[,，]?/, '')
        .replace(/^Python 脚本正文已生成[,，]?/, '写入工作区文件')
        .replace(/^正在/, '')
        .replace(/^已/, '')
        .trim();
}

function resolveVisibleLineCount(lineCount, chars) {
    if (lineCount <= 2) {
        return lineCount;
    }
    if (chars <= 0) {
        return Math.min(4, lineCount);
    }
    return Math.max(3, Math.min(lineCount, Math.ceil(chars / 320) + 2));
}

function resolveRequestKey(payload) {
    return String(payload?.requestId || payload?.sessionId || '').trim();
}

function normalizeUpper(value) {
    return String(value || '')
        .trim()
        .toUpperCase();
}

function runtimeModeLabel(mode) {
    const normalized = normalizeUpper(mode);
    if (normalized === 'REACT') {
        return 'ReAct';
    }
    if (normalized === 'DIRECT') {
        return 'Direct';
    }
    if (normalized === 'CODE') {
        return 'Code';
    }
    return normalized || '-';
}

function runtimeNodeLabel(node) {
    const labels = {
        triageNode: '理解请求',
        reasoningNode: '分析问题',
        actionNode: '执行处理',
        observationNode: '整理结果',
        codeEscalationNode: '代码处理',
        finalAnswerNode: '生成答复',
        limitExceededNode: '保护终止',
    };
    const normalized = String(node || '').trim();
    return labels[normalized] || normalized || '-';
}

function runtimePhaseLabel(phase) {
    const phaseLabelMap = {
        TRIAGE: '理解请求',
        REASONING: '分析问题',
        ACTION: '执行处理',
        OBSERVATION: '整理结果',
        FINAL_CANDIDATE: '准备答复',
        COMPLETION_CHECK: '确认完成',
        FINAL_STREAMING: '生成答复',
        FINALIZING: '整理答复',
        COMPLETED: '已完成',
        FAILED: '执行失败',
    };
    const normalized = normalizeUpper(phase);
    return phaseLabelMap[normalized] || normalized || '-';
}

function normalizeUserFacingRuntimeText(text, phase) {
    const normalized = String(text || '').trim();
    if (!normalized) {
        return '';
    }
    const phaseText = normalizeUpper(phase);
    if (phaseText === 'OBSERVATION') {
        return '正在整理结果';
    }
    if (phaseText === 'FINAL_CANDIDATE' || phaseText === 'COMPLETION_CHECK') {
        return '正在准备答复';
    }
    if (phaseText === 'FINALIZING') {
        return '正在整理答复';
    }
    return normalized
        .replace(/观察/gu, '结果')
        .replace(/闭环/gu, '完成情况')
        .replace(/完成条件/gu, '答复准备')
        .replace(/流式输出答复/gu, '生成答复');
}

function formatEventTime(value) {
    const normalized = String(value || '').trim();
    if (!normalized) {
        return '';
    }
    const date = new Date(normalized);
    if (Number.isNaN(date.getTime())) {
        return '';
    }
    return date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
    });
}

function taskCardClass(status) {
    if (status === 'running') {
        return 'border-sky-100 bg-white';
    }
    if (status === 'done') {
        return 'border-emerald-100 bg-emerald-50/70';
    }
    if (status === 'failed') {
        return 'border-rose-100 bg-rose-50/70';
    }
    return 'border-slate-200 bg-white/70';
}

function taskIndexClass(status) {
    if (status === 'running') {
        return 'bg-sky-600 text-white';
    }
    if (status === 'done') {
        return 'bg-emerald-500 text-white';
    }
    if (status === 'failed') {
        return 'bg-rose-500 text-white';
    }
    return 'bg-white text-slate-500 ring-1 ring-slate-200';
}
</script>
