<template>
    <FrontChatWorkspace
        :adapter="intelligentChatAdapter"
        header-title="知识库助手"
        header-status-text="已关联知识库数据"
        empty-title="知识库智能问答"
        empty-description="基于已接入知识库检索证据，为你提供可溯源的答案。"
        draft-placeholder="基于选定的知识库提问..."
        footer-status-text="内容由 AI 生成，请注意甄别"
        footer-state-label="Ready"
        :show-knowledge-select="true"
        :enable-attachments="false"
        :knowledge-options="knowledgeOptions"
        :default-knowledge="defaultKnowledge"
        @unauthorized="$emit('unauthorized')"
    />
</template>

<script>
import { listKnowledgeBases } from '@/api/knowledge-bases';
import FrontChatWorkspace from './front-chat/FrontChatWorkspace.vue';
import { intelligentChatAdapter } from './front-chat/adapters/intelligentChatAdapter';

function normalizeKnowledgeId(value) {
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

function buildKnowledgeOptions(records = []) {
    const options = [];
    const seen = new Set();
    records.forEach(item => {
        const value = normalizeKnowledgeId(item?.id ?? item?.kbId);
        const label = String(item?.name ?? item?.kbName ?? '').trim();
        if (!value || !label || seen.has(value)) {
            return;
        }
        seen.add(value);
        options.push({ value, label });
    });
    return options;
}

export default {
    name: 'FrontIntelligentChatPanel',
    components: {
        FrontChatWorkspace,
    },
    emits: ['unauthorized'],
    data() {
        return {
            intelligentChatAdapter,
            knowledgeOptions: [],
        };
    },
    computed: {
        defaultKnowledge() {
            return this.knowledgeOptions[0]?.value || '';
        },
    },
    mounted() {
        this.loadKnowledgeOptions();
    },
    methods: {
        async loadKnowledgeOptions() {
            try {
                const data = await listKnowledgeBases({}, () => this.$emit('unauthorized'));
                const records = Array.isArray(data?.records) ? data.records : [];
                this.knowledgeOptions = buildKnowledgeOptions(records);
            } catch (error) {
                this.knowledgeOptions = [];
            }
        },
    },
};
</script>
