<template>
    <div class="agent-chat-workspace flex flex-col h-full bg-white p-[22px]">
        <!-- Assistant Card Wrapper -->
        <div class="flex-1 min-h-0 flex flex-col">
            <!-- Custom Chat Header -->
            <div
                class="chat-header flex items-center justify-between py-4 border-b border-slate-100 shrink-0"
            >
                <div class="flex items-center gap-4">
                    <div class="robot-avatar">
                        <span class="material-symbols-outlined text-[24px] text-white">{{
                            agentIcon
                        }}</span>
                    </div>
                    <div>
                        <h2 class="text-lg font-bold text-[#1d2460] leading-tight">
                            {{ agentName }}
                        </h2>
                        <div class="flex items-center gap-1.5 mt-0.5">
                            <span
                                class="w-2 h-2 rounded-full transition-colors duration-300"
                                :class="isOnline ? 'bg-emerald-500 animate-pulse' : 'bg-slate-300'"
                            ></span>
                            <span
                                class="text-xs font-medium transition-colors duration-300"
                                :class="isOnline ? 'text-emerald-600' : 'text-slate-400'"
                            >
                                {{ isOnline ? '在线' : '加载中...' }}
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            <FrontChatWorkspace
                class="flex-1 min-h-0"
                :adapter="generalChatAdapter"
                :show-header="false"
                :show-sidebar="showSidebar"
                :show-sidebar-toggle="true"
                :show-history-sidebar="false"
                :enable-attachments="true"
                :empty-title="computedEmptyTitle"
                :empty-description="computedEmptyDescription"
                :empty-icon="computedEmptyIcon"
                :draft-placeholder="computedDraftPlaceholder"
                :session-storage-key="sessionStorageKey"
                :reset-on-mount="true"
                @unauthorized="$emit('unauthorized')"
                @request-finished="$emit('request-finished', $event)"
            />
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import FrontChatWorkspace from '@/views/front/components/front-chat/FrontChatWorkspace.vue';
import { generalChatAdapter } from '@/views/front/components/front-chat/adapters/generalChatAdapter';
import {
    agentConfigState,
    ensureAgentConfigLoaded,
    getAgentDisplayName,
    getAgentIcon,
} from '@/composables/useAgentConfig';

const emit = defineEmits(['unauthorized', 'request-finished']);

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
});

// 加载 Agent 配置
onMounted(async () => {
    await ensureAgentConfigLoaded({
        onUnauthorized: () => emit('unauthorized'),
    });
});

// 从配置获取动态值
const agentName = computed(() => getAgentDisplayName());
const agentIcon = computed(() => getAgentIcon());
const isOnline = computed(() => agentConfigState.initialized && !agentConfigState.loading);

// 空状态提示：优先使用 props，否则从 agentConfigState.template 获取
const computedEmptyTitle = computed(() => {
    if (props.emptyTitle) return props.emptyTitle;
    const template = agentConfigState.template;
    if (template?.agentName) return template.agentName;
    return '开始对话';
});

const computedEmptyDescription = computed(() => {
    if (props.emptyDescription) return props.emptyDescription;
    const template = agentConfigState.template;
    if (template?.description) return template.description;
    return '请输入您的问题或指令，开始与 AI 助手对话。';
});

const computedEmptyIcon = computed(() => {
    if (props.emptyIcon) return props.emptyIcon;
    const template = agentConfigState.template;
    if (template?.icon) return template.icon;
    return 'smart_toy';
});

const computedDraftPlaceholder = computed(() => {
    if (props.draftPlaceholder) return props.draftPlaceholder;
    return '请输入您的问题或指令...';
});
</script>

<style>
.agent-chat-workspace .robot-icon-brand {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    background: linear-gradient(135deg, #6da8ff, #7b5cff);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #ffffff;
    box-shadow: 0 4px 16px rgba(109, 168, 255, 0.35);
    transition: box-shadow 0.2s ease;
}

.agent-chat-workspace .robot-icon-brand:hover {
    box-shadow: 0 6px 20px rgba(109, 168, 255, 0.45);
}

.agent-chat-workspace .chat-header {
    background: #ffffff;
}

.agent-chat-workspace .robot-avatar {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: linear-gradient(135deg, #6da8ff, #7b5cff);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 2px 8px rgba(109, 168, 255, 0.3);
}

.agent-chat-workspace .max-w-3xl,
.agent-chat-workspace .max-w-4xl {
    max-width: 1000px !important;
}

.agent-chat-workspace .custom-scrollbar {
    padding: 1rem 0 !important;
}

.agent-chat-workspace .rounded-2xl.rounded-tl-none {
    background: #f7f8fc !important;
    border: 1px solid #e8ecf4 !important;
    box-shadow: none !important;
    padding: 1rem 1.25rem !important;
    color: #3c4766 !important;
    font-size: 14px !important;
    line-height: 1.75 !important;
    border-radius: 18px !important;
    border-top-left-radius: 4px !important;
    margin-bottom: 16px !important;
}

.agent-chat-workspace .rounded-2xl.rounded-tr-none {
    background: linear-gradient(135deg, #f1edff, #ede8ff) !important;
    color: #5c42d6 !important;
    box-shadow: none !important;
    padding: 1rem 1.25rem !important;
    font-size: 14px !important;
    line-height: 1.75 !important;
    border-radius: 18px !important;
    border-top-right-radius: 4px !important;
    border: 1px solid #e5e0f7 !important;
    width: fit-content !important;
    margin-left: auto !important;
    margin-bottom: 16px !important;
}

.agent-chat-workspace .bg-blue-600.text-white.rounded-lg {
    display: none !important;
}

.agent-chat-workspace .shrink-0.border-t {
    border-top: none !important;
    background: #ffffff !important;
    padding: 1rem 0 0 0 !important;
}

.agent-chat-workspace form {
    border-radius: 20px !important;
    border: 1px solid #e0e6f0 !important;
    background: #ffffff !important;
    padding: 10px 12px 10px 16px !important;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04) !important;
    transition:
        border-color 0.2s ease,
        box-shadow 0.2s ease !important;
}

.agent-chat-workspace form:focus-within {
    border-color: #c7d2fe !important;
    box-shadow: 0 2px 12px rgba(109, 168, 255, 0.15) !important;
}

.agent-chat-workspace button[type='submit'] {
    background: linear-gradient(135deg, #7c6cff, #5a49ff) !important;
    width: 38px !important;
    height: 38px !important;
    border-radius: 50% !important;
    box-shadow: 0 2px 8px rgba(92, 73, 255, 0.35) !important;
    transition:
        transform 0.2s ease,
        box-shadow 0.2s ease !important;
}

.agent-chat-workspace button[type='submit']:hover {
    transform: scale(1.05) !important;
    box-shadow: 0 4px 12px rgba(92, 73, 255, 0.45) !important;
}

.agent-chat-workspace button[type='submit']:active {
    transform: scale(0.98) !important;
}
</style>
