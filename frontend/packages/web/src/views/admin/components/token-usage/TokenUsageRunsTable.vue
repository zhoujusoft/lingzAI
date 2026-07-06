<script setup>
import MiniPagination from '@/components/MiniPagination.vue';

const props = defineProps({
    title: {
        type: String,
        default: '运行明细',
    },
    subtitle: {
        type: String,
        default: '',
    },
    runs: {
        type: Array,
        default: () => [],
    },
    loading: {
        type: Boolean,
        default: false,
    },
    total: {
        type: Number,
        default: 0,
    },
    page: {
        type: Number,
        default: 1,
    },
    pageSize: {
        type: Number,
        default: 10,
    },
    pageSizeOptions: {
        type: Array,
        default: () => [10, 20, 50],
    },
    emptyText: {
        type: String,
        default: '暂无数据',
    },
});

const emit = defineEmits(['page-change', 'page-size-change']);

function formatNumber(value) {
    return new Intl.NumberFormat('zh-CN').format(Number(value) || 0);
}

function formatDuration(ms) {
    const numeric = Number(ms) || 0;
    if (numeric >= 60000) {
        return `${(numeric / 60000).toFixed(1)} min`;
    }
    if (numeric >= 1000) {
        return `${(numeric / 1000).toFixed(1)} s`;
    }
    return `${numeric} ms`;
}

function formatCompact(value) {
    const numeric = Number(value) || 0;
    if (numeric >= 1000000) {
        return `${(numeric / 1000000).toFixed(numeric >= 10000000 ? 0 : 1)}M`;
    }
    if (numeric >= 10000) {
        return `${(numeric / 10000).toFixed(numeric >= 100000 ? 0 : 1)}万`;
    }
    if (numeric >= 1000) {
        return `${(numeric / 1000).toFixed(numeric >= 10000 ? 0 : 1)}k`;
    }
    return new Intl.NumberFormat('zh-CN').format(numeric);
}

function statusClass(status) {
    if (status === 'COMPLETED') {
        return 'bg-emerald-100 text-emerald-700';
    }
    if (status === 'FAILED') {
        return 'bg-red-100 text-red-700';
    }
    if (status === 'CANCELLED') {
        return 'bg-amber-100 text-amber-700';
    }
    return 'bg-slate-100 text-slate-500';
}
</script>

<template>
    <section class="rounded-xl border border-slate-200 bg-white px-4 py-4">
        <div class="flex items-center justify-between gap-3">
            <div>
                <h3 class="text-base font-semibold text-slate-800">{{ title }}</h3>
                <p v-if="subtitle" class="mt-1 text-sm text-slate-500">{{ subtitle }}</p>
            </div>
            <span class="text-sm text-slate-400">共 {{ formatCompact(total) }} 条</span>
        </div>
        <div class="mt-4 overflow-hidden rounded-lg border border-slate-200">
            <div class="custom-scrollbar overflow-x-auto">
                <table class="min-w-[1160px] w-full border-collapse">
                    <thead>
                        <tr
                            class="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500"
                        >
                            <th class="px-3 py-2.5 font-medium">运行时间</th>
                            <th class="px-3 py-2.5 font-medium">运行来源</th>
                            <th class="px-3 py-2.5 font-medium">Agent / 用户</th>
                            <th class="px-3 py-2.5 font-medium">模型</th>
                            <th class="px-3 py-2.5 font-medium">总 Token</th>
                            <th class="px-3 py-2.5 font-medium">输入 / 输出</th>
                            <th class="px-3 py-2.5 font-medium">调用</th>
                            <th class="px-3 py-2.5 font-medium">耗时</th>
                            <th class="px-3 py-2.5 font-medium">状态</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-if="loading && !runs.length">
                            <td colspan="9" class="px-3 py-8 text-center text-sm text-slate-400">
                                加载中...
                            </td>
                        </tr>
                        <tr
                            v-for="item in runs"
                            :key="item.assistantMessageId"
                            class="border-t border-slate-100 align-top transition hover:bg-slate-50"
                        >
                            <td class="px-3 py-3 text-sm text-slate-600">
                                <p class="font-medium text-slate-700">
                                    {{ item.startedAt || '--' }}
                                </p>
                                <p class="mt-0.5 text-xs text-slate-400">
                                    {{ item.completedAt || '--' }}
                                </p>
                            </td>
                            <td class="px-3 py-3">
                                <span
                                    class="inline-flex rounded-full bg-cyan-50 px-2 py-0.5 text-xs font-medium text-cyan-700"
                                >
                                    {{ item.runSceneLabel || '未知来源' }}
                                </span>
                                <p class="mt-1 text-xs text-slate-400">
                                    {{ item.sessionTypeLabel || '--' }}
                                </p>
                            </td>
                            <td class="px-3 py-3">
                                <p class="text-sm font-medium text-slate-700">
                                    {{ item.agentName || item.runtimeSkillName || '--' }}
                                </p>
                                <p class="mt-0.5 text-sm text-slate-500">
                                    {{ item.userName || '--' }}
                                </p>
                            </td>
                            <td class="px-3 py-3 text-sm text-slate-600">
                                <p class="font-medium text-slate-700">
                                    {{ item.modelName || '--' }}
                                </p>
                                <p class="mt-0.5 text-xs text-slate-400">
                                    {{ item.modelProvider || '--' }}
                                </p>
                            </td>
                            <td class="px-3 py-3">
                                <p class="font-mono text-sm font-semibold text-teal-600">
                                    {{ formatNumber(item.totalTokens) }}
                                </p>
                            </td>
                            <td class="px-3 py-3 text-sm text-slate-600">
                                <p>入 {{ formatNumber(item.promptTokens) }}</p>
                                <p class="mt-0.5">出 {{ formatNumber(item.completionTokens) }}</p>
                            </td>
                            <td class="px-3 py-3 text-sm text-slate-600">
                                <p>{{ item.llmCallCount }} 次模型</p>
                                <p class="mt-0.5">{{ item.toolCallCount }} 次工具</p>
                            </td>
                            <td class="px-3 py-3 text-sm text-slate-600">
                                {{ formatDuration(item.durationMs) }}
                            </td>
                            <td class="px-3 py-3">
                                <span
                                    class="rounded-full px-2 py-0.5 text-xs font-medium"
                                    :class="statusClass(item.runStatus)"
                                >
                                    {{ item.runStatus || 'UNKNOWN' }}
                                </span>
                            </td>
                        </tr>
                        <tr v-if="!loading && !runs.length">
                            <td colspan="9" class="px-3 py-8 text-center text-sm text-slate-400">
                                {{ emptyText }}
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="mt-3 flex justify-end">
            <MiniPagination
                :page="page"
                :page-size="pageSize"
                :total="total"
                :page-size-options="pageSizeOptions"
                @page-change="emit('page-change', $event)"
                @page-size-change="emit('page-size-change', $event)"
            />
        </div>
    </section>
</template>
