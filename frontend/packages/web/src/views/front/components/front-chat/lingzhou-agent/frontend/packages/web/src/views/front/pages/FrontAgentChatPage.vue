<template>
    <div class="flex h-screen w-screen flex-col overflow-hidden bg-[#f5f7fb]">
        <!-- Top Header Toolbar -->
        <FrontAgentChatHeader />

        <!-- Main Body -->
        <div class="flex flex-1 overflow-hidden p-[18px] gap-[18px]">
            <!-- Chat Workspace - 左侧独立卡片 -->
            <div class="chat-workspace-card" :class="{ 'is-collapsed': showPreview }">
                <FrontAgentChatWorkspace
                    @unauthorized="handleUnauthorized"
                    @request-finished="handleRequestFinished"
                />
            </div>

            <!-- Preview Panel - 右侧独立卡片 -->
            <Transition name="preview-slide">
                <FrontAgentChatPreview
                    v-if="showPreview"
                    class="preview-panel-card"
                    :customer="previewData.customer"
                    :summary="previewData.summary"
                    :opportunities="previewData.opportunities"
                    :contacts="previewData.contacts"
                    @close="showPreview = false"
                />
            </Transition>
        </div>
    </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import FrontAgentChatHeader from '../components/agent-chat/FrontAgentChatHeader.vue';
import FrontAgentChatWorkspace from '../components/agent-chat/FrontAgentChatWorkspace.vue';
import FrontAgentChatPreview from '../components/agent-chat/FrontAgentChatPreview.vue';

const emit = defineEmits(['unauthorized']);

// 预览面板状态
const showPreview = ref(false);

// 预览数据 - 待对接真实业务 API
const previewData = reactive({
    customer: null,
    summary: '',
    opportunities: [],
    contacts: [],
});

function handleUnauthorized() {
    emit('unauthorized');
}

function handleRequestFinished(event) {
    // TODO: 根据对话事件触发业务数据加载
    console.log('Request finished:', event);
}
</script>

<style scoped>
/* Chat workspace card - 独立卡片样式 */
.chat-workspace-card {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    max-width: 800px;
    margin: 0 auto;
    height: 100%;
    background: #ffffff;
    border-radius: 24px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
    overflow: hidden;
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 当预览显示时，聊天区域收缩到固定宽度并取消居中（靠左） */
.chat-workspace-card.is-collapsed {
    flex: 0 0 430px;
    max-width: 430px;
    margin: 0;
}

/* Preview panel card - 独立卡片样式 */
.preview-panel-card {
    flex: 1;
    min-width: 0;
    height: 100%;
    background: #ffffff;
    border-radius: 24px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
    overflow: hidden;
}

/* Preview slide animation */
.preview-slide-enter-active,
.preview-slide-leave-active {
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.preview-slide-enter-from,
.preview-slide-leave-to {
    opacity: 0;
    transform: translateX(20px);
    flex: 0 0 0px;
    margin-left: -18px;
}
</style>
