<template>
    <div class="agent-chat-workspace flex flex-col h-full">
        <!-- Assistant Card Wrapper -->
        <div class="flex-1 min-h-0 flex flex-col">
            <!-- Custom Chat Header -->
            <div
                class="chat-header flex w-full shrink-0 items-center justify-between px-2 pb-3 pt-4 sm:px-3 lg:px-4"
            >
                <div class="flex items-center gap-4">
                    <div
                        class="agent-avatar flex h-10 w-10 items-center justify-center rounded-full text-strong"
                    >
                        <img
                            v-if="isImageAgentIcon"
                            :src="agentIcon"
                            alt="assistant avatar"
                            class="h-full w-full rounded-full object-cover"
                        />
                        <span
                            v-else-if="isMaterialAgentIcon"
                            class="material-symbols-outlined text-xl"
                        >
                            {{ agentIcon }}
                        </span>
                        <span v-else class="text-xl leading-none">{{ agentIcon }}</span>
                    </div>
                    <div>
                        <h2 class="text-base leading-tight font-semibold text-strong">
                            {{ agentName }}
                        </h2>
                        <div class="flex items-center gap-1.5 mt-0.5">
                            <span
                                class="status-dot w-2 h-2 rounded-full transition-colors duration-300"
                                :class="isOnline ? 'online' : 'offline'"
                            ></span>
                            <span
                                class="text-xs transition-colors duration-300"
                                :class="isOnline ? 'text-emerald-600' : 'text-slate-400'"
                            >
                                {{ isOnline ? '在线' : '加载中...' }}
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            <AgentChatConversationLayout
                ref="chatWorkspaceRef"
                class="flex-1 min-h-0"
                :adapter="adapter"
                :show-header="false"
                :show-sidebar="showSidebar"
                :enable-attachments="true"
                :enable-skill-mention="true"
                :assistant-icon="agentIcon"
                :empty-title="computedEmptyTitle"
                :empty-description="computedEmptyDescription"
                :empty-icon="computedEmptyIcon"
                :draft-placeholder="computedDraftPlaceholder"
                :session-storage-key="sessionStorageKey"
                :request-options="requestOptions"
                :active-skill-context="activeSkillContext"
                :reset-on-mount="true"
                :welcome-actions="welcomeActions"
                preview-mode="external"
                @unauthorized="$emit('unauthorized')"
                @conversation-context-change="$emit('conversation-context-change', $event)"
                @reset-chat-context="$emit('reset-chat-context')"
                @skill-context-change="$emit('skill-context-change', $event)"
                @request-finished="$emit('request-finished', $event)"
                @runtime-phase-change="$emit('runtime-phase-change', $event)"
                @render-payload="$emit('render-payload', $event)"
                @artifact-preview="$emit('artifact-preview', $event)"
                @welcome-action="handleWelcomeAction"
            />
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, ref, inject, watch } from 'vue';
import { isImageSource, isMaterialSymbolName } from '@/utils/iconDisplay';
import { generalChatAdapter } from '@/views/front/components/front-chat/adapters/generalChatAdapter';
import AgentChatConversationLayout from './AgentChatConversationLayout.vue';
import {
    agentConfigState,
    ensureAgentConfigLoaded,
    getAgentDisplayName,
    getAgentIcon,
} from '@/composables/useAgentConfig';

const emit = defineEmits([
    'unauthorized',
    'conversation-context-change',
    'reset-chat-context',
    'skill-context-change',
    'request-finished',
    'runtime-phase-change',
    'render-payload',
    'artifact-preview',
    'welcome-action',
]);

// inject: 监听 Header 点击技能的事件
const pendingSkillSelect = inject('pendingSkillSelect', ref(null));

// 子组件引用
const chatWorkspaceRef = ref(null);

const props = defineProps({
    showSidebar: {
        type: Boolean,
        default: true,
    },
    emptyTitle: {
        type: String,
        default: '',
    },
    emptyDescription: {
        type: String,
        default: '',
    },
    emptyIcon: {
        type: String,
        default: '',
    },
    draftPlaceholder: {
        type: String,
        default: '',
    },
    sessionStorageKey: {
        type: String,
        default: 'agent-chat-session',
    },
    welcomeActions: {
        type: Array,
        default: () => [],
    },
    adapter: {
        type: Object,
        default: () => generalChatAdapter,
    },
    requestOptions: {
        type: Object,
        default: () => ({}),
    },
    activeSkillContext: {
        type: Object,
        default: null,
    },
});

