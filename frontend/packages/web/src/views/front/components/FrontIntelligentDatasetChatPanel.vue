<template>
    <FrontChatWorkspace
        :adapter="intelligentDatasetChatAdapter"
        header-title="智能问数助手"
        header-status-text="已连接当前数据集 Agent"
        empty-title="Agent 智能问数"
        empty-description="会按需理解数据集结构、查看 schema，并可多次执行只读 SQL 完成统计分析。"
        draft-placeholder="输入统计分析问题，例如按月份统计订单趋势..."
        footer-status-text="内容由 AI 生成，请注意甄别"
        footer-state-label="Ready"
        :show-knowledge-select="true"
        :enable-attachments="false"
        :knowledge-options="datasetOptions"
        :default-knowledge="defaultDataset"
        select-label="数据集选择"
        empty-icon="database"
        header-icon="database"
        @unauthorized="$emit('unauthorized')"
    />
</template>

<script>
import { listIntegrationDatasets } from '@/api/integration';
import FrontChatWorkspace from './front-chat/FrontChatWorkspace.vue';
import { intelligentDatasetChatAdapter } from './front-chat/adapters/intelligentDatasetChatAdapter';

function normalizeDatasetId(value) {
    if (value == null) {
        return '';
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return String(value);
    }
    const text = String(value).trim();
    if (!text || !/^\d+$/.test(text)) {
        return '';
    }
    return text;
}

function isPublishedDataset(item) {
    return String(item?.publishStatus || '').trim().toUpperCase() === 'PUBLISHED';
}

function buildDatasetOptions(records = []) {
    const options = [];
    const seen = new Set();
    records.forEach(item => {
        if (!isPublishedDataset(item)) {
            return;
        }
        const value = normalizeDatasetId(item?.id);
        const label = String(item?.name || '').trim();
        if (!value || !label || seen.has(value)) {
            return;
        }
        seen.add(value);
        options.push({ value, label });
    });
    return options;
}

export default {
    name: 'FrontIntelligentDatasetChatPanel',
    components: {
        FrontChatWorkspace,
    },
    emits: ['unauthorized'],
    data() {
        return {
            intelligentDatasetChatAdapter,
            datasetOptions: [],
        };
    },
    computed: {
        defaultDataset() {
            return this.datasetOptions[0]?.value || '';
        },
    },
    mounted() {
        this.loadDatasetOptions();
    },
    methods: {
        async loadDatasetOptions() {
            try {
                const records = await listIntegrationDatasets({}, () => this.$emit('unauthorized'));
                this.datasetOptions = buildDatasetOptions(Array.isArray(records) ? records : []);
            } catch (error) {
                this.datasetOptions = [];
            }
        },
    },
};
</script>
