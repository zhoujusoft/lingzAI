<template>
    <section class="flex h-full min-h-0 flex-col gap-2 text-strong">
        <div class="shrink-0 px-1">
            <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2">
                    <button
                        v-for="tab in tabs"
                        :key="tab.key"
                        type="button"
                        :class="[
                            'rounded-lg px-3 py-1.5 text-sm font-medium transition-all',
                            activeTab === tab.key
                                ? 'bg-accent-soft text-primary'
                                : 'text-body hover:text-strong',
                        ]"
                        @click="selectTab(tab.key)"
                    >
                        {{ tab.label }}
                    </button>
                </div>

                <div class="flex items-center gap-2 text-xs text-muted">
                    <span
                        >{{ formatStorage(currentFileStats.totalSizeBytes) }} /
                        {{ formatStorage(STORAGE_LIMIT_BYTES) }}</span
                    >
                    <div class="h-1.5 w-20 overflow-hidden rounded-full bg-border-soft/70">
                        <div
                            class="h-full rounded-full bg-gradient-to-r from-sky-500 to-emerald-400"
                            :style="{ width: `${usagePercent}%` }"
                        ></div>
                    </div>
                    <span>{{ usagePercent.toFixed(0) }}%</span>
                </div>
            </div>
        </div>

        <div :class="panelViewportClass">
            <FrontFileAssetsPanel
                v-show="activeTab === 'files'"
                @stats-change="handleFileStatsChange"
            />
            <FrontAgentMemoryPanel v-show="activeTab === 'memory'" embedded />
        </div>
    </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import FrontAgentMemoryPanel from '@/views/front/components/FrontAgentMemoryPanel.vue';
import FrontFileAssetsPanel from '@/views/front/components/FrontFileAssetsPanel.vue';

const route = useRoute();
const router = useRouter();
const STORAGE_LIMIT_BYTES = 8 * 1024 * 1024 * 1024;

const tabs = Object.freeze([
    {
        key: 'files',
        label: '文件夹',
        description: '统一管理上传文件、运行产物和临时目录，按虚拟文件夹结构集中查看。',
    },
    {
        key: 'memory',
        label: '记忆',
        description: '维护用户长期生效的 PROFILE.md、SOUL.md 和个人技能绑定。',
    },
]);
const currentFileStats = ref({
    totalCount: 0,
    totalSizeBytes: 0,
});

const activeTab = computed(() => {
    const current = String(route.query.tab || '')
        .trim()
        .toLowerCase();
    return tabs.some(tab => tab.key === current) ? current : 'files';
});
const activeTabMeta = computed(() => tabs.find(tab => tab.key === activeTab.value) || tabs[0]);
const panelViewportClass = computed(() =>
    activeTab.value === 'memory'
        ? 'min-h-0 flex-1 overflow-y-auto'
        : 'min-h-0 flex-1 overflow-hidden'
);
const usagePercent = computed(() => {
    if (STORAGE_LIMIT_BYTES <= 0) {
        return 0;
    }
    return Math.min(100, (currentFileStats.value.totalSizeBytes / STORAGE_LIMIT_BYTES) * 100);
});

function selectTab(tabKey) {
    if (!tabs.some(tab => tab.key === tabKey) || activeTab.value === tabKey) {
        return;
    }
    router.replace({
        query: {
            ...route.query,
            tab: tabKey,
        },
    });
}

function handleFileStatsChange(stats) {
    currentFileStats.value = {
        totalCount: Number(stats?.totalCount || 0),
        totalSizeBytes: Number(stats?.totalSizeBytes || 0),
    };
}

function formatStorage(value) {
    const size = Number(value || 0);
    if (!Number.isFinite(size) || size <= 0) {
        return '0 B';
    }
    if (size < 1024) {
        return `${size} B`;
    }
    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} KB`;
    }
    if (size < 1024 * 1024 * 1024) {
        return `${(size / (1024 * 1024)).toFixed(1)} MB`;
    }
    return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}
</script>