// 加载 Agent 配置
onMounted(async () => {
    await ensureAgentConfigLoaded({
        onUnauthorized: () => emit('unauthorized'),
    });
});

function selectSkillMention(skill) {
    if (!skill?.id) {
        return;
    }
    if (chatWorkspaceRef.value && typeof chatWorkspaceRef.value.selectSkillMention === 'function') {
        chatWorkspaceRef.value.selectSkillMention(skill);
    }
}

function appendInsightMessage(payload) {
    if (!payload?.skill?.id) {
        return;
    }
    if (
        chatWorkspaceRef.value &&
        typeof chatWorkspaceRef.value.appendInsightMessage === 'function'
    ) {
        chatWorkspaceRef.value.appendInsightMessage(payload);
        return;
    }
    selectSkillMention(payload.skill);
}

function handleWelcomeAction(action) {
    emit('welcome-action', action);
}

// 监听 Header 点击技能事件
watch(pendingSkillSelect, skill => {
    if (!skill?.id) {
        return;
    }
    selectSkillMention(skill);
    // 清空待选状态
    pendingSkillSelect.value = null;
});

// 从配置获取动态值
const agentName = computed(() => getAgentDisplayName());
const agentIcon = computed(() => {
    const icon = String(getAgentIcon() || '').trim();
    if (!icon || icon === 'smart_toy') {
        return '🤖';
    }
    return icon;
});
const isImageAgentIcon = computed(() => isImageSource(agentIcon.value));
const isMaterialAgentIcon = computed(() => isMaterialSymbolName(agentIcon.value));
const isOnline = computed(() => agentConfigState.initialized && !agentConfigState.loading);

// 空状态提示：优先使用 props，否则从 agentConfigState.template 获取
const computedEmptyTitle = computed(() => {
    if (props.emptyTitle) return props.emptyTitle;
    const template = agentConfigState.template;
    if (template && (template.displayName || template.agentName)) {
        return template.displayName || template.agentName;
    }
    return '开始对话';
});

const computedEmptyDescription = computed(() => {
    if (props.emptyDescription) return props.emptyDescription;
    const template = agentConfigState.template;
    if (template && template.description) return template.description;
    return '请输入您的问题或指令，开始与 AI 助手对话。';
});

const computedEmptyIcon = computed(() => {
    if (props.emptyIcon) return props.emptyIcon;
    const template = agentConfigState.template;
    if (template && (template.avatarUrl || template.icon)) {
        return template.avatarUrl || template.icon;
    }
    return '🤖';
});

const computedDraftPlaceholder = computed(() => {
    if (props.draftPlaceholder) return props.draftPlaceholder;
    return '请输入您的问题或指令...';
});

// 获取当前会话的 sessionId
const sessionId = computed(() => {
    // AgentChatConversationLayout extends FrontChatWorkspace (Options API)
    // 需要通过组件实例访问 sessionId
    const workspace = chatWorkspaceRef.value;
    console.log('[FrontAgentChatWorkspace] sessionId computed:', {
        hasWorkspace: !!workspace,
        workspaceKeys: workspace ? Object.keys(workspace) : [],
        sessionId: workspace?.sessionId,
    });
    if (!workspace) return '';
    // Options API 组件的 data 属性可以直接访问
    return workspace.sessionId || '';
});

// 提供一个方法让父组件可以获取 sessionId
function getSessionId() {
    const id = chatWorkspaceRef.value?.sessionId || '';
    console.log('[FrontAgentChatWorkspace] getSessionId:', id);
    return id;
}

defineExpose({
    appendInsightMessage,
    selectSkillMention,
    chatWorkspaceRef,
    sessionId,
    getSessionId,
});
</script>

<style scoped>
.agent-chat-workspace {
    background: transparent;
}

.chat-header {
    background: transparent;
    border-bottom: none;
}

.agent-avatar {
    background: #f0f4f9;
    box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.9);
}

.status-dot.online {
    background: #10b981;
    animation: pulse 2s ease-in-out infinite;
}

.status-dot.offline {
    background: #cbd5e1;
}

@keyframes pulse {
    0%,
    100% {
        opacity: 1;
    }
    50% {
        opacity: 0.6;
    }
}
</style>
