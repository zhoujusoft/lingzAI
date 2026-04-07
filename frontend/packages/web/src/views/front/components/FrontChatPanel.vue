<template>
    <FrontChatWorkspace
        :adapter="activeAdapter"
        :header-title="headerTitle"
        :header-status-text="headerStatusText"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        :draft-placeholder="draftPlaceholder"
        footer-status-text="内容由 AI 生成，请注意甄别"
        footer-state-label="Ready"
        :show-knowledge-select="false"
        :enable-attachments="isSkillMode"
        :default-knowledge="defaultSkillId"
        :session-storage-key="mixedChatSessionStorageKey"
        @unauthorized="$emit('unauthorized')"
        @conversation-context-change="handleConversationContextChange"
        @reset-chat-context="handleResetChatContext"
    />
</template>

<script>
import FrontChatWorkspace from './front-chat/FrontChatWorkspace.vue';
import { generalChatAdapter } from './front-chat/adapters/generalChatAdapter';
import { skillChatAdapter } from './front-chat/adapters/skillChatAdapter';
import { MIXED_CHAT_SESSION_STORAGE_KEY } from '@/model/session';

function normalizeSkillId(value) {
    if (value == null) {
        return '';
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
        return String(value);
    }
    const text = String(value).trim();
    return /^\d+$/.test(text) ? text : '';
}

export default {
    name: 'FrontChatPanel',
    components: {
        FrontChatWorkspace,
    },
    emits: ['unauthorized'],
    props: {
        initialSkill: {
            type: Object,
            default: null,
        },
    },
    data() {
        return {
            generalChatAdapter,
            skillChatAdapter,
            mixedChatSessionStorageKey: MIXED_CHAT_SESSION_STORAGE_KEY,
            currentMode: 'general',
            currentSkillContext: null,
        };
    },
    computed: {
        isSkillMode() {
            return this.currentMode === 'skill' && Boolean(this.defaultSkillId);
        },
        defaultSkillId() {
            const direct = normalizeSkillId(this.currentSkillContext?.id);
            return direct || '';
        },
        activeAdapter() {
            return this.isSkillMode ? this.skillChatAdapter : this.generalChatAdapter;
        },
        headerTitle() {
            if (!this.isSkillMode) {
                return 'AI 聊天助手';
            }
            return this.currentSkillContext?.displayName || this.currentSkillContext?.runtimeSkillName || '技能对话';
        },
        headerStatusText() {
            if (!this.isSkillMode) {
                return '在线可用';
            }
            return this.currentSkillContext?.description || '当前技能上下文已固定';
        },
        emptyTitle() {
            if (!this.isSkillMode) {
                return 'AI 助手对话';
            }
            return this.currentSkillContext?.displayName || '技能对话';
        },
        emptyDescription() {
            return this.isSkillMode
                ? this.currentSkillContext?.description || '围绕当前技能发起任务，系统会保留该技能独立的会话记录。'
                : '支持多轮上下文交流，快速完成问答、写作和内容整理。';
        },
        draftPlaceholder() {
            return this.isSkillMode ? '向当前技能发起任务...' : '输入你的问题...';
        },
    },
    watch: {
        initialSkill: {
            immediate: true,
            handler(skill) {
                this.resetEntryContext(skill);
            },
        },
    },
    methods: {
        normalizeSkillContext(skill) {
            const id = normalizeSkillId(skill?.id ?? skill?.scopeId);
            if (!id) {
                return null;
            }
            return {
                id,
                displayName:
                    skill?.displayName || skill?.scopeDisplayName || skill?.runtimeSkillName || '技能对话',
                description: skill?.description || '',
                runtimeSkillName: skill?.runtimeSkillName || '',
            };
        },
        resetEntryContext(skill) {
            const normalized = this.normalizeSkillContext(skill);
            if (normalized) {
                this.currentMode = 'skill';
                this.currentSkillContext = normalized;
                return;
            }
            this.currentMode = 'general';
            this.currentSkillContext = null;
        },
        handleConversationContextChange(context) {
            if (String(context?.sessionType || '').trim() === 'SKILL_CHAT') {
                const normalized = this.normalizeSkillContext({
                    id: context?.scopeId,
                    scopeDisplayName: context?.scopeDisplayName,
                    displayName: context?.scopeDisplayName,
                });
                if (normalized) {
                    this.currentMode = 'skill';
                    this.currentSkillContext = {
                        ...normalized,
                        description: this.currentSkillContext?.id === normalized.id
                            ? this.currentSkillContext?.description || ''
                            : '',
                    };
                    return;
                }
            }
            this.currentMode = 'general';
            this.currentSkillContext = null;
        },
        handleResetChatContext() {
            this.resetEntryContext(this.initialSkill);
        },
    },
};
</script>
